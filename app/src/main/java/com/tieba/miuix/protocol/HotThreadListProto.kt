// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 首页热门推荐流接口（`/c/f/forum/hotThreadList`，`CMD=309661`）。
 *
 * 这是 App「推荐」页的数据源，**匿名可用**（实测 HTTP 200 / 10 KB 左右）。
 * 请求体 `HotThreadListRequest{ data=1 }`，
 * `HotThreadListRequestData{ common=1, tab_id=2, tab_code=3 }`
 * （`tab_id` 固定 `"1"`，`tab_code` 传 `"all"`）。
 *
 * 响应体 `HotThreadListResponse{ error=1, data=2 }`，
 * `HotThreadListResponseData{ topic_list=1, thread_info=2, hot_thread_tab_info=3 }`。
 *
 * 这里只取 `thread_info`——实测它与 `frs/page` 的 `ThreadInfo` 是同一套字段编号
 * （`id=1, title=3, reply_num=4, view_num=5, last_time_int=7, author=18,
 * first_post_content=21/112/142, thread_type=26, fid=27, fname=28,
 * first_post_id=40, create_time=45, author_id=56, ...`），
 * 所以直接复用 [ThreadInfo.parse]。
 *
 * 注意：解析依赖 [ProtoReader.skip] 对 length-delimited 字段的正确落点，
 * 之前 `skip` 少推进 1 字节会让 `data` 子消息整体错位、`thread_info` 一条都取不到。
 */
object HotThreadListProto {

    /** 默认的推荐流 tab。 */
    const val TAB_CODE_ALL = "all"

    private const val TAB_ID = "1"

    /** 响应结果。 */
    data class Response(
        val error: TiebaError,
        val threads: List<ThreadInfo>,
    )

    fun pack(common: CommonReq, tabCode: String = TAB_CODE_ALL): ByteArray {
        val w = ProtoWriter()
        w.message(1) {
            message(1) { common.writeTo(this) }
            string(2, TAB_ID)
            // tab_code 为空时服务端会返回空列表，兜底成 "all"。
            string(3, tabCode.ifBlank { TAB_CODE_ALL })
        }
        return w.toByteArray()
    }

    fun parse(bytes: ByteArray): Response {
        val reader = ProtoReader.of(bytes)
        var error = TiebaError()
        val threads = ArrayList<ThreadInfo>()
        var loopGuard = 0
        while (reader.hasMore) {
            if (++loopGuard > MAX_FIELDS) break
            val tag = reader.readTag()
            when (tag ushr 3) {
                1 -> error = TiebaError.parse(reader.readMessage())
                2 -> readData(reader.readMessage(), threads)
                else -> reader.skip(tag and 7)
            }
        }
        return Response(error, threads)
    }

    /** 解析 `HotThreadListResponseData`，只取 `thread_info`（字段 2）。 */
    private fun readData(data: ProtoReader, out: MutableList<ThreadInfo>) {
        var loopGuard = 0
        while (data.hasMore) {
            if (++loopGuard > MAX_FIELDS) break
            val tag = data.readTag()
            when (tag ushr 3) {
                // topic_list（1）与 hot_thread_tab_info（3）暂未使用。
                2 -> out += ThreadInfo.parse(data.readMessage())
                else -> data.skip(tag and 7)
            }
        }
    }

    /** 单个响应最多几十个字段，防御性上限，避免解析器异常时空转。 */
    private const val MAX_FIELDS = 4096
}
