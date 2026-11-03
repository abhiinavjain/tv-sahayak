package com.tvsahayak.core

import com.tvsahayak.core.content.DefaultCatalog
import com.tvsahayak.core.nlu.LlmIntent
import com.tvsahayak.core.plan.Action
import com.tvsahayak.core.plan.Capabilities
import com.tvsahayak.core.plan.Planner
import com.tvsahayak.core.plan.UiDump
import com.tvsahayak.core.proto.Polo
import com.tvsahayak.core.proto.ProtoMessage
import com.tvsahayak.core.proto.ProtoWriter
import com.tvsahayak.core.proto.RemoteMsg
import com.tvsahayak.core.safety.SafeShell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

class SafeShellTest {
    private fun ok(vararg a: String) = assertTrue(a.joinToString(" "), SafeShell.isAllowed(a.toList()))
    private fun refused(vararg a: String) = assertFalse(a.joinToString(" "), SafeShell.isAllowed(a.toList()))

    @Test fun allowsHarmlessCommands() {
        ok("input", "keyevent", "3")
        ok("input", "text", SafeShell.inputTextArg("Bigg Boss"))
        ok("input", "tap", "640", "360")
        ok("am", "start", "-a", "android.intent.action.VIEW", "-d", "https://www.netflix.com/title/80057281", "com.netflix.ninja")
        ok("uiautomator", "dump", "/dev/tty")
        ok("dumpsys", "window", "windows")
        ok("dumpsys", "package", "in.startv.hotstar")
        ok("getprop", "ro.product.model")
        ok("pm", "list", "packages")
    }

    @Test fun refusesAnythingThatChangesTheBox() {
        refused("pm", "uninstall", "com.netflix.ninja")
        refused("pm", "disable-user", "com.airtel.launcher")
        refused("pm", "clear", "in.startv.hotstar")
        refused("pm", "list", "packages", ";", "reboot")
        refused("settings", "put", "global", "adb_enabled", "0")
        refused("reboot")
        refused("reboot", "recovery")
        refused("reboot", "bootloader")
        refused("rm", "-rf", "/sdcard")
        refused("setprop", "persist.sys.foo", "1")
        refused("su")
        refused("am", "start", "-a", "android.intent.action.MASTER_CLEAR")
        refused("am", "start", "-a", "android.intent.action.FACTORY_RESET")
        refused("am", "start", "-n", "com.android.settings/.Settings")
        refused("am", "force-stop", "com.airtel.launcher")
        refused("am", "broadcast", "-a", "android.intent.action.MASTER_CLEAR")
        refused("input", "text", "a;reboot")
        refused("input", "text", "\$(reboot)")
        refused("input", "keyevent", "26 ; rm")
        refused("uiautomator", "dump", "/sdcard/x.xml")
        refused("getprop", "persist.foo")
        refused("cmd", "package", "uninstall", "x")
    }

    @Test fun quotesEveryArgument() {
        assertEquals("'am' 'start' '-d' 'https://x.com/a?b=1&c=2'",
            SafeShell.check(listOf("am", "start", "-d", "https://x.com/a?b=1&c=2")))
        assertEquals("Bigg%sBoss", SafeShell.inputTextArg("Bigg Boss!"))
    }
}

class ProtocolTest {
    @Test fun varintRoundTrip() {
        val msg = ProtoWriter().varint(1, 300).string(2, "hello").message(3, ProtoWriter().varint(1, 7)).toByteArray()
        val out = ByteArrayOutputStream(); ProtoWriter.writeDelimited(out, msg)
        val back = ProtoMessage.parse(ProtoWriter.readDelimited(ByteArrayInputStream(out.toByteArray()))!!)
        assertEquals(300L, back.long(1)); assertEquals("hello", back.string(2)); assertEquals(7L, back.message(3)!!.long(1))
    }

    @Test fun keyMessageMatchesProtobufEncoding() {
        // remote_key_inject { key_code: KEYCODE_HOME(3), direction: SHORT(3) } = field 10, len 4, 08 03 10 03
        assertEquals("52040803 1003".replace(" ", ""), RemoteMsg.key(3).joinToString("") { "%02x".format(it) })
    }

    @Test fun appLinkForPackage() {
        val m = ProtoMessage.parse(RemoteMsg.appLink("com.netflix.ninja")).message(90)!!
        assertEquals("market://launch?id=com.netflix.ninja", m.string(1))
    }

    @Test fun pairingSecret() {
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val c = gen.generateKeyPair().public as RSAPublicKey
        val s = gen.generateKeyPair().public as RSAPublicKey
        // Build the code the TV would show: first byte of the hash + 4 random hex chars.
        val tail = "A1B2"
        val md = MessageDigest.getInstance("SHA-256")
        listOf(c.modulus, c.publicExponent, s.modulus, s.publicExponent).forEach { md.update(it.toByteArray().dropWhile { b -> b == 0.toByte() }.toByteArray()) }
        md.update(byteArrayOf(0xA1.toByte(), 0xB2.toByte()))
        val code = "%02X".format(md.digest()[0]) + tail
        assertNotNull(Polo.secretHash(c, s, code))
        val wrong = "%02X".format((code.substring(0, 2).toInt(16) + 1) % 256) + tail
        assertNull(Polo.secretHash(c, s, wrong))
    }

    @Test fun pairingStatus() {
        assertEquals(200L, Polo.status(Polo.options()))
    }
}

class PlannerTest {
    private val planner = Planner(DefaultCatalog.entries)

    @Test fun netflixUsesTitleDeepLink() {
        val p = planner.plan(Command.Play("Stranger Things", App.NETFLIX), Capabilities())
        assertEquals(Action.LaunchLink("https://www.netflix.com/title/80057281", App.NETFLIX), p.first())
    }

