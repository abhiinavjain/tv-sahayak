package com.tvsahayak.app.control

import android.os.Build
import android.util.Log
import com.tvsahayak.core.proto.Polo
import com.tvsahayak.core.proto.ProtoMessage
import com.tvsahayak.core.proto.ProtoWriter
import com.tvsahayak.core.proto.RemoteMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/**
 * Android TV Remote Service v2 client: the same protocol the Google TV app uses, so it works on the stock
 * Airtel box with nothing enabled. It can press keys, open apps and links, and type into search boxes.
 */
class AtvRemote(private val certs: CertStore, private val scope: CoroutineScope) {

    enum class State { DISCONNECTED, CONNECTING, CONNECTED }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state
    /** Package of the app in front, reported by the box when a text field gets focus. */
    @Volatile var currentApp: String? = null; private set
    @Volatile var tvIsOn: Boolean? = null; private set

    private var socket: SSLSocket? = null
    private var job: Job? = null
    private var host: String? = null
    @Volatile private var imeCounter = 0L
    @Volatile private var fieldCounter = 0L

    // ---------------- Pairing (port 6467) ----------------

    /**
     * Starts pairing; the TV shows a 6-character code. [askCode] waits for the user to type it
     * (null = cancelled). Returns null on success or a short error message.
     */
    suspend fun pair(host: String, askCode: suspend () -> String?): String? = withContext(Dispatchers.IO) {
        certs.forget(host)
        try {
            (certs.socketFactory(null).createSocket() as SSLSocket).use { s ->
                s.connect(InetSocketAddress(host, 6467), 5000)
                s.soTimeout = 120_000
                s.startHandshake()
                val server = s.session.peerCertificates[0] as X509Certificate
                val out = s.outputStream; val inp = s.inputStream
                fun exchange(msg: ByteArray): Long {
                    ProtoWriter.writeDelimited(out, msg)
                    val reply = ProtoWriter.readDelimited(inp) ?: throw IOException("box closed the connection")
                    return Polo.status(reply)
                }
                if (exchange(Polo.pairingRequest("TV Sahayak (${Build.MODEL})")) != Polo.STATUS_OK) return@withContext "Box refused pairing"
                if (exchange(Polo.options()) != Polo.STATUS_OK) return@withContext "Box refused options"
                if (exchange(Polo.configuration()) != Polo.STATUS_OK) return@withContext "Box refused configuration"
                val code = askCode() ?: return@withContext "Cancelled"
                val hash = Polo.secretHash(certs.clientCert.publicKey as RSAPublicKey, server.publicKey as RSAPublicKey, code)
                    ?: return@withContext "Code galat hai (wrong code). Try again."
                if (exchange(Polo.secret(hash)) != Polo.STATUS_OK) return@withContext "Box rejected the code"
                certs.pin(host, server)
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "pairing failed", e)
            "Pairing failed: ${e.message}"
        }
    }

    // ---------------- Remote control (port 6466) ----------------

    fun connect(host: String) {
        if (this.host == host && _state.value != State.DISCONNECTED) return
        disconnect()
        this.host = host
        job = scope.launch(Dispatchers.IO) {
            var backoff = 1000L
            while (isActive) {
                try {
                    _state.value = State.CONNECTING
                    val s = certs.socketFactory(host).createSocket() as SSLSocket
                    s.connect(InetSocketAddress(host, 6466), 5000)
                    s.soTimeout = 0
                    s.startHandshake()
                    socket = s
                    backoff = 1000L
                    readLoop(s)
                } catch (e: Exception) {
                    Log.i(TAG, "connection lost: ${e.message}")
                }
                socket = null
                _state.value = State.DISCONNECTED
                delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000)
            }
        }
    }

    fun disconnect() {
        job?.cancel(); job = null
        runCatching { socket?.close() }
        socket = null; host = null
        _state.value = State.DISCONNECTED
    }

    private fun readLoop(s: SSLSocket) {
        val inp = s.inputStream
        while (true) {
            val data = ProtoWriter.readDelimited(inp) ?: throw IOException("closed")
            val m = ProtoMessage.parse(data)
            when {
                m.has(RemoteMsg.CONFIGURE) -> send(RemoteMsg.configure(Build.MODEL, Build.MANUFACTURER))
                m.has(RemoteMsg.SET_ACTIVE) -> { send(RemoteMsg.setActive()); _state.value = State.CONNECTED }
                m.has(RemoteMsg.PING_REQUEST) -> send(RemoteMsg.pingResponse(m.message(RemoteMsg.PING_REQUEST)?.long(1) ?: 0))
                m.has(RemoteMsg.IME_KEY_INJECT) -> m.message(RemoteMsg.IME_KEY_INJECT)?.message(1)?.string(12)?.let { currentApp = it }
                m.has(RemoteMsg.IME_BATCH_EDIT) -> m.message(RemoteMsg.IME_BATCH_EDIT)?.let {
                    imeCounter = it.long(1) ?: imeCounter; fieldCounter = it.long(2) ?: fieldCounter
                }
                m.has(RemoteMsg.START) -> tvIsOn = m.message(RemoteMsg.START)?.long(1) == 1L
                m.has(RemoteMsg.ERROR) -> Log.w(TAG, "box reported an error")
            }
        }
    }

    @Synchronized private fun send(msg: ByteArray) {
        val s = socket ?: throw IOException("Box not connected")
        ProtoWriter.writeDelimited(s.outputStream, msg)
    }

    val isConnected get() = _state.value == State.CONNECTED

    suspend fun awaitConnected(timeoutMs: Long = 6000) = runCatching {
        withTimeout(timeoutMs) { while (!isConnected) delay(100) }
    }.isSuccess

    suspend fun key(code: Int) = withContext(Dispatchers.IO) { send(RemoteMsg.key(code)) }
    suspend fun openLink(linkOrPackage: String) = withContext(Dispatchers.IO) { send(RemoteMsg.appLink(linkOrPackage)) }
    suspend fun type(text: String) = withContext(Dispatchers.IO) { send(RemoteMsg.imeText(imeCounter, fieldCounter, text)) }

    companion object { private const val TAG = "AtvRemote" }
}
