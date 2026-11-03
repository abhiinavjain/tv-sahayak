package com.tvsahayak.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Wallpapers the family can pick in Setup: rich gradients with a soft gold rangoli. */
enum class Wallpaper(val hindi: String, val stops: List<Color>, val accent: Color, val accentDeep: Color, val gold: Color) {
    ROYAL("शाही", listOf(Color(0xFF14082E), Color(0xFF3B0D4A), Color(0xFF6E1739)), Color(0xFFFF9933), Color(0xFFD84315), Color(0xFFFFD27A)),
    PEACOCK("मोर", listOf(Color(0xFF02172A), Color(0xFF053B5C), Color(0xFF0A6466)), Color(0xFF26C6DA), Color(0xFF00838F), Color(0xFFF4C430)),
    SUNSET("संध्या", listOf(Color(0xFF2B0716), Color(0xFF7A1734), Color(0xFFC2410C)), Color(0xFFFFB300), Color(0xFFE65100), Color(0xFFFFE0A3));

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: ROYAL
    }
}

@Composable
fun WallpaperBackground(w: Wallpaper, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(w.stops))) {
        Canvas(Modifier.fillMaxSize()) {
            // Soft glows give depth behind the glass cards.
            drawCircle(Brush.radialGradient(listOf(w.accent.copy(alpha = 0.28f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.18f), radius = size.width * 0.7f),
                radius = size.width * 0.7f, center = Offset(size.width * 0.15f, size.height * 0.18f))
            drawCircle(Brush.radialGradient(listOf(w.gold.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.75f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width * 0.9f, size.height * 0.75f))
            rangoli(Offset(size.width * 0.92f, size.height * 0.07f), size.width * 0.55f, w.gold.copy(alpha = 0.16f))
            rangoli(Offset(size.width * 0.05f, size.height * 0.93f), size.width * 0.5f, w.gold.copy(alpha = 0.13f))
            sparkles(w.gold.copy(alpha = 0.35f))
        }
    }
}

/** A simple mandala: rings, a ring of petals and a ring of dots. */
private fun DrawScope.rangoli(center: Offset, r: Float, color: Color) {
    listOf(0.25f, 0.5f, 0.78f, 1f).forEach { drawCircle(color, r * it, center, style = Stroke(width = 2.5f)) }
    val petals = 16
    repeat(petals) { i ->
        rotate(360f / petals * i, center) {
            drawOval(color, Offset(center.x - r * 0.07f, center.y - r * 0.74f), Size(r * 0.14f, r * 0.42f), style = Stroke(width = 2.5f))
        }
    }
    repeat(32) { i ->
        val a = Math.toRadians(360.0 / 32 * i)
        drawCircle(color, 4f, Offset(center.x + (r * 0.9f * cos(a)).toFloat(), center.y + (r * 0.9f * sin(a)).toFloat()))
    }
    repeat(8) { i ->
        rotate(45f * i, center) {
            drawOval(color, Offset(center.x - r * 0.05f, center.y - r * 0.24f), Size(r * 0.1f, r * 0.22f))
        }
    }
}

private fun DrawScope.sparkles(color: Color) {
    // Fixed positions so the wallpaper doesn't shimmer between recompositions.
    val spots = listOf(0.12f to 0.42f, 0.82f to 0.33f, 0.55f to 0.12f, 0.3f to 0.68f, 0.7f to 0.55f, 0.9f to 0.9f, 0.4f to 0.95f, 0.2f to 0.08f)
    spots.forEach { (x, y) -> drawCircle(color, 3.5f, Offset(size.width * x, size.height * y)) }
}

/** Frosted-glass surface used for cards and buttons on top of the wallpaper. */
fun Modifier.glass(shape: Shape, strength: Float = 0.12f) =
    this.clip(shape).background(Color.White.copy(alpha = strength)).border(1.dp, Color.White.copy(alpha = 0.22f), shape)
