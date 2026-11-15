package com.ioristudios.music.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.external.DriveAuthManager
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SurfaceDarkElevated
import com.ioristudios.music.ui.theme.TextMuted
import com.ioristudios.music.ui.theme.TextPrimary
import com.ioristudios.music.ui.theme.TextSecondary

@Composable
fun GoogleDriveSetupDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val packageName = context.packageName
    val sha1 = DriveAuthManager.getCertificateSha1(context)
    val sha256 = DriveAuthManager.getCertificateSha256(context)

    fun copyToClipboard(label: String, value: String) {
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDarkElevated,
        tonalElevation = 16.dp,
        icon = {
            Icon(Icons.Filled.CloudQueue, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(36.dp))
        },
        title = {
            Text(
                "Google Drive Setup",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Google Drive AppData requires this app's package name and certificate fingerprint to be registered as an Android OAuth 2.0 Client in Google Cloud Console.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                CredentialItem(
                    label = "Package Name",
                    value = packageName,
                    onCopy = { copyToClipboard("Package Name", packageName) }
                )

                CredentialItem(
                    label = "SHA-1 Certificate Fingerprint",
                    value = sha1,
                    onCopy = { copyToClipboard("SHA-1", sha1) }
                )

                CredentialItem(
                    label = "SHA-256 Certificate Fingerprint",
                    value = sha256,
                    onCopy = { copyToClipboard("SHA-256", sha256) }
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonPurpleSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = NeonPurple, modifier = Modifier.size(16.dp))
                            Text("Setup Instructions (1 minute):", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "1. Open console.cloud.google.com and select your project.\n" +
                            "2. Under APIs & Services > Library, enable 'Google Drive API'.\n" +
                            "3. Under APIs & Services > Credentials, click 'Create Credentials' > 'OAuth client ID'.\n" +
                            "4. Choose Application type: 'Android'.\n" +
                            "5. Paste the Package name and SHA-1 above, then click 'Create'.\n" +
                            "6. Changes propagate within 2-3 minutes. Return here and tap 'Link'.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val allText = "Package: $packageName\nSHA-1: $sha1\nSHA-256: $sha256"
                    copyToClipboard("All credentials", allText)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Copy All")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun CredentialItem(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, NeonPurpleSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onCopy)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = NeonPurple,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}