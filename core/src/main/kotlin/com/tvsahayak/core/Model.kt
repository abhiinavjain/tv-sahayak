package com.tvsahayak.core

/** Android KeyEvent codes. The Android TV Remote protocol and `adb input keyevent` both use them. */
enum class Key(val code: Int) {
    HOME(3), BACK(4), DPAD_UP(19), DPAD_DOWN(20), DPAD_LEFT(21), DPAD_RIGHT(22), DPAD_CENTER(23),
    VOLUME_UP(24), VOLUME_DOWN(25), POWER(26), ENTER(66), SEARCH(84), PLAY_PAUSE(85),
    STOP(86), NEXT(87), PREVIOUS(88), REWIND(89), FAST_FORWARD(90), MUTE(164), PLAY(126), PAUSE(127),
    MENU(82), CHANNEL_UP(166), CHANNEL_DOWN(167), LIVE_TV(170), GUIDE(172), INFO(165),
    D0(7), D1(8), D2(9), D3(10), D4(11), D5(12), D6(13), D7(14), D8(15), D9(16);

    companion object {
        fun digit(d: Int): Key = entries.first { it.name == "D$d" }
    }
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
        listOf("zee5", "zee 5", "zee five", "ज़ी फाइव", "जी फाइव", "ज़ी 5"), "https://www.zee5.com"),
    SONYLIV("SonyLIV", "सोनी लिव", listOf("com.sonyliv"),
        // Plain "Sony" means the Sony TV channel, so it is not an alias here.
        listOf("sonyliv", "sony liv", "sony live", "सोनी लिव", "सोनी लाइव"), "https://www.sonyliv.com"),
    AIRTEL_XSTREAM("Airtel Xstream Play", "एयरटेल एक्सस्ट्रीम", listOf("tv.accedo.airtel.wynk", "com.airtel.xstream", "tv.airtel.xstream"),
        listOf("airtel xstream", "xstream", "airtel", "stream play", "एयरटेल", "एक्सस्ट्रीम", "एयरटेल एक्सस्ट्रीम"), "https://www.airtelxstream.in");

    companion object {
        private val hosts = mapOf("hotstar.com" to JIOHOTSTAR, "jiocinema.com" to JIOHOTSTAR, "netflix.com" to NETFLIX,
            "primevideo.com" to PRIME_VIDEO, "amazon." to PRIME_VIDEO, "zee5.com" to ZEE5, "sonyliv.com" to SONYLIV,
            "youtube.com" to YOUTUBE, "youtu.be" to YOUTUBE, "airtelxstream.in" to AIRTEL_XSTREAM)

        /** Which app a shared show link belongs to, e.g. a hotstar.com link -> JioHotstar. */
        fun fromLink(url: String): App? {
            val host = Regex("^https?://([^/?#]+)").find(url.trim())?.groupValues?.get(1)?.lowercase() ?: return null
            return hosts.entries.firstOrNull { host.contains(it.key) }?.value
        }

        /** Pulls the first link out of shared text ("Watch Anupamaa on JioHotstar https://...") without tracking parameters. */
        fun linkIn(text: String): String? = Regex("https?://\\S+").find(text)?.value?.substringBefore('?')?.trimEnd('.', ',', ')')

        /** A readable show name from a link's path, e.g. .../shows/anupamaa/1260022017 -> "Anupamaa". */
        fun titleFromLink(url: String): String =
            url.substringBefore('?').trimEnd('/').split('/').lastOrNull { it.any(Char::isLetter) && it != "shows" && it != "title" }
                ?.replace(Regex("-\\d+$"), "")?.split('-')?.joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }.orEmpty()
    }
}

sealed interface Command {
    data class Press(val key: Key, val times: Int = 1) : Command
    data class Volume(val dir: VolumeDir, val steps: Int = 2) : Command
    data class PowerTv(val on: Boolean) : Command
    data class OpenApp(val app: App) : Command
    data class Search(val query: String, val app: App?) : Command
    data class Play(val title: String, val app: App?, val episode: EpisodeSelector? = null) : Command
    /** Live TV channel on the Airtel box, by name ("Star Plus") or number ("channel 109"). */
    data class Channel(val name: String?, val number: Int? = null) : Command
    data object Unknown : Command
}

data class ParseResult(val command: Command, val confidence: Double, val note: String = "")
