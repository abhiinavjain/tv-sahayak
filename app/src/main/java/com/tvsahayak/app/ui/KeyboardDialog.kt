package com.tvsahayak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Type on the phone instead of the TV's on-screen keyboard. The phone keyboard works in Hindi and English and has
 * its own voice-typing mic. "यूट्यूब पर खोजें" opens YouTube results directly; "टीवी पर लिखें" fills the search box
 * that is open on the TV right now (in any app).
 */
@Composable
fun KeyboardDialog(
    w: Wallpaper,
    recent: List<String>,
    onYoutube: (String) -> Unit,
    onTypeOnTv: (String) -> Unit,
    onEnter: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        KeyboardPanel(w, recent, onYoutube, onTypeOnTv, onEnter, onDismiss, Modifier.padding(horizontal = 12.dp).imePadding())
    }
}

/** The dialog's content, separate so it can also be rendered on its own (screenshots). */
@Composable
fun KeyboardPanel(
    w: Wallpaper,
    recent: List<String>,
    onYoutube: (String) -> Unit,
    onTypeOnTv: (String) -> Unit,
    onEnter: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialText: String = "",
    autoFocus: Boolean = true,
) {
    var text by remember { mutableStateOf(initialText) }
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() } // keyboard opens straight away
    run {
        Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(w.stops)).border(2.dp, w.gold.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
            .padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Keyboard, null, Modifier.size(30.dp), tint = w.gold)
                Text("टाइप करके खोजें", color = w.gold, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 10.dp).weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "बंद करें", tint = Color.White, modifier = Modifier.size(30.dp)) }
            }

            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("जैसे: भजन, कपिल शर्मा, cricket…", fontSize = 22.sp, color = Color.White.copy(alpha = 0.55f)) },
                textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White),
                singleLine = true, shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) onYoutube(text.trim()) }),
                trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Default.Close, "मिटाएं", tint = Color.White) } },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = w.gold, unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                    cursorColor = w.gold, focusedContainerColor = Color.White.copy(alpha = 0.10f), unfocusedContainerColor = Color.White.copy(alpha = 0.06f)))

            BigAction("यूट्यूब पर खोजें", Icons.Default.Search, listOf(Color(0xFFFF1744), Color(0xFFB71C1C)), enabled = text.isNotBlank()) {
                onYoutube(text.trim())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigAction("टीवी पर लिखें", Icons.Default.Tv, listOf(w.accent, w.accentDeep), Modifier.weight(1.4f), enabled = text.isNotBlank()) {
                    onTypeOnTv(text.trim())
                }
                BigAction("Enter", Icons.AutoMirrored.Filled.KeyboardReturn, listOf(Color(0xFF546E7A), Color(0xFF37474F)), Modifier.weight(1f)) { onEnter() }
            }
            Text("“टीवी पर लिखें” से पहले टीवी पर किसी ऐप का सर्च वाला डिब्बा खोलें।", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)

            if (recent.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = w.gold, modifier = Modifier.size(22.dp))
                    Text("पिछली खोजें", color = w.gold, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { r ->
                        Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                            .clickable { onYoutube(r) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(r, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun BigAction(label: String, icon: ImageVector, colors: List<Color>, modifier: Modifier = Modifier.fillMaxWidth(),
                      enabled: Boolean = true, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(modifier.height(66.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(if (enabled) colors else colors.map { it.copy(alpha = 0.35f) }))
        .clickable(enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(28.dp), tint = Color.White)
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 8.dp))
    }
}
