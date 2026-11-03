package com.tvsahayak.app.control

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

/** Finds the Airtel box (mDNS, Android TV Remote service) and the LG TV (SSDP, webOS second screen) on the Wi-Fi. */
class Discovery(private val context: Context) {

    data class Device(val kind: Kind, val name: String, val host: String)
    enum class Kind { BOX, LG_TV }

    fun scan(): Flow<Device> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi.createMulticastLock("tvsahayak").apply { setReferenceCounted(false); acquire() }
        val executor = Executors.newSingleThreadExecutor()

        val boxListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) = Unit
            override fun onDiscoveryStopped(t: String) = Unit
            override fun onStartDiscoveryFailed(t: String, code: Int) = Unit
            override fun onStopDiscoveryFailed(t: String, code: Int) = Unit
            override fun onServiceLost(s: NsdServiceInfo) = Unit
            override fun onServiceFound(s: NsdServiceInfo) {
                val cb = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo, code: Int) = Unit
                    override fun onServiceResolved(s: NsdServiceInfo) {
                        val host = if (Build.VERSION.SDK_INT >= 34) s.hostAddresses.firstOrNull()?.hostAddress
                        else @Suppress("DEPRECATION") s.host?.hostAddress
                        if (host != null) trySend(Device(Kind.BOX, s.serviceName, host))
                    }
                }
                if (Build.VERSION.SDK_INT >= 34) nsd.resolveService(s, executor, cb) else @Suppress("DEPRECATION") nsd.resolveService(s, cb)
            }
        }
        nsd.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, boxListener)

        launch(Dispatchers.IO) { ssdpLg().forEach { trySend(it) } }

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(boxListener) }
            lock.release(); executor.shutdown()
        }
    }

    private fun ssdpLg(): List<Device> {
        val st = "urn:lge-com:service:webos-second-screen:1"
        val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $st\r\n\r\n".toByteArray()
        val found = LinkedHashMap<String, Device>()
        DatagramSocket().use { s ->
            s.soTimeout = 3000
            repeat(2) { s.send(DatagramPacket(msg, msg.size, InetAddress.getByName("239.255.255.250"), 1900)) }
            val buf = ByteArray(2048)
            try {
                while (true) {
                    val p = DatagramPacket(buf, buf.size); s.receive(p)
                    val text = String(p.data, 0, p.length)
                    if (!text.contains("webos", ignoreCase = true) && !text.contains("LG", ignoreCase = false)) continue
                    val host = p.address.hostAddress ?: continue
                    val name = Regex("(?im)^DLNADeviceName\\.lge\\.com:\\s*(.+)$").find(text)?.groupValues?.get(1)?.trim() ?: "LG TV"
                    found[host] = Device(Kind.LG_TV, name, host)
                }
            } catch (_: SocketTimeoutException) { }
        }
        return found.values.toList()
    }
}
