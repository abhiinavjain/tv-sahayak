package com.tvsahayak.core.content

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Picks an episode from the links on a public JioHotstar show page. Each episode card reads like
 * "Sachin, Sailee Seek Guidance S2 E959 30 Oct 2026 28m …" and links to …/<episode-id>/watch.
 */
object HotstarEpisodes {
    data class Link(val href: String, val text: String)

    private val dateRe = Regex("""\b(\d{1,2}) (Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]* (\d{4})\b""")
    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val showRe = Regex("""^(?:https://www\.hotstar\.com)?/in/shows/[^/?]+/\d+/?$""")

    fun isShowPage(url: String) = showRe.matches(url.substringBefore('?'))

    fun dateOf(text: String): LocalDate? = dateRe.find(text)?.destructured?.let { (d, m, y) ->
        runCatching { LocalDate.of(y.toInt(), months.indexOf(m) + 1, d.toInt()) }.getOrNull()
    }

    /** Watch link for the episode that aired on [date], or the newest episode when [date] is null. */
    fun pick(links: List<Link>, date: LocalDate?): String? {
        val dated = links.filter { "/watch" in it.href }.mapNotNull { l -> dateOf(l.text)?.let { it to l } }
        val chosen = if (date == null) dated.maxByOrNull { it.first }?.second else dated.firstOrNull { it.first == date }?.second
        return chosen?.href?.let(::absolute)
    }

    /** First show page among search results. */
    fun showFromSearch(links: List<Link>): String? = links.map { it.href.substringBefore('?') }.firstOrNull(::isShowPage)?.let(::absolute)

    fun absolute(href: String) = if (href.startsWith("http")) href.substringBefore('?') else "https://www.hotstar.com" + href.substringBefore('?')

    /** "30 अक्टूबर" for the spoken reply. */
    fun hindiDate(d: LocalDate): String = "${d.dayOfMonth} " + listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त",
        "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")[d.monthValue - 1]

    fun englishDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
}
