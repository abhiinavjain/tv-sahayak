# TV Sahayak: setup

One-time setup, about 15 minutes, done by whoever is comfortable with phones. Afterwards the family only uses the big mic button.

What you need: the Android phone, the Airtel Xstream box (Sercomm ASD-6101SR, Android TV 14), the LG TV, all on the **same Wi-Fi**.

## 1. Install the app on the phone
1. Copy `tv-sahayak.apk` to the phone (USB, Google Drive or WhatsApp to yourself).
2. Open it. Android asks to allow installing from this source: allow it **for that app only**.
3. Open **TV Sahayak** and allow the microphone.
4. Optional: long-press the app icon and drag the **बोलिए** shortcut to the home screen. It opens the app already listening.

## 2. Pair the Airtel box (needed)
This uses the same protocol as Google's "Google TV" remote app. Nothing on the box is changed.
1. In the app: gear icon → PIN `1234` → **Scan**. The box appears as 📦. Tap **Use**.
2. Tap **Pair box**. A 6-character code appears on the TV. Type it in and tap **Pair**.
3. The dot on the main screen turns green.

To remove: on the box, open Settings → Remotes & Accessories (or Apps → Android TV Remote Service → Clear storage, or simply forget the device). The Airtel remote keeps working the whole time.

## 3. Pair the LG TV (for TV volume and power)
1. On the TV: Settings → General → Devices → **Turn on via Wi-Fi** (sometimes called "Mobile TV On") → On. This lets the app switch the TV on.
2. In the app: **Scan**, then **Use** on 📺 LG TV (or type the TV's IP from TV Settings → Network).
3. Tap **Pair LG TV**. Press **OK/Accept** on the TV prompt within a minute.
4. Try "Volume badhao". If the box's volume keys already change the TV volume (HDMI-CEC / SimpLink), leave **Send volume to the LG TV** off. Otherwise turn it on.

To remove: on the TV, Settings → General → External Devices / Mobile connection management → delete "TV Sahayak".

## 4. Optional: ADB for automatic Play
Without this step the app opens the right app and show page and says "OK दबाइए". With it, the app also clicks the latest episode and **Play**.

The app only sends commands from a fixed allowlist: open app or link, press keys, type into search, read the screen. It never sends uninstall, disable, settings, reboot or reset commands, and the unit tests check that.

On the box:
1. Settings → Device Preferences → About → click **Build** 7 times. Developer options appear.
2. Settings → Device Preferences → Developer options:
   - If you see **Network debugging** / **ADB over network**: turn it on. Port is **5555**.
   - On Android TV 14 you may only see **Wireless debugging**. That needs a one-time pairing with a laptop. Run these on the laptop (same Wi-Fi) with Android platform-tools:
     ```bash
     adb pair <box-ip>:<pairing-port>        # use the code shown under "Pair device with pairing code"
     ```
     ```bash
     adb connect <box-ip>:<port-shown-on-the-wireless-debugging-screen>
     ```
     ```bash
     adb tcpip 5555
     ```
     After that the app can use port 5555 until the box restarts. After a restart, repeat the last two commands, or live without auto-play (everything else keeps working).
3. In the app: turn on **Use ADB** → **Connect ADB & find apps**. The TV asks "Allow USB debugging?": tick **Always allow** → OK.
4. The log lists which OTT apps were found on the box (YouTube, JioHotstar, Netflix, Prime Video, ZEE5, SonyLIV, Airtel Xstream Play).

How to undo is in [UNDO.md](UNDO.md).

## 5. Optional: Claude for unusual sentences
The app understands Hindi, English and Hinglish commands offline. If it doesn't understand a sentence and you've added a key, it asks Claude once and **remembers the answer**, so the same sentence works offline the next time.
1. Create an API key at console.anthropic.com and set a low monthly spend limit there.
2. Setup → turn on **Use Claude as fallback** → paste the key → **Save**.
3. The default model is `claude-opus-5-5`. For a cheaper option, type `claude-haiku-5-5` in the Model field. There is also a daily call limit (default 30).

Only the spoken sentence is sent: no contacts, location or other data.

## 6. Favourites
Add shows the family watches under **Family favourites**: the name, other ways people say it (Hindi spelling too, e.g. `अनुपमा, anupama`) and the app it's on. Mark daily serials so "aaj ka episode" works.

## 7. Check it works
Setup → **Test without speaking** → type a command → **Run**. With **Dry run** ticked, it only shows the steps.

| Say | What happens |
|---|---|
| Volume badhao / आवाज़ कम करो | TV volume up / down |
| TV home kholo | Home screen |
| Open YouTube / Hotstar kholo | Opens the app |
| Netflix par Stranger Things chalao | Opens Stranger Things in Netflix (+ Play with ADB) |
| Anupama ka latest episode chalao | JioHotstar → Anupamaa → latest episode (+ Play with ADB) |
| Aaj ka Bigg Boss episode play karo | JioHotstar → Bigg Boss → today's episode |
| Cricket highlights search karo | YouTube search |
| Ruko / Chalao / Wapas jao / Aage karo | Pause / play / back / fast-forward |
| TV band karo / TV chalu karo | LG TV off / on |

## Building the app (laptop)
```bash
cd ~/Desktop/voice-remote && ./gradlew :core:test :app:assembleDebug
```
The APK is at `app/build/outputs/apk/debug/app-debug.apk`.
