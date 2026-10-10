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
        val r = ProtoReader.of(bytes)
        var error = TiebaError()
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
                            // topic_list（1）与 hot_thread_tab_info（3）暂未使用。
                            2 -> threads += ThreadInfo.parse(data.readMessage())
                            else -> data.skip(t and 7)
                        }
                    }
                }
                else -> r.skip(tag and 7)
            }
        }
        return Response(error, threads)
    }
}
