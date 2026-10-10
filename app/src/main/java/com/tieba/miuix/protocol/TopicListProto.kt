// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 热话题列表接口（`/c/f/recommend/topicList`，`CMD=309289`）。
 *
 * 来源是 TiebaLite 发现页的「热聊」tab（`MixedTiebaApiImpl.topicListFlow`），
 * **匿名可用**（实测 HTTP 200 / 10252 字节 / 30 条话题）。
 *
 * 请求体 `TopicListRequest{ data=1 }`，
 * `TopicListRequestData{ common=1, call_from=2, list_type=3, need_tab_list=4, fid=5 }`，
 * 实参固定为 `call_from = "newbang"`、`list_type = "all"`、`need_tab_list = "0"`、`fid = 0`。
 *
 * 响应体 `TopicListResponse{ error=1, data=2 }`，
 * `TopicListResponseData` 里 **实测只下发字段 8（`repeated NewTopicList topic_list`）**，
 * 字段 1/2/3/6/7（榜单模块、tab 列表等）在这条通道上没有数据。
 *
 * 实测 `NewTopicList` 的字段编号与 aiotieba/TiebaLite 的 .proto 一致：
 * `topic_id=1, topic_name=2, topic_desc=3, discuss_num=4, topic_image=5, topic_tag=6`，
 * `topic_image` 是可直接加载的 `http://tiebapic.baidu.com/...jpg?tbpicau=...` 完整地址。
 */
object TopicListProto {

    /** 来源标识，服务端据此决定下发哪一路榜单。 */
    private const val CALL_FROM = "newbang"

    private const val LIST_TYPE = "all"

    private const val NEED_TAB_LIST = "0"

    /** 一条热话题。 */
    data class Topic(
        val id: Long,
        val name: String,
        val desc: String,
        val discussNum: Int,
        val image: String,
        val tag: Int,
    ) {
        /** 没有配图时不要请求空地址。 */
        val hasImage: Boolean get() = image.isNotEmpty()
    }

    /** 响应结果。 */
    data class Response(
        val error: TiebaError,
        val topics: List<Topic>,
    )

    fun pack(common: CommonReq): ByteArray {
        val w = ProtoWriter()
        w.message(1) {
            message(1) { common.writeTo(this) }
            string(2, CALL_FROM)
            string(3, LIST_TYPE)
            string(4, NEED_TAB_LIST)
            int64(5, 0L)
        }
        return w.toByteArray()
    }

    fun parse(bytes: ByteArray): Response {
        val reader = ProtoReader.of(bytes)
        var error = TiebaError()
        val topics = ArrayList<Topic>()
        var loopGuard = 0
        while (reader.hasMore) {
            if (++loopGuard > MAX_FIELDS) break
            val tag = reader.readTag()
            when (tag ushr 3) {
                1 -> error = TiebaError.parse(reader.readMessage())
                2 -> readData(reader.readMessage(), topics)
                else -> reader.skip(tag and 7)
            }
        }
        return Response(error, topics)
    }

    private fun readData(data: ProtoReader, out: MutableList<Topic>) {
        var loopGuard = 0
        while (data.hasMore) {
            if (++loopGuard > MAX_FIELDS) break
            val tag = data.readTag()
            when (tag ushr 3) {
                8 -> out += parseTopic(data.readMessage())
                else -> data.skip(tag and 7)
            }
        }
    }

    private fun parseTopic(reader: ProtoReader): Topic {
        var id = 0L
        var name = ""
        var desc = ""
        var discussNum = 0
        var image = ""
        var tagValue = 0
        var loopGuard = 0
        while (reader.hasMore) {
            if (++loopGuard > MAX_FIELDS) break
            val tag = reader.readTag()
            when (tag ushr 3) {
                1 -> id = reader.readInt64()
                2 -> name = reader.readString()
                3 -> desc = reader.readString()
                4 -> discussNum = reader.readVarint().toInt()
                5 -> image = reader.readString()
                6 -> tagValue = reader.readInt32()
                else -> reader.skip(tag and 7)
            }
        }
        return Topic(id, name, desc, discussNum, image, tagValue)
    }

    /** 单条话题最多几个字段，防御性上限。 */
    private const val MAX_FIELDS = 4096
}
