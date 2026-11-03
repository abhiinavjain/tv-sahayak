package com.tvsahayak.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
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
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
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

private val shadowText = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 6f))

/**
 * The screen the family uses: a big mic, one-tap channels and apps, and a full remote with large buttons and
 * Hindi labels, on the wallpaper chosen in Setup. When the big mic scrolls away a smaller one floats at the bottom.
 */
@Composable
fun FamilyScreen(vm: MainViewModel, onMic: () -> Unit, onSetup: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val box by vm.boxState.collectAsState()
    val w by vm.wallpaper.collectAsState()
    var askPin by remember { mutableStateOf(false) }
    var showNumbers by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val listening = ui.phase == Phase.LISTENING

    Box(Modifier.fillMaxSize()) {
        WallpaperBackground(w)

        Column(Modifier.fillMaxSize().verticalScroll(scroll).statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {

            // Header
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(w.gold, w.accent))),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Tv, null, Modifier.size(26.dp), tint = Color(0xFF3B1A00)) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("टीवी सहायक", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = w.gold, style = shadowText)
                    val (dot, label) = when (box) {
                        AtvRemote.State.CONNECTED -> Palette.ok to "टीवी बॉक्स जुड़ा है"
                        AtvRemote.State.CONNECTING -> Palette.warn to "जोड़ रहे हैं…"
                        AtvRemote.State.DISCONNECTED -> Palette.bad to "बॉक्स नहीं जुड़ा"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(dot, CircleShape))
                        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                IconButton(onClick = { askPin = true }, Modifier.glass(CircleShape, 0.08f)) {
                    Icon(Icons.Default.Settings, "Setup", tint = Color.White.copy(alpha = 0.85f))
                }
            }

            // What the app heard and did
            Column(Modifier.fillMaxWidth().padding(top = 14.dp).glass(RoundedCornerShape(24.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ui.reply, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    lineHeight = 30.sp, style = shadowText)
                if (ui.heard.isNotBlank()) Text("“${ui.heard}”", color = w.gold.copy(alpha = 0.9f), fontSize = 17.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }

            HeroMic(w, listening, onMic)

            SectionTitle(w, Icons.Default.LiveTv, "चैनल")
            val channels = listOf(
                Triple("Star Plus", "स्टार प्लस", listOf(Color(0xFFFF8F00), Color(0xFFD84315))),
                Triple("Colors", "कलर्स", listOf(Color(0xFFAB47BC), Color(0xFFEC407A))),
                Triple("Zee TV", "ज़ी टीवी", listOf(Color(0xFF5C6BC0), Color(0xFF8E24AA))),
                Triple("Sony TV", "सोनी", listOf(Color(0xFF1E88E5), Color(0xFF283593))),
                Triple("Sony SAB", "सोनी सब", listOf(Color(0xFF43A047), Color(0xFF00897B))),
                Triple("Star Bharat", "स्टार भारत", listOf(Color(0xFFE53935), Color(0xFFAD1457))),
            )
            Grid(channels, 3) { (name, hindi, colors) ->
                BrandTile(hindi, Modifier.weight(1f).height(76.dp), colors) { vm.command(Command.Channel(name)) }
            }

            SectionTitle(w, Icons.Default.Apps, "ऐप्स")
            val apps = listOf(
                Triple(App.JIOHOTSTAR, "हॉटस्टार", listOf(Color(0xFF1A237E), Color(0xFF7B1FA2))),
                Triple(App.YOUTUBE, "यूट्यूब", listOf(Color(0xFFFF1744), Color(0xFFB71C1C))),
                Triple(App.ZEE5, "ज़ी5", listOf(Color(0xFF6A1B9A), Color(0xFF311B92))),
                Triple(App.SONYLIV, "सोनी लिव", listOf(Color(0xFF37474F), Color(0xFF102027))),
                Triple(App.NETFLIX, "नेटफ्लिक्स", listOf(Color(0xFFE50914), Color(0xFF3A0006))),
                Triple(App.PRIME_VIDEO, "प्राइम", listOf(Color(0xFF00A8E1), Color(0xFF01579B))),
            )
            Grid(apps, 3) { (app, hindi, colors) ->
                BrandTile(hindi, Modifier.weight(1f).height(66.dp), colors) { vm.openApp(app) }
            }

            SectionTitle(w, Icons.Default.SettingsRemote, "रिमोट")
            Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp), 0.08f).padding(vertical = 18.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    RoundKey(Icons.Default.PowerSettingsNew, "टीवी बंद", Brush.linearGradient(listOf(Color(0xFFFF5252), Color(0xFFB71C1C)))) { vm.command(Command.PowerTv(false)) }
                    RoundKey(Icons.AutoMirrored.Filled.VolumeOff, "म्यूट") { vm.command(Command.Volume(VolumeDir.MUTE, 1)) }
                    RoundKey(Icons.Default.Home, "होम") { vm.press(Key.HOME) }
                    RoundKey(Icons.AutoMirrored.Filled.ArrowBack, "पीछे") { vm.press(Key.BACK) }
                }
                Spacer(Modifier.height(18.dp))
                DPad(w, onKey = vm::press)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Rocker("आवाज़", onUp = { vm.command(Command.Volume(VolumeDir.UP, 1)) }, onDown = { vm.command(Command.Volume(VolumeDir.DOWN, 1)) })
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoundKey(Icons.Default.PlayArrow, "चलाएं/रोकें", Brush.linearGradient(listOf(w.accent, w.accentDeep)), size = 74.dp) { vm.press(Key.PLAY_PAUSE) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            RoundKey(Icons.Default.FastRewind, "पीछे करें", size = 54.dp) { vm.press(Key.REWIND) }
                            RoundKey(Icons.Default.FastForward, "आगे करें", size = 54.dp) { vm.press(Key.FAST_FORWARD) }
                        }
                    }
                    Rocker("चैनल", onUp = { vm.press(Key.CHANNEL_UP) }, onDown = { vm.press(Key.CHANNEL_DOWN) })
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassTile("लाइव टीवी", Icons.Default.LiveTv, Modifier.weight(1f)) { vm.press(Key.LIVE_TV) }
                    GlassTile(if (showNumbers) "नंबर छुपाएं" else "चैनल नंबर", Icons.Default.Dialpad, Modifier.weight(1f)) { showNumbers = !showNumbers }
                }
                AnimatedVisibility(showNumbers) {
                    NumberPad(w, onDigit = { vm.press(Key.digit(it)) }, onOk = { vm.press(Key.DPAD_CENTER) }, onGuide = { vm.press(Key.GUIDE) })
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("Made by Abhinav Jain", color = w.gold, fontSize = 15.sp, fontWeight = FontWeight.Bold, style = shadowText)
            Text("अभिनव जैन द्वारा बनाया गया", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            Spacer(Modifier.height(110.dp)) // room for the floating mic
        }

        // Floating mic once the big one has scrolled away.
        AnimatedVisibility(scroll.value > 700, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Row(Modifier.navigationBarsPadding().padding(bottom = 16.dp).shadow(16.dp, RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp))
                .background(Brush.linearGradient(if (listening) listOf(Color(0xFFFF5252), Color(0xFFB71C1C)) else listOf(w.accent, w.accentDeep)))
                .clickable(onClick = onMic).padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(34.dp), tint = Color.White)
                Text(if (listening) "रुकिए" else "बोलिए", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (askPin) PinDialog(vm.settings.pin, onDismiss = { askPin = false }, onOk = { askPin = false; onSetup() })
}

@Composable
private fun HeroMic(w: Wallpaper, listening: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "mic")
    val pulse by t.animateFloat(1f, 1.15f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")
    val glow by t.animateFloat(0.25f, 0.5f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")
    val haptic = LocalHapticFeedback.current
    val main = if (listening) listOf(Color(0xFFFF5252), Color(0xFFB71C1C)) else listOf(w.accent, w.accentDeep)
    Box(Modifier.padding(vertical = 18.dp).size(210.dp), contentAlignment = Alignment.Center) {
        // Outer gold ring always breathes gently; it pulses harder while listening.
        Box(Modifier.size(200.dp).scale(if (listening) pulse else 1f).clip(CircleShape)
            .background(Brush.radialGradient(listOf(main.first().copy(alpha = glow), Color.Transparent))))
        Box(Modifier.size(172.dp).border(3.dp, Brush.sweepGradient(listOf(w.gold, w.accent, w.gold)), CircleShape))
        Column(Modifier.size(156.dp).shadow(20.dp, CircleShape).clip(CircleShape).background(Brush.linearGradient(main))
            .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(if (listening) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(70.dp), tint = Color.White)
            Text(if (listening) "रुकिए" else "बोलिए", fontSize = 26.sp, color = Color.White, fontWeight = FontWeight.ExtraBold, style = shadowText)
        }
    }
}

@Composable
private fun SectionTitle(w: Wallpaper, icon: ImageVector, text: String) =
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = w.gold)
        Text(text, color = w.gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp), style = shadowText)
        Box(Modifier.padding(start = 12.dp).weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(w.gold.copy(alpha = 0.6f), Color.Transparent))))
    }

