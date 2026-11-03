package com.tvsahayak.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.tvsahayak.app.ui.FamilyScreen
import com.tvsahayak.app.ui.SetupScreen
import com.tvsahayak.app.ui.SahayakTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // the wallpaper runs behind the status bar
        setContent {
            SahayakTheme {
                val setup by vm.showSetup.collectAsState()
                if (setup) SetupScreen(vm, onClose = { vm.showSetup.value = false })
                else FamilyScreen(vm, onMic = ::listen, onSetup = { vm.showSetup.value = true })
            }
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) = when (intent?.action) {
        ACTION_LISTEN -> listen()
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(vm::receiveShared) ?: Unit
        else -> Unit
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object { const val ACTION_LISTEN = "com.tvsahayak.app.LISTEN" }
}
