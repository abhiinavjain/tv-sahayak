package com.tvsahayak.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvsahayak.app.MainViewModel
import com.tvsahayak.app.MainViewModel.Phase
import com.tvsahayak.app.control.AtvRemote
import com.tvsahayak.core.App
import com.tvsahayak.core.Command
import com.tvsahayak.core.Key
import com.tvsahayak.core.VolumeDir

/**
 * The screen the family uses: a big mic, one-tap channels and apps, and a full remote with large buttons
 * and Hindi labels. Everything scrolls, so it also fits small phones.
 */
@Composable
fun FamilyScreen(vm: MainViewModel, onMic: () -> Unit, onSetup: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val box by vm.boxState.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    var showNumbers by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Palette.background).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {

        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("टीवी सहायक", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.text)
                val (dot, label) = when (box) {
                    AtvRemote.State.CONNECTED -> Palette.ok to "टीवी बॉक्स जुड़ा है"
                    AtvRemote.State.CONNECTING -> Palette.warn to "जोड़ रहे हैं…"
                    AtvRemote.State.DISCONNECTED -> Palette.bad to "बॉक्स नहीं जुड़ा"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(dot, CircleShape))
                    Text(label, color = Palette.muted, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            IconButton(onClick = { askPin = true }) { Icon(Icons.Default.Settings, "Setup", tint = Palette.muted) }
        }

        // What the app heard and did
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(20.dp)).background(Palette.card).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ui.reply, color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = 30.sp)
            if (ui.heard.isNotBlank()) Text("“${ui.heard}”", color = Palette.muted, fontSize = 17.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp))
        }

        MicButton(listening = ui.phase == Phase.LISTENING, onClick = onMic)

        // Channels
        SectionTitle("चैनल")
        val channels = listOf(
            Triple("Star Plus", "स्टार प्लस", listOf(Color(0xFFE65100), Color(0xFFBF360C))),
            Triple("Colors", "कलर्स", listOf(Color(0xFF8E24AA), Color(0xFFD81B60))),
            Triple("Zee TV", "ज़ी टीवी", listOf(Color(0xFF3949AB), Color(0xFF6A1B9A))),
            Triple("Sony TV", "सोनी", listOf(Color(0xFF0D47A1), Color(0xFF1565C0))),
            Triple("Sony SAB", "सोनी सब", listOf(Color(0xFF2E7D32), Color(0xFF00695C))),
            Triple("Star Bharat", "स्टार भारत", listOf(Color(0xFFC62828), Color(0xFFAD1457))),
        )
        Grid(channels, columns = 3) { (name, hindi, colors) ->
            Tile(hindi, null, Modifier.weight(1f).height(72.dp), Brush.linearGradient(colors)) { vm.command(Command.Channel(name)) }
        }

        // Apps
        SectionTitle("ऐप्स")
        val apps = listOf(
            Triple(App.JIOHOTSTAR, "हॉटस्टार", listOf(Color(0xFF1A237E), Color(0xFF6A1B9A))),
            Triple(App.YOUTUBE, "यूट्यूब", listOf(Color(0xFFC62828), Color(0xFFB71C1C))),
            Triple(App.ZEE5, "ज़ी5", listOf(Color(0xFF4A148C), Color(0xFF311B92))),
            Triple(App.SONYLIV, "सोनी लिव", listOf(Color(0xFF263238), Color(0xFF37474F))),
            Triple(App.NETFLIX, "नेटफ्लिक्स", listOf(Color(0xFF212121), Color(0xFF8B0000))),
            Triple(App.PRIME_VIDEO, "प्राइम", listOf(Color(0xFF01579B), Color(0xFF0277BD))),
        )
        Grid(apps, columns = 3) { (app, hindi, colors) ->
            Tile(hindi, null, Modifier.weight(1f).height(64.dp), Brush.linearGradient(colors)) { vm.openApp(app) }
        }

        // Remote
        SectionTitle("रिमोट")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundKey(Icons.Default.PowerSettingsNew, "टीवी बंद", Palette.bad) { vm.command(Command.PowerTv(false)) }
            RoundKey(Icons.AutoMirrored.Filled.VolumeOff, "म्यूट") { vm.command(Command.Volume(VolumeDir.MUTE, 1)) }
            RoundKey(Icons.Default.Home, "होम") { vm.press(Key.HOME) }
            RoundKey(Icons.AutoMirrored.Filled.ArrowBack, "पीछे") { vm.press(Key.BACK) }
        }
        Spacer(Modifier.height(16.dp))
        DPad(onKey = vm::press)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Rocker("आवाज़", onUp = { vm.command(Command.Volume(VolumeDir.UP, 1)) }, onDown = { vm.command(Command.Volume(VolumeDir.DOWN, 1)) })
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundKey(Icons.Default.PlayArrow, "चलाएं/रोकें", Palette.accent, size = 72.dp) { vm.press(Key.PLAY_PAUSE) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RoundKey(Icons.Default.FastRewind, "पीछे करें", size = 52.dp) { vm.press(Key.REWIND) }
                    RoundKey(Icons.Default.FastForward, "आगे करें", size = 52.dp) { vm.press(Key.FAST_FORWARD) }
                }
            }
            Rocker("चैनल", onUp = { vm.press(Key.CHANNEL_UP) }, onDown = { vm.press(Key.CHANNEL_DOWN) })
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("लाइव टीवी", Icons.Default.LiveTv, Modifier.weight(1f).height(60.dp), Brush.linearGradient(listOf(Palette.tile, Palette.tile))) { vm.press(Key.LIVE_TV) }
            Tile(if (showNumbers) "नंबर छुपाएं" else "चैनल नंबर", Icons.Default.Dialpad, Modifier.weight(1f).height(60.dp),
                Brush.linearGradient(listOf(Palette.tile, Palette.tile))) { showNumbers = !showNumbers }
        }
        AnimatedVisibility(showNumbers) { NumberPad(onDigit = { vm.press(Key.digit(it)) }, onOk = { vm.press(Key.DPAD_CENTER) }, onGuide = { vm.press(Key.GUIDE) }) }

        Spacer(Modifier.height(28.dp))
        Text("Made by Abhinav Jain", color = Palette.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("अभिनव जैन द्वारा बनाया गया", color = Palette.muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
    }

    if (askPin) PinDialog(vm.settings.pin, onDismiss = { askPin = false }, onOk = { askPin = false; onSetup() })
}

