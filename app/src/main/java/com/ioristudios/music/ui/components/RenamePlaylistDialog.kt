package com.ioristudios.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ioristudios.music.ui.theme.CoreWhiteDim
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleFaint
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SurfaceDarkSheet
import com.ioristudios.music.ui.theme.TextMuted
import com.ioristudios.music.ui.theme.TextSecondary
import com.ioristudios.music.ui.util.rememberHapticFeedback

@Composable
fun RenamePlaylistDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    val haptic = rememberHapticFeedback()
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(initialName, TextRange(initialName.length)))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Pop-in scale animation
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "dialogScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
        label = "dialogAlpha"
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceDarkSheet)
                .border(1.dp, NeonPurpleFaint, RoundedCornerShape(20.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Rename Playlist",
                color = CoreWhiteDim,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PLAYLIST NAME",
                    color = NeonPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = {
                        if (it.text.length <= 40) {
                            textFieldValue = it
                            errorMessage = null
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = CoreWhiteDim,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(NeonPurple),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonPurpleSubtle.copy(alpha = 0.3f))
                                .border(1.dp, if (errorMessage != null) Color.Red.copy(alpha = 0.7f) else NeonPurpleFaint, RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (textFieldValue.text.isEmpty()) {
                                Text(
                                    text = "Enter playlist name...",
                                    color = TextMuted,
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = Color.Red.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Maximum 40 characters",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${textFieldValue.text.length}/40",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        haptic.performClick()
                        onDismiss()
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }

                Button(
                    onClick = {
                        val trimmed = textFieldValue.text.trim()
                        if (trimmed.isEmpty()) {
                            errorMessage = "Name cannot be empty"
                        } else {
                            haptic.performClick()
                            onRename(trimmed)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = CoreWhiteDim
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = textFieldValue.text.isNotBlank()
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
