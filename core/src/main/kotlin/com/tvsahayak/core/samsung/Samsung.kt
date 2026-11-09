package com.tvsahayak.core.samsung

import com.tvsahayak.core.App
import com.tvsahayak.core.Key

/** Key names of the Samsung Smart TV (Tizen) remote API, for the keys the app uses. */
object SamsungKeys {
    fun of(key: Key): String? = when (key) {
        Key.HOME -> "KEY_HOME"
        Key.BACK -> "KEY_RETURN"
        Key.DPAD_UP -> "KEY_UP"
        Key.DPAD_DOWN -> "KEY_DOWN"
        Key.DPAD_LEFT -> "KEY_LEFT"
        Key.DPAD_RIGHT -> "KEY_RIGHT"
        Key.DPAD_CENTER, Key.ENTER -> "KEY_ENTER"
        Key.VOLUME_UP -> "KEY_VOLUP"
        Key.VOLUME_DOWN -> "KEY_VOLDOWN"
        Key.MUTE -> "KEY_MUTE"
        Key.POWER -> "KEY_POWER"
        Key.PLAY -> "KEY_PLAY"
        Key.PAUSE -> "KEY_PAUSE"
        Key.STOP -> "KEY_STOP"
        Key.REWIND, Key.PREVIOUS -> "KEY_REWIND"
        Key.FAST_FORWARD, Key.NEXT -> "KEY_FF"
        Key.CHANNEL_UP -> "KEY_CHUP"
        Key.CHANNEL_DOWN -> "KEY_CHDOWN"
        Key.LIVE_TV -> "KEY_TV"
        Key.GUIDE -> "KEY_GUIDE"
        Key.MENU -> "KEY_MENU"
        Key.INFO -> "KEY_INFO"
        Key.D0, Key.D1, Key.D2, Key.D3, Key.D4, Key.D5, Key.D6, Key.D7, Key.D8, Key.D9 -> "KEY_" + key.name.drop(1)
        Key.PLAY_PAUSE -> null // no single toggle key on Tizen; the client alternates KEY_PAUSE / KEY_PLAY
        Key.SEARCH -> null
    }
}

/** Finds each streaming app among the apps installed on the Samsung TV. */
object SamsungApps {
    /** Store IDs that are the same on every Tizen TV, used until the TV's own list has been read. */
    val knownIds = mapOf(App.YOUTUBE to "111299001912", App.NETFLIX to "3201907018807", App.PRIME_VIDEO to "3201910019365")

    private val keywords = mapOf(
        App.YOUTUBE to listOf("youtube"), App.JIOHOTSTAR to listOf("jiohotstar", "hotstar", "jiocinema"),
        App.NETFLIX to listOf("netflix"), App.PRIME_VIDEO to listOf("primevideo", "amazonprime"),
        App.ZEE5 to listOf("zee5"), App.SONYLIV to listOf("sonyliv"), App.AIRTEL_XSTREAM to listOf("xstream", "airtel"),
    )
    private val avoid = listOf("kids", "music", "studio") // "YouTube Kids" is not "YouTube"

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** [installed]: (appId, name) pairs from the TV. Picks the shortest matching name per app. */
    fun match(installed: List<Pair<String, String>>): Map<App, String> = keywords.mapNotNull { (app, words) ->
        installed.filter { (_, name) -> val n = norm(name); words.any { it in n } && avoid.none { it in n } }
            .minByOrNull { it.second.length }?.let { app to it.first }
    }.toMap()

    /**
     * What to hand the app with a deep-link launch. YouTube on Tizen takes the link's query ("v=ID" or
     * "search_query=…"); other apps get the whole link and fall back to their home screen if they don't use it.
     */
    fun metaTag(app: App, uri: String): String = when (app) {
        App.YOUTUBE -> uri.substringAfter('?', "")
        else -> uri
    }
}
