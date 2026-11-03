package com.tvsahayak.core.nlu

import com.tvsahayak.core.App
import com.tvsahayak.core.Command
import com.tvsahayak.core.EpisodeSelector
import com.tvsahayak.core.Key
import com.tvsahayak.core.ParseResult
import com.tvsahayak.core.VolumeDir
import com.tvsahayak.core.content.CatalogEntry
import com.tvsahayak.core.content.Channel
import com.tvsahayak.core.content.DefaultChannels
import com.tvsahayak.core.content.Loanwords

/**
 * Offline understanding of Hindi / English / Hinglish TV commands. Free, private and instant;
 * anything below [ParseResult.confidence] 0.6 is worth asking Claude about (if the family turned that on).
 */
class RuleParser(
    private val catalog: List<CatalogEntry>,
    /** Phrases Claude understood earlier, keyed by [key]. */
    private val learned: Map<String, Command> = emptyMap(),
    channels: List<Channel> = DefaultChannels.list,
) {
    private fun set(vararg words: String) = words.flatMap { w -> Normalizer.tokens(w).map { it.folded } }.toSet()

    private val volumeWords = set("volume", "awaaz", "aawaz", "awaj", "avaaz", "sound", "आवाज़", "आवाज", "वॉल्यूम", "वॉल्युम")
    private val upWords = set("badhao", "badha", "badhaiye", "badhado", "bada", "tez", "tej", "zyada", "jyada", "upar", "up",
        "increase", "high", "louder", "बढ़ाओ", "बढ़ा", "बढ़ाइए", "बढ़ाइये", "तेज़", "तेज", "ज़्यादा", "ज्यादा", "ऊपर")
    private val downWords = set("kam", "kum", "ghatao", "ghata", "dheere", "dhire", "dheema", "down", "decrease", "low", "lower",
        "neeche", "halka", "कम", "घटाओ", "धीरे", "धीमा", "नीचे", "हल्का")
    private val muteWords = set("mute", "chup", "म्यूट", "चुप")
    private val offWords = set("band", "bandh", "off", "बंद")
    private val onWords = set("chalu", "on", "चालू", "ऑन")
    private val playVerbs = set("chalao", "chala", "chalaiye", "chalado", "play", "lagao", "laga", "lagaiye", "lagado",
        "dikhao", "dikha", "dikhaiye", "dikhado", "watch", "dekhna", "dekhni", "dekhao", "dekho", "sunao", "suna",
        "चलाओ", "चला", "चलाइए", "चलाइये", "चलादो", "प्ले", "लगाओ", "लगा", "लगाइए", "दिखाओ", "दिखा", "दिखाइए", "देखना", "देखो", "सुनाओ")
    private val openVerbs = set("open", "kholo", "khol", "kholiye", "kholdo", "launch", "start", "jao", "chalo",
        "खोलो", "खोल", "खोलिए", "खोलिये", "ओपन", "स्टार्ट", "जाओ", "चलो")
    private val searchVerbs = set("search", "dhundho", "dhoondo", "dhundo", "dhoondho", "khojo", "khoj", "find", "talash",
        "dhundhiye", "सर्च", "ढूंढो", "ढूँढो", "ढूंढिए", "खोजो", "खोज", "तलाश")
    private val pauseWords = set("pause", "ruko", "roko", "rok", "ruk", "thehro", "stop", "रुको", "रोको", "रोक", "रुक", "ठहरो", "पॉज़", "पॉज", "स्टॉप")
    private val resumeWords = set("resume", "continue", "रिज़्यूम")
    private val homeWords = set("home", "होम", "ghar", "घर")
    private val backWords = set("back", "wapas", "vapas", "वापस", "बैक")
    private val forwardWords = set("forward", "aage", "आगे", "फॉरवर्ड")
    private val rewindWords = set("rewind", "peeche", "piche", "पीछे")
    private val nextWords = set("next", "agla", "agli", "अगला", "अगली", "नेक्स्ट")
    private val okWords = set("ok", "okay", "select", "enter", "ओके", "सेलेक्ट")
    private val leftWords = set("left", "baaye", "baen", "बाएं", "बाएँ", "लेफ्ट")
    private val rightWords = set("right", "daaye", "daen", "दाएं", "दाएँ", "राइट")
    private val tvWords = set("tv", "television", "टीवी", "टी वी")
    private val channelWords = set("channel", "चैनल", "number", "नंबर")
    private val liveWords = set("live", "लाइव")
    private val previousWords = set("pichla", "pichhla", "previous", "पिछला")
    private val todayWords = set("aaj", "today", "आज")
    private val yesterdayWords = set("kal", "yesterday", "कल")
    private val latestWords = set("latest", "naya", "nayi", "naye", "new", "last", "recent", "abhi", "नया", "नई", "नए", "लेटेस्ट", "लास्ट")
    private val episodeWords = set("episode", "episodes", "ep", "part", "bhag", "एपिसोड", "भाग")
    private val fillers = set("karo", "kar", "kare", "karein", "kariye", "karna", "kardo", "do", "de", "dijiye", "dena", "par", "pe",
        "per", "mein", "me", "mai", "in", "ka", "ki", "ke", "ko", "please", "plz", "zara", "jara", "mujhe", "hume", "humein",
        "mera", "meri", "the", "a", "an", "se", "wala", "wali", "vala", "vali", "hai", "ho", "ji", "chahiye", "sabse", "ek", "baar",
        "bar", "times", "go", "thoda", "thodi", "bahut", "full", "and", "aur", "now", "to", "for", "app", "pr",
        "करो", "कर", "करें", "करिए", "करना", "दो", "दे", "दीजिए", "दीजिये", "पर", "पे", "में", "का", "की", "के", "को", "प्लीज़",
        "ज़रा", "जरा", "मुझे", "वाला", "वाली", "है", "हो", "जी", "चाहिए", "सबसे", "एक", "बार", "थोड़ा", "थोड़ी", "बहुत", "और", "ऐप")
    private val numberWords = mapOf("teen" to 3, "char" to 4, "paanch" to 5, "panch" to 5, "das" to 10,
        "तीन" to 3, "चार" to 4, "पांच" to 5, "पाँच" to 5, "दस" to 10)
        .mapKeys { Normalizer.tokens(it.key).first().folded }

    private val appAliases: List<Pair<App, List<String>>> = App.entries
        .flatMap { app -> app.aliases.map { app to Normalizer.tokens(it).map { t -> t.folded } } }
        .sortedByDescending { it.second.size }

    private val channelAliases: List<Pair<Channel, List<String>>> = channels
        .flatMap { c -> (c.aliases + c.name).map { c to Normalizer.tokens(it).map { t -> t.folded } } }
        .filter { it.second.isNotEmpty() }
        .sortedByDescending { it.second.size }

    private val catalogAliases: List<Pair<CatalogEntry, List<String>>> = catalog
        .flatMap { e -> (e.aliases + e.title).map { e to Normalizer.tokens(it).map { t -> t.folded } } }
        .filter { it.second.isNotEmpty() }
        .sortedByDescending { it.second.size }

    fun key(text: String): String = Normalizer.tokens(text).joinToString(" ") { it.folded }

    fun parse(text: String): ParseResult {
        val tokens = Normalizer.tokens(text)
        if (tokens.isEmpty()) return ParseResult(Command.Unknown, 0.0, "empty")
        learned[key(text)]?.let { return ParseResult(it, 1.0, "learned") }

        val f = tokens.map { it.folded }
        val used = BooleanArray(f.size)
        // Multi-word show names ("Aaj Tak", "Bigg Boss") are protected so their words aren't read as commands.
        val protected = BooleanArray(f.size)
        fun has(words: Set<String>) = f.indices.any { !used[it] && !protected[it] && words.any { w -> Normalizer.same(f[it], w) } }
        fun mark(words: Set<String>) = f.indices.forEach { if (!protected[it] && words.any { w -> Normalizer.same(f[it], w) }) used[it] = true }

        val app = findPhrase(f, used, appAliases)?.let { (a, range) -> range.forEach { used[it] = true }; a }
        findPhrase(f, used, catalogAliases.filter { it.second.size >= 2 })?.second?.forEach { protected[it] = true }

        val steps = f.firstNotNullOfOrNull { it.toIntOrNull() ?: numberWords[it] }?.coerceIn(1, 20)
            ?: when {
                f.any { it in set("thoda", "thodi", "थोड़ा", "थोड़ी") } -> 1
                f.any { it in set("bahut", "full", "बहुत") } -> 5
                else -> 2
            }

        val hasPlay = has(playVerbs); val hasOpen = has(openVerbs); val hasSearch = has(searchVerbs)
        val episodeSel = when {
            has(todayWords) -> EpisodeSelector.TODAY
            has(yesterdayWords) -> EpisodeSelector.YESTERDAY
            has(latestWords) -> EpisodeSelector.LATEST
            has(episodeWords) -> EpisodeSelector.LATEST
            else -> null
        }

        // Volume and mute
        if (has(volumeWords) || (has(muteWords) && app == null)) {
            val dir = when {
                has(muteWords) || has(offWords) -> VolumeDir.MUTE
                has(downWords) -> VolumeDir.DOWN
                has(upWords) -> VolumeDir.UP
                else -> null
            }
            if (dir != null) return ParseResult(Command.Volume(dir, if (dir == VolumeDir.MUTE) 1 else steps), 0.95)
        }

        // Live TV channels: "Star Plus lagao", "channel 109 lagao", "channel badlo / agla channel".
        if (app == null) {
            val number = f.firstNotNullOfOrNull { it.toIntOrNull() }
            val channelWord = has(channelWords)
            if (channelWord && number != null) return ParseResult(Command.Channel(null, number), 0.95)
            if (channelWord && (has(nextWords) || has(upWords) || has(forwardWords))) return ParseResult(Command.Press(Key.CHANNEL_UP), 0.9)
            if (channelWord && (has(previousWords) || has(downWords) || has(rewindWords))) return ParseResult(Command.Press(Key.CHANNEL_DOWN), 0.9)
            val glue = listOf(playVerbs, openVerbs, fillers, tvWords, channelWords, liveWords)
            fun isGlue(i: Int) = glue.any { s -> s.any { Normalizer.same(f[i], it) } }
            findPhrase(f, used, channelAliases)?.let { (ch, range) ->
                if (f.indices.all { it in range || used[it] || isGlue(it) }) return ParseResult(Command.Channel(ch.name), 0.92)
                // "Star Plus ka Anupama": the channel name is only context for the show.
                range.forEach { used[it] = true }
            }
            if (number != null && (hasPlay || channelWord) && f.indices.all { f[it].toIntOrNull() != null || isGlue(it) })
                return ParseResult(Command.Channel(null, number), 0.85)
        }

        // Everything that isn't a command word is the title / search query.
        listOf(playVerbs, openVerbs, searchVerbs, todayWords, yesterdayWords, latestWords, episodeWords, fillers, tvWords)
            .forEach { mark(it) }
        // "on" is a preposition in "play X on Netflix" but a command in "TV on karo".
        if (app != null || hasPlay || hasSearch) mark(set("on"))
        f.indices.forEach { if (f[it].toIntOrNull() != null || f[it] in numberWords) used[it] = true }
        val contentIdx = f.indices.filter { !used[it] }
        val controlOnly = contentIdx.all { i ->
            listOf(homeWords, backWords, forwardWords, rewindWords, nextWords, okWords, leftWords, rightWords, upWords,
                downWords, pauseWords, resumeWords, offWords, onWords).any { s -> s.any { Normalizer.same(f[i], it) } }
        }

        if (app == null && controlOnly && contentIdx.isNotEmpty()) {
            val isTv = f.any { it in tvWords }
            val key = when {
                has(homeWords) -> Key.HOME
                isTv && has(offWords) -> return ParseResult(Command.PowerTv(false), 0.9)
                isTv && has(onWords) -> return ParseResult(Command.PowerTv(true), 0.9)
                has(pauseWords) || has(offWords) -> Key.PAUSE
                has(resumeWords) || has(onWords) -> Key.PLAY
                has(backWords) -> Key.BACK
                has(forwardWords) -> Key.FAST_FORWARD
                has(rewindWords) -> Key.REWIND
                has(nextWords) -> Key.NEXT
                has(okWords) -> Key.DPAD_CENTER
                has(leftWords) -> Key.DPAD_LEFT
                has(rightWords) -> Key.DPAD_RIGHT
                has(upWords) -> Key.DPAD_UP
                has(downWords) -> Key.DPAD_DOWN
                else -> null
            }
            if (key != null) {
                val times = if (key in setOf(Key.DPAD_UP, Key.DPAD_DOWN, Key.DPAD_LEFT, Key.DPAD_RIGHT, Key.FAST_FORWARD, Key.REWIND)) steps.coerceAtMost(10) else 1
                return ParseResult(Command.Press(key, if (f.any { it.toIntOrNull() != null || it in numberWords }) times else 1), 0.9)
            }
        }

        val query = contentIdx.joinToString(" ") { Loanwords.restore(tokens[it].text, f[it]) }

        if (hasSearch) {
            val exact = matchCatalog(f, contentIdx)?.takeIf { m -> m.second == contentIdx.count { i -> fillers.none { Normalizer.same(f[i], it) } } }?.first
            return if (query.isEmpty()) {
                if (app != null) ParseResult(Command.Search("", app), 0.85) else ParseResult(Command.Press(Key.SEARCH), 0.8)
            } else ParseResult(Command.Search(exact?.searchQuery ?: exact?.title ?: query, app ?: exact?.app), 0.9)
        }

        if (contentIdx.isNotEmpty()) {
            val match = matchCatalog(f, contentIdx)
            val verb = hasPlay || hasOpen || episodeSel != null
            if (match != null) {
                val (entry, covered) = match
                val leftovers = covered < contentIdx.count { i -> fillers.none { Normalizer.same(f[i], it) } }
                return if (!leftovers) {
                    val sel = if (entry.episodic) episodeSel else null
                    ParseResult(Command.Play(entry.title, app ?: entry.app, sel), if (verb) 0.92 else 0.75, "catalog")
                } else {
                    val defaultApp = if (entry.searchQuery == null) entry.app else null
                    ParseResult(Command.Play(query, app ?: defaultApp, episodeSel), if (verb) 0.75 else 0.5, "catalog+extra")
                }
            }
            return ParseResult(Command.Play(query, app, episodeSel), if (verb) 0.65 else 0.35, "unknown title")
        }

        if (app != null) return ParseResult(Command.OpenApp(app), if (hasOpen || hasPlay) 0.95 else 0.8)
        if (hasPlay) return ParseResult(Command.Press(Key.PLAY), 0.8)
        return ParseResult(Command.Unknown, 0.0, "no rule")
    }

    /**
     * Names are compared loosely (by consonants) when that is safe: multi-word names, or long single words.
     * Short single words stay strict, so "sunao" never matches "Sony".
     */
    private fun nameMatch(token: String, aliasWord: String, alias: List<String>) =
        if (alias.size >= 2 || Normalizer.skeleton(aliasWord).length >= 4) Normalizer.sameName(token, aliasWord)
        else Normalizer.same(token, aliasWord)

    /** Finds the longest alias whose tokens all appear contiguously in unused positions. */
    private fun <T> findPhrase(f: List<String>, used: BooleanArray, aliases: List<Pair<T, List<String>>>): Pair<T, IntRange>? {
        for ((item, alias) in aliases) {
            for (start in 0..f.size - alias.size) {
                val range = start until start + alias.size
                if (range.all { !used[it] && nameMatch(f[it], alias[it - start], alias) }) return item to range
            }
        }
        return null
    }

    /** Best catalog entry among the non-filler content tokens, plus how many of them it covers. */
    private fun matchCatalog(f: List<String>, contentIdx: List<Int>): Pair<CatalogEntry, Int>? {
        val content = contentIdx.map { f[it] }.filter { w -> fillers.none { Normalizer.same(w, it) } }
        var best: Pair<CatalogEntry, Int>? = null
        for ((entry, alias) in catalogAliases) {
            // Alias tokens may include filler words ("ka", "kya"); compare on content words only, in order.
            val aliasContent = alias.filter { a -> fillers.none { Normalizer.same(a, it) } }.ifEmpty { alias }
            for (start in 0..content.size - aliasContent.size) {
                if (aliasContent.indices.all { nameMatch(content[start + it], aliasContent[it], aliasContent) }) {
                    if (best == null || aliasContent.size > best.second) best = entry to aliasContent.size
                    break
                }
            }
        }
        return best
    }
}
