package com.tvsahayak.core.safety

/**
 * The only door to `adb shell` on the Airtel box. Commands are built as argument lists and checked against
 * an allowlist of harmless, read-only or input-only operations. Everything else (uninstall, disable, settings,
 * reboot, rm, setprop, su, recovery, ...) is refused, so the box can never be modified through this app.
 */
object SafeShell {

    class Refused(val argv: List<String>, reason: String) : IllegalArgumentException("Refused '${argv.joinToString(" ")}': $reason")

    private val safeArg = Regex("^[A-Za-z0-9_./:=?&%,+@#~\\-]*$")
    private val blockedWords = listOf("settings", "factory", "master_clear", "reset", "wipe", "reboot", "recovery",
        "bootloader", "uninstall", "disable", "install", "clear", "setprop", "su", "rm", "mount", "chmod", "format", "master")
    private val amFlags = setOf("-a", "-d", "-n", "-c", "-f", "-t", "--es", "--ez", "--ei", "-W", "-e")
    private val dumpTargets = setOf(listOf("window", "windows"), listOf("window"), listOf("activity", "activities"),
        listOf("media_session"))

    /** Throws [Refused] unless [argv] is allowed. Returns the shell string to send. */
    fun check(argv: List<String>): String {
        if (argv.isEmpty()) throw Refused(argv, "empty")
        argv.forEach { if (!safeArg.matches(it)) throw Refused(argv, "unsafe characters in '$it'") }
        val (cmd, rest) = argv.first() to argv.drop(1)
        val ok = when (cmd) {
            "input" -> checkInput(rest)
            "am" -> checkAm(rest)
            "uiautomator" -> rest == listOf("dump", "/dev/tty")
            "dumpsys" -> rest in dumpTargets || (rest.size == 2 && rest[0] == "package" && isPackage(rest[1]))
            "getprop" -> rest.isEmpty() || (rest.size == 1 && rest[0].startsWith("ro."))
            "pm" -> rest == listOf("list", "packages")
            else -> false
        }
        if (!ok) throw Refused(argv, "not on the allowlist")
        // Single quotes make '&', '?' and '%' in URLs literal; safeArg already forbids quotes inside args.
        return argv.joinToString(" ") { "'$it'" }
    }

    fun isAllowed(argv: List<String>): Boolean = runCatching { check(argv) }.isSuccess

    private fun checkInput(rest: List<String>): Boolean = when (rest.firstOrNull()) {
        "keyevent" -> rest.size in 2..21 && rest.drop(1).all { it.toIntOrNull() in 0..300 }
        "tap" -> rest.size == 3 && rest.drop(1).all { it.toIntOrNull() in 0..8000 }
        "text" -> rest.size == 2 && rest[1].length <= 100
        else -> false
    }

    private fun checkAm(rest: List<String>): Boolean {
        if (rest.firstOrNull() != "start") return false
        var i = 1
        while (i < rest.size) {
            val flag = rest[i]
            if (i == rest.size - 1 && isPackage(flag)) return true // trailing package name
            if (flag !in amFlags) return false
            val takes = when (flag) { "-W" -> 0; "--es", "--ez", "--ei", "-e" -> 2; else -> 1 }
            if (takes > 0 && i + takes >= rest.size) return false
            val values = rest.subList(i + 1, minOf(rest.size, i + 1 + takes))
            if (values.any { v -> !v.startsWith("http") && v.lowercase().split(Regex("[^a-z]+")).any { it in blockedWords } }) return false
            if (flag == "-a" && !values[0].startsWith("android.intent.action.") ) return false
            if (flag == "-a" && values[0] !in setOf("android.intent.action.VIEW", "android.intent.action.MAIN",
                    "android.intent.action.SEARCH")) return false
            if (flag == "-n" && values[0].lowercase().let { it.startsWith("com.android.settings") || it.contains("tvsettings") }) return false
            i += 1 + takes
        }
        return true
    }

    private fun isPackage(s: String) = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$").matches(s)

    /** `input text` wants spaces as %s and no shell specials; keep letters, digits and spaces only. */
    fun inputTextArg(text: String): String =
        text.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(100).replace(" ", "%s")
}
