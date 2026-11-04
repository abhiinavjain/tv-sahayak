package com.tvsahayak.core

import com.tvsahayak.core.content.DefaultCatalog
import com.tvsahayak.core.nlu.RuleParser
import com.tvsahayak.core.nlu.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RuleParserTest {
    private val today = LocalDate.of(2026, 11, 4) // a Wednesday
    private val parser = RuleParser(DefaultCatalog.entries, today = { today })

    private fun check(text: String, expected: Command, minConfidence: Double = 0.6) {
        val r = parser.parse(text)
        assertEquals("'$text' (${r.note})", expected, r.command)
        assertTrue("'$text' confidence ${r.confidence} < $minConfidence", r.confidence >= minConfidence)
    }

    private val anupamaLatest = Command.Play("Anupamaa", App.JIOHOTSTAR, EpisodeSelector.LATEST)
    private val strangerThings = Command.Play("Stranger Things", App.NETFLIX)
    private val biggBossToday = Command.Play("Bigg Boss", App.JIOHOTSTAR, EpisodeSelector.TODAY, LocalDate.of(2026, 11, 4))
    private val volUp = Command.Volume(VolumeDir.UP, 2)

    @Test fun userExamples() {
        check("Anupama ka latest episode chalao", anupamaLatest)
        check("Open YouTube", Command.OpenApp(App.YOUTUBE))
        check("Netflix par Stranger Things chalao", strangerThings)
        check("Aaj ka Bigg Boss episode play karo", biggBossToday)
        check("Cricket highlights search karo", Command.Search("cricket highlights", null))
        check("Volume badhao", volUp)
        check("TV home kholo", Command.Press(Key.HOME))
    }

    @Test fun devanagari() {
        check("अनुपमा का लेटेस्ट एपिसोड चलाओ", anupamaLatest)
        check("यूट्यूब खोलो", Command.OpenApp(App.YOUTUBE))
        check("नेटफ्लिक्स पर स्ट्रेंजर थिंग्स चलाओ", strangerThings)
        check("आज का बिग बॉस एपिसोड प्ले करो", biggBossToday)
        check("आवाज़ बढ़ाओ", volUp)
        check("आवाज़ कम करो", Command.Volume(VolumeDir.DOWN, 2))
        check("क्रिकेट हाइलाइट्स सर्च करो", Command.Search("cricket highlights", null))
        check("टीवी होम खोलो", Command.Press(Key.HOME))
    }

    @Test fun mixedScript() {
        // Google's hi-IN recognizer often keeps brand names in Latin letters.
        check("Netflix पर Stranger Things चलाओ", strangerThings)
        check("YouTube खोलो", Command.OpenApp(App.YOUTUBE))
        check("Anupama का latest episode चलाओ", anupamaLatest)
    }

    @Test fun volumeVariants() {
        check("awaaz thoda badhao", Command.Volume(VolumeDir.UP, 1))
        check("volume bahut kam karo", Command.Volume(VolumeDir.DOWN, 5))
        check("volume 5 badhao", Command.Volume(VolumeDir.UP, 5))
        check("aawaz tez karo", volUp)
        check("volume up", volUp)
        check("awaaz band karo", Command.Volume(VolumeDir.MUTE, 1))
        check("mute karo", Command.Volume(VolumeDir.MUTE, 1))
        check("sound dheere karo", Command.Volume(VolumeDir.DOWN, 2))
    }

    @Test fun controls() {
        check("ruko", Command.Press(Key.PAUSE))
        check("pause karo", Command.Press(Key.PAUSE))
        check("roko", Command.Press(Key.PAUSE))
        check("play karo", Command.Press(Key.PLAY))
        check("chalao", Command.Press(Key.PLAY))
        check("wapas jao", Command.Press(Key.BACK))
        check("go back", Command.Press(Key.BACK))
        check("home", Command.Press(Key.HOME))
        check("tv band karo", Command.PowerTv(false))
        check("tv on karo", Command.PowerTv(true))
        check("टीवी बंद करो", Command.PowerTv(false))
        check("ok", Command.Press(Key.DPAD_CENTER))
        check("aage karo", Command.Press(Key.FAST_FORWARD))
        check("agla episode", Command.Press(Key.NEXT))
    }

    @Test fun apps() {
        check("hotstar kholo", Command.OpenApp(App.JIOHOTSTAR))
        check("jio hotstar open karo", Command.OpenApp(App.JIOHOTSTAR))
        check("prime video chalao", Command.OpenApp(App.PRIME_VIDEO))
        check("zee5 kholo", Command.OpenApp(App.ZEE5))
        check("sony liv open karo", Command.OpenApp(App.SONYLIV))
        check("airtel xstream kholo", Command.OpenApp(App.AIRTEL_XSTREAM))
        check("netflix", Command.OpenApp(App.NETFLIX))
        check("सोनी लिव खोलो", Command.OpenApp(App.SONYLIV))
    }

    @Test fun shows() {
        check("anupama lagao", Command.Play("Anupamaa", App.JIOHOTSTAR))
        check("anupamaa ka aaj ka episode dikhao", Command.Play("Anupamaa", App.JIOHOTSTAR, EpisodeSelector.TODAY, today))
        check("kal ka anupama episode chalao", Command.Play("Anupamaa", App.JIOHOTSTAR, EpisodeSelector.YESTERDAY, today.minusDays(1)))
        check("big boss chalao", Command.Play("Bigg Boss", App.JIOHOTSTAR))
        check("tarak mehta ka naya episode lagao", Command.Play("Taarak Mehta Ka Ooltah Chashmah", App.SONYLIV, EpisodeSelector.LATEST))
        check("तारक मेहता चलाओ", Command.Play("Taarak Mehta Ka Ooltah Chashmah", App.SONYLIV))
        check("youtube par bhajan chalao", Command.Play("Bhajan", App.YOUTUBE))
        check("bhajan sunao", Command.Play("Bhajan", App.YOUTUBE))
        check("aaj tak lagao", Command.Play("Aaj Tak", App.YOUTUBE))
        check("panchayat dikhao", Command.Play("Panchayat", App.PRIME_VIDEO))
        check("play stranger things on netflix", strangerThings)
        check("youtube par anupama chalao", Command.Play("Anupamaa", App.YOUTUBE))
    }

    @Test fun unknownTitlesAndSearch() {
        check("youtube par lata mangeshkar ke gaane chalao", Command.Play("lata mangeshkar gaane", App.YOUTUBE))
        check("shah rukh khan ki movie search karo", Command.Search("shah rukh khan movie", null))
        check("netflix par squid game dhundho", Command.Search("squid game", App.NETFLIX))
        check("anupama search karo", Command.Search("Anupamaa", App.JIOHOTSTAR))
        check("search karo", Command.Press(Key.SEARCH))
    }

    @Test fun hindiSpellingsOfShowNames() {
        // Exactly what the hi-IN recognizer produced on the real phone.
        check("यह रिश्ता क्या कहलाता है चलाओ", Command.Play("Yeh Rishta Kya Kehlata Hai", App.JIOHOTSTAR))
        check("तारक मेहता का उल्टा चश्मा लगाओ", Command.Play("Taarak Mehta Ka Ooltah Chashmah", App.SONYLIV))
        check("गुम है किसी के प्यार में चलाओ", Command.Play("Ghum Hai Kisikey Pyaar Meiin", App.JIOHOTSTAR))
        check("बिग बॉस लगाओ", Command.Play("Bigg Boss", App.JIOHOTSTAR))
    }

    @Test fun channels() {
        check("स्टार प्लस लाइव टीवी चलाओ", Command.Channel("Star Plus"))
        check("star plus lagao", Command.Channel("Star Plus"))
        check("कलर्स चलाओ", Command.Channel("Colors"))
        check("colors tv lagao", Command.Channel("Colors"))
        check("ज़ी टीवी लगाओ", Command.Channel("Zee TV"))
        check("zee tv chalao", Command.Channel("Zee TV"))
        check("सोनी लगाओ", Command.Channel("Sony TV"))
        check("sony sab lagao", Command.Channel("Sony SAB"))
        check("channel 109 lagao", Command.Channel(null, 109))
        check("109 लगाओ", Command.Channel(null, 109))
        check("agla channel", Command.Press(Key.CHANNEL_UP))
        check("channel badhao", Command.Press(Key.CHANNEL_UP))
        check("pichla channel", Command.Press(Key.CHANNEL_DOWN))
        // Names that only sound similar must not turn into channels or apps.
        check("bhajan sunao", Command.Play("Bhajan", App.YOUTUBE))
        check("sony liv kholo", Command.OpenApp(App.SONYLIV))
        check("zee5 kholo", Command.OpenApp(App.ZEE5))
        check("star plus ka anupama chalao", Command.Play("Anupamaa", App.JIOHOTSTAR))
    }

    @Test fun grandmasSerialsByDate() {
        val aarambhi = { d: LocalDate? -> Command.Play("Dr. Aarambhi", App.JIOHOTSTAR, null, d) }
        check("Dr arambhi ka 27 oct ka ep lagao", aarambhi(LocalDate.of(2026, 10, 27)))
        check("डॉक्टर आरंभी का 27 अक्टूबर का एपिसोड लगाओ", aarambhi(LocalDate.of(2026, 10, 27)))
        check("udne ki asha ka 30 oct ka ep lagao", Command.Play("Udne Ki Aasha", App.JIOHOTSTAR, null, LocalDate.of(2026, 10, 30)))
        check("उड़ने की आशा का 30 अक्टूबर का एपिसोड लगाओ", Command.Play("Udne Ki Aasha", App.JIOHOTSTAR, null, LocalDate.of(2026, 10, 30)))
        check("mannat ka 2 tarikh ka episode", Command.Play("Mannat", App.JIOHOTSTAR, null, LocalDate.of(2026, 11, 2)))
        check("मन्नत का सोमवार का एपिसोड", Command.Play("Mannat", App.JIOHOTSTAR, null, LocalDate.of(2026, 11, 2)))
        check("mahadev and sons parso ka episode", Command.Play("Mahadev & Sons", App.JIOHOTSTAR, null, LocalDate.of(2026, 11, 2)))
        check("kyuki rishto ke bhi roop badalte hai lagao", Command.Play("Kyunki Rishton Ke Bhi Roop Badalte Hain", App.JIOHOTSTAR))
        check("क्योंकि रिश्तों के भी रूप बदलते हैं चलाओ", Command.Play("Kyunki Rishton Ke Bhi Roop Badalte Hain", App.JIOHOTSTAR))
        check("udne ki aasha 28th october", Command.Play("Udne Ki Aasha", App.JIOHOTSTAR, null, LocalDate.of(2026, 10, 28)))
        // A date later than today means last year's.
        check("anupama 25 december ka episode", Command.Play("Anupamaa", App.JIOHOTSTAR, null, LocalDate.of(2025, 12, 25)))
        // Dates must not turn into channel numbers or volume steps.
        check("mannat ka 3 nov ka ep", Command.Play("Mannat", App.JIOHOTSTAR, null, LocalDate.of(2026, 11, 3)))
    }

    @Test fun lowConfidenceGoesToFallback() {
        assertTrue(parser.parse("mausam kaisa hai").confidence < 0.6)
        assertEquals(Command.Unknown, parser.parse("").command)
    }

    @Test fun learnedPhrasesWin() {
        val learned = RuleParser(DefaultCatalog.entries, today = { today }, learned = mapOf(parser.key("woh saas bahu wala") to anupamaLatest))
        assertEquals(anupamaLatest, learned.parse("Woh saas bahu wala").command)
    }

    @Test fun transliteration() {
        assertEquals("anupama chalao", Transliterator.toLatin("अनुपमा चलाओ").replace("aa", "a"))
        assertEquals("bigg boss", Transliterator.toLatin("bigg boss"))
    }
}
