// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 贴吧首页帖子列表接口（`/c/f/frs/page`，`CMD=301001`）。
 *
 * 请求体为 `FrsPageReqIdl`，响应体为 `FrsPageResIdl`。
 * 字段编号：`DataReq{kv=1, rn=2, rn_need=3, is_good=4, pn=15, common=39, sort_type=47}`；
 * `DataRes{forum=2, page=4, thread_list=7}`。
 */
object FrsPageProto {

    /** 请求参数。 */
    data class Params(
        val kw: String,
        val pn: Int,
        val rn: Int,
        val rnNeed: Int,
        val isGood: Boolean,
        val sortType: Int,
        val common: CommonReq,
    )

    /** 响应结果。 */
    data class Response(
        val error: TiebaError,
        val forum: SimpleForum?,
        val page: Page?,
        val threads: List<ThreadInfo>,
    )

    fun pack(params: Params): ByteArray {
        val w = ProtoWriter()
        w.message(1) {
            string(1, params.kw)
            int32(2, params.rn)
            int32(3, params.rnNeed)
            bool(4, params.isGood)
            // pn==1 时置 0，这是 aiotieba 中记录的服务端行为。
            int32(15, if (params.pn == 1) 0 else params.pn)
            message(39) { params.common.writeTo(this) }
            int32(47, params.sortType)
        }
        return w.toByteArray()
    }

    fun parse(bytes: ByteArray): Response {
        val r = ProtoReader.of(bytes)
        var error = TiebaError()
        var forum: SimpleForum? = null
        var page: Page? = null
        val threads = ArrayList<ThreadInfo>()
        while (r.hasMore) {
            val tag = r.readTag()
            when (tag ushr 3) {
                1 -> error = TiebaError.parse(r.readMessage())
                2 -> {
                    val data = r.readMessage()
                    while (data.hasMore) {
                        val t = data.readTag()
                        when (t ushr 3) {
                            2 -> forum = SimpleForum.parse(data.readMessage())
                            4 -> page = Page.parse(data.readMessage())
                            7 -> threads += ThreadInfo.parse(data.readMessage())
                            else -> data.skip(t and 7)
                        }
                    }
                }
                else -> r.skip(tag and 7)
            }
        }
        return Response(error, forum, page, threads)
    }
}