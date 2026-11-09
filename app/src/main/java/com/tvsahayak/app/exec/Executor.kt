package com.tvsahayak.app.exec

import android.util.Log
import com.tvsahayak.app.Settings
import com.tvsahayak.app.control.AtvRemote
import com.tvsahayak.app.control.BoxAdb
import com.tvsahayak.app.control.LgTv
import com.tvsahayak.app.control.SamsungTv
import com.tvsahayak.core.Key
import com.tvsahayak.core.samsung.SamsungApps
import com.tvsahayak.core.samsung.SamsungKeys
import com.tvsahayak.core.VolumeDir
import com.tvsahayak.core.plan.Action
import com.tvsahayak.core.plan.Capabilities
import com.tvsahayak.core.plan.UiDump
import com.tvsahayak.app.control.EpisodeFinder
import com.tvsahayak.core.App
import com.tvsahayak.core.content.HotstarEpisodes
import com.tvsahayak.core.plan.Planner
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Runs a plan from the core [com.tvsahayak.core.plan.Planner] over whichever connections are set up. */
class Executor(
    private val settings: Settings,
    private val remote: AtvRemote,
    private val adb: BoxAdb,
    private val tv: LgTv,
    private val samsung: SamsungTv,
    private val episodes: EpisodeFinder,
) {
    data class Outcome(val say: String?, val error: String?, val log: List<String>)

    @Volatile var adbReady = false

    fun capabilities() = if (settings.isSamsung) Capabilities(
        // A Samsung TV is both the "box" and the TV: keys, apps and volume all go to it.
        tv = settings.samsungHost.isNotBlank(),
        liveTvKey = settings.liveTvKey,
    ) else Capabilities(
        adb = settings.adbEnabled && adbReady,
        tv = settings.tvHost.isNotBlank() && settings.tvClientKey.isNotBlank(),
        volumeViaTv = settings.volumeViaTv,
        liveTvKey = settings.liveTvKey,
    )

    suspend fun ensureConnections() {
        if (settings.isSamsung) {
            if (settings.samsungHost.isNotBlank()) samsung.connect(settings.samsungHost, settings.samsungToken)
            return
        }
        if (settings.boxHost.isNotBlank()) remote.connect(settings.boxHost)
        if (settings.adbEnabled && !adbReady && settings.boxHost.isNotBlank()) {
            adbReady = adb.connect(settings.boxHost, settings.adbPort).isSuccess
        }
    }

    suspend fun run(actions: List<Action>, progress: (String) -> Unit = {}): Outcome {
        val log = mutableListOf<String>()
        var say: String? = null
        var note: String? = null // replaces the planned reply when something had to fall back
        if (settings.dryRun) {
            actions.forEach { log += "(dry run) $it"; if (it is Action.Say) say = it.hindi }
            return Outcome(say, null, log)
        }
        ensureConnections()
        if (settings.isSamsung) return runSamsung(actions, progress, log)
        for (a in actions) {
            log += a.toString()
            try {
                when (a) {
                    is Action.Say -> say = a.hindi
                    is Action.Wait -> delay(a.ms)
                    is Action.Press -> repeat(a.times) { press(a.key.code); delay(150) }
                    is Action.TvVolume -> {
                        val ok = when (a.dir) {
                            VolumeDir.MUTE -> tv.toggleMute(settings.tvHost, settings.tvClientKey)
                            else -> tv.volume(settings.tvHost, settings.tvClientKey, a.dir == VolumeDir.UP, a.steps)
                        }
                        if (!ok) return Outcome(null, "टीवी से जुड़ नहीं पाए", log)
                    }
                    Action.TvOff -> if (!tv.turnOff(settings.tvHost, settings.tvClientKey)) return Outcome(null, "टीवी से जुड़ नहीं पाए", log)
                    Action.TvWake -> {
                        if (settings.tvMac.isBlank()) return Outcome(null, "टीवी का MAC पता सेटअप में डालिए", log)
                        tv.wake(settings.tvMac)
                    }
                    is Action.LaunchApp -> {
                        if (adbReady) adb.launch(settings.packageFor(a.app))
                        else if (!openVerified(a.app, appLinks(a.app), log)) return Outcome(null, "${a.app.hindiName} नहीं खुला", log)
                    }
                    is Action.LaunchLink -> {
                        if (adbReady) adb.view(a.uri, settings.packageFor(a.app))
                        else if (!openVerified(a.app, listOf(a.uri) + appLinks(a.app), log)) return Outcome(null, "${a.app.hindiName} नहीं खुला", log)
                    }
                    is Action.OpenEpisode -> {
                        progress("एपिसोड ढूंढ रहे हैं…")
                        val show = a.showUrl ?: HotstarEpisodes.showFromSearch(episodes.links(Planner.searchLink(App.JIOHOTSTAR, a.query)!!) {
                            HotstarEpisodes.showFromSearch(it) != null
                        })
                        val episode = show?.let { s ->
                            HotstarEpisodes.pick(episodes.links(s) { l -> HotstarEpisodes.pick(l, a.date) != null }, a.date)
                        }
                        log += "  show: ${show ?: "not found"}"
                        log += "  episode: ${episode ?: "not found"}"
                        val target = episode ?: show ?: Planner.searchLink(App.JIOHOTSTAR, a.query)!!
                        if (episode == null) note = when {
                            show == null -> "\"${a.query}\" हॉटस्टार पर ढूंढ रहे हैं"
                            a.date != null -> "${HotstarEpisodes.hindiDate(a.date!!)} का एपिसोड नहीं मिला। शो खोल दिया, एपिसोड चुनिए।"
                            else -> "शो खोल दिया। चलाने के लिए OK दबाइए।"
                        }
                        if (adbReady) adb.view(target, settings.packageFor(App.JIOHOTSTAR))
                        else if (!openVerified(App.JIOHOTSTAR, listOf(target), log)) return Outcome(null, "जियो हॉटस्टार नहीं खुला", log)
                    }
                    is Action.Type -> if (adbReady) adb.type(a.text) else boxRemote { remote.type(a.text) }
                    is Action.Select -> {
                        if (!adbReady) continue
                        val node = selectWithRetry(a.labels)
                        if (node == null) {
                            log += "  not found on screen"
                            if (!a.optional) return Outcome(null, "स्क्रीन पर नहीं मिला, रिमोट से चुन लीजिए", log)
                        } else {
                            log += "  tap '${node.label}' at ${node.cx},${node.cy}"
                            adb.tap(node.cx, node.cy)
                        }
                    }
                }
            } catch (e: BoxUnavailable) {
                return Outcome(null, "बॉक्स से जुड़ नहीं पाए। बॉक्स चालू है? वाई-फ़ाई एक ही है?", log)
            } catch (e: Exception) {
                Log.w(TAG, "action $a failed", e)
                return Outcome(null, "कुछ गड़बड़ हो गई: ${e.message ?: e.javaClass.simpleName}", log)
            }
        }
        return Outcome(note ?: say, null, log)
    }

    /** The same plan, carried out on a Samsung TV (no box). */
    private suspend fun runSamsung(actions: List<Action>, progress: (String) -> Unit, log: MutableList<String>): Outcome {
        val host = settings.samsungHost
        if (host.isBlank()) return Outcome(null, "सेटअप में सैमसंग टीवी जोड़िए", log)
        if (samsung.state.value != AtvRemote.State.CONNECTED && !samsung.connect(host, settings.samsungToken))
            return Outcome(null, "सैमसंग टीवी से जुड़ नहीं पाए। टीवी चालू है? वाई-फ़ाई एक ही है?", log)
        var say: String? = null
        var note: String? = null
        fun appId(app: App) = settings.samsungApps[app] ?: SamsungApps.knownIds[app]
        for (a in actions) {
            log += a.toString()
            try {
                when (a) {
                    is Action.Say -> say = a.hindi
                    is Action.Wait -> delay(a.ms)
                    is Action.Press -> repeat(a.times) {
                        val ok = if (a.key == Key.PLAY_PAUSE) samsung.playPause() else SamsungKeys.of(a.key)?.let(samsung::key) ?: true
                        if (!ok) return Outcome(null, "सैमसंग टीवी से जुड़ नहीं पाए", log)
                        delay(200)
                    }
                    is Action.TvVolume -> repeat(if (a.dir == VolumeDir.MUTE) 1 else a.steps) {
                        samsung.key(when (a.dir) { VolumeDir.UP -> "KEY_VOLUP"; VolumeDir.DOWN -> "KEY_VOLDOWN"; VolumeDir.MUTE -> "KEY_MUTE" })
                        delay(200)
                    }
                    Action.TvOff -> samsung.key("KEY_POWER")
                    Action.TvWake -> {
                        if (settings.samsungMac.isBlank()) return Outcome(null, "टीवी का MAC पता सेटअप में डालिए", log)
                        samsung.wake(settings.samsungMac)
                    }
                    is Action.LaunchApp -> {
                        val id = appId(a.app) ?: return Outcome(null, "${a.app.hindiName} टीवी पर नहीं मिला", log)
                        samsung.launch(host, id, null); log += "  launched $id"
                    }
                    is Action.LaunchLink -> {
                        val id = appId(a.app) ?: return Outcome(null, "${a.app.hindiName} टीवी पर नहीं मिला", log)
                        val tag = SamsungApps.metaTag(a.app, a.uri)
                        samsung.launch(host, id, tag); log += "  launched $id with $tag"
                    }
                    is Action.OpenEpisode -> {
                        progress("एपिसोड ढूंढ रहे हैं…")
                        val show = a.showUrl ?: HotstarEpisodes.showFromSearch(episodes.links(Planner.searchLink(App.JIOHOTSTAR, a.query)!!) {
                            HotstarEpisodes.showFromSearch(it) != null
                        })
                        val episode = show?.let { s -> HotstarEpisodes.pick(episodes.links(s) { l -> HotstarEpisodes.pick(l, a.date) != null }, a.date) }
                        log += "  show: ${show ?: "not found"}"
                        log += "  episode: ${episode ?: "not found"}"
                        val id = appId(App.JIOHOTSTAR) ?: return Outcome(null, "जियो हॉटस्टार टीवी पर नहीं मिला", log)
                        samsung.launch(host, id, episode ?: show)
                        // Whether the TV app opens the exact episode depends on the app; say so plainly if it may not.
                        if (episode == null && a.date != null) note = "${HotstarEpisodes.hindiDate(a.date!!)} का एपिसोड नहीं मिला। हॉटस्टार खोल दिया।"
                    }
                    is Action.Type -> samsung.text(a.text)
                    is Action.Select -> Unit // reading the screen needs ADB on an Android TV box
                }
            } catch (e: Exception) {
                Log.w(TAG, "samsung action $a failed", e)
                return Outcome(null, "कुछ गड़बड़ हो गई: ${e.message ?: e.javaClass.simpleName}", log)
            }
        }
        return Outcome(note ?: say, null, log)
    }

    /** Ways to open an app through the TV Remote protocol, most likely to work first. */
    private fun appLinks(app: App): List<String> =
        listOf(app.webLink) + (listOf(settings.packageFor(app)) + app.packages).distinct().map { "market://launch?id=$it" }

    /**
     * Sends links one by one, moving to the next only when the box reports that a different app came to the front.
     * If the box doesn't report the front app, the first link is assumed to have worked: sending a second one
     * could replace the show page that just opened with the app's home screen.
     */
    private suspend fun openVerified(app: App, links: List<String>, log: MutableList<String>): Boolean {
        val wanted = (app.packages + settings.packageFor(app)).toSet()
        for (link in links) {
            val before = remote.currentApp
            boxRemote { remote.openLink(link) }
            log += "  sent $link"
            if (before in wanted) return true // the app is already in front and handles the link itself
            val front = withTimeoutOrNull(4000) {
                while (remote.currentApp == before || remote.currentApp == null) delay(200)
                remote.currentApp
            }
            log += "  app in front: ${front ?: "not reported"}"
            if (front == null || front in wanted) return true
        }
        return false
    }

    private suspend fun selectWithRetry(labels: List<String>): UiDump.Node? {
        repeat(3) { attempt ->
            UiDump.find(UiDump.parse(adb.uiDump()), labels)?.let { return it }
            delay(1500L * (attempt + 1))
        }
        return null
    }

    private suspend fun press(code: Int) {
        if (remote.isConnected || (settings.boxHost.isNotBlank() && remote.awaitConnected(3000))) remote.key(code)
        else if (adbReady) adb.keys(code)
        else throw BoxUnavailable()
    }

    private suspend fun boxRemote(block: suspend () -> Unit) {
        if (!remote.isConnected && !remote.awaitConnected()) throw BoxUnavailable()
        block()
    }

    private class BoxUnavailable : Exception()

    companion object { private const val TAG = "Executor" }
}
