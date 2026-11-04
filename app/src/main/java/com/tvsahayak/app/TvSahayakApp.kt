package com.tvsahayak.app

import android.app.Application
import com.tvsahayak.app.control.AtvRemote
import com.tvsahayak.app.control.BoxAdb
import com.tvsahayak.app.control.CertStore
import com.tvsahayak.app.control.Discovery
import com.tvsahayak.app.control.EpisodeFinder
import com.tvsahayak.app.control.LgTv
import com.tvsahayak.app.exec.Executor
import com.tvsahayak.app.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Holds the long-lived connections so they survive screen rotation and screen switches. */
class TvSahayakApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settings: Settings; private set
    lateinit var remote: AtvRemote; private set
    lateinit var adb: BoxAdb; private set
    lateinit var tv: LgTv; private set
    lateinit var executor: Executor; private set
    lateinit var claude: ClaudeFallback; private set
    lateinit var speaker: Speaker; private set
    lateinit var discovery: Discovery; private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        remote = AtvRemote(CertStore(this), scope)
        adb = BoxAdb(this)
        tv = LgTv { key -> settings.tvClientKey = key }
        executor = Executor(settings, remote, adb, tv, EpisodeFinder(this))
        claude = ClaudeFallback(settings)
        speaker = Speaker(this)
        discovery = Discovery(this)
        if (settings.boxHost.isNotBlank()) remote.connect(settings.boxHost)
    }
}
