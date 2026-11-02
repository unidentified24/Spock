package io.github.unidentified24.spock.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import io.github.unidentified24.spock.ui.components.SpockMark
import io.github.unidentified24.spock.ui.theme.*

val LocalNavigationRail=staticCompositionLocalOf{false}

@Composable fun SpockNavigationRail(nav:NavHostController){
    val current by nav.currentBackStackEntryAsState()
    NavigationRail(Modifier.fillMaxHeight().background(GridBackground).statusBarsPadding(),containerColor=GridBackground,header={SpockMark(Modifier.padding(12.dp).size(44.dp))}){
        Spacer(Modifier.height(24.dp))
        listOf(Triple("home","Listen",Icons.Default.GraphicEq),Triple("search","Explore",Icons.Default.Explore),Triple("library","Collection",Icons.Default.GridView),Triple("downloads","Downloads",Icons.Default.Download),Triple("account","Account",Icons.Default.Person)).forEach{(route,label,icon)->
            NavigationRailItem(selected=current?.destination?.route==route,onClick={nav.navigate(route){popUpTo(nav.graph.findStartDestination().id);launchSingleTop=true}},icon={Icon(icon,label)},label={Text(label)})
        }
    }
}
