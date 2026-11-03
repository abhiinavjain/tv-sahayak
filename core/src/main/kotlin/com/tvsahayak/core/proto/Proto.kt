package com.tvsahayak.core.proto

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** Just enough protobuf (varint + length-delimited fields) for the Android TV Remote protocol. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, v: Long) = apply { tag(field, 0); writeVarint(out, v) }
    fun bool(field: Int, v: Boolean) = varint(field, if (v) 1 else 0)
    fun bytes(field: Int, b: ByteArray) = apply { tag(field, 2); writeVarint(out, b.size.toLong()); out.write(b) }
    fun string(field: Int, s: String) = bytes(field, s.toByteArray(Charsets.UTF_8))
    fun message(field: Int, m: ProtoWriter) = bytes(field, m.toByteArray())
    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) = writeVarint(out, ((field shl 3) or wire).toLong())

    companion object {
        fun writeVarint(out: OutputStream, value: Long) {
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) { out.write(v.toInt()); return }
                out.write(((v and 0x7F) or 0x80).toInt()); v = v ushr 7
            }
        }

        /** Writes one length-prefixed message, the framing both TV Remote ports use. */
        fun writeDelimited(out: OutputStream, msg: ByteArray) {
            val buf = ByteArrayOutputStream()
            writeVarint(buf, msg.size.toLong()); buf.write(msg)
            out.write(buf.toByteArray()); out.flush()
        }

        fun readDelimited(input: InputStream): ByteArray? {
            var len = 0L; var shift = 0
            while (true) {
                val b = input.read()
                if (b < 0) return null
                len = len or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) break
                shift += 7
                if (shift > 35) throw IllegalStateException("bad length")
            }
            if (len > 1_000_000) throw IllegalStateException("message too big: $len")
            val data = ByteArray(len.toInt()); var off = 0
            while (off < data.size) {
                val n = input.read(data, off, data.size - off)
                if (n < 0) return null
                off += n
            }
            return data
        }
    }
}

/** Parsed message: field number -> values (Long for varints, ByteArray for length-delimited). */
class ProtoMessage(val fields: Map<Int, List<Any>>) {
    fun long(f: Int): Long? = fields[f]?.firstOrNull() as? Long
    fun bytes(f: Int): ByteArray? = fields[f]?.firstOrNull() as? ByteArray
    fun string(f: Int): String? = bytes(f)?.toString(Charsets.UTF_8)
    fun message(f: Int): ProtoMessage? = bytes(f)?.let { parse(it) }
    fun has(f: Int) = fields.containsKey(f)

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = LinkedHashMap<Int, MutableList<Any>>()
            var i = 0
            fun varint(): Long {
                var r = 0L; var s = 0
                while (true) {
                    val b = data[i++].toInt() and 0xFF
                    r = r or ((b and 0x7F).toLong() shl s)
                    if (b and 0x80 == 0) return r
                    s += 7
                }
            }
            while (i < data.size) {
                val tag = varint()
                val field = (tag ushr 3).toInt()
                when ((tag and 7).toInt()) {
                    0 -> fields.getOrPut(field) { mutableListOf() }.add(varint())
                    2 -> { val n = varint().toInt(); fields.getOrPut(field) { mutableListOf() }.add(data.copyOfRange(i, i + n)); i += n }
                    5 -> i += 4
                    1 -> i += 8
                    else -> throw IllegalStateException("unsupported wire type in tag $tag")
                }
            }
            return ProtoMessage(fields)
        }
    }
}
