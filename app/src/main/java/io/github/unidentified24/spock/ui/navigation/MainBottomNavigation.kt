package io.github.unidentified24.spock.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.unidentified24.spock.ui.components.MiniPlayer
import io.github.unidentified24.spock.ui.theme.*

class NoRippleInteractionSource : MutableInteractionSource by MutableInteractionSource()

@Composable
fun MainBottomNavigation(navController: NavHostController, bottomBarState: MutableState<Boolean>, bottomBarPlayerState: MutableState<Boolean>) {
    val destinations = listOf(Routes.Home, Routes.Search, Routes.Library)
    val entry by navController.currentBackStackEntryAsState()
    AnimatedVisibility(bottomBarState.value, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp)) {
            AnimatedVisibility(bottomBarPlayerState.value) { MiniPlayer(navController) }
            NavigationBar(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.dp, OutlineSubtle, RoundedCornerShape(26.dp)),
                containerColor = GridBackground,
                tonalElevation = 0.dp,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = entry?.destination?.route == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = destination.label) },
                        label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppPalette, selectedTextColor = Color.White,
                            unselectedIconColor = TextMuted, unselectedTextColor = TextMuted,
                            indicatorColor = AppPalette.copy(alpha = 0.13f),
                        ),
                    )
                }
            }
        }
    }
}
