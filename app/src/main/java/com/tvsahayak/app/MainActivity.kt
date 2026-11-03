package com.tvsahayak.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        setContent {
            SahayakTheme {
                var setup by remember { mutableStateOf(false) }
                if (setup) SetupScreen(vm, onClose = { setup = false })
                else FamilyScreen(vm, onMic = ::listen, onSetup = { setup = true })
            }
        }
        if (intent?.action == ACTION_LISTEN) listen()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_LISTEN) listen()
    }

    private fun listen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startListening()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object { const val ACTION_LISTEN = "com.tvsahayak.app.LISTEN" }
}
