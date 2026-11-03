package com.tvsahayak.core.plan

import com.tvsahayak.core.nlu.Normalizer

/** Reads `uiautomator dump` XML and finds the on-screen element to click. */
object UiDump {
    data class Node(val text: String, val desc: String, val clickable: Boolean, val focusable: Boolean,
                    val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
        val cx get() = (x1 + x2) / 2
        val cy get() = (y1 + y2) / 2
        val label get() = text.ifBlank { desc }
    }

    private val nodeRe = Regex("<node\\b([^>]*)>")
    private fun attr(attrs: String, name: String) =
        Regex("\\b$name=\"([^\"]*)\"").find(attrs)?.groupValues?.get(1)?.let(::unescape) ?: ""

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&#10;", " ")

    fun parse(xml: String): List<Node> = nodeRe.findAll(xml).mapNotNull { m ->
        val a = m.groupValues[1]
        val b = Regex("\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]").find(attr(a, "bounds"))?.groupValues ?: return@mapNotNull null
        Node(attr(a, "text"), attr(a, "content-desc"), attr(a, "clickable") == "true", attr(a, "focusable") == "true",
            b[1].toInt(), b[2].toInt(), b[3].toInt(), b[4].toInt())
    }.toList()

    /**
     * Best element for any of [labels]: exact match beats "starts with" beats fuzzy; earlier labels win ties,
     * and on equal score the element nearest the top-left (first row/tile) wins.
     */
    fun find(nodes: List<Node>, labels: List<String>): Node? {
        var best: Node? = null; var bestScore = 0.0
        for ((li, label) in labels.withIndex()) {
            val want = fold(label)
            if (want.isEmpty()) continue
            for (n in nodes) {
                val have = fold(n.label)
                if (have.isEmpty()) continue
                val s = when {
                    have == want -> 3.0
                    have.startsWith(want) || want.startsWith(have) && have.length >= 4 -> 2.0
                    have.contains(want) -> 1.5
                    Normalizer.similarity(have, want) >= 0.88 -> 1.0
                    else -> 0.0
                } - li * 0.01 - (n.y1 * 10_000 + n.x1) / 1e9
                if (s > bestScore) { best = n; bestScore = s }
            }
        }
        return best
    }

    private fun fold(s: String) = Normalizer.tokens(s).joinToString(" ") { it.folded }
}
