package com.tvsahayak.app

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import com.tvsahayak.app.MainViewModel.Phase
import com.tvsahayak.app.MainViewModel.UiState
import com.tvsahayak.app.control.AtvRemote
import com.tvsahayak.app.ui.FamilyScreen
import com.tvsahayak.app.ui.SahayakTheme
import com.tvsahayak.app.ui.SetupScreen
import com.tvsahayak.app.ui.Wallpaper
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the real app screens, phone-sized, into app/build/video_frames/ for the demo video.
 * Run with: ./gradlew :app:testDebugUnitTest --tests '*VideoFramesTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w392dp-h850dp-xxhdpi")
class VideoFramesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val out = File("build/video_frames").apply { mkdirs() }

    private fun snap(name: String) {
        compose.mainClock.advanceTimeBy(400)
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        out.resolve("$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Test-only: puts the screen into a given state without a microphone or a real box. */
    @Suppress("UNCHECKED_CAST")
    private fun MainViewModel.show(state: UiState) {
        val f = MainViewModel::class.java.getDeclaredField("_ui").apply { isAccessible = true }
        (f.get(this) as MutableStateFlow<UiState>).value = state
    }

    @Suppress("UNCHECKED_CAST")
    private fun connected(app: TvSahayakApp) {
        val f = AtvRemote::class.java.getDeclaredField("_state").apply { isAccessible = true }
        (f.get(app.remote) as MutableStateFlow<AtvRemote.State>).value = AtvRemote.State.CONNECTED
    }

    @Test fun mainScreenStates() {
        val app = compose.activity.application as TvSahayakApp
        connected(app)
        val vm = MainViewModel(app)
        compose.mainClock.autoAdvance = false
        lateinit var scroll: ScrollState
        compose.setContent {
            scroll = rememberScrollState()
            SahayakTheme { FamilyScreen(vm, onMic = {}, onSetup = {}, scroll = scroll) }
        }

        vm.show(UiState()); snap("01_idle")
        vm.show(UiState(Phase.LISTENING, reply = "बोलिए… सुन रहे हैं"))
        for (i in 0 until 8) { compose.mainClock.advanceTimeBy(200); snap("02_listen_$i") }
        vm.show(UiState(Phase.LISTENING, heard = "अनुपमा का लेटेस्ट", reply = "बोलिए… सुन रहे हैं")); snap("03_partial")
        vm.show(UiState(Phase.WORKING, heard = "अनुपमा का लेटेस्ट एपिसोड चलाओ", reply = "समझ रहे हैं…")); snap("04_working")
        vm.show(UiState(Phase.DONE, heard = "अनुपमा का लेटेस्ट एपिसोड चलाओ", reply = "Anupamaa खोल दिया। चलाने के लिए OK दबाइए।")); snap("05_done_anupama")
        vm.show(UiState(Phase.DONE, heard = "स्टार प्लस लगाओ", reply = "स्टार प्लस लगा दिया")); snap("06_done_starplus")
        vm.show(UiState(Phase.DONE, heard = "Netflix पर Stranger Things चलाओ", reply = "Stranger Things खोल दिया। चलाने के लिए OK दबाइए।")); snap("07_done_netflix")
        vm.show(UiState(Phase.DONE, heard = "आवाज़ बढ़ाओ", reply = "आवाज़ बढ़ा दी")); snap("08_done_volume")

        // Scroll to the remote in ~90 even steps for a smooth scroll in the video.
        vm.show(UiState())
        val max = scroll.maxValue
        for (i in 0..90) {
            runBlocking { scroll.scrollTo(max * i / 90) }
            snap("10_scroll_%02d".format(i))
        }

        for (w in Wallpaper.entries) {
            vm.wallpaper.value = w
            snap("20_wall_${w.name.lowercase()}_bottom")
        }
    }

    @Test fun wallpapersTop() {
        val app = compose.activity.application as TvSahayakApp
        connected(app)
        val vm = MainViewModel(app)
        compose.mainClock.autoAdvance = false
        compose.setContent { SahayakTheme { FamilyScreen(vm, onMic = {}, onSetup = {}) } }
        for (w in Wallpaper.entries) {
            vm.wallpaper.value = w
            vm.show(UiState(Phase.DONE, heard = "बिग बॉस लगाओ", reply = "Bigg Boss खोल दिया। चलाने के लिए OK दबाइए।"))
            snap("21_wall_${w.name.lowercase()}_top")
        }
    }

    @Test fun setupScreen() {
        val app = compose.activity.application as TvSahayakApp
        connected(app)
        val vm = MainViewModel(app)
        compose.mainClock.autoAdvance = false
        compose.setContent { SahayakTheme { SetupScreen(vm, onClose = {}) } }
        snap("30_setup")
    }
}
