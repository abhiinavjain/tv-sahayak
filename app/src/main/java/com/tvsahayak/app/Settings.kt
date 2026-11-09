package com.tvsahayak.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.tvsahayak.core.App
import com.tvsahayak.core.Command
import com.tvsahayak.core.EpisodeSelector
import com.tvsahayak.core.content.CatalogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Everything set up once on the Setup screen. Plain prefs, except the Claude key, which is Keystore-encrypted. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Which home this phone controls: "box_lg" (Airtel box + LG TV) or "samsung" (a Samsung TV on its own). */
    var tvMode by str("tv_mode", "box_lg")
    val isSamsung get() = tvMode == "samsung"
    var samsungHost by str("samsung_host")
    var samsungToken by str("samsung_token")
    var samsungMac by str("samsung_mac")
    /** App ID on the Samsung TV for each streaming app, read from the TV's installed-app list. */
    var samsungApps: Map<App, String>
        get() = runCatching {
            val o = JSONObject(p.getString("samsung_apps", "{}")!!)
            o.keys().asSequence().mapNotNull { k -> App.entries.firstOrNull { it.name == k }?.let { it to o.getString(k) } }.toMap()
        }.getOrDefault(emptyMap())
        set(v) = p.edit().putString("samsung_apps", JSONObject(v.mapKeys { it.key.name }).toString()).apply()
    var boxHost by str("box_host")
    var boxName by str("box_name")
    var boxModel by str("box_model")
    var tvHost by str("tv_host")
    var tvMac by str("tv_mac")
    var tvClientKey by str("tv_client_key")
    var adbEnabled by bool("adb_enabled", false)
    var adbPort by int("adb_port", 5555)
    var volumeViaTv by bool("volume_via_tv", false)
    var claudeEnabled by bool("claude_enabled", false)
    var claudeModel by str("claude_model", "claude-opus-5-5")
    var claudeDailyLimit by int("claude_daily_limit", 30)
    var dryRun by bool("dry_run", false)
    var liveTvKey by bool("live_tv_key", true)
    var wallpaper by str("wallpaper", "ROYAL")
    /** Channel numbers on the Airtel box, by channel name (e.g. "Zee TV" -> 109). */
    var channelNumbers: Map<String, Int>
        get() = runCatching {
            val o = JSONObject(p.getString("channel_numbers", "{}")!!)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
        set(v) = p.edit().putString("channel_numbers", JSONObject(v).toString()).apply()
    var pin by str("pin", "1234")
    /** Installed package for each app, found through `pm list packages` (ADB) on setup. */
    var resolvedPackages: Map<App, String>
        get() = runCatching {
            val o = JSONObject(p.getString("packages", "{}")!!)
            o.keys().asSequence().mapNotNull { k -> App.entries.firstOrNull { it.name == k }?.let { it to o.getString(k) } }.toMap()
        }.getOrDefault(emptyMap())
        set(v) = p.edit().putString("packages", JSONObject(v.mapKeys { it.key.name }).toString()).apply()

    fun packageFor(app: App): String = resolvedPackages[app] ?: app.packages.first()

    // Claude usage counter, reset each day.
    fun claudeCallsToday(): Int = if (p.getString("claude_day", "") == today()) p.getInt("claude_calls", 0) else 0
    fun countClaudeCall() = p.edit().putString("claude_day", today()).putInt("claude_calls", claudeCallsToday() + 1).apply()
    private fun today() = java.time.LocalDate.now().toString()

    var claudeApiKey: String
        get() = p.getString("claude_key", null)?.let { runCatching { decrypt(it) }.getOrNull() }.orEmpty()
        set(v) = p.edit().putString("claude_key", if (v.isBlank()) null else encrypt(v.trim())).apply()

    /** Family favourites added on the Setup screen, on top of the built-in catalog. */
    var extraCatalog: List<CatalogEntry>
        get() = runCatching {
            val a = JSONArray(p.getString("catalog", "[]"))
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                CatalogEntry(o.getString("title"), App.valueOf(o.getString("app")),
                    o.optString("aliases").split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    episodic = o.optBoolean("episodic"), deepLink = o.optString("link").ifBlank { null })
            }
        }.getOrDefault(emptyList())
        set(v) = p.edit().putString("catalog", JSONArray(v.map {
            JSONObject().put("title", it.title).put("app", it.app.name).put("aliases", it.aliases.joinToString(","))
                .put("episodic", it.episodic).put("link", it.deepLink ?: "")
        }).toString()).apply()

    /** Phrases Claude understood, so they work offline next time. */
    var learned: Map<String, Command>
        get() = runCatching {
            val o = JSONObject(p.getString("learned", "{}")!!)
            o.keys().asSequence().mapNotNull { k -> decodeCommand(o.getJSONObject(k))?.let { k to it } }.toMap()
        }.getOrDefault(emptyMap())
        set(v) = p.edit().putString("learned", JSONObject().apply {
            v.forEach { (k, c) -> encodeCommand(c)?.let { put(k, it) } }
        }.toString()).apply()

    private fun encodeCommand(c: Command): JSONObject? = when (c) {
        is Command.Play -> JSONObject().put("t", "play").put("title", c.title).put("app", c.app?.name ?: "").put("ep", c.episode?.name ?: "")
        is Command.Search -> JSONObject().put("t", "search").put("title", c.query).put("app", c.app?.name ?: "")
        is Command.OpenApp -> JSONObject().put("t", "open").put("app", c.app.name)
        else -> null // controls are cheap for the rules; only content is worth remembering
    }

    private fun decodeCommand(o: JSONObject): Command? {
        val app = App.entries.firstOrNull { it.name == o.optString("app") }
        return when (o.getString("t")) {
            "play" -> Command.Play(o.getString("title"), app, EpisodeSelector.entries.firstOrNull { it.name == o.optString("ep") })
            "search" -> Command.Search(o.getString("title"), app)
            "open" -> app?.let { Command.OpenApp(it) }
            else -> null
        }
    }

    // --- Keystore helpers ---
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry("tvsahayak", null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("tvsahayak", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(s: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(c.iv + c.doFinal(s.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(s: String): String {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12)) }
        return String(c.doFinal(b, 12, b.size - 12))
    }

    // --- property delegates ---
    private fun str(k: String, def: String = "") = object : kotlin.properties.ReadWriteProperty<Any, String> {
        override fun getValue(thisRef: Any, property: kotlin.reflect.KProperty<*>) = p.getString(k, def) ?: def
        override fun setValue(thisRef: Any, property: kotlin.reflect.KProperty<*>, value: String) = p.edit().putString(k, value).apply()
    }
    private fun bool(k: String, def: Boolean) = object : kotlin.properties.ReadWriteProperty<Any, Boolean> {
        override fun getValue(thisRef: Any, property: kotlin.reflect.KProperty<*>) = p.getBoolean(k, def)
        override fun setValue(thisRef: Any, property: kotlin.reflect.KProperty<*>, value: Boolean) = p.edit().putBoolean(k, value).apply()
    }
    private fun int(k: String, def: Int) = object : kotlin.properties.ReadWriteProperty<Any, Int> {
        override fun getValue(thisRef: Any, property: kotlin.reflect.KProperty<*>) = p.getInt(k, def)
        override fun setValue(thisRef: Any, property: kotlin.reflect.KProperty<*>, value: Int) = p.edit().putInt(k, value).apply()
    }
}
