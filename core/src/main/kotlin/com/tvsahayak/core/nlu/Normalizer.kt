package com.tvsahayak.core.nlu

/**
 * Turns recognizer output (Latin, Devanagari or mixed) into tokens that compare well,
 * e.g. "चलाओ", "chalaao" and "chalao" all fold to "chalao".
 */
object Normalizer {

    data class Token(val text: String, val folded: String)

    fun tokens(input: String): List<Token> =
        Transliterator.toLatin(input.lowercase())
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(' ')
            .filter { it.isNotBlank() }
            .map { Token(it, fold(it)) }

    /** Spelling folding for Hinglish: long vowels, w/v, z/j, ph/f, doubled letters, final schwa-like 'a'/'e'. */
    fun fold(word: String): String {
        var w = word
        w = w.replace("x", "ks").replace("ph", "f").replace("w", "v").replace("z", "j").replace("q", "k")
            .replace("ck", "k").replace("chh", "ch").replace(Regex("c(?!h)"), "k").replace("kh", "k").replace("gh", "g")
            .replace("ee", "i").replace("oo", "u").replace("ou", "u").replace("aa", "a")
            .replace("ai", "e").replace("ay", "e").replace("y", "i")
        w = w.replace(Regex("(.)\\1+"), "$1")
        if (w.length > 3 && w.endsWith("h") && w[w.length - 2] in "aeiou") w = w.dropLast(1) // ooltah, chashmah
        if (w.length > 4 && (w.endsWith("e") || w.endsWith("a"))) w = w.dropLast(1)
        return w
    }

    /** Consonants only: "kehlata" and "kahalaataa" both give "khlt", "yeh" and "yah" both give "yh". */
    fun skeleton(folded: String): String = folded.filter { it !in "aeiou" }

    /** Looser match for names of shows, channels and apps, where Hindi and English spellings differ most. */
    fun sameName(a: String, b: String): Boolean {
        if (same(a, b)) return true
        val sa = skeleton(a); val sb = skeleton(b)
        // One-consonant words ("yeh"/"yah" fold to "ieh"/"iah") also need the same first letter.
        return sa == sb && (sa.length >= 2 || (sa.length == 1 && a[0] == b[0]))
    }

    /** Jaro-Winkler similarity, 0..1. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val range = maxOf(0, maxOf(a.length, b.length) / 2 - 1)
        val aM = BooleanArray(a.length)
        val bM = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            for (j in maxOf(0, i - range)..minOf(b.length - 1, i + range)) {
                if (!bM[j] && a[i] == b[j]) { aM[i] = true; bM[j] = true; matches++; break }
            }
        }
        if (matches == 0) return 0.0
        var t = 0; var k = 0
        for (i in a.indices) if (aM[i]) {
            while (!bM[k]) k++
            if (a[i] != b[k]) t++
            k++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - t / 2.0) / m) / 3
        var prefix = 0
        while (prefix < minOf(4, a.length, b.length) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    /** Folded tokens match: exact for short words, fuzzy for long ones. */
    fun same(a: String, b: String): Boolean =
        a == b || (minOf(a.length, b.length) >= 5 && similarity(a, b) >= 0.9)
}
