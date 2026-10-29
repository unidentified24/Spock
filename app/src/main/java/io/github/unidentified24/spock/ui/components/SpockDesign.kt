package io.github.unidentified24.spock.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.unidentified24.spock.ui.theme.*

@Composable
fun SpockPageTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("S P O C K", color = AppPalette, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color.White, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, color = TextMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SpockWelcome(onConnect: () -> Unit, onLocal: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(AppBackground).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("S P O C K", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text("MUSIC, IN YOUR ORBIT", color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        }
        Column {
            Box(
                Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(io.github.unidentified24.spock.ui.theme.AppPalette.copy(alpha = 0.15f), GridBackground)))
                    .border(1.dp, OutlineSubtle, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(36, 68, 106, 142, 90, 120, 58, 32).forEachIndexed { index, height ->
                        Box(Modifier.width(13.dp).height(height.dp).clip(RoundedCornerShape(8.dp))
                            .background(if (index < 4) AppPalette else SecondaryPalette.copy(alpha = 0.7f)))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("A little more\nyou. A lot more\nmusic.", color = Color.White,
                style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(14.dp))
            Text("Your favorites, new discoveries, and the tracks you keep coming back to. All in one place.",
                color = TextMuted, style = MaterialTheme.typography.bodyLarge)
        }
        Column {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppPalette, contentColor = AppBackground)) {
                Text("Connect Spotify", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onLocal, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Explore local music", color = Color.White)
            }
            Spacer(Modifier.height(14.dp))
            Text("Sign in securely with Spotify. Local files work without an account.",
                color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
