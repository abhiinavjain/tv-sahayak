# Undoing everything

Nothing in TV Sahayak changes the Airtel box's firmware, launcher, system apps or settings. No root, no flashing, no factory reset. The Airtel remote works the whole time. Each piece can be removed on its own:

| What | How to remove it |
|---|---|
| Box pairing (TV Remote protocol) | Box: Settings → Remotes & Accessories, remove TV Sahayak. Or Settings → Apps → System apps → Android TV Remote Service → Clear storage. This removes every paired phone. |
| ADB access | Box: Settings → Device Preferences → Developer options → turn off Network/Wireless debugging, then turn **Developer options** off. Optional: "Revoke USB debugging authorizations". |
| LG TV pairing | TV: Settings → General → External Devices → Mobile connection management (wording varies by model) → delete TV Sahayak. |
| "Turn on via Wi-Fi" on the TV | TV: Settings → General → Devices → Turn on via Wi-Fi → Off. |
| Claude key | App Setup: turn off **Use Claude as fallback**, or delete the key in the Anthropic Console. |
| The app | Long-press TV Sahayak → Uninstall. That removes its keys and settings from the phone. |
