package com.ioristudios.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ioristudios.music.ui.theme.NeonPurple
import com.ioristudios.music.ui.theme.NeonPurpleGlow
import com.ioristudios.music.ui.theme.NeonPurpleSubtle
import com.ioristudios.music.ui.theme.SurfaceDark
import com.ioristudios.music.ui.theme.TextMuted

data class NavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: String
)

val bottomNavItems = listOf(
    NavItem("Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic, "library"),
    NavItem("Now Playing", Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "now_playing"),
    NavItem("Playlists", Icons.Filled.QueueMusic, Icons.Outlined.QueueMusic, "playlists")
)

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, SurfaceDark),
                        startY = 0f,
                        endY = size.height * 0.3f
                    )
                )
            }
            .background(SurfaceDark)
    ) {
        // Top glow line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            NeonPurpleSubtle,
                            NeonPurple.copy(alpha = 0.4f),
                            NeonPurpleSubtle,
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.route
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) NeonPurple else TextMuted,
                    animationSpec = tween(300),
                    label = "navIconColor"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) NeonPurple else TextMuted,
                    animationSpec = tween(300),
                    label = "navLabelColor"
                )

                Column(
                    modifier = Modifier
                        .clickable { onNavigate(item.route) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.label,
                            tint = iconColor,
                            modifier = Modifier.size(26.dp)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = item.selectedIcon,
                                contentDescription = null,
                                tint = NeonPurpleGlow.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .size(30.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                    Text(
                        text = item.label,
                        color = labelColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
