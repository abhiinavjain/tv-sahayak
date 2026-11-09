package com.tvsahayak.core.nlu

import com.tvsahayak.core.App
import com.tvsahayak.core.Command
import com.tvsahayak.core.EpisodeSelector
import com.tvsahayak.core.Key
import com.tvsahayak.core.VolumeDir

/** Prompt, JSON schema and result mapping for the Claude fallback. The HTTP call itself lives in the app. */
object LlmIntent {

    val SYSTEM_PROMPT = """
        You turn one spoken TV command from an Indian family into JSON. Commands may be Hindi (Devanagari or Latin
        letters), English or Hinglish, and may contain speech-recognition mistakes. The TV (an Airtel Xstream
        Android TV box, or a Samsung smart TV) has these apps: ${App.entries.joinToString { it.name }}.
        Fields:
        - action: one of play, search, open_app, channel, volume_up, volume_down, mute, key, tv_on, tv_off, unknown.
          Use channel for live TV channels (Star Plus, Colors, Zee TV, Sony, Sony SAB, ...), with the channel name as title.
        - title: for play/search, the show, movie, channel or topic, correctly spelled in English letters
          (e.g. "Anupamaa", "Bigg Boss", "cricket highlights"). Empty otherwise.
        - app: the app the user named, or the one that streams this content in India, or "" if unsure.
        - episode: latest, today, yesterday, or "" when the user didn't ask for an episode.
        - key: for action=key, one of ${Key.entries.joinToString { it.name }}.
        - steps: how many times, default 1 (volume default 2).
        Use unknown when the request is not about controlling the TV.
    """.trimIndent()

    val SCHEMA_JSON = """
        {"type":"object","additionalProperties":false,
         "required":["action","title","app","episode","key","steps"],
         "properties":{
          "action":{"type":"string","enum":["play","search","open_app","channel","volume_up","volume_down","mute","key","tv_on","tv_off","unknown"]},
          "title":{"type":"string"},
          "app":{"type":"string","enum":["", ${App.entries.joinToString { "\"${it.name}\"" }}]},
          "episode":{"type":"string","enum":["","latest","today","yesterday"]},
          "key":{"type":"string","enum":["", ${Key.entries.joinToString { "\"${it.name}\"" }}]},
          "steps":{"type":"integer"}}}
    """.trimIndent()

    fun toCommand(f: Map<String, Any?>): Command {
        val title = (f["title"] as? String).orEmpty().trim()
        val app = (f["app"] as? String)?.let { a -> App.entries.firstOrNull { it.name == a } }
        val steps = ((f["steps"] as? Number)?.toInt() ?: 1).coerceIn(1, 20)
        val episode = when (f["episode"]) {
            "latest" -> EpisodeSelector.LATEST; "today" -> EpisodeSelector.TODAY; "yesterday" -> EpisodeSelector.YESTERDAY
            else -> null
        }
        return when (f["action"]) {
            "play" -> if (title.isEmpty()) Command.Press(Key.PLAY) else Command.Play(title, app, episode)
            "search" -> Command.Search(title, app)
            "open_app" -> app?.let { Command.OpenApp(it) } ?: Command.Unknown
            "channel" -> if (title.isEmpty()) Command.Unknown else Command.Channel(title)
            "volume_up" -> Command.Volume(VolumeDir.UP, maxOf(steps, 2))
            "volume_down" -> Command.Volume(VolumeDir.DOWN, maxOf(steps, 2))
            "mute" -> Command.Volume(VolumeDir.MUTE, 1)
            "key" -> Key.entries.firstOrNull { it.name == f["key"] }?.let { Command.Press(it, steps) } ?: Command.Unknown
            "tv_on" -> Command.PowerTv(true)
            "tv_off" -> Command.PowerTv(false)
            else -> Command.Unknown
        }
    }
}
