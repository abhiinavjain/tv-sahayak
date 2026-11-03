package com.tvsahayak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvsahayak.app.MainViewModel
import com.tvsahayak.app.MainViewModel.Phase
import com.tvsahayak.app.control.AtvRemote
import com.tvsahayak.core.App
import com.tvsahayak.core.Command
import com.tvsahayak.core.Key
import com.tvsahayak.core.VolumeDir

/** The screen the family uses: one huge mic button, a few big buttons, large Hindi text. */
@Composable
fun FamilyScreen(vm: MainViewModel, onMic: () -> Unit, onSetup: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val box by vm.boxState.collectAsState()
    var askPin by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.background).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (dot, label) = when (box) {
                AtvRemote.State.CONNECTED -> Palette.ok to "टीवी बॉक्स जुड़ा है"
                AtvRemote.State.CONNECTING -> Palette.warn to "जोड़ रहे हैं…"
                AtvRemote.State.DISCONNECTED -> Palette.bad to "बॉक्स नहीं जुड़ा"
            }
            Box(Modifier.size(14.dp).background(dot, CircleShape))
            Text(label, color = Palette.muted, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
            IconButton(onClick = { askPin = true }) { Icon(Icons.Default.Settings, "Setup", tint = Palette.muted) }
        }

        Spacer(Modifier.height(12.dp))
        Text(ui.reply, color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            lineHeight = 38.sp, modifier = Modifier.fillMaxWidth().height(120.dp))
        Text(if (ui.heard.isBlank()) " " else "“${ui.heard}”", color = Palette.muted, fontSize = 20.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(56.dp))

        val listening = ui.phase == Phase.LISTENING
        Button(onClick = onMic, shape = CircleShape, modifier = Modifier.size(200.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (listening) Palette.bad else Palette.accent)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(84.dp), tint = Color.White)
                Text(if (listening) "रुकिए" else "बोलिए", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigTile("आवाज़ +", Icons.AutoMirrored.Filled.VolumeUp, Modifier.weight(1f)) { vm.command(Command.Volume(VolumeDir.UP, 2)) }
            BigTile("आवाज़ −", Icons.AutoMirrored.Filled.VolumeDown, Modifier.weight(1f)) { vm.command(Command.Volume(VolumeDir.DOWN, 2)) }
            BigTile("रोकें/चलाएं", Icons.Default.PlayArrow, Modifier.weight(1f)) { vm.press(Key.PLAY_PAUSE) }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigTile("होम", Icons.Default.Home, Modifier.weight(1f)) { vm.press(Key.HOME) }
            BigTile("पीछे", Icons.AutoMirrored.Filled.ArrowBack, Modifier.weight(1f)) { vm.press(Key.BACK) }
            BigTile("YouTube", Icons.Default.PlayArrow, Modifier.weight(1f), Palette.youtube) { vm.openApp(App.YOUTUBE) }
        }
        Spacer(Modifier.height(10.dp))
        // Small D-pad for choosing things on screen, like the Airtel remote's arrow ring.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PadKey(Icons.Default.KeyboardArrowLeft, Modifier.weight(1f)) { vm.press(Key.DPAD_LEFT) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PadKey(Icons.Default.KeyboardArrowUp, Modifier.fillMaxWidth()) { vm.press(Key.DPAD_UP) }
                Button(onClick = { vm.press(Key.DPAD_CENTER) }, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent)) { Text("OK", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                PadKey(Icons.Default.KeyboardArrowDown, Modifier.fillMaxWidth()) { vm.press(Key.DPAD_DOWN) }
            }
            PadKey(Icons.Default.KeyboardArrowRight, Modifier.weight(1f)) { vm.press(Key.DPAD_RIGHT) }
        }
    }

    if (askPin) PinDialog(vm.settings.pin, onDismiss = { askPin = false }, onOk = { askPin = false; onSetup() })
}

@Composable
private fun BigTile(label: String, icon: ImageVector, modifier: Modifier, color: Color = Palette.tile, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(96.dp), shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(36.dp), tint = Color.White)
            Text(label, fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun PadKey(icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Palette.tile)) { Icon(icon, null, Modifier.size(36.dp), tint = Color.White) }
}
