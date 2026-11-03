package com.tvsahayak.app.exec

import android.util.Log
import com.tvsahayak.app.Settings
import com.tvsahayak.app.control.AtvRemote
import com.tvsahayak.app.control.BoxAdb
import com.tvsahayak.app.control.LgTv
import com.tvsahayak.core.VolumeDir
import com.tvsahayak.core.plan.Action
import com.tvsahayak.core.plan.Capabilities
import com.tvsahayak.core.plan.UiDump
import com.tvsahayak.core.App
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Runs a plan from the core [com.tvsahayak.core.plan.Planner] over whichever connections are set up. */
class Executor(
    private val settings: Settings,
    private val remote: AtvRemote,
    private val adb: BoxAdb,
    private val tv: LgTv,
) {
    data class Outcome(val say: String?, val error: String?, val log: List<String>)

    @Volatile var adbReady = false

    fun capabilities() = Capabilities(
        adb = settings.adbEnabled && adbReady,
        tv = settings.tvHost.isNotBlank() && settings.tvClientKey.isNotBlank(),
        volumeViaTv = settings.volumeViaTv,
        liveTvKey = settings.liveTvKey,
    )

    suspend fun ensureConnections() {
        if (settings.boxHost.isNotBlank()) remote.connect(settings.boxHost)
        if (settings.adbEnabled && !adbReady && settings.boxHost.isNotBlank()) {
            adbReady = adb.connect(settings.boxHost, settings.adbPort).isSuccess
        }
    }

    suspend fun run(actions: List<Action>): Outcome {
        val log = mutableListOf<String>()
        var say: String? = null
        if (settings.dryRun) {
            actions.forEach { log += "(dry run) $it"; if (it is Action.Say) say = it.hindi }
            return Outcome(say, null, log)
        }
        ensureConnections()
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
        return Outcome(say, null, log)
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
