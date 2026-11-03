package com.tvsahayak.core.plan

import com.tvsahayak.core.App
import com.tvsahayak.core.Command
import com.tvsahayak.core.EpisodeSelector
import com.tvsahayak.core.Key
import com.tvsahayak.core.VolumeDir
import com.tvsahayak.core.content.CatalogEntry
import java.net.URLEncoder

/** One step the phone performs. The app layer decides which transport carries it. */
sealed interface Action {
    data class Press(val key: Key, val times: Int = 1) : Action
    /** Sent to the LG TV itself (webOS API), not to the box. */
    data class TvVolume(val dir: VolumeDir, val steps: Int = 1) : Action
    data object TvOff : Action
    data object TvWake : Action
    data class LaunchApp(val app: App) : Action
    data class LaunchLink(val uri: String, val app: App) : Action
    data class Type(val text: String) : Action
    data class Wait(val ms: Long) : Action
    /** ADB only: read the screen and click the element whose text best matches one of [labels]. */
    data class Select(val labels: List<String>, val optional: Boolean = false) : Action
    data class Say(val hindi: String, val english: String) : Action
}

data class Capabilities(
    val adb: Boolean = false,
    /** The LG TV is paired (webOS API). */
    val tv: Boolean = false,
    /** Send volume to the LG TV instead of the box (when the box's HDMI-CEC / SimpLink volume doesn't work). */
    val volumeViaTv: Boolean = false,
)

class Planner(private val catalog: List<CatalogEntry>) {

    fun plan(cmd: Command, caps: Capabilities): List<Action> = when (cmd) {
        is Command.Press -> listOf(Action.Press(cmd.key, cmd.times), Action.Say(sayKey(cmd.key), cmd.key.name.lowercase()))
        is Command.Volume -> volume(cmd, caps)
        is Command.PowerTv -> when {
            cmd.on && caps.tv -> listOf(Action.TvWake, Action.Say("टीवी चालू कर रहे हैं", "Turning TV on"))
            !cmd.on && caps.tv -> listOf(Action.TvKey("KEY_POWER"), Action.Say("टीवी बंद कर रहे हैं", "Turning TV off"))
            else -> listOf(Action.Press(Key.POWER), Action.Say("पावर बटन दबाया", "Pressed power"))
        }
        is Command.OpenApp -> listOf(Action.LaunchApp(cmd.app), Action.Say("${cmd.app.hindiName} खोल रहे हैं", "Opening ${cmd.app.displayName}"))
        is Command.Search -> search(cmd.query, cmd.app ?: App.YOUTUBE, caps)
        is Command.Play -> play(cmd, caps)
        Command.Unknown -> listOf(Action.Say("माफ़ कीजिए, समझ नहीं आया। फिर से बोलिए।", "Sorry, I didn't understand. Please say it again."))
    }

    private fun volume(cmd: Command.Volume, caps: Capabilities): List<Action> {
        val (key, say) = when (cmd.dir) {
            VolumeDir.UP -> Key.VOLUME_UP to "आवाज़ बढ़ा दी"
            VolumeDir.DOWN -> Key.VOLUME_DOWN to "आवाज़ कम कर दी"
            VolumeDir.MUTE -> Key.MUTE to "आवाज़ बंद/चालू की"
        }
        val step = if (caps.tv && caps.volumeViaTv) Action.TvVolume(cmd.dir, cmd.steps) else Action.Press(key, cmd.steps)
        return listOf(step, Action.Say(say, "Volume ${cmd.dir.name.lowercase()}"))
    }

    private fun search(query: String, app: App, caps: Capabilities): List<Action> {
        val steps = mutableListOf<Action>()
        if (query.isBlank()) {
            steps += Action.LaunchApp(app); steps += Action.Wait(3000); steps += Action.Press(Key.SEARCH)
        } else {
            steps += searchSteps(query, app, caps)
        }
        steps += Action.Say("${app.hindiName} पर \"$query\" ढूंढ रहे हैं".replace(" पर \"\"", ""), "Searching $query on ${app.displayName}")
        return steps
    }

    private fun searchSteps(query: String, app: App, caps: Capabilities): List<Action> {
        searchLink(app, query)?.let { return listOf(Action.LaunchLink(it, app), Action.Wait(4000)) }
        // Generic: open the app, open its search screen, type, submit. Typing works through ADB or the remote's keyboard.
        return listOf(Action.LaunchApp(app), Action.Wait(5000), Action.Press(Key.SEARCH), Action.Wait(1500),
            Action.Type(query), Action.Wait(500), Action.Press(Key.ENTER), Action.Wait(3000))
    }

    private fun play(cmd: Command.Play, caps: Capabilities): List<Action> {
        val entry = catalog.firstOrNull { it.title.equals(cmd.title, ignoreCase = true) }
        val app = cmd.app ?: entry?.app ?: App.YOUTUBE
        val steps = mutableListOf<Action>()
        val link = entry?.deepLink?.takeIf { (cmd.app ?: entry.app) == entry.app }
        if (link != null) {
            steps += Action.LaunchLink(link, app); steps += Action.Wait(5000)
        } else {
            steps += searchSteps(entry?.searchQuery ?: cmd.title, app, caps)
            if (caps.adb) steps += Action.Select(listOf(cmd.title) + entry?.aliases.orEmpty().take(2))
        }
        if (caps.adb) {
            steps += Action.Wait(3000)
            val episodic = entry?.episodic == true || cmd.episode != null
            if (episodic) steps += Action.Select(episodeLabels(cmd.episode), optional = true)
            steps += Action.Select(PLAY_LABELS, optional = true)
            steps += Action.Say("${entry?.title ?: cmd.title} चला रहे हैं", "Playing ${entry?.title ?: cmd.title}")
        } else {
            steps += Action.Say("${entry?.title ?: cmd.title} खोल दिया। रिमोट से OK दबाइए।", "Opened ${entry?.title ?: cmd.title}. Press OK to play.")
        }
        return steps
    }

    private fun episodeLabels(sel: EpisodeSelector?) = when (sel) {
        EpisodeSelector.YESTERDAY -> listOf("Yesterday", "कल")
        else -> listOf("Latest Episode", "Latest", "New Episode", "Today", "आज", "नया एपिसोड", "Episode 1", "Episodes")
    }

    private fun sayKey(k: Key) = when (k) {
        Key.HOME -> "होम खोल रहे हैं"
        Key.BACK -> "पीछे जा रहे हैं"
        Key.PAUSE -> "रोक दिया"
        Key.PLAY, Key.PLAY_PAUSE -> "चला दिया"
        Key.FAST_FORWARD -> "आगे कर रहे हैं"
        Key.REWIND -> "पीछे कर रहे हैं"
        Key.NEXT -> "अगला"
        else -> "ठीक है"
    }

    companion object {
        val PLAY_LABELS = listOf("Play", "Watch Now", "Watch", "Resume", "Continue Watching", "Start Watching",
            "Play Episode", "देखें", "अभी देखें", "चलाएं", "प्ले")

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

        /** Search deep links known to work on Android TV. Others fall back to the app's search screen. */
        fun searchLink(app: App, q: String): String? = when (app) {
            App.YOUTUBE -> "https://www.youtube.com/results?search_query=${enc(q)}"
            else -> null
        }
    }
}