@Composable
private fun <T> Grid(items: List<T>, columns: Int, cell: @Composable RowScope.(T) -> Unit) {
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
private fun BrandTile(label: String, modifier: Modifier, colors: List<Color>, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.shadow(8.dp, shape).clip(shape).background(Brush.linearGradient(colors))
        .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }.padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
            maxLines = 2, lineHeight = 22.sp, style = shadowText)
    }
}

@Composable
private fun GlassTile(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(modifier.height(60.dp).glass(RoundedCornerShape(18.dp), 0.14f)
        .clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(24.dp), tint = Color.White)
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RoundKey(icon: ImageVector, label: String, brush: Brush? = null, size: Dp = 62.dp, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val base = Modifier.size(size)
        val bg = if (brush != null) base.shadow(8.dp, CircleShape).clip(CircleShape).background(brush) else base.glass(CircleShape, 0.16f)
        Box(bg.clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
            contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(size * 0.5f), tint = Color.White) }
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Arrow ring with a big OK in the middle, like the Airtel remote. */
@Composable
private fun DPad(w: Wallpaper, onKey: (Key) -> Unit) {
    val haptic = LocalHapticFeedback.current
    fun tap(k: Key) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onKey(k) }
    Box(Modifier.width(270.dp).aspectRatio(1f).glass(CircleShape, 0.12f).border(2.dp, w.gold.copy(alpha = 0.45f), CircleShape)) {
        @Composable fun Arrow(icon: ImageVector, k: Key, align: Alignment) =
            Box(Modifier.align(align).size(84.dp).clip(CircleShape).clickable { tap(k) }, contentAlignment = Alignment.Center) {
                Icon(icon, k.name, Modifier.size(54.dp), tint = Color.White)
            }
        Arrow(Icons.Default.KeyboardArrowUp, Key.DPAD_UP, Alignment.TopCenter)
        Arrow(Icons.Default.KeyboardArrowDown, Key.DPAD_DOWN, Alignment.BottomCenter)
        Arrow(Icons.Default.KeyboardArrowLeft, Key.DPAD_LEFT, Alignment.CenterStart)
        Arrow(Icons.Default.KeyboardArrowRight, Key.DPAD_RIGHT, Alignment.CenterEnd)
        Box(Modifier.align(Alignment.Center).size(106.dp).shadow(12.dp, CircleShape).clip(CircleShape)
            .background(Brush.linearGradient(listOf(w.accent, w.accentDeep))).border(2.dp, w.gold, CircleShape)
            .clickable { tap(Key.DPAD_CENTER) }, contentAlignment = Alignment.Center) {
            Text("OK", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, style = shadowText)
        }
    }
}

/** Tall + / − pair for volume and channel. */
@Composable
private fun Rocker(label: String, onUp: () -> Unit, onDown: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.width(78.dp).glass(RoundedCornerShape(39.dp), 0.16f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(74.dp).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onUp() },
            contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "$label +", Modifier.size(42.dp), tint = Color.White) }
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().height(74.dp).clickable { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onDown() },
            contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, "$label −", Modifier.size(42.dp), tint = Color.White) }
    }
}

@Composable
private fun NumberPad(w: Wallpaper, onDigit: (Int) -> Unit, onOk: () -> Unit, onGuide: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("गाइड", "0", "OK")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    val shape = RoundedCornerShape(18.dp)
                    val m = Modifier.weight(1f).height(62.dp)
                    Box((if (k == "OK") m.clip(shape).background(Brush.linearGradient(listOf(w.accent, w.accentDeep))) else m.glass(shape, 0.14f))
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
