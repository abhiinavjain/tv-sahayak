package com.tvsahayak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvsahayak.app.MainViewModel
import com.tvsahayak.app.control.Discovery
import com.tvsahayak.core.App
import com.tvsahayak.core.content.CatalogEntry

/** One-time setup, done by whoever is good with phones. Hidden behind the PIN. */
@Composable
fun SetupScreen(vm: MainViewModel, onClose: () -> Unit) {
    val s = vm.settings
    val found by vm.found.collectAsState()
    val log by vm.log.collectAsState()
    val pairing by vm.pairing.collectAsState()
    val box by vm.boxState.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val boxMsg by vm.boxMsg.collectAsState()
    val tvMsg by vm.tvMsg.collectAsState()

    var boxHost by remember { mutableStateOf(s.boxHost) }
    var tvHost by remember { mutableStateOf(s.tvHost) }
    var tvMac by remember { mutableStateOf(s.tvMac) }
    var adbPort by remember { mutableStateOf(s.adbPort.toString()) }
    var adbOn by remember { mutableStateOf(s.adbEnabled) }
    var volTv by remember { mutableStateOf(s.volumeViaTv) }
    var claudeOn by remember { mutableStateOf(s.claudeEnabled) }
    var claudeKey by remember { mutableStateOf("") }
    var claudeModel by remember { mutableStateOf(s.claudeModel) }
    var claudeLimit by remember { mutableStateOf(s.claudeDailyLimit.toString()) }
    var dryRun by remember { mutableStateOf(s.dryRun) }
    var pin by remember { mutableStateOf(s.pin) }
    var test by remember { mutableStateOf("") }
    var favourites by remember { mutableStateOf(s.extraCatalog) }
    var liveKey by remember { mutableStateOf(s.liveTvKey) }
    val channelList = remember { vm.channels() }
    var channelNumbers by remember { mutableStateOf(channelList.associate { it.name to (it.number?.toString() ?: "") }) }

    Column(Modifier.fillMaxSize().background(Palette.background).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Setup", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onClose) { Text("Done") }
        }

        Section("1. Find devices on Wi-Fi")
        Text("Keep the box and TV switched on, and the phone on the same Wi-Fi.", color = Palette.muted)
        OutlinedButton(onClick = vm::scan, enabled = !scanning) { Text(if (scanning) "Scanning… (15 s)" else "Scan") }
        if (!scanning && found.isEmpty() && log.any { it.startsWith("Scan finished") }) {
            Text("Nothing found. Type the IP addresses below instead: on the box, Settings → Network & Internet → your Wi-Fi; " +
                "on the LG TV, Settings → General → Network → Wi-Fi Connection → Advanced settings.", color = Palette.warn)
        }
        found.forEach { d ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${if (d.kind == Discovery.Kind.BOX) "📦 Box" else "📺 LG TV"}  ${d.name}  ${d.host}", Modifier.weight(1f))
                TextButton(onClick = { if (d.kind == Discovery.Kind.BOX) boxHost = d.host else tvHost = d.host }) { Text("Use") }
            }
        }

        Section("2. Airtel box (Android TV Remote) — $box")
        Field("Box IP address", boxHost) { boxHost = it }
        Text("Press Pair: a 6-character code appears on the TV. Type it in.", color = Palette.muted)
        Text("Find the box's IP on the box: Settings → Network & Internet → your Wi-Fi (or Ethernet). It is different from the TV's IP.",
            color = Palette.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.checkBox(boxHost.trim()) }, enabled = boxHost.isNotBlank()) { Text("Check") }
            Button(onClick = { vm.pairBox(boxHost.trim()) }, enabled = boxHost.isNotBlank()) { Text("Pair box") }
        }
        if (boxMsg.isNotBlank()) Text(boxMsg, color = Palette.warn)

        Section("3. Optional: ADB for auto-play")
        Text("Lets the app press Play on the right episode. Needs Developer options → Network/Wireless debugging on the box. " +
            "Only allow-listed, non-destructive commands are ever sent. Turn off anytime.", color = Palette.muted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(adbOn, { adbOn = it; if (!it) vm.disableAdb() }); Text("  Use ADB")
        }
        if (adbOn) {
            Field("ADB port (5555, or the port shown under Wireless debugging)", adbPort, KeyboardType.Number) { adbPort = it.filter(Char::isDigit) }
            Button(onClick = { s.adbPort = adbPort.toIntOrNull() ?: 5555; s.boxHost = boxHost.trim(); vm.connectAdb() }) { Text("Connect ADB & find apps") }
        }

        Section("4. LG TV (volume, power)")
        Field("LG TV IP address", tvHost) { tvHost = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.checkTv(tvHost.trim()) }, enabled = tvHost.isNotBlank()) { Text("Check") }
            Button(onClick = { vm.pairTv(tvHost.trim()) }, enabled = tvHost.isNotBlank()) { Text("Pair LG TV") }
        }
        if (tvMsg.isNotBlank()) Text(tvMsg, color = Palette.warn)
        Field("TV MAC (for switching on; filled after pairing)", tvMac) { tvMac = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(volTv, { volTv = it }); Text("  Send volume to the LG TV (if the box volume keys don't change TV volume)")
        }

        Section("TV channels (Airtel box)")
        Text("Look up each channel's number in the box's Channel Guide and type it here. Then \"Star Plus lagao\" or the " +
            "channel buttons tune it directly. Without a number, the channel opens in its app (JioHotstar / ZEE5 / SonyLIV).",
            color = Palette.muted)
        channelList.forEach { ch ->
            Field("${ch.name} (${ch.hindiName})", channelNumbers[ch.name].orEmpty(), KeyboardType.Number) { v ->
                channelNumbers = channelNumbers + (ch.name to v.filter(Char::isDigit).take(4))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(liveKey, { liveKey = it }); Text("  Press Live TV before the number (turn off if the box shows a menu instead)")
        }

        Section("5. Claude (only when offline rules fail)")
        Text("Used only for sentences the app couldn't understand by itself. Answers are remembered so the same sentence " +
            "works offline next time. Set a monthly spend limit in the Anthropic Console: a key stored on a phone can be extracted.",
            color = Palette.muted)
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(claudeOn, { claudeOn = it }); Text("  Use Claude as fallback") }
        if (claudeOn) {
            OutlinedTextField(claudeKey, { claudeKey = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (s.claudeApiKey.isBlank()) "API key" else "API key (saved — type to replace)") },
                visualTransformation = PasswordVisualTransformation())
            Field("Model", claudeModel) { claudeModel = it }
            Field("Max Claude calls per day", claudeLimit, KeyboardType.Number) { claudeLimit = it.filter(Char::isDigit) }
            Text("Used today: ${s.claudeCallsToday()}", color = Palette.muted)
        }

        Section("6. Family favourites")
        Text("Easiest: in the JioHotstar / SonyLIV / ZEE5 app on this phone, open the show → Share → TV Sahayak. " +
            "Or open the show on the website and copy its link here. Saying the show's name then opens that page on the TV.",
            color = Palette.muted)
        val shared by vm.sharedLink.collectAsState()
        FavouriteEditor(favourites, shared, onShareUsed = { vm.sharedLink.value = null }) { favourites = it; s.extraCatalog = it }

        Section("7. Test without speaking")
        Field("Type a command, e.g. Anupama ka latest episode chalao", test) { test = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.handle(listOf(test)) }, enabled = test.isNotBlank()) { Text("Run") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(dryRun, { dryRun = it; s.dryRun = it }); Text("Dry run (show steps only)") }
        }

        Section("8. Setup PIN")
        Field("PIN", pin, KeyboardType.NumberPassword) { pin = it.filter(Char::isDigit).take(8) }

        Button(onClick = {
            s.boxHost = boxHost.trim(); s.tvHost = tvHost.trim(); s.tvMac = tvMac.trim()
            s.adbPort = adbPort.toIntOrNull() ?: 5555; s.volumeViaTv = volTv
            s.claudeEnabled = claudeOn; s.claudeModel = claudeModel.trim().ifBlank { "claude-opus-5-5" }
            s.claudeDailyLimit = claudeLimit.toIntOrNull() ?: 30
            if (claudeKey.isNotBlank()) { s.claudeApiKey = claudeKey; claudeKey = "" }
            s.extraCatalog = favourites
            s.liveTvKey = liveKey
            s.channelNumbers = channelNumbers.mapNotNull { (k, v) -> v.toIntOrNull()?.let { k to it } }.toMap()
            if (pin.length >= 4) s.pin = pin
        }, Modifier.fillMaxWidth()) { Text("Save") }

        Text("Made by Abhinav Jain · अभिनव जैन द्वारा बनाया गया", color = Palette.muted, fontSize = 13.sp)

        Section("Log")
        Text(log.takeLast(60).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Palette.muted)
    }

    pairing?.let {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.submitCode(null) },
            title = { Text("Code on the TV") },
            text = {
                OutlinedTextField(code, { code = it.uppercase().filter { c -> c in "0123456789ABCDEF" }.take(6) }, singleLine = true,
                    label = { Text("6 characters") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            },
            confirmButton = { TextButton(onClick = { vm.submitCode(code) }, enabled = code.length == 6) { Text("Pair") } },
            dismissButton = { TextButton(onClick = { vm.submitCode(null) }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FavouriteEditor(list: List<CatalogEntry>, shared: String?, onShareUsed: () -> Unit, onChange: (List<CatalogEntry>) -> Unit) {
    var title by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var app by remember { mutableStateOf(App.JIOHOTSTAR) }
    var episodic by remember { mutableStateOf(true) }
    LaunchedEffect(shared) {
        if (shared != null) {
            link = shared; title = App.titleFromLink(shared); App.fromLink(shared)?.let { app = it }
            onShareUsed()
        }
    }
    list.forEach { e ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${e.title} → ${e.app.displayName}${if (e.episodic) " (serial)" else ""}${if (e.deepLink != null) " 🔗" else ""}", Modifier.weight(1f))
            TextButton(onClick = { onChange(list - e) }) { Text("Remove") }
        }
    }
    Field("Show name", title) { title = it }
    Field("Other ways people say it, comma separated (Hindi too)", aliases) { aliases = it }
    Field("Show link (optional, opens the show directly)", link, KeyboardType.Uri) { v ->
        link = v.trim(); App.fromLink(link)?.let { app = it }
        if (title.isBlank()) title = App.titleFromLink(link)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        App.entries.forEach { a ->
            TextButton(onClick = { app = a }) { Text(a.displayName.split(' ').first(), fontWeight = if (a == app) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(episodic, { episodic = it }); Text("Daily serial / has episodes")
        OutlinedButton(onClick = {
            val aliasList = aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            onChange(list.filterNot { it.title.equals(title.trim(), ignoreCase = true) } +
                CatalogEntry(title.trim(), app, aliasList, episodic, deepLink = link.takeIf { it.startsWith("http") }))
            title = ""; aliases = ""; link = ""
        }, enabled = title.isNotBlank(), modifier = Modifier.padding(start = 12.dp)) { Text("Add") }
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = type))
