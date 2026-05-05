package com.ioristudios.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.data.model.Song
import com.ioristudios.music.ui.theme.CoreWhiteDim
import com.ioristudios.music.ui.theme.ErrorRed
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleFaint
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SurfaceDarkSheet
import com.ioristudios.music.ui.theme.TextSecondary
import com.ioristudios.music.ui.util.pressAnimation
import com.ioristudios.music.ui.util.rememberHapticFeedback
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsSheet(
    song: Song,
    isMultiSelect: Boolean = false,
    onDismiss: () -> Unit,
    onSetRingtone: () -> Unit = {},
    onTrimSong: () -> Unit = {},
    onEditName: () -> Unit = {},
    onShare: () -> Unit = {},
    onDelete: () -> Unit = {},
    onBulkDelete: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDarkSheet,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NeonPurpleSubtle)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Song info header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeonPurpleFaint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.RingVolume,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = song.title,
                        color = CoreWhiteDim,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = song.artist,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color = NeonPurpleFaint
            )

            // Options with staggered entrance animation
            AnimatedOptionItem(
                icon = Icons.Filled.RingVolume,
                label = "Set as Ringtone",
                onClick = onSetRingtone,
                index = 0
            )
            AnimatedOptionItem(
                icon = Icons.Filled.ContentCut,
                label = "Trim Song",
                onClick = onTrimSong,
                index = 1
            )
            AnimatedOptionItem(
                icon = Icons.Filled.Edit,
                label = "Edit Song Name",
                onClick = onEditName,
                index = 2
            )
            AnimatedOptionItem(
                icon = Icons.Filled.Share,
                label = "Share",
                onClick = onShare,
                index = 3
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                thickness = 0.5.dp,
                color = NeonPurpleFaint
            )

            AnimatedOptionItem(
                icon = Icons.Filled.Delete,
                label = "Delete",
                onClick = { showDeleteConfirm = true },
                tint = ErrorRed,
                index = 4
            )

            if (isMultiSelect) {
                AnimatedOptionItem(
                    icon = Icons.Filled.DeleteSweep,
                    label = "Bulk Delete Selected",
                    onClick = { showBulkDeleteConfirm = true },
                    tint = ErrorRed,
                    index = 5
                )
            }
        }
    }

    // Confirmation dialogs
    if (showDeleteConfirm) {
        ConfirmationDialog(
            title = "Delete Song",
            message = "Are you sure you want to delete \"${song.title}\"? This action cannot be undone.",
            confirmText = "Delete",
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showBulkDeleteConfirm) {
        ConfirmationDialog(
            title = "Bulk Delete",
            message = "Are you sure you want to delete all selected songs? This action cannot be undone.",
            confirmText = "Delete All",
            onConfirm = {
                showBulkDeleteConfirm = false
                onBulkDelete()
            },
            onDismiss = { showBulkDeleteConfirm = false }
        )
    }
}

@Composable
private fun AnimatedOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = NeonPurple,
    index: Int
) {
    val haptic = rememberHapticFeedback()
    val interactionSource = remember { MutableInteractionSource() }

    // Staggered slide-in from right
    val animatedProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 50L)
        animatedProgress.animateTo(1f, tween(250))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = animatedProgress.value
                translationX = (1f - animatedProgress.value) * 80f
            }
            .pressAnimation(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                haptic.performClick()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = CoreWhiteDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
