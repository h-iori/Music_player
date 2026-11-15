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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object DriveAuthManager {
    const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    private val driveScopes = listOf(Scope(DRIVE_APPDATA_SCOPE))

    fun getCertificateSha1(context: Context): String {
        return try {
            val cert = getSigningCertificateBytes(context) ?: return "2C:42:98:B7:C9:F4:58:16:8E:EE:FE:5B:55:5A:57:93:BB:0A:F2:EB"
            val md = java.security.MessageDigest.getInstance("SHA-1")
            md.digest(cert).joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "2C:42:98:B7:C9:F4:58:16:8E:EE:FE:5B:55:5A:57:93:BB:0A:F2:EB"
        }
    }

    fun getCertificateSha256(context: Context): String {
        return try {
            val cert = getSigningCertificateBytes(context) ?: return "7A:ED:21:78:47:CD:A4:1E:FE:09:D4:E4:8D:FA:2C:46:E7:AF:99:14:14:CF:24:71:89:1E:6E:B0:95:30:28:2E"
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.digest(cert).joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "7A:ED:21:78:47:CD:A4:1E:FE:09:D4:E4:8D:FA:2C:46:E7:AF:99:14:14:CF:24:71:89:1E:6E:B0:95:30:28:2E"
        }
    }

    @Suppress("DEPRECATION")
    private fun getSigningCertificateBytes(context: Context): ByteArray? {
        val pm = context.packageManager
        val pkg = context.packageName
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            val info = pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_SIGNATURES)
            info.signatures?.firstOrNull()?.toByteArray()
        }
    }

    fun formatAuthError(error: Throwable, context: Context): String {
        val sha1 = getCertificateSha1(context)
        val pkg = context.packageName
        if (error is com.google.android.gms.common.api.ApiException) {
            return when (error.statusCode) {
                8 -> "Google Drive setup required (UNREGISTERED_ON_API_CONSOLE).\nRegister OAuth Client ID in Google Cloud Console for package '$pkg' with SHA-1: $sha1"
                10 -> "Google Drive setup required (DEVELOPER_ERROR).\nRegister OAuth Client ID in Google Cloud Console for package '$pkg' with SHA-1: $sha1"
                16 -> "Drive authorization was cancelled."
                7 -> "Network error connecting to Google Play Services. Please check your internet connection."
                17 -> "Google Drive API is disabled. Please enable 'Google Drive API' in Google Cloud Console."
                else -> "Drive authorization failed (Code ${error.statusCode}): ${error.status.statusMessage ?: error.localizedMessage}"
            }
        }
        val msg = error.message.orEmpty()
        if (msg.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true)) {
            return "Google Drive setup required (UNREGISTERED_ON_API_CONSOLE).\nRegister package '$pkg' with SHA-1: $sha1 in Google Cloud Console."
        }
        return error.localizedMessage ?: "Drive authorization failed"
    }

    fun requestAuthorization(
        activity: Activity,
        onReady: (DriveAccess) -> Unit,
        onResolutionRequired: (PendingIntent) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val prefs = DriveBackupPreferences(activity)
        val builder = AuthorizationRequest.builder().setRequestedScopes(driveScopes)
        val savedAccount = prefs.linkedAccountHint
        if (!savedAccount.isNullOrBlank()) {
            builder.setAccount(android.accounts.Account(savedAccount, "com.google"))
        }
        Identity.getAuthorizationClient(activity)
            .authorize(builder.build())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent != null) {
                        onResolutionRequired(pendingIntent)
                    } else {
                        onError(IllegalStateException("Drive permission needs confirmation, but no prompt was returned."))
                    }
                } else {
                    val access = result.toDriveAccess()
                    if (access != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val email = DriveRestClient(access.accessToken).getUserEmail() ?: savedAccount
                            val finalAccess = access.copy(accountName = email)
                            prefs.cachedAccessToken = finalAccess.accessToken
                            prefs.tokenExpiresAt = System.currentTimeMillis() + 3500_000L
                            if (email != null) prefs.linkedAccountHint = email
                            withContext(Dispatchers.Main) {
                                onReady(finalAccess)
                            }
                        }
                    } else {
                        onError(IllegalStateException("Drive authorization did not return an access token."))
                    }
                }
            }
            .addOnFailureListener { error ->
                onError(Exception(formatAuthError(error, activity), error))
            }
    }

    fun handleAuthorizationResult(
        activity: Activity,
        resultCode: Int,
        data: Intent?,
        onReady: (DriveAccess) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            runCatching {
                Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            }.onSuccess { result ->
                val access = result.toDriveAccess()
                if (access != null) {
                    val prefs = DriveBackupPreferences(activity)
                    CoroutineScope(Dispatchers.IO).launch {
                        val email = DriveRestClient(access.accessToken).getUserEmail()
                        val finalAccess = access.copy(accountName = email)
                        prefs.cachedAccessToken = finalAccess.accessToken
                        prefs.tokenExpiresAt = System.currentTimeMillis() + 3500_000L
                        if (email != null) prefs.linkedAccountHint = email
                        withContext(Dispatchers.Main) {
                            onReady(finalAccess)
                        }
                    }
                } else {
                    onError(IllegalStateException("Drive authorization did not return an access token."))
                }
            }.onFailure { error ->
                onError(Exception(formatAuthError(error, activity), error))
            }
        } else {
            if (data != null) {
                val parsed = runCatching {
                    Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
                }
                if (parsed.isFailure) {
                    val error = parsed.exceptionOrNull()!!
                    onError(Exception(formatAuthError(error, activity), error))
                    return
                }
            }
            val sha1 = getCertificateSha1(activity)
            val pkg = activity.packageName
            onError(
                IllegalStateException(
                    "Drive authorization was not completed (Result: Canceled).\n" +
                    "If the Google account picker closed immediately, Google rejected it because the app is UNREGISTERED_ON_API_CONSOLE.\n" +
                    "To fix: In Google Cloud Console, register an Android OAuth Client ID for package '$pkg' with SHA-1: $sha1"
                )
            )
        }
    }

    suspend fun getAccessForBackground(context: Context): DriveAccess = withContext(Dispatchers.IO) {
        val prefs = DriveBackupPreferences(context.applicationContext)
        val cached = prefs.cachedAccessToken
        val expiry = prefs.tokenExpiresAt
        val now = System.currentTimeMillis()
        if (!cached.isNullOrBlank() && expiry > now + 60_000L) {
            return@withContext DriveAccess(
                accessToken = cached,
                accountName = prefs.linkedAccountHint,
                hasDriveAppDataScope = true
            )
        }

        val requestBuilder = AuthorizationRequest.builder().setRequestedScopes(driveScopes)
        val accountName = prefs.linkedAccountHint
        if (!accountName.isNullOrBlank()) {
            requestBuilder.setAccount(android.accounts.Account(accountName, "com.google"))
        }

        val result = Identity.getAuthorizationClient(context.applicationContext)
            .authorize(requestBuilder.build())
            .awaitTask()
        if (result.hasResolution()) {
            if (!cached.isNullOrBlank()) {
                return@withContext DriveAccess(
                    accessToken = cached,
                    accountName = accountName,
                    hasDriveAppDataScope = true
                )
            }
            throw IllegalStateException("Drive account must be linked again before backup can run.")
        }
        val access = result.toDriveAccess()
            ?: throw IllegalStateException("Drive authorization did not return an access token.")
        val email = DriveRestClient(access.accessToken).getUserEmail() ?: accountName
        val finalAccess = access.copy(accountName = email)
        prefs.cachedAccessToken = finalAccess.accessToken
        prefs.tokenExpiresAt = now + 3500_000L
        if (email != null) prefs.linkedAccountHint = email
        finalAccess
    }

    suspend fun revoke(context: Context) {
        val prefs = DriveBackupPreferences(context.applicationContext)
        prefs.clearLink()
        Identity.getAuthorizationClient(context.applicationContext)
            .revokeAccess(
                com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder()
                    .setScopes(driveScopes)
                    .build()
            )
            .awaitTask()
    }

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
