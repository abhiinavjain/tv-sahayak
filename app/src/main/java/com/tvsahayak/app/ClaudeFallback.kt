package com.tvsahayak.app

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.tvsahayak.core.Command
import com.tvsahayak.core.nlu.LlmIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Last resort for commands the offline rules didn't understand. Only runs when the family has added an API key,
 * the daily limit isn't used up and the phone is online. Sends only the spoken sentence, nothing else.
 */
class ClaudeFallback(private val settings: Settings) {
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    private val schema: JsonOutputFormat.Schema by lazy {
        val json = JSONObject(LlmIntent.SCHEMA_JSON)
        JsonOutputFormat.Schema.builder().apply {
            json.keys().forEach { k -> putAdditionalProperty(k, JsonValue.from(toPlain(json.get(k)))) }
        }.build()
    }

    fun available(): Boolean =
        settings.claudeEnabled && settings.claudeApiKey.isNotBlank() && settings.claudeCallsToday() < settings.claudeDailyLimit

    suspend fun understand(alternatives: List<String>): Command? = withContext(Dispatchers.IO) {
        if (!available()) return@withContext null
        val key = settings.claudeApiKey
        val c = client?.takeIf { clientKey == key } ?: AnthropicOkHttpClient.builder().apiKey(key).build().also { client = it; clientKey = key }
        settings.countClaudeCall()
        try {
            val params = MessageCreateParams.builder()
                .model(settings.claudeModel)
                .maxTokens(4000L)
                .system(LlmIntent.SYSTEM_PROMPT)
                .outputConfig(OutputConfig.builder()
                    .effort(OutputConfig.Effort.LOW)
                    .format(JsonOutputFormat.builder().schema(schema).build())
                    .build())
                .addUserMessage("Speech recognizer alternatives, best first:\n" + alternatives.joinToString("\n") { "- $it" })
                .build()
            val response = c.messages().create(params)
            if (response.stopReason().map { it.toString() }.orElse("") == "refusal") return@withContext null
            val text = response.content().mapNotNull { b -> b.text().map { it.text() }.orElse(null) }.joinToString("")
            val o = JSONObject(text)
            LlmIntent.toCommand(o.keys().asSequence().associateWith { o.get(it) })
        } catch (e: Exception) {
            Log.w("ClaudeFallback", "fallback failed", e)
            null
        }
    }

    private fun toPlain(v: Any?): Any? = when (v) {
        is JSONObject -> v.keys().asSequence().associateWith { toPlain(v.get(it)) }
        is org.json.JSONArray -> (0 until v.length()).map { toPlain(v.get(it)) }
        JSONObject.NULL -> null
        else -> v
    }
}
