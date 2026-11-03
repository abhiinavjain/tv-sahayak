package com.tvsahayak.core.nlu

/**
 * Minimal Devanagari -> Hinglish-style Latin, e.g. "अनुपमा चलाओ" -> "anupama chalao".
 * Deletes the inherent 'a' at word end, which is how people type Hindi in Latin letters.
 */
object Transliterator {
    private const val VIRAMA = '्'
    private const val NUKTA = '़'

    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j",
        'झ' to "jh", 'ञ' to "n", 'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t",
        'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n", 'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh",
        'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s",
        'ह' to "h",
    )
    private val nuktaForms = mapOf("k" to "q", "j" to "z", "ph" to "f", "d" to "d", "dh" to "dh", "kh" to "kh", "g" to "g")
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ए' to "e", 'ऐ' to "ai",
        'ओ' to "o", 'औ' to "au", 'ऋ' to "ri", 'ऑ' to "o", 'ऍ' to "e",
    )
    private val matras = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'े' to "e", 'ै' to "ai", 'ो' to "o",
        'ौ' to "au", 'ृ' to "ri", 'ॉ' to "o", 'ॅ' to "e",
    )
    private val digits = "०१२३४५६७८९"

    fun toLatin(input: String): String {
        if (input.none { it in 'ऀ'..'ॿ' }) return input
        // NFD splits precomposed nukta letters (U+0958..U+095F) into consonant + nukta.
        val s = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
        val out = StringBuilder()
        var pendingA = false
        fun flush(wordEnds: Boolean) {
            if (pendingA && !wordEnds) out.append('a')
            pendingA = false
        }
        for ((i, c) in s.withIndex()) {
            val next = s.getOrNull(i + 1)
            when {
                c in consonants -> {
                    flush(false)
                    out.append(consonants.getValue(c))
                    pendingA = true
                }
                c == NUKTA -> {
                    // Rewrite the consonant just written into its nukta form (ज + ़ = z).
                    val forms = nuktaForms.entries.sortedByDescending { it.key.length }
                    for ((plain, form) in forms) if (out.endsWith(plain)) {
                        out.setLength(out.length - plain.length); out.append(form); break
                    }
                }
                c in matras -> { pendingA = false; out.append(matras.getValue(c)) }
                c == VIRAMA -> pendingA = false
                c == 'ं' || c == 'ँ' -> { flush(false); out.append('n') }
                c == 'ः' -> { flush(false); out.append('h') }
                c in vowels -> { flush(false); out.append(vowels.getValue(c)) }
                c in digits -> { flush(true); out.append('0' + digits.indexOf(c)) }
                c == '।' -> { flush(true); out.append(' ') }
                else -> {
                    flush(!c.isLetterOrDigit())
                    out.append(c)
                }
            }
            if (next == null) flush(true)
        }
        return out.toString()
    }
}
