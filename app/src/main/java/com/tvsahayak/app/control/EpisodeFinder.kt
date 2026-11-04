package com.tvsahayak.app.control

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tvsahayak.core.content.HotstarEpisodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Reads the links on a public JioHotstar page (search results or a show's episode list) the way a browser shows
 * them, using an invisible WebView on the phone. Nothing is sent anywhere except the normal page load.
 */
class EpisodeFinder(private val context: Context) {

    /** Waits until [done] is satisfied by the page's links, or [timeoutMs] passes; returns what it saw. */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun links(url: String, timeoutMs: Long = 15_000, done: (List<HotstarEpisodes.Link>) -> Boolean): List<HotstarEpisodes.Link> =
        withContext(Dispatchers.Main) {
            val web = WebView(context.applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.blockNetworkImage = true // faster; only the text and links are needed
                settings.userAgentString = DESKTOP_UA // the desktop page lists episodes with their dates
                webViewClient = WebViewClient()
            }
            var last = emptyList<HotstarEpisodes.Link>()
            try {
                web.loadUrl(url)
                withTimeoutOrNull(timeoutMs) {
                    while (true) {
                        delay(800)
                        last = read(web)
                        if (done(last)) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "reading $url failed", e)
            } finally {
                web.stopLoading(); web.destroy()
            }
            last
        }

    private suspend fun read(web: WebView): List<HotstarEpisodes.Link> = suspendCancellableCoroutine { cont ->
        web.evaluateJavascript(JS) { raw ->
            val links = runCatching {
                val json = JSONTokener(raw).nextValue() as String
                val a = JSONArray(json)
                (0 until a.length()).map { i -> a.getJSONObject(i).let { HotstarEpisodes.Link(it.getString("h"), it.getString("t")) } }
            }.getOrDefault(emptyList())
            if (cont.isActive) cont.resume(links)
        }
    }

    companion object {
        private const val TAG = "EpisodeFinder"
        private const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        private const val JS = """JSON.stringify([...document.querySelectorAll('a[href*="/shows/"]')].map(a => ({h: a.getAttribute('href'),
            t: (a.innerText || a.getAttribute('aria-label') || '').replace(/\s+/g, ' ').slice(0, 200)})))"""
    }
}
