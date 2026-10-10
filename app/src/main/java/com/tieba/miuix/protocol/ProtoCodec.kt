// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import java.io.ByteArrayOutputStream

/**
 * 一个极简的 protobuf (proto3) 写出器。
 *
 * 只实现贴吧 APP 端接口实际用到的 wire type：
 *  -0 Varint       (int32 / int64 / uint32 / uint64 / bool / enum)
 *  -2 Length-delimited (string / bytes / message)
 */
internal class ProtoWriter(initialCapacity: Int = 256) {
    private val out = ByteArrayOutputStream(initialCapacity)

    fun toByteArray(): ByteArray = out.toByteArray()

    fun writeVarint(value: Long) {
        var v = value
        while (true) {
            if (v and LONG_INV_7BIT == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    fun writeTag(field: Int, wireType: Int) {
        writeVarint(((field shl 3) or wireType).toLong())
    }

    /** proto3 中默认值不会被序列化，这里保持一致，避免多余的字节。 */
    fun int32(field: Int, value: Int) {
        if (value == 0) return
        writeTag(field, WIRE_VARINT)
        writeVarint(value.toLong() and 0xFFFFFFFFL)
    }

    fun uint32(field: Int, value: Int) = int32(field, value)

    fun int64(field: Int, value: Long) {
        if (value == 0L) return
        writeTag(field, WIRE_VARINT)
        writeVarint(value)
    }

    fun bool(field: Int, value: Boolean) = int32(field, if (value) 1 else 0)

    fun string(field: Int, value: String) {
        if (value.isEmpty()) return
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeTag(field, WIRE_LENGTH_DELIMITED)
        writeVarint(bytes.size.toLong())
        out.write(bytes, 0, bytes.size)
    }

    fun bytes(field: Int, value: ByteArray) {
        if (value.isEmpty()) return
        writeTag(field, WIRE_LENGTH_DELIMITED)
        writeVarint(value.size.toLong())
        out.write(value, 0, value.size)
    }

    /** 写入一个子消息字段，即使内容为空也会写入（贴吧部分接口依赖字段存在）。 */
    fun message(field: Int, block: ProtoWriter.() -> Unit) {
        val child = ProtoWriter()
        child.block()
        val bytes = child.toByteArray()
        writeTag(field, WIRE_LENGTH_DELIMITED)
        writeVarint(bytes.size.toLong())
        out.write(bytes, 0, bytes.size)
    }

    fun double(field: Int, value: Double) {
        if (value == 0.0) return
        writeTag(field, WIRE_FIXED64)
        val bits = value.toRawBits()
        for (i in 0 until 8) out.write(((bits ushr (i * 8)) and 0xFF).toInt())
    }

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5

        private const val LONG_INV_7BIT = -128L
    }
}

/**
 * 与 [ProtoWriter] 对应的极简 protobuf 读取器。
 */
internal class ProtoReader(
    private val buf: ByteArray,
    private var pos: Int = 0,
    private val limit: Int = buf.size,
) {
    val hasMore: Boolean get() = pos < limit

    fun readTag(): Int = readVarint().toInt()

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= limit) return result
            val b = buf[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 63) return result
        }
    }

    fun readInt32(): Int = readVarint().toInt()

    fun readInt64(): Long = readVarint()

    fun readUInt32(): Int = readVarint().toInt()

    fun readBool(): Boolean = readVarint() != 0L

    fun readDouble(): Double {
        var bits = 0L
        for (i in 0 until 8) {
            val b = if (pos < limit) buf[pos++].toInt() and 0xFF else 0
            bits = bits or (b.toLong() shl (i * 8))
        }
        return Double.fromBits(bits)
    }

    fun readLengthDelimited(): ByteArray {
        val len = readVarint().toInt()
        val end = (pos + len).coerceAtMost(limit)
        val slice = buf.copyOfRange(pos, end)
        pos = end
        return slice
    }

    fun readString(): String = String(readLengthDelimited(), Charsets.UTF_8)

    /** 返回一个针对嵌套消息的 sub reader。 */
    fun readMessage(): ProtoReader {
        val len = readVarint().toInt()
        val end = (pos + len).coerceAtMost(limit)
        val sub = ProtoReader(buf, pos, end)
        pos = end
        return sub
    }

    fun skip(wireType: Int) {
        when (wireType) {
            ProtoWriter.WIRE_VARINT -> readVarint()
            ProtoWriter.WIRE_FIXED64 -> pos += 8
            ProtoWriter.WIRE_LENGTH_DELIMITED -> pos += readVarint().toInt()
            ProtoWriter.WIRE_FIXED32 -> pos += 4
            else -> pos = limit
        }
    }

    companion object {
        fun of(bytes: ByteArray): ProtoReader = ProtoReader(bytes)
    }
}

/** 读取一个 length-delimited 字段的便捷写法。 */
internal inline fun ProtoReader.readMessageField(block: (ProtoReader) -> Unit) {
    block(readMessage())
}
