package com.tvsahayak.app.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

/** High-contrast colours, readable for older eyes. */
object Palette {
    val background = Color(0xFF1B1B1F)
    val tile = Color(0xFF3A3A44)
    val card = Color(0xFF2A2A33)
    val accent = Color(0xFFF57C00)
    val youtube = Color(0xFFC62828)
    val text = Color(0xFFFFFFFF)
    val muted = Color(0xFFBDBDC7)
    val ok = Color(0xFF43A047)
    val warn = Color(0xFFFFB300)
    val bad = Color(0xFFE53935)
}

@Composable
fun SahayakTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Palette.accent, background = Palette.background, surface = Color(0xFF26262C)),
) {
    // Surface sets white as the default text colour; without it plain Text comes out black on the dark background.
    Surface(color = Palette.background, contentColor = Palette.text, content = content)
}

/** Keeps the Setup screen away from accidental taps. */
@Composable
fun PinDialog(pin: String, onDismiss: () -> Unit, onOk: () -> Unit) {
    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Setup PIN") },
        text = {
            OutlinedTextField(entered, { entered = it; wrong = false }, singleLine = true, isError = wrong,
                label = { Text(if (wrong) "Wrong PIN" else "PIN (default 1234)") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        },
        confirmButton = { TextButton(onClick = { if (entered == pin) onOk() else wrong = true }) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