@Composable
private fun MicButton(listening: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(1f, 1.12f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "scale")
    val haptic = LocalHapticFeedback.current
    Box(Modifier.padding(vertical = 18.dp).size(190.dp), contentAlignment = Alignment.Center) {
        if (listening) Box(Modifier.size(190.dp).scale(pulse).background(Palette.bad.copy(alpha = 0.25f), CircleShape))
        Column(Modifier.size(160.dp).clip(CircleShape)
            .background(if (listening) Brush.linearGradient(listOf(Palette.bad, Color(0xFFB71C1C)))
                        else Brush.linearGradient(listOf(Color(0xFFFF9800), Palette.accent)))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(72.dp), tint = Color.White)
            Text(if (listening) "रुकिए" else "बोलिए", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, color = Palette.muted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp))

@Composable
private fun <T> Grid(items: List<T>, columns: Int, cell: @Composable androidx.compose.foundation.layout.RowScope.(T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cell(it) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Tile(label: String, icon: ImageVector?, modifier: Modifier, brush: Brush, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(brush)
        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }.padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, Modifier.size(26.dp).padding(end = 4.dp), tint = Color.White)
        Text(label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 22.sp)
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: String, color: Color = Palette.tile, size: Dp = 64.dp, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size).clip(CircleShape).background(color)
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(size * 0.5f), tint = Color.White) }
        Text(label, color = Palette.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Arrow ring with a big OK in the middle, like the Airtel remote. */
@Composable
private fun DPad(onKey: (Key) -> Unit) {
    val haptic = LocalHapticFeedback.current
    fun tap(k: Key) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onKey(k) }
    Box(Modifier.width(270.dp).aspectRatio(1f).clip(CircleShape).background(Palette.card).border(2.dp, Palette.tile, CircleShape)) {
        @Composable fun Arrow(icon: ImageVector, k: Key, align: Alignment) =
            Box(Modifier.align(align).size(84.dp).clip(CircleShape).clickable { tap(k) }, contentAlignment = Alignment.Center) {
                Icon(icon, k.name, Modifier.size(52.dp), tint = Color.White)
            }
        Arrow(Icons.Default.KeyboardArrowUp, Key.DPAD_UP, Alignment.TopCenter)
        Arrow(Icons.Default.KeyboardArrowDown, Key.DPAD_DOWN, Alignment.BottomCenter)
        Arrow(Icons.Default.KeyboardArrowLeft, Key.DPAD_LEFT, Alignment.CenterStart)
        Arrow(Icons.Default.KeyboardArrowRight, Key.DPAD_RIGHT, Alignment.CenterEnd)
        Box(Modifier.align(Alignment.Center).size(104.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF9800), Palette.accent))).clickable { tap(Key.DPAD_CENTER) },
            contentAlignment = Alignment.Center) { Text("OK", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
    }
}

/** Tall + / − pair for volume and channel. */
@Composable
private fun Rocker(label: String, onUp: () -> Unit, onDown: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.width(76.dp).clip(RoundedCornerShape(38.dp)).background(Palette.tile), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(72.dp).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUp() },
            contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "$label +", Modifier.size(40.dp), tint = Color.White) }
        Text(label, color = Palette.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().height(72.dp).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDown() },
            contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, "$label −", Modifier.size(40.dp), tint = Color.White) }
    }
}

@Composable
private fun NumberPad(onDigit: (Int) -> Unit, onOk: () -> Unit, onGuide: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("गाइड", "0", "OK"))
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    Box(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (k == "OK") Palette.accent else Palette.tile)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            when (k) { "OK" -> onOk(); "गाइड" -> onGuide(); else -> onDigit(k.toInt()) }
                        }, contentAlignment = Alignment.Center) {
                        Text(k, color = Color.White, fontSize = if (k.length == 1) 28.sp else 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
