# TV Sahayak 📺🎙️

Made by Abhinav Jain.

A voice remote for the family. Say "Anupama ka latest episode chalao", "Netflix par Stranger Things chalao" or "Volume badhao" into an Android phone, and the TV does it: an Airtel Xstream box with an LG TV, or a Samsung Smart TV on its own. Commands can be Hindi, English or Hinglish.

- **Phone only.** There is no server: the phone talks to the box and TV over home Wi-Fi.
- **Safe for the box.** It uses the official Android TV Remote protocol (the same one as the Google TV app). Optional ADB is limited to an allowlist of harmless commands. There is no root, flashing or settings change, and the Airtel remote keeps working. See [docs/UNDO.md](docs/UNDO.md).
- **Mostly offline.** Built-in rules understand commands and show names (Devanagari or Latin). Claude is an opt-in last resort, and its answers are remembered for next time.
- **Made for grandma.** One huge mic button, large Hindi text, spoken Hindi replies, big volume/home/back/play buttons and a D-pad.

Setup: [docs/SETUP.md](docs/SETUP.md).

## Layout
| Path | What |
|---|---|
| `core/` | Pure Kotlin, unit-tested on the laptop: Hindi→Latin transliteration, `RuleParser` (Hinglish commands), show catalog, `Planner` (command → steps), `SafeShell` (ADB allowlist), TV Remote protocol messages and pairing math, UI-dump matching, Claude prompt/schema. |
| `app/` | Android app: `AtvRemote` (pairing on port 6467, control on 6466), `BoxAdb` (optional, via `SafeShell`), `LgTv` (webOS API), `Discovery`, speech in/out, `ClaudeFallback`, `Executor`, Compose screens. |

## Build
JDK 17 and the Android SDK (platform 35) are needed.
```bash
./gradlew :core:test :app:assembleDebug
```
