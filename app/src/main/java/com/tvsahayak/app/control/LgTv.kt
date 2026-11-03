package com.tvsahayak.app.control

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * LG webOS TV second-screen API (the one LG's ThinQ app uses). The first connection shows
 * "Allow TV Sahayak?" on the TV; after OK the TV gives a client key, which we keep.
 * Used for TV volume, turning the TV off and waking it up. The box and its remote are not involved.
 */
class LgTv(private val onClientKey: (String) -> Unit) {

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    // The TV's certificate is self-signed; this client only talks to the TV address chosen on the Setup screen.
    private val http = OkHttpClient.Builder()
        .sslSocketFactory(SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }.socketFactory, trustAll)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var wsHost: String? = null
    private val ids = AtomicInteger()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject?>>()

    private val permissions = listOf("LAUNCH", "CONTROL_AUDIO", "CONTROL_POWER", "CONTROL_INPUT_MEDIA_PLAYBACK",
        "READ_INSTALLED_APPS", "READ_NETWORK_STATE", "READ_POWER_STATE", "CONTROL_DISPLAY", "CONTROL_INPUT_TV")

    /** Opens and registers a connection; [clientKey] blank = first pairing (the family has 60 s to press OK on the TV). */
    private suspend fun socket(host: String, clientKey: String): WebSocket? {
        if (ws != null && wsHost == host) return ws
        ws?.cancel(); ws = null
        // Newer webOS firmware only accepts the TLS port 3001; older sets use plain 3000.
        for (url in listOf("wss://$host:3001/", "ws://$host:3000/")) {
            val registered = CompletableDeferred<Boolean>()
            val socket = http.newWebSocket(Request.Builder().url(url).build(), listener(registered))
            val manifest = JSONObject().put("manifestVersion", 1).put("appVersion", "1.0")
                .put("appId", "com.tvsahayak.app").put("vendorId", "tvsahayak").put("localizedAppNames", JSONObject().put("", "TV Sahayak"))
                .put("permissions", JSONArray(permissions))
            val payload = JSONObject().put("forcePairing", false).put("pairingType", "PROMPT").put("manifest", manifest)
            if (clientKey.isNotBlank()) payload.put("client-key", clientKey)
            socket.send(JSONObject().put("type", "register").put("id", "register_0").put("payload", payload).toString())
            val ok = withTimeoutOrNull(if (clientKey.isBlank()) 60_000 else 8_000) { registered.await() } ?: false
            if (ok) { ws = socket; wsHost = host; return socket }
            socket.cancel()
        }
        return null
    }

    private fun listener(registered: CompletableDeferred<Boolean>) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (o.optString("type")) {
                "registered" -> {
                    o.optJSONObject("payload")?.optString("client-key")?.takeIf { it.isNotBlank() }?.let(onClientKey)
                    registered.complete(true)
                }
                "error" -> {
                    if (o.optString("id") == "register_0") registered.complete(false)
                    pending.remove(o.optString("id"))?.complete(null)
                }
                "response" -> pending.remove(o.optString("id"))?.complete(o.optJSONObject("payload"))
            }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "LG ws failed: ${t.message}"); registered.complete(false)
            if (ws === webSocket) ws = null
            pending.values.forEach { it.complete(null) }; pending.clear()
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (ws === webSocket) ws = null }
    }

    private suspend fun request(host: String, key: String, uri: String, payload: JSONObject? = null): JSONObject? {
        val s = socket(host, key) ?: return null
        val id = "r${ids.incrementAndGet()}"
        val d = CompletableDeferred<JSONObject?>()
        pending[id] = d
        s.send(JSONObject().put("type", "request").put("id", id).put("uri", uri).apply { payload?.let { put("payload", it) } }.toString())
        return withTimeoutOrNull(5000) { d.await() }.also { pending.remove(id) }
    }

    /** First-time pairing. Returns null on success, or an error message. */
    suspend fun pair(host: String): String? =
        if (socket(host, "") != null) null else "TV did not allow the connection. Is the TV on, and did you press OK?"

    suspend fun volume(host: String, key: String, up: Boolean, steps: Int): Boolean {
        repeat(steps) {
            request(host, key, if (up) "ssap://audio/volumeUp" else "ssap://audio/volumeDown") ?: return false
            delay(150)
        }
        return true
    }

    suspend fun toggleMute(host: String, key: String): Boolean {
        val muted = request(host, key, "ssap://audio/getStatus")?.optBoolean("mute") ?: return false
        return request(host, key, "ssap://audio/setMute", JSONObject().put("mute", !muted)) != null
    }

    suspend fun turnOff(host: String, key: String): Boolean = request(host, key, "ssap://system/turnOff") != null

    /** The TV's Wi-Fi/LAN MAC address, needed to wake it later. */
    suspend fun macAddress(host: String, key: String): String? =
        request(host, key, "ssap://com.webos.service.connectionmanager/getinfo")?.let { p ->
            (p.optJSONObject("wifiInfo")?.optString("macAddress") ?: "").ifBlank { p.optJSONObject("wiredInfo")?.optString("macAddress") }
        }?.takeIf { it.isNotBlank() }

    /**
     * Wake-on-LAN. Works when the TV's "Turn on via Wi-Fi" (or "Mobile TV On") setting is enabled
     * in Settings > General > Devices / Connection.
     */
    suspend fun wake(mac: String) = withContext(Dispatchers.IO) {
        val m = mac.split(':', '-').map { it.toInt(16).toByte() }
        require(m.size == 6) { "bad MAC $mac" }
        val payload = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { m }.toByteArray()
        DatagramSocket().use { s ->
            s.broadcast = true
            repeat(3) { s.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), 9)) }
        }
    }

    fun close() { ws?.cancel(); ws = null }

    companion object { private const val TAG = "LgTv" }
}
