package io.github.unidentified24.spock.ui.navigation
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.unidentified24.spock.ui.components.MiniPlayer
import io.github.unidentified24.spock.ui.theme.*

class NoRippleInteractionSource : MutableInteractionSource by MutableInteractionSource()
@Composable
fun MainBottomNavigation(navController:NavHostController,bottomBarState:MutableState<Boolean>,bottomBarPlayerState:MutableState<Boolean>) {
    val current by navController.currentBackStackEntryAsState()
    if(!bottomBarState.value)return
    Column(Modifier.fillMaxWidth().background(AppBackground).navigationBarsPadding()) {
        if(bottomBarPlayerState.value)MiniPlayer(navController)
        Row(Modifier.padding(horizontal=20.dp,vertical=6.dp).fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            val destinations=listOf(Triple(Routes.Home,"Listen",Icons.Default.GraphicEq),Triple(Routes.Search,"Explore",Icons.Default.Explore),Triple(Routes.Library,"Collection",Icons.Default.GridView))
            destinations.forEach { (route,label,icon)->
                val selected=current?.destination?.route==route.route
                Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if(selected) AppPalette.copy(alpha=.13f) else GridBackground)
                    .clickable{navController.navigate(route.route){popUpTo(navController.graph.findStartDestination().id){saveState=true};launchSingleTop=true;restoreState=true}}
                    .padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                    Icon(icon,null,tint=if(selected)AppPalette else TextMuted,modifier=Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(label,color=if(selected)Color.White else TextMuted,style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