    @Test fun latestEpisodeWithAdbSelectsEpisodeThenPlay() {
        val p = planner.plan(Command.Play("Kumkum Bhagya", App.ZEE5, EpisodeSelector.LATEST), Capabilities(adb = true))
        assertEquals(Action.LaunchApp(App.ZEE5), p.first())
        assertTrue(p.contains(Action.Type("Kumkum Bhagya")))
        val selects = p.filterIsInstance<Action.Select>()
        assertEquals(4, selects.size) // search box, show, episode, Play
        assertEquals(Planner.PLAY_LABELS, selects.last().labels)
    }

    @Test fun knownShowOpensItsPageDirectly() {
        val p = planner.plan(Command.Play("Anupamaa", App.JIOHOTSTAR, EpisodeSelector.LATEST), Capabilities())
        assertEquals(Action.LaunchLink("https://www.hotstar.com/in/shows/anupamaa/1260022017", App.JIOHOTSTAR), p.first())
        assertTrue((p.last() as Action.Say).hindi.contains("OK"))
    }

    @Test fun unknownHotstarShowUsesSearchLink() {
        val p = planner.plan(Command.Play("Jhanak", App.JIOHOTSTAR), Capabilities())
        assertEquals(Action.LaunchLink("https://www.hotstar.com/in/explore?search_query=Jhanak", App.JIOHOTSTAR), p.first())
    }

    @Test fun withoutAdbOpensAppAndAsksToChoose() {
        val p = planner.plan(Command.Play("Kumkum Bhagya", App.ZEE5, EpisodeSelector.LATEST), Capabilities())
        assertEquals(Action.LaunchApp(App.ZEE5), p.first())
        // The Search key opens Google Assistant on Android TV, so it must not be pressed or typed into.
        assertTrue(p.none { it is Action.Select || it is Action.Type || it == Action.Press(Key.SEARCH) })
        assertTrue((p.last() as Action.Say).hindi.contains("OK"))
    }

    @Test fun youtubeSearchUsesLink() {
        val p = planner.plan(Command.Search("cricket highlights", null), Capabilities())
        assertEquals(Action.LaunchLink("https://www.youtube.com/results?search_query=cricket+highlights", App.YOUTUBE), p.first())
    }

    @Test fun volumeRoute() {
        assertEquals(Action.Press(Key.VOLUME_UP, 2), planner.plan(Command.Volume(VolumeDir.UP, 2), Capabilities()).first())
        assertEquals(Action.TvVolume(VolumeDir.UP, 2),
            planner.plan(Command.Volume(VolumeDir.UP, 2), Capabilities(tv = true, volumeViaTv = true)).first())
        assertEquals(Action.TvOff, planner.plan(Command.PowerTv(false), Capabilities(tv = true)).first())
    }
}

class UiDumpTest {
    private val xml = """
        <hierarchy rotation="0">
          <node text="" content-desc="Anupamaa" clickable="true" focusable="true" bounds="[100,300][400,500]"/>
          <node text="Anupamaa: Namaste America" clickable="true" focusable="true" bounds="[420,300][720,500]"/>
          <node text="Episode 1450 · Today" clickable="true" focusable="true" bounds="[100,600][400,700]"/>
          <node text="Watch Now" clickable="true" focusable="true" bounds="[100,800][300,860]"/>
        </hierarchy>
    """.trimIndent()

    @Test fun findsExactTitleFirst() {
        val n = UiDump.find(UiDump.parse(xml), listOf("Anupamaa"))!!
        assertEquals(250, n.cx); assertEquals(400, n.cy)
    }

    @Test fun findsPlayButton() {
        assertEquals("Watch Now", UiDump.find(UiDump.parse(xml), Planner.PLAY_LABELS)!!.label)
    }

    @Test fun findsTodayEpisode() {
        assertTrue(UiDump.find(UiDump.parse(xml), listOf("Today"))!!.label.contains("Today"))
    }
}

class SharedLinkTest {
    @Test fun recognisesSharedShowLinks() {
        val text = "Watch Anupama on JioHotstar https://www.hotstar.com/in/shows/anupamaa/1260022017?utm_source=share"
        val link = App.linkIn(text)!!
        assertEquals("https://www.hotstar.com/in/shows/anupamaa/1260022017", link)
        assertEquals(App.JIOHOTSTAR, App.fromLink(link))
        assertEquals("Anupamaa", App.titleFromLink(link))
        assertEquals("Taarak Mehta Ka Ooltah Chashmah",
            App.titleFromLink("https://www.sonyliv.com/shows/taarak-mehta-ka-ooltah-chashmah-1700000084"))
        assertEquals(App.SONYLIV, App.fromLink("https://www.sonyliv.com/shows/x-1"))
        assertNull(App.fromLink("https://example.com/x"))
    }
}

class LlmIntentTest {
    @Test fun mapsJson() {
        assertEquals(Command.Play("Anupamaa", App.JIOHOTSTAR, EpisodeSelector.LATEST),
            LlmIntent.toCommand(mapOf("action" to "play", "title" to "Anupamaa", "app" to "JIOHOTSTAR", "episode" to "latest", "steps" to 1)))
        assertEquals(Command.Volume(VolumeDir.UP, 3), LlmIntent.toCommand(mapOf("action" to "volume_up", "steps" to 3)))
        assertEquals(Command.Unknown, LlmIntent.toCommand(mapOf("action" to "open_app", "app" to "")))
        assertTrue(LlmIntent.SCHEMA_JSON.contains("\"NETFLIX\""))
    }
}
