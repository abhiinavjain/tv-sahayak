package com.tvsahayak.core

/** Android KeyEvent codes. The Android TV Remote protocol and `adb input keyevent` both use them. */
enum class Key(val code: Int) {
    HOME(3), BACK(4), DPAD_UP(19), DPAD_DOWN(20), DPAD_LEFT(21), DPAD_RIGHT(22), DPAD_CENTER(23),
    VOLUME_UP(24), VOLUME_DOWN(25), POWER(26), ENTER(66), SEARCH(84), PLAY_PAUSE(85),
    STOP(86), NEXT(87), PREVIOUS(88), REWIND(89), FAST_FORWARD(90), MUTE(164), PLAY(126), PAUSE(127),
}

enum class VolumeDir { UP, DOWN, MUTE }

/** Which episode the user wants. */
enum class EpisodeSelector { LATEST, TODAY, YESTERDAY }

/** OTT apps on the box. Packages are candidates; the real one is picked from `pm list packages` when ADB is on. */
enum class App(
    val displayName: String,
    val hindiName: String,
    val packages: List<String>,
    /** Spoken names in Latin and Devanagari. */
    val aliases: List<String>,
    /** Web address the TV app claims; the most reliable way to open it through the TV Remote protocol. */
    val webLink: String,
) {
    YOUTUBE("YouTube", "यूट्यूब", listOf("com.google.android.youtube.tv", "com.google.android.youtube.tvkids"),
        listOf("youtube", "you tube", "utube", "यूट्यूब", "यू ट्यूब"), "https://www.youtube.com"),
    JIOHOTSTAR("JioHotstar", "जियो हॉटस्टार", listOf("in.startv.hotstar", "in.startv.hotstartvonly"),
        listOf("jiohotstar", "jio hotstar", "hotstar", "jio cinema", "jiocinema", "जियो हॉटस्टार", "हॉटस्टार", "जियो सिनेमा"), "https://www.hotstar.com"),
    NETFLIX("Netflix", "नेटफ्लिक्स", listOf("com.netflix.ninja"),
        listOf("netflix", "net flix", "नेटफ्लिक्स"), "https://www.netflix.com/title"),
    PRIME_VIDEO("Prime Video", "प्राइम वीडियो", listOf("com.amazon.amazonvideo.livingroom"),
        listOf("prime video", "amazon prime", "prime", "amazon", "प्राइम वीडियो", "प्राइम", "अमेज़न प्राइम"), "https://app.primevideo.com"),
    ZEE5("ZEE5", "ज़ी फाइव", listOf("com.graymatrix.did"),
        listOf("zee5", "zee 5", "zee five", "zee", "ज़ी फाइव", "जी फाइव", "ज़ी 5"), "https://www.zee5.com"),
    SONYLIV("SonyLIV", "सोनी लिव", listOf("com.sonyliv"),
        listOf("sonyliv", "sony liv", "sony live", "sony", "सोनी लिव", "सोनी लाइव", "सोनी"), "https://www.sonyliv.com"),
    AIRTEL_XSTREAM("Airtel Xstream Play", "एयरटेल एक्सस्ट्रीम", listOf("tv.accedo.airtel.wynk", "com.airtel.xstream", "tv.airtel.xstream"),
        listOf("airtel xstream", "xstream", "airtel", "stream play", "एयरटेल", "एक्सस्ट्रीम", "एयरटेल एक्सस्ट्रीम"), "https://www.airtelxstream.in"),
}

sealed interface Command {
    data class Press(val key: Key, val times: Int = 1) : Command
    data class Volume(val dir: VolumeDir, val steps: Int = 2) : Command
    data class PowerTv(val on: Boolean) : Command
    data class OpenApp(val app: App) : Command
    data class Search(val query: String, val app: App?) : Command
    data class Play(val title: String, val app: App?, val episode: EpisodeSelector? = null) : Command
    data object Unknown : Command
}

data class ParseResult(val command: Command, val confidence: Double, val note: String = "")
