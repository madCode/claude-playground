package spotter.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spotter.app.today.Today

/**
 * The app's screen: today's sheet in the middle, a newer build waiting when there is one, and the
 * version, copyable in one tap.
 */
@Composable
fun HomeScreen(
    version: String,
    waitingBuild: Int?,
    onInstall: () -> Unit,
    onCopyVersion: () -> Unit,
    today: Today = Today.Loading,
    onAllowCalendar: () -> Unit = {},
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Color(0xFFF7F7F5)) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Spotter",
                    fontSize = 28.sp,
                    color = Color(0xFF16181D),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                if (waitingBuild != null) {
                    Surface(color = Color(0xFFE8EEFC), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "A newer build, $waitingBuild, is ready.",
                                fontSize = 16.sp,
                                color = Color(0xFF1E3A8A),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = onInstall,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text("Install") }
                        }
                    }
                }
                TodayView(today, onAllowCalendar)
                Spacer(Modifier.heightIn(min = 8.dp))
                Text(
                    "Version $version · tap to copy",
                    fontSize = 14.sp,
                    color = Color(0xFF5B606B),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "copy the version", onClick = onCopyVersion)
                        .padding(vertical = 12.dp),
                )
            }
        }
    }
}
