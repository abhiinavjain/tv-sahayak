package com.tvsahayak.core.content

import com.tvsahayak.core.App

/**
 * Shows the family watches, and which app has them. [deepLink] is used only when it is known to be right;
 * otherwise the app's own search is used. Family members can add more entries from the Setup screen.
 */
data class CatalogEntry(
    val title: String,
    val app: App,
    val aliases: List<String> = emptyList(),
    /** Daily serial or weekly show: "latest/aaj ka episode" makes sense. */
    val episodic: Boolean = false,
    val deepLink: String? = null,
    /** Query typed into the app's search box, if different from [title]. */
    val searchQuery: String? = null,
)

object DefaultCatalog {
    val entries: List<CatalogEntry> = listOf(
        // Show-page links were checked on hotstar.com / sonyliv.com (Nov 2026). Opening one lands on the show,
        // where the TV app focuses "Watch"/"Resume" for the latest episode.
        CatalogEntry("Anupamaa", App.JIOHOTSTAR, listOf("anupama", "anupamaa", "अनुपमा", "अनुपमाँ"), episodic = true,
            deepLink = "https://www.hotstar.com/in/shows/anupamaa/1260022017"),
        CatalogEntry("Bigg Boss", App.JIOHOTSTAR, listOf("bigg boss", "big boss", "बिग बॉस", "बिग बोस"), episodic = true,
            deepLink = "https://www.hotstar.com/in/shows/bigg-boss/1271682428"),
        CatalogEntry("Yeh Rishta Kya Kehlata Hai", App.JIOHOTSTAR,
            listOf("yeh rishta kya kehlata hai", "yeh rishta", "ये रिश्ता क्या कहलाता है", "ये रिश्ता"), episodic = true,
            deepLink = "https://www.hotstar.com/in/shows/yeh-rishta/586"),
        CatalogEntry("Ghum Hai Kisikey Pyaar Meiin", App.JIOHOTSTAR,
            listOf("ghum hai kisikey pyaar meiin", "ghum hai kisi ke pyar mein", "गुम है किसी के प्यार में"), episodic = true,
            deepLink = "https://www.hotstar.com/in/shows/ghum-hai-kisikey/1260043179"),
        CatalogEntry("Taarak Mehta Ka Ooltah Chashmah", App.SONYLIV,
            listOf("taarak mehta ka ooltah chashmah", "tarak mehta", "taarak mehta", "तारक मेहता", "तारक मेहता का उल्टा चश्मा"), episodic = true,
            deepLink = "https://www.sonyliv.com/shows/taarak-mehta-ka-ooltah-chashmah-1700000084"),
        CatalogEntry("Kaun Banega Crorepati", App.SONYLIV, listOf("kaun banega crorepati", "kbc", "कौन बनेगा करोड़पति", "केबीसी"), episodic = true),
        CatalogEntry("CID", App.SONYLIV, listOf("cid", "सीआईडी", "सी आई डी"), episodic = true),
        CatalogEntry("Indian Idol", App.SONYLIV, listOf("indian idol", "इंडियन आइडल"), episodic = true),
        CatalogEntry("Kumkum Bhagya", App.ZEE5, listOf("kumkum bhagya", "कुमकुम भाग्य"), episodic = true),
        CatalogEntry("Kundali Bhagya", App.ZEE5, listOf("kundali bhagya", "कुंडली भाग्य"), episodic = true),
        CatalogEntry("Stranger Things", App.NETFLIX, listOf("stranger things", "स्ट्रेंजर थिंग्स"),
            deepLink = "https://www.netflix.com/title/80057281"),
        CatalogEntry("Panchayat", App.PRIME_VIDEO, listOf("panchayat", "पंचायत")),
        CatalogEntry("Mirzapur", App.PRIME_VIDEO, listOf("mirzapur", "मिर्जापुर", "मिर्ज़ापुर")),
        CatalogEntry("Cricket", App.JIOHOTSTAR, listOf("cricket", "match", "क्रिकेट", "मैच"), searchQuery = "cricket"),
        CatalogEntry("Bhajan", App.YOUTUBE, listOf("bhajan", "bhajans", "भजन"), searchQuery = "bhajan"),
        CatalogEntry("Aarti", App.YOUTUBE, listOf("aarti", "arti", "आरती"), searchQuery = "aarti"),
        CatalogEntry("Hindi News", App.YOUTUBE, listOf("news", "samachar", "khabar", "न्यूज़", "समाचार", "खबर", "ख़बर"),
            searchQuery = "hindi news live"),
        CatalogEntry("Aaj Tak", App.YOUTUBE, listOf("aaj tak", "आज तक"), searchQuery = "aaj tak live"),
        CatalogEntry("Ramayan", App.YOUTUBE, listOf("ramayan", "ramayana", "रामायण"), searchQuery = "ramayan"),
        CatalogEntry("Purane Gaane", App.YOUTUBE, listOf("purane gaane", "old songs", "पुराने गाने"), searchQuery = "purane hindi gaane"),
    )
}

/**
 * Words people say in English but the Hindi recognizer writes in Devanagari, keyed by folded transliteration,
 * so a search query comes out as "cricket highlights" rather than "kriket haailaits".
 */
object Loanwords {
    private val words = listOf(
        "cricket", "highlights", "highlight", "match", "news", "live", "songs", "song", "movie", "movies", "comedy",
        "episode", "latest", "video", "trailer", "music", "cartoon", "kids", "india", "world", "cup", "today",
        "full", "new", "old", "dance", "show", "serial", "best", "top", "film", "hindi", "english", "recipe",
    )
    private val byFold: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (w in words) m[com.tvsahayak.core.nlu.Normalizer.fold(w)] = w
        // Common Devanagari spellings that fold differently from the English word.
        val spellings = mapOf("haailaaits" to "highlights", "haailaait" to "highlight", "kriket" to "cricket",
            "nyooz" to "news", "laaiv" to "live", "moovee" to "movie", "moovi" to "movie", "seriyal" to "serial")
        for ((spelling, word) in spellings) m[com.tvsahayak.core.nlu.Normalizer.fold(spelling)] = word
        m
    }

    fun restore(token: String, folded: String): String =
        byFold[folded] ?: byFold[com.tvsahayak.core.nlu.Normalizer.fold(token)] ?: token
}
