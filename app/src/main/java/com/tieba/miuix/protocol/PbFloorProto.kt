// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 楼中楼接口（`/c/f/pb/floor`，`CMD=302002`，protobuf 通道）。
 *
 * 请求体 `PbFloorReqIdl`、响应体 `PbFloorResIdl`。字段编号：
 * `DataReq{kz=1, pid=2, spid=3, pn=4, common=9, forum_id=11}`；
 * `DataRes{page=1, post=3, subpost_list=4, thread=5, forum=6}`。
 *
 * 楼中楼**不需要登录**即可读取（发楼中楼才需要），所以这里按匿名请求发送。
 */
object PbFloorProto {

    /** 请求参数。 */
    data class Params(
        /** 主题帖 id（`kz`）。 */
        val tid: Long,
        /** 楼层 id（`pid`）。 */
        val pid: Long,
        /** 楼中楼页码，从 1 开始。 */
        val pn: Int = 1,
        /** 吧 id，拿不到时传 0。 */
        val forumId: Long = 0L,
        val common: CommonReq,
    )

    /** 响应结果。 */
    data class Response(
        val error: TiebaError,
        val page: Page?,
        val post: Post?,
        /** 楼中楼列表。 */
        val subPosts: List<SubPostList>,
        val thread: ThreadInfo?,
        val forum: SimpleForum?,
    ) {
        /** 是否还有下一页楼中楼。 */
        val hasMore: Boolean
            get() {
                val p = page ?: return false
                return p.currentPage < p.totalPage
            }
    }

    fun pack(params: Params): ByteArray {
        val w = ProtoWriter()
        w.message(1) {
            int64(1, params.tid)
            int64(2, params.pid)
            int32(4, params.pn)
            message(9) { params.common.writeTo(this) }
            int64(11, params.forumId)
        }
        return w.toByteArray()
    }

    fun parse(bytes: ByteArray): Response {
        val r = ProtoReader.of(bytes)
        var error = TiebaError()
        var page: Page? = null
        var post: Post? = null
        var thread: ThreadInfo? = null
        var forum: SimpleForum? = null
        val subPosts = ArrayList<SubPostList>()
        while (r.hasMore) {
            val tag = r.readTag()
            when (tag ushr 3) {
                1 -> error = TiebaError.parse(r.readMessage())
                2 -> {
                    val data = r.readMessage()
                    while (data.hasMore) {
                        val t = data.readTag()
                        when (t ushr 3) {
                            1 -> page = Page.parse(data.readMessage())
                            3 -> post = Post.parse(data.readMessage())
                            4 -> subPosts += SubPostList.parse(data.readMessage())
                            5 -> thread = ThreadInfo.parse(data.readMessage())
                            6 -> forum = SimpleForum.parse(data.readMessage())
                            else -> data.skip(t and 7)
                        }
                    }
                }
                else -> r.skip(tag and 7)
            }
        }
        return Response(error, page, post, subPosts, thread, forum)
    }
}
