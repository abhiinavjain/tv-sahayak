package com.tvsahayak.core.content

import com.tvsahayak.core.App

/**
 * Live TV channels on the Airtel Xstream box. [number] is the box's channel number (see its Channel Guide);
 * the family fills these in on the Setup screen. Without a number, the channel's streaming app is opened instead.
 */
data class Channel(
    val name: String,
    val hindiName: String,
    val aliases: List<String>,
    val app: App,
    val number: Int? = null,
)

object DefaultChannels {
    val list = listOf(
        Channel("Star Plus", "स्टार प्लस", listOf("star plus", "starplus", "स्टार प्लस", "स्टारप्लस"), App.JIOHOTSTAR),
        Channel("Colors", "कलर्स", listOf("colors", "colours", "colors tv", "कलर्स", "कलर्स टीवी", "कलर"), App.JIOHOTSTAR),
        Channel("Zee TV", "ज़ी टीवी", listOf("zee tv", "ज़ी टीवी", "जी टीवी", "zee tv hd"), App.ZEE5, 109),
        Channel("Sony TV", "सोनी टीवी", listOf("sony", "sony tv", "sony entertainment", "सोनी", "सोनी टीवी"), App.SONYLIV),
        Channel("Sony SAB", "सोनी सब", listOf("sony sab", "sab tv", "सोनी सब", "सब टीवी"), App.SONYLIV),
        Channel("Star Bharat", "स्टार भारत", listOf("star bharat", "स्टार भारत"), App.JIOHOTSTAR),
        Channel("And TV", "एंड टीवी", listOf("and tv", "&tv", "एंड टीवी"), App.ZEE5),
        Channel("DD National", "डीडी नेशनल", listOf("dd national", "doordarshan", "दूरदर्शन", "डीडी नेशनल"), App.YOUTUBE),
        Channel("Aastha", "आस्था", listOf("aastha", "astha", "आस्था"), App.YOUTUBE),
    )
}
