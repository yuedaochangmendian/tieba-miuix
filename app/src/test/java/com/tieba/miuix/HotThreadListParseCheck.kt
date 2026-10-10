// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix

import com.tieba.miuix.protocol.HotThreadListProto
import com.tieba.miuix.protocol.ProtoReader

/**
 * 不依赖 Android 的解析验证：确认 `hotThreadList` 真实响应能被解析出帖子。
 *
 * 用 `gradlew compileDebugUnitTestKotlin` 编好后手工跑：
 * `java -cp <debugUnitTest classes>;<debug classes>;app/src/test/resources;<kotlin-stdlib> com.tieba.miuix.HotThreadListParseCheck`
 */
object HotThreadListParseCheck {

    private fun p(r: ProtoReader) = r.position()

    @JvmStatic
    fun main(args: Array<String>) {
        // 1) skip 落点：长度分隔字段必须精确落在 61 字节负载之后
        val bytes = ByteArray(66)
        bytes[0] = 0x0A
        bytes[1] = 0x3D
        for (i in 2 until 63) bytes[i] = 0x41
        bytes[63] = 0x12
        bytes[64] = 0x08
        bytes[65] = 0x00

        val r = ProtoReader.of(bytes)
        check(r.readTag() == 10) { "tag1" }
        r.skip(2)
        check(p(r) == 63) { "skip landed at ${p(r)}, expected 63" }
        val t2 = r.readTag()
        check(t2 ushr 3 == 2) { "tag2 field=${t2 ushr 3}, expected 2" }
        println("[t] skip/depth ok: tag1=10 pos_after_skip=${63} tag2=$t2")

        // 2) 真实响应解析
        val raw = javaClass.classLoader!!
            .getResourceAsStream("hot_thread_list.bin")!!
            .use { it.readBytes() }
        val response = HotThreadListProto.parse(raw)
        println("[t] real response bytes=${raw.size} errno=${response.error.errno} threads=${response.threads.size}")
        response.threads.forEach { t ->
            println("[t]   tid=${t.id} fname='${t.fname}' title='${t.title}' reply=${t.replyNum}")
        }
        check(response.threads.isNotEmpty()) { "expected threads, got 0" }
        println("[t] PARSE OK")
    }
}
