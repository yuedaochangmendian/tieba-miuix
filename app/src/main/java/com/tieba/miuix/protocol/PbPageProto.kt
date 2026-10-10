// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 主题帖内楼层接口（`/c/f/pb/page`，`CMD=302001`）。
 *
 * 请求体为 `PbPageReqIdl`，响应体为 `PbPageResIdl`。
 * 字段编号：`DataReq{kz=4, lz=5, r=6, pid=7, with_floor=8, floor_rn=9, rn=13, pn=18, common=25, floor_sort_type=74}`；
 * `DataRes{forum=2, page=3, post_list=6, thread=8, user_list=13}`。
 */
object PbPageProto {

    /** 请求参数。 */
    data class Params(
        /** 主题帖 id（`kz`，以字符串形式传递）。 */
        val tid: Long,
        val pn: Int,
        val rn: Int,
        val sortType: Int,
        val onlyThreadAuthor: Boolean,
        /** 是否拉取楼中楼；开启时需要携带 BDUSS。 */
        val withComments: Boolean,
        val commentSortByAgree: Boolean,
        val commentRn: Int,
        val common: CommonReq,
    )

    /** 响应结果。 */
    data class Response(
        val error: TiebaError,
        val forum: SimpleForum?,
        val page: Page?,
        val thread: ThreadInfo?,
        val posts: List<Post>,
    )

    fun pack(params: Params): ByteArray {
        val w = ProtoWriter()
        w.message(1) {
            string(4, params.tid.toString())
            bool(5, params.onlyThreadAuthor)
            int32(6, params.sortType)
            int32(13, params.rn)
            int32(18, params.pn)
            message(25) { params.common.writeTo(this) }
            if (params.withComments) {
                bool(8, true)
                int32(9, params.commentRn)
                int32(74, if (params.commentSortByAgree) 1 else 0)
            }
        }
        return w.toByteArray()
    }

    fun parse(bytes: ByteArray): Response {
        val r = ProtoReader.of(bytes)
        var error = TiebaError()
        var forum: SimpleForum? = null
        var page: Page? = null
        var thread: ThreadInfo? = null
        val posts = ArrayList<Post>()
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
                            3 -> page = Page.parse(data.readMessage())
                            6 -> posts += Post.parse(data.readMessage())
                            8 -> thread = ThreadInfo.parse(data.readMessage())
                            else -> data.skip(t and 7)
                        }
                    }
                }
                else -> r.skip(tag and 7)
            }
        }
        return Response(error, forum, page, thread, posts)
    }
}