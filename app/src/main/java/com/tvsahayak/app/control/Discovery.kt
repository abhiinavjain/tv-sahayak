package com.tvsahayak.app.control

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

/**
 * Finds the Airtel box and the LG TV on the Wi-Fi three ways, because phones and routers often drop multicast:
 * mDNS (Android TV Remote service), SSDP (LG webOS), and a sweep of the phone's /24 subnet for the
 * TV Remote port 6466 and LG's 3000/3001.
 */
class Discovery(private val context: Context) {

    data class Device(val kind: Kind, val name: String, val host: String, val via: String)
    enum class Kind { BOX, LG_TV, SAMSUNG }

    fun scan(log: (String) -> Unit): Flow<Device> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi.createMulticastLock("tvsahayak").apply { setReferenceCounted(false); acquire() }
        val executor = Executors.newSingleThreadExecutor()

        // Before Android 14 only one resolve may run at a time, so resolves are queued.
        val toResolve = LinkedBlockingQueue<NsdServiceInfo>()
        val resolver = launch(Dispatchers.IO) {
            while (true) {
                val info = runInterruptibleTake(toResolve) ?: break
                val done = java.util.concurrent.CountDownLatch(1)
                val cb = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(s: NsdServiceInfo, code: Int) { log("mDNS: could not resolve ${s.serviceName} (code $code)"); done.countDown() }
                    override fun onServiceResolved(s: NsdServiceInfo) {
                        val host = if (Build.VERSION.SDK_INT >= 34) s.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                            ?: s.hostAddresses.firstOrNull()?.hostAddress
                        else @Suppress("DEPRECATION") s.host?.hostAddress
                        if (host != null) trySend(Device(Kind.BOX, s.serviceName, host, "mDNS"))
                        done.countDown()
                    }
                }
                if (Build.VERSION.SDK_INT >= 34) nsd.resolveService(info, executor, cb) else @Suppress("DEPRECATION") nsd.resolveService(info, cb)
                done.await(4, java.util.concurrent.TimeUnit.SECONDS)
            }
        }

        val boxListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) = Unit
            override fun onDiscoveryStopped(t: String) = Unit
            override fun onStartDiscoveryFailed(t: String, code: Int) = log("mDNS search failed to start (code $code)")
            override fun onStopDiscoveryFailed(t: String, code: Int) = Unit
            override fun onServiceLost(s: NsdServiceInfo) = Unit
            override fun onServiceFound(s: NsdServiceInfo) { toResolve.offer(s) }
        }
        nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, boxListener)

        launch(Dispatchers.IO) {
            runCatching { ssdpLg().forEach { trySend(it) } }.onFailure { log("SSDP failed: ${it.message}") }
        }
        launch(Dispatchers.IO) {
            runCatching { ssdpSamsung().forEach { trySend(it) } }.onFailure { log("Samsung SSDP failed: ${it.message}") }
        }
        launch(Dispatchers.IO) {
            runCatching { sweep(log) { trySend(it) } }.onFailure { log("Network sweep failed: ${it.message}") }
        }

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(boxListener) }
            resolver.cancel(); toResolve.clear()
            lock.release(); executor.shutdown()
        }
    }

    private suspend fun runInterruptibleTake(q: LinkedBlockingQueue<NsdServiceInfo>): NsdServiceInfo? =
        kotlinx.coroutines.runInterruptible { q.take() }

    /** The phone's Wi-Fi IPv4 address and prefix, e.g. 192.168.1.23/24. */
    private fun localSubnet(): Pair<Inet4Address, Int>? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        return (la.address as Inet4Address) to la.prefixLength
    }

    private suspend fun sweep(log: (String) -> Unit, found: (Device) -> Unit) = coroutineScope {
        val (me, prefix) = localSubnet() ?: run { log("Not on Wi-Fi?"); return@coroutineScope }
        log("Checking every address on ${me.hostAddress}/$prefix …")
        val b = me.address
        // Big networks are cut down to the phone's own /24; homes are almost always /24 anyway.
        val hosts = (1..254).map { InetAddress.getByAddress(byteArrayOf(b[0], b[1], b[2], it.toByte())) }.filter { it != me }
        val gate = Semaphore(48)
        for (h in hosts) launch(Dispatchers.IO) {
            gate.withPermit {
                val ip = h.hostAddress ?: return@withPermit
                val lg = open(ip, 3001) || open(ip, 3000)
                when {
                    open(ip, 6466) || open(ip, 6467) -> found(Device(Kind.BOX, "Android TV", ip, "port 6466"))
                    // Android TV boxes with Chromecast built-in answer on 8009 even when the remote service is asleep.
                    !lg && open(ip, 8009) -> found(Device(Kind.BOX, "Maybe Airtel box (Cast)", ip, "port 8009"))
                }
                if (lg) found(Device(Kind.LG_TV, "Maybe LG TV", ip, "port 3000/3001"))
                if (!lg && open(ip, 8001)) found(Device(Kind.SAMSUNG, "Maybe Samsung TV", ip, "port 8001"))
            }
        }
    }

    /** What answers at [host]: used by the Setup screen's Check button. */
    suspend fun probe(host: String): String = coroutineScope {
        val ports = linkedMapOf(6466 to "TV Remote control", 6467 to "TV Remote pairing", 5555 to "ADB", 8009 to "Chromecast",
            3000 to "LG webOS", 3001 to "LG webOS (TLS)", 8001 to "Samsung remote API", 8002 to "Samsung remote API (TLS)")
        val open = ports.keys.map { p -> p to async(Dispatchers.IO) { open(host, p) } }
            .filter { it.second.await() }.map { it.first }
        val guess = when {
            6466 in open || 6467 in open -> "This is an Android TV box ✔ (use it as Box IP)"
            3000 in open || 3001 in open -> "This looks like the LG TV, not the box (use it as LG TV IP)"
            8001 in open || 8002 in open -> "This looks like a Samsung TV (choose \"Samsung TV\" at the top of Setup)"
            8009 in open -> "Cast device, but the TV Remote service isn't answering. Is the box on, not in standby?"
            open.isEmpty() -> "Nothing answered. Wrong address, device off, or not on this Wi-Fi"
            else -> "Unknown device"
        }
        "$host: " + (if (open.isEmpty()) "no ports open" else open.joinToString { "$it ${ports[it]}" }) + "\n→ $guess"
    }

    private fun open(host: String, port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), 600) }; true
    }.getOrDefault(false)

    private fun ssdpSamsung(): List<Device> {
        val st = "urn:samsung.com:device:RemoteControlReceiver:1"
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
                    if (!text.contains("samsung", ignoreCase = true)) continue
                    val host = p.address.hostAddress ?: continue
                    found[host] = Device(Kind.SAMSUNG, "Samsung TV", host, "SSDP")
                }
            } catch (_: SocketTimeoutException) { }
        }
        return found.values.toList()
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
                    if (!text.contains("webos", ignoreCase = true) && !text.contains("lge", ignoreCase = true)) continue
                    val host = p.address.hostAddress ?: continue
                    val name = Regex("(?im)^DLNADeviceName\\.lge\\.com:\\s*(.+)$").find(text)?.groupValues?.get(1)?.trim() ?: "LG TV"
                    found[host] = Device(Kind.LG_TV, name, host, "SSDP")
                }
            } catch (_: SocketTimeoutException) { }
        }
        return found.values.toList()
    }
}
