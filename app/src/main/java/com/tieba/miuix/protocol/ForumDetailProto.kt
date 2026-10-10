// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 吧详情接口（`/c/f/forum/getforumdetail`，`CMD=303021`）。
 *
 * 请求体 `GetForumDetailReqIdl{DataReq{forum_id=1, common=2}}`，
 * 响应体 `GetForumDetailResIdl{error=1, data=2}`，
 * `DataRes{forum_info=1, thread_list=2, forum_member=21}`。
 *
 * 字段编号取自 TiebaLite 的 `app/src/main/protos/GetForumDetail` 目录下的 `.proto`
 * 与 `RecommendForumInfo.proto`，已用真机请求核对过。
 */
object ForumDetailProto {

    /** `Abstract`：帖子摘要里的一段内容（proto 里字段名是 `duringTime` / `voiceMD5`）。 */
    data class AbstractSegment(
        val type: Int = 0,
        val text: String = "",
        val link: String = "",
        val src: String = "",
        val un: String = "",
        val duringTime: String = "",
        val voiceMd5: String = "",
    )

    /** 吧里的一条主题帖（`SimpleThreadInfo`）。 */
    data class SimpleThread(
        val tid: Long = 0,
        val title: String = "",
        val replyNum: Int = 0,
        val lastTimeInt: Int = 0,
        val abstracts: List<AbstractSegment> = emptyList(),
    ) {
        /** 摘要正文，用来在列表里做预览。 */
        val preview: String
            get() = abstracts.joinToString("") { it.text }.trim()
    }

    /** 吧详情里的一份资料。 */
    data class Detail(
        val forumId: Long = 0,
        val forumName: String = "",
        val avatar: String = "",
        val memberCount: Int = 0,
        val threadCount: Int = 0,
        val slogan: String = "",
        val lv1Name: String = "",
        val lv2Name: String = "",
        val isLiked: Boolean = false,
        val threads: List<SimpleThread> = emptyList(),
    ) {
        val avatarUrl: String
            get() = when {
                avatar.isEmpty() -> ""
                avatar.startsWith("http", ignoreCase = true) -> avatar
                else -> "https://tb.himg.baidu.com/forum/image/$avatar"
            }
    }

    /** 响应结果。 */
    data class Response(
        val error: TiebaError = TiebaError(),
        val detail: Detail? = null,
    )

    fun pack(forumId: Long, common: CommonReq): ByteArray {
        val w = ProtoWriter()
        w.message(1) {
            int64(1, forumId)
            message(2) { common.writeTo(this) }
        }
        return w.toByteArray()
    }

    fun parse(bytes: ByteArray): Response {
        val r = ProtoReader.of(bytes)
        var error = TiebaError()
        var detail: Detail? = null
        while (r.hasMore) {
            val tag = r.readTag()
            when (tag ushr 3) {
                1 -> error = TiebaError.parse(r.readMessage())
                2 -> {
                    val data = r.readMessage()
                    var forumInfo: Detail? = null
                    val threads = mutableListOf<SimpleThread>()
                    while (data.hasMore) {
                        val dataTag = data.readTag()
                        when (dataTag ushr 3) {
                            1 -> forumInfo = parseDetail(data.readMessage())
                            2 -> threads += parseSimpleThread(data.readMessage())
                            else -> data.skip(dataTag and 7)
                        }
                    }
                    detail = forumInfo?.copy(threads = threads)
                }

                else -> r.skip(tag and 7)
            }
        }
        return Response(error = error, detail = detail)
    }

    private fun parseSimpleThread(r: ProtoReader): SimpleThread {
        var tid = 0L
        var title = ""
        var replyNum = 0
        var lastTimeInt = 0
        val abstracts = mutableListOf<AbstractSegment>()
        while (r.hasMore) {
            val tag = r.readTag()
            when (tag ushr 3) {
                1 -> tid = r.readInt64()
                2 -> title = r.readString()
                3 -> replyNum = r.readInt32()
                4 -> lastTimeInt = r.readInt32()
                5 -> abstracts += parseAbstract(r.readMessage())
                else -> r.skip(tag and 7)
            }
        }
        return SimpleThread(
            tid = tid,
            title = title,
            replyNum = replyNum,
            lastTimeInt = lastTimeInt,
            abstracts = abstracts,
        )
    }

    private fun parseAbstract(r: ProtoReader): AbstractSegment {
        var type = 0
        var text = ""
        var link = ""
        var src = ""
        var un = ""
        var duringTime = ""
        var voiceMd5 = ""
        while (r.hasMore) {
            val tag = r.readTag()
            when (tag ushr 3) {
                1 -> type = r.readInt32()
                2 -> text = r.readString()
                3 -> link = r.readString()
                4 -> src = r.readString()
                5 -> un = r.readString()
                6 -> duringTime = r.readString()
                7 -> voiceMd5 = r.readString()
                else -> r.skip(tag and 7)
            }
        }
        return AbstractSegment(
            type = type,
            text = text,
            link = link,
            src = src,
            un = un,
            duringTime = duringTime,
            voiceMd5 = voiceMd5,
        )
    }

    private fun parseDetail(r: ProtoReader): Detail {
        var forumId = 0L
        var forumName = ""
        var avatar = ""
        var memberCount = 0
        var threadCount = 0
        var slogan = ""
        var lv1Name = ""
        var lv2Name = ""
        var isLiked = false
        while (r.hasMore) {
            val tag = r.readTag()
            when (tag ushr 3) {
                1 -> avatar = r.readString()
                2 -> forumId = r.readInt64()
                3 -> forumName = r.readString()
                4 -> isLiked = r.readUInt32() != 0
                5 -> memberCount = r.readUInt32()
                6 -> threadCount = r.readUInt32()
                7 -> slogan = r.readString()
                18 -> lv1Name = r.readString()
                19 -> lv2Name = r.readString()
                else -> r.skip(tag and 7)
            }
        }
        return Detail(
            forumId = forumId,
            forumName = forumName,
            avatar = avatar,
            memberCount = memberCount,
            threadCount = threadCount,
            slogan = slogan,
            lv1Name = lv1Name,
            lv2Name = lv2Name,
            isLiked = isLiked,
        )
    }
}
