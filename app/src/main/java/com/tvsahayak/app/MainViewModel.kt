package com.tvsahayak.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvsahayak.app.control.AtvRemote
import com.tvsahayak.app.control.Discovery
import com.tvsahayak.app.voice.VoiceInput
import com.tvsahayak.core.App
import com.tvsahayak.core.Command
import com.tvsahayak.core.Key
import com.tvsahayak.core.ParseResult
import com.tvsahayak.core.content.CatalogEntry
import com.tvsahayak.core.content.DefaultCatalog
import com.tvsahayak.core.nlu.RuleParser
import com.tvsahayak.core.plan.Planner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TvSahayakApp
    val settings get() = app.settings
    val boxState: StateFlow<AtvRemote.State> = app.remote.state

    enum class Phase { IDLE, LISTENING, WORKING, DONE, ERROR }

    data class UiState(
        val phase: Phase = Phase.IDLE,
        val heard: String = "",
        val reply: String = "बटन दबाकर बोलिए",
        val busyLabel: String = "",
    )

    private val _ui = MutableStateFlow(UiState())
    val ui = _ui.asStateFlow()

    private val _log = MutableStateFlow(listOf<String>())
    val log = _log.asStateFlow()

    private val voice = VoiceInput(application)

    init { app.remote.onLog = { log(it) } }

    private fun catalog(): List<CatalogEntry> = DefaultCatalog.entries + settings.extraCatalog
    private fun log(line: String) = _log.update { (it + line).takeLast(200) }

    // ---------------- Voice flow ----------------

    fun startListening() {
        if (_ui.value.phase == Phase.LISTENING) { voice.stop(); _ui.update { it.copy(phase = Phase.IDLE) }; return }
        voice.start("hi-IN", object : VoiceInput.Callback {
            override fun onListening() = _ui.update { UiState(phase = Phase.LISTENING, reply = "बोलिए… सुन रहे हैं") }
            override fun onPartial(text: String) = _ui.update { it.copy(heard = text) }
            override fun onResult(alternatives: List<String>) = handle(alternatives)
            override fun onError(hindi: String) = _ui.update { it.copy(phase = Phase.ERROR, reply = hindi) }
        })
    }

    /** Also used by the Setup screen's "type a command" box. */
    fun handle(alternatives: List<String>) {
        viewModelScope.launch {
            _ui.update { it.copy(phase = Phase.WORKING, heard = alternatives.first(), reply = "समझ रहे हैं…") }
            val parser = RuleParser(catalog(), settings.learned)
            val best: Pair<String, ParseResult> = alternatives.map { it to parser.parse(it) }.maxBy { it.second.confidence }
            log("heard: ${alternatives.joinToString(" | ")}")
            log("rules: ${best.second.command} (${"%.2f".format(best.second.confidence)} ${best.second.note})")

            var cmd = best.second.command
            if (best.second.confidence < 0.6 && app.claude.available()) {
                _ui.update { it.copy(reply = "सोच रहे हैं…") }
                val llm = app.claude.understand(alternatives)
                log("claude: $llm")
                if (llm != null && llm != Command.Unknown) {
                    cmd = llm
                    if (llm is Command.Play || llm is Command.Search || llm is Command.OpenApp) {
                        settings.learned = settings.learned + (parser.key(best.first) to llm)
                    }
                }
            }
            run(cmd)
        }
    }

    fun press(key: Key) = viewModelScope.launch { run(Command.Press(key)) }
    fun openApp(a: App) = viewModelScope.launch { run(Command.OpenApp(a)) }
    fun command(c: Command) = viewModelScope.launch { run(c) }

    private suspend fun run(cmd: Command) {
        val plan = Planner(catalog()).plan(cmd, app.executor.capabilities())
        val out = app.executor.run(plan)
        out.log.forEach { log("  $it") }
        val reply = out.error ?: out.say ?: "ठीक है"
        if (cmd !is Command.Press) app.speaker.say(reply)
        _ui.update { it.copy(phase = if (out.error != null || cmd == Command.Unknown) Phase.ERROR else Phase.DONE, reply = reply) }
    }

    // ---------------- Setup ----------------

    private var scanJob: Job? = null
    private val _found = MutableStateFlow(listOf<Discovery.Device>())
    val found = _found.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    fun scan() {
        scanJob?.cancel()
        _found.value = emptyList()
        _scanning.value = true
        log("Scan started")
        scanJob = viewModelScope.launch {
            withTimeoutOrNull(15_000) {
                app.discovery.scan(::log).collect { d ->
                    _found.update { l ->
                        val same = l.firstOrNull { it.host == d.host && it.kind == d.kind }
                        when {
                            same == null -> { log("found ${d.kind} ${d.name} ${d.host} (${d.via})"); l + d }
                            // A real name from mDNS/SSDP beats the sweep's guess.
                            same.via.startsWith("port") && !d.via.startsWith("port") -> l - same + d
                            else -> l
                        }
                    }
                }
            }
            _scanning.value = false
            log("Scan finished: ${_found.value.size} device(s)")
        }
    }

    /** Pairing code entry, shown as a dialog while [pairingCode] is non-null. */
    private val _pairing = MutableStateFlow<CompletableDeferred<String?>?>(null)
    val pairing = _pairing.asStateFlow()
    fun submitCode(code: String?) { _pairing.value?.complete(code); _pairing.value = null }

    /** Latest result line for the box and TV sections of Setup. */
    private val _boxMsg = MutableStateFlow("")
    val boxMsg = _boxMsg.asStateFlow()
    private val _tvMsg = MutableStateFlow("")
    val tvMsg = _tvMsg.asStateFlow()
    private fun boxSays(m: String) { _boxMsg.value = m; log("box: $m") }
    private fun tvSays(m: String) { _tvMsg.value = m; log("tv: $m") }

    fun checkBox(host: String) = viewModelScope.launch { boxSays("Checking $host …"); boxSays(app.discovery.probe(host)) }
    fun checkTv(host: String) = viewModelScope.launch { tvSays("Checking $host …"); tvSays(app.discovery.probe(host)) }

    fun pairBox(host: String) = viewModelScope.launch {
        boxSays("Connecting to $host … a code should appear on the TV")
        app.remote.disconnect()
        val err = app.remote.pair(host) {
            boxSays("Type the code shown on the TV")
            val d = CompletableDeferred<String?>(); _pairing.value = d; d.await()
        }
        if (err == null) {
            settings.boxHost = host
            app.remote.connect(host)
            boxSays(if (app.remote.awaitConnected(8000)) "Box paired and connected ✔" else "Paired ✔ but control connection not up yet; retrying in background")
        } else {
            boxSays("Pairing failed: $err")
            boxSays(_boxMsg.value + "\n" + app.discovery.probe(host))
        }
    }

    fun connectAdb() = viewModelScope.launch {
        settings.adbEnabled = true
        log("ADB: connecting to ${settings.boxHost}:${settings.adbPort} — accept the prompt on the TV (tick Always allow)")
        app.adb.connect(settings.boxHost, settings.adbPort)
            .onSuccess { model ->
                settings.boxModel = model
                app.executor.adbReady = true
                val pk = app.adb.resolvePackages()
                settings.resolvedPackages = pk
                log("ADB connected ✔ model=$model; apps: ${pk.entries.joinToString { "${it.key.displayName}=${it.value}" }}")
                val missing = App.entries.filter { it !in pk }
                if (missing.isNotEmpty()) log("not installed / not found: ${missing.joinToString { it.displayName }}")
            }
            .onFailure { log("ADB failed: ${it.message}. On Android TV 14, see docs/SETUP.md (Wireless debugging).") }
    }

    fun disableAdb() {
        settings.adbEnabled = false
        app.executor.adbReady = false
        app.adb.close()
        log("ADB turned off in the app. To remove access fully, turn off Developer options on the box.")
    }

    fun pairTv(host: String) = viewModelScope.launch {
        tvSays("Press OK / Allow on the TV prompt (within 60 s)…")
        settings.tvHost = host
        val err = app.tv.pair(host)
        if (err != null) { tvSays("$err\n" + app.discovery.probe(host)); return@launch }
        val mac = app.tv.macAddress(host, settings.tvClientKey)?.also { settings.tvMac = it }
        tvSays("LG TV paired ✔" + (mac?.let { " (MAC $it saved for switching on)" } ?: ""))
    }

    override fun onCleared() { voice.stop() }
}
