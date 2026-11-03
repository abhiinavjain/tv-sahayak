package com.tvsahayak.core.proto

import java.math.BigInteger
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey

/**
 * Messages of the Android TV Remote Service v2 (what the Google TV app uses), as documented by the
 * open-source androidtvremote2 project's polo.proto and remotemessage.proto.
 * Pairing runs on port 6467, remote control on port 6466, both over TLS with a client certificate.
 */
object Polo {
    const val STATUS_OK = 200L
    private const val PROTOCOL_VERSION = 2L
    private const val ENCODING_HEXADECIMAL = 3L
    private const val ROLE_INPUT = 1L

    private fun outer(field: Int, body: ProtoWriter) =
        ProtoWriter().varint(1, PROTOCOL_VERSION).varint(2, STATUS_OK).message(field, body).toByteArray()

    private fun hexEncoding() = ProtoWriter().varint(1, ENCODING_HEXADECIMAL).varint(2, 6)

    fun pairingRequest(clientName: String) = outer(10, ProtoWriter().string(1, "atvremote").string(2, clientName))
    fun options() = outer(20, ProtoWriter().message(1, hexEncoding()).varint(3, ROLE_INPUT))
    fun configuration() = outer(30, ProtoWriter().message(1, hexEncoding()).varint(2, ROLE_INPUT))
    fun secret(hash: ByteArray) = outer(40, ProtoWriter().bytes(1, hash))

    /** Status field of a reply; anything but 200 means the box refused (e.g. 402 = wrong code). */
    fun status(reply: ByteArray): Long = ProtoMessage.parse(reply).long(2) ?: 0

    /**
     * The secret proves we saw the 6-character code on the TV:
     * SHA-256(client modulus, client exponent, server modulus, server exponent, code[2..6] as bytes).
     * The first byte of the hash must equal code[0..2], which catches typos before sending.
     */
    fun secretHash(client: RSAPublicKey, server: RSAPublicKey, code: String): ByteArray? {
        val c = code.trim().uppercase()
        require(c.length == 6 && c.all { it in "0123456789ABCDEF" }) { "Code must be 6 characters 0-9 / A-F" }
        val md = MessageDigest.getInstance("SHA-256")
        md.update(unsigned(client.modulus)); md.update(unsigned(client.publicExponent))
        md.update(unsigned(server.modulus)); md.update(unsigned(server.publicExponent))
        md.update(c.substring(2).chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        val hash = md.digest()
        return if ((hash[0].toInt() and 0xFF) == c.substring(0, 2).toInt(16)) hash else null
    }

    private fun unsigned(n: BigInteger): ByteArray = n.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
}

object RemoteMsg {
    const val CONFIGURE = 1
    const val SET_ACTIVE = 2
    const val ERROR = 3
    const val PING_REQUEST = 8
    const val KEY_INJECT = 10
    const val IME_KEY_INJECT = 20
    const val IME_BATCH_EDIT = 21
    const val START = 40
    const val SET_VOLUME = 50
    private const val APP_LINK = 90
    private const val PING_RESPONSE = 9

    private const val CODE = 622L
    const val DIRECTION_SHORT = 3L

    fun configure(model: String, vendor: String) = ProtoWriter().message(CONFIGURE, ProtoWriter()
        .varint(1, CODE)
        .message(2, ProtoWriter().string(1, model).string(2, vendor).varint(3, 1).string(4, "1")
            .string(5, "atvremote").string(6, "1.0.0"))).toByteArray()

    fun setActive() = ProtoWriter().message(SET_ACTIVE, ProtoWriter().varint(1, CODE)).toByteArray()
    fun pingResponse(val1: Long) = ProtoWriter().message(PING_RESPONSE, ProtoWriter().varint(1, val1)).toByteArray()
    fun key(keyCode: Int, direction: Long = DIRECTION_SHORT) =
        ProtoWriter().message(KEY_INJECT, ProtoWriter().varint(1, keyCode.toLong()).varint(2, direction)).toByteArray()

    /** Opens a URL or app on the box. A bare package name becomes market://launch?id=..., which launches it. */
    fun appLink(linkOrPackage: String): ByteArray {
        val link = if ("://" in linkOrPackage) linkOrPackage else "market://launch?id=$linkOrPackage"
        return ProtoWriter().message(APP_LINK, ProtoWriter().string(1, link)).toByteArray()
    }

    /** Replaces the focused text field's contents (the on-screen keyboard must be open). */
    fun imeText(imeCounter: Long, fieldCounter: Long, text: String) = ProtoWriter().message(IME_BATCH_EDIT, ProtoWriter()
        .varint(1, imeCounter).varint(2, fieldCounter)
        .message(3, ProtoWriter().varint(1, 1)
            .message(2, ProtoWriter().varint(1, (text.length - 1).toLong()).varint(2, (text.length - 1).toLong()).string(3, text))))
        .toByteArray()
}
