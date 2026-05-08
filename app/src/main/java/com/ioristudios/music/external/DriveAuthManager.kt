package com.ioristudios.music.external

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object DriveAuthManager {
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    private val driveScopes = listOf(Scope(DRIVE_APPDATA_SCOPE))

    fun requestAuthorization(
        activity: Activity,
        onReady: (DriveAccess) -> Unit,
        onResolutionRequired: (PendingIntent) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        onResolutionRequired(pendingIntent)
                    } else {
                        onError(IllegalStateException("Drive permission needs confirmation, but no prompt was returned."))
                    }
                } else {
                    result.toDriveAccess()?.let(onReady)
                        ?: onError(IllegalStateException("Drive authorization did not return an access token."))
                }
            }
            .addOnFailureListener(onError)
    }

    fun handleAuthorizationResult(
        activity: Activity,
        data: Intent?,
        onReady: (DriveAccess) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (data == null) {
            onError(IllegalStateException("Drive permission was cancelled."))
            return
        }
        runCatching {
            Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
        }.onSuccess { result ->
            result.toDriveAccess()?.let(onReady)
                ?: onError(IllegalStateException("Drive authorization did not return an access token."))
        }.onFailure(onError)
    }

    suspend fun getAccessForBackground(context: Context): DriveAccess {
        val result = Identity.getAuthorizationClient(context.applicationContext)
            .authorize(authorizationRequest())
            .awaitTask()
        if (result.hasResolution()) {
            throw IllegalStateException("Drive account must be linked again before backup can run.")
        }
        return result.toDriveAccess()
            ?: throw IllegalStateException("Drive authorization did not return an access token.")
    }

    suspend fun revoke(context: Context) {
        Identity.getAuthorizationClient(context.applicationContext)
            .revokeAccess(
                com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder()
                    .setScopes(driveScopes)
                    .build()
            )
            .awaitTask()
    }

    private fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(driveScopes)
            .build()

    private fun AuthorizationResult.toDriveAccess(): DriveAccess? {
        val token = accessToken ?: return null
        val granted = grantedScopes?.map { it.toString() }.orEmpty()
        return DriveAccess(
            accessToken = token,
            accountName = null,
            hasDriveAppDataScope = DRIVE_APPDATA_SCOPE in granted || granted.isEmpty()
        )
    }

    private suspend fun <T> Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value -> continuation.resume(value) }
            addOnFailureListener { error -> continuation.resumeWithException(error) }
            addOnCanceledListener { continuation.cancel() }
        }
}

data class DriveAccess(
    val accessToken: String,
    val accountName: String?,
    val hasDriveAppDataScope: Boolean
)
