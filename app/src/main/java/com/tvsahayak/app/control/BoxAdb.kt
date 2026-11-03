package com.tvsahayak.app.control

import android.content.Context
import android.util.Log
import com.tvsahayak.core.App
import com.tvsahayak.core.safety.SafeShell
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Optional ADB over Wi-Fi to the box, used only to read the screen and click "Play" on the right episode.
 * Every command goes through [SafeShell]; this class has no way to run anything else.
 * Turning off Developer Options on the box removes this access completely.
 */
class BoxAdb(context: Context) {
    private val privFile = File(context.filesDir, "adbkey")
    private val pubFile = File(context.filesDir, "adbkey.pub")
    private val lock = Mutex()
    private var dadb: Dadb? = null
    private var target: Pair<String, Int>? = null

    private fun keyPair(): AdbKeyPair {
        if (!privFile.exists()) AdbKeyPair.generate(privFile, pubFile)
        return AdbKeyPair.read(privFile, pubFile)
    }

    /** First connection shows "Allow USB debugging?" on the TV; tick "Always allow". */
    suspend fun connect(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                if (target != host to port || dadb == null) {
                    runCatching { dadb?.close() }
                    dadb = Dadb.create(host, port, keyPair())
                    target = host to port
                }
                shellLocked(listOf("getprop", "ro.product.model")).trim()
            }.onFailure { runCatching { dadb?.close() }; dadb = null }
        }
    }

    fun close() { runCatching { dadb?.close() }; dadb = null }

    suspend fun shell(argv: List<String>): String = withContext(Dispatchers.IO) { lock.withLock { shellLocked(argv) } }

    private fun shellLocked(argv: List<String>): String {
        val cmd = SafeShell.check(argv) // throws for anything not on the allowlist
        val d = dadb ?: throw IllegalStateException("ADB not connected")
        val r = d.shell(cmd)
        Log.d(TAG, "$cmd -> exit ${r.exitCode}")
        return r.output
    }

    suspend fun keys(vararg codes: Int) = shell(listOf("input", "keyevent") + codes.map { it.toString() })
    suspend fun tap(x: Int, y: Int) = shell(listOf("input", "tap", x.toString(), y.toString()))
    suspend fun type(text: String) = shell(listOf("input", "text", SafeShell.inputTextArg(text)))
    suspend fun uiDump(): String = shell(listOf("uiautomator", "dump", "/dev/tty"))

    suspend fun view(uri: String, pkg: String) =
        shell(listOf("am", "start", "-a", "android.intent.action.VIEW", "-d", uri, pkg))

    suspend fun launch(pkg: String) =
        shell(listOf("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LEANBACK_LAUNCHER", pkg))

    suspend fun foregroundPackage(): String? =
        Regex("mCurrentFocus=.*? ([a-zA-Z0-9_.]+)/").find(shell(listOf("dumpsys", "window", "windows")))?.groupValues?.get(1)

    /** Which of each app's candidate packages are installed (read-only). */
    suspend fun resolvePackages(): Map<App, String> {
        val installed = shell(listOf("pm", "list", "packages")).lines().map { it.removePrefix("package:").trim() }.toSet()
        val result = mutableMapOf<App, String>()
        for (app in App.entries) {
            app.packages.firstOrNull { it in installed }?.let { result[app] = it; continue }
            if (app == App.AIRTEL_XSTREAM) installed.firstOrNull { "xstream" in it || "wynk" in it }?.let { result[app] = it }
        }
        return result
    }

    companion object { private const val TAG = "BoxAdb" }
}
