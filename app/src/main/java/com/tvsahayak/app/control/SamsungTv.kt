package com.tvsahayak.app.control

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Samsung Smart TV (Tizen, 2016+) remote API, used when there is no Airtel box: the phone talks to the TV itself.
 * The first connection shows "Allow TV Sahayak?" on the TV; after Allow the TV hands out a token we keep.
 * Keys, typing into search boxes, the installed-app list and app launches all go over one WebSocket.
 */
class SamsungTv(private val onToken: (String) -> Unit) {

    private val _state = MutableStateFlow(AtvRemote.State.DISCONNECTED)
    val state: StateFlow<AtvRemote.State> = _state

    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
        override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    // The TV's certificate is self-signed; this client only talks to the TV address chosen on the Setup screen.
    private val http = OkHttpClient.Builder()
        .sslSocketFactory(SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }.socketFactory, trustAll)
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(4, TimeUnit.SECONDS).readTimeout(0, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val lock = Mutex()
    private var ws: WebSocket? = null
    private var wsHost: String? = null
    @Volatile private var appsReply: CompletableDeferred<List<Pair<String, String>>>? = null
    private var paused = false // Tizen has no play/pause toggle key, so we alternate

    /** Device info from http://tv:8001/api/v2/ (name, model, Wi-Fi MAC for switching on). */
    suspend fun info(host: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("http://$host:8001/api/v2/").build()).execute().use { r ->
                JSONObject(r.body!!.string()).optJSONObject("device")
            }
        }.getOrNull()
    }

    /** Opens (or reuses) the connection. With a blank [token] the TV asks the family to Allow; we wait up to [waitMs]. */
    suspend fun connect(host: String, token: String, waitMs: Long = 8_000): Boolean = lock.withLock {
        if (ws != null && wsHost == host && _state.value == AtvRemote.State.CONNECTED) return true
        ws?.cancel(); ws = null
        _state.value = AtvRemote.State.CONNECTING
        val name = Base64.encodeToString("TV Sahayak".toByteArray(), Base64.NO_WRAP)
        // 2018+ TVs use the TLS port 8002 with a token; 2016-2017 TVs only answer on plain 8001.
        for (url in listOf("wss://$host:8002/api/v2/channels/samsung.remote.control?name=$name" + if (token.isNotBlank()) "&token=$token" else "",
            "ws://$host:8001/api/v2/channels/samsung.remote.control?name=$name")) {
            val ready = CompletableDeferred<Boolean>()
            val socket = http.newWebSocket(Request.Builder().url(url).build(), listener(ready))
            if (withTimeoutOrNull(waitMs) { ready.await() } == true) {
                ws = socket; wsHost = host
                _state.value = AtvRemote.State.CONNECTED
                return true
            }
            socket.cancel()
        }
        _state.value = AtvRemote.State.DISCONNECTED
        false
    }

    private fun listener(ready: CompletableDeferred<Boolean>) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (o.optString("event")) {
                "ms.channel.connect" -> {
                    o.optJSONObject("data")?.optString("token")?.takeIf { it.isNotBlank() }?.let(onToken)
                    ready.complete(true)
                }
                "ms.channel.unauthorized", "ms.channel.timeOut" -> ready.complete(false)
                "ed.installedApp.get" -> {
                    val list = o.optJSONObject("data")?.optJSONArray("data")
                    val apps = (0 until (list?.length() ?: 0)).map { i -> list!!.getJSONObject(i).let { it.optString("appId") to it.optString("name") } }
                    appsReply?.complete(apps)
                }
            }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.i(TAG, "samsung ws failed: ${t.message}")
            ready.complete(false)
            if (ws === webSocket) { ws = null; _state.value = AtvRemote.State.DISCONNECTED }
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (ws === webSocket) { ws = null; _state.value = AtvRemote.State.DISCONNECTED }
        }
    }

    /** First-time pairing. Returns null on success, or an error message. */
    suspend fun pair(host: String): String? {
        close()
        return if (connect(host, "", waitMs = 30_000)) null
        else "TV did not allow the connection. Is the TV on? Press Allow on the TV within 30 seconds."
    }

    private fun send(json: JSONObject): Boolean = ws?.send(json.toString()) ?: false

    private fun remote(params: JSONObject) = JSONObject().put("method", "ms.remote.control").put("params", params)
    private fun emit(event: String, data: JSONObject? = null) = JSONObject().put("method", "ms.channel.emit")
        .put("params", JSONObject().put("event", event).put("to", "host").apply { data?.let { put("data", it) } })

    fun key(name: String): Boolean =
        send(remote(JSONObject().put("Cmd", "Click").put("DataOfCmd", name).put("Option", "false").put("TypeOfRemote", "SendRemoteKey")))

    fun playPause(): Boolean = key(if (paused) "KEY_PLAY" else "KEY_PAUSE").also { if (it) paused = !paused }

    /** Types into the text box on screen (works when the TV's keyboard is open, e.g. in an app's search). */
    fun text(s: String): Boolean {
        val b64 = Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP)
        return send(remote(JSONObject().put("Cmd", b64).put("DataOfCmd", "base64").put("TypeOfRemote", "SendInputString"))) &&
            send(remote(JSONObject().put("TypeOfRemote", "SendInputEnd")))
    }

    /** (appId, name) of every app on the TV. */
    suspend fun installedApps(): List<Pair<String, String>> {
        val reply = CompletableDeferred<List<Pair<String, String>>>()
        appsReply = reply
        if (!send(emit("ed.installedApp.get"))) return emptyList()
        return withTimeoutOrNull(6_000) { reply.await() } ?: emptyList()
    }

    /**
     * Launches an app; [metaTag] passes a deep link (a show or episode link, or "search_query=…" for YouTube).
     * Without a WebSocket (older TVs) the plain REST launch is used.
     */
    suspend fun launch(host: String, appId: String, metaTag: String?): Boolean {
        val data = JSONObject().put("appId", appId).put("action_type", "DEEP_LINK").put("metaTag", metaTag ?: "")
        if (send(emit("ed.apps.launch", data))) return true
        return withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url("http://$host:8001/api/v2/applications/$appId")
                    .post("".toRequestBody("application/json".toMediaType())).build()).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }
    }

    /** Wake-on-LAN; works when the TV's "Power On with Mobile" (or "Network Standby") setting is on. */
    suspend fun wake(mac: String) = WakeOnLan.send(mac)

    fun close() { ws?.cancel(); ws = null; _state.value = AtvRemote.State.DISCONNECTED }

    companion object { private const val TAG = "SamsungTv" }
}

object WakeOnLan {
    suspend fun send(mac: String) = withContext(Dispatchers.IO) {
        val m = mac.split(':', '-').map { it.toInt(16).toByte() }
        require(m.size == 6) { "bad MAC $mac" }
        val payload = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { m }.toByteArray()
        java.net.DatagramSocket().use { s ->
            s.broadcast = true
            repeat(3) {
                s.send(java.net.DatagramPacket(payload, payload.size, java.net.InetAddress.getByName("255.255.255.255"), 9))
                delay(100)
            }
        }
    }
}
