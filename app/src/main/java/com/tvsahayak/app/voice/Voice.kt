package com.tvsahayak.app.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Push-to-talk speech recognition. Hindi (India) handles Hinglish well and keeps English
 * brand names like "Netflix" in Latin letters; up to 5 alternatives are returned for the parser to choose from.
 */
class VoiceInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    interface Callback {
        fun onListening()
        fun onPartial(text: String)
        fun onResult(alternatives: List<String>)
        fun onError(hindi: String)
    }

    fun start(language: String, cb: Callback) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { cb.onError("इस फ़ोन में आवाज़ पहचानने की सुविधा नहीं है"); return }
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = cb.onListening()
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(cb::onPartial)
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (list.isEmpty()) cb.onError("कुछ सुनाई नहीं दिया, फिर से बोलिए") else cb.onResult(list)
            }
            override fun onError(error: Int) = cb.onError(when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "कुछ सुनाई नहीं दिया, फिर से बोलिए"
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "इंटरनेट नहीं चल रहा"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक की अनुमति दीजिए"
                else -> "फिर से बोलिए"
            })
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        })
    }

    fun stop() { recognizer?.destroy(); recognizer = null }
}

/** Speaks replies in Hindi so the family hears what happened without reading the screen. */
class Speaker(context: Context) {
    private var ready = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts.language = Locale("hi", "IN")
        }
    }
    var enabled = true

    fun say(text: String) {
        if (enabled && ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
    }

    fun shutdown() = tts.shutdown()
}
