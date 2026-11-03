package com.tvsahayak.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.tvsahayak.app.ui.FamilyScreen
import com.tvsahayak.app.ui.SahayakTheme
import com.tvsahayak.app.ui.Wallpaper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Saves the main screen in each wallpaper to app/build/screenshots/ for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h2700dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun mainScreen() {
        val vm = MainViewModel(compose.activity.application)
        compose.mainClock.autoAdvance = false // the mic's breathing animation never settles
        compose.setContent { SahayakTheme { FamilyScreen(vm, onMic = {}, onSetup = {}) } }
        for (wp in Wallpaper.entries) {
            vm.wallpaper.value = wp
            compose.mainClock.advanceTimeBy(500)
            val view = compose.activity.window.decorView
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            File("build/screenshots").apply { mkdirs() }.resolve("main_${wp.name.lowercase()}.png").outputStream().use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
