// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import org.json.JSONObject

/**
 * 消息中心（回复我的 / @我的）。
 *
 * 这两个接口是 **NewTiebaApi 的表单通道**，而不是网页端：
 * - 回复我的：`POST http://tiebac.baidu.com/c/u/feed/replyme`，参数只有 `pn`
 * - @我的：`POST http://tiebac.baidu.com/c/u/feed/atme`，参数只有 `pn`
 * - 未读角标：`POST http://tiebac.baidu.com/c/s/msg`，参数 `bookmark=1`
 *
 * 三个接口**都必须登录**：匿名时统一返回
 * `{"error_code":"1","error_msg":"用户未登录或登录失败，请更换账号或重试",
 *   "info":{"checkUserLogin":"1","needlogin":"1"}}`。
 *
 * 这里刻意不复用网页端的 `/mo/q/message/` 系列路径：那条路径在客户端请求头下
 * 只会返回一张「正在加载中…」的 HTML 错误页（实测 5733 字节），拿不到数据。
 */
enum class MessageType(val path: String, val listKey: String) {
    /** 回复我的。 */
    REPLY_ME("/c/u/feed/replyme", "reply_list"),

    /** @我的。 */
    AT_ME("/c/u/feed/atme", "at_list"),
}

/**
 * 一条消息。
 *
 * [isFloorReply] 区分这条消息来自「楼层回复」还是「楼中楼回复」，
 * [quotePid] 只有楼中楼回复才有值，回帖时要用它做 `quote_id`。
 */
data class MessageItem(
    val title: String = "",
    val content: String = "",
    val quoteContent: String = "",
    val quoteUserName: String = "",
    val forumName: String = "",
    val threadId: Long = 0,
    val postId: Long = 0,
    val quotePid: Long = 0,
    val time: Long = 0,
    val unread: Boolean = false,
    val isFloorReply: Boolean = false,
    val replyerName: String = "",
    val replyerPortrait: String = "",
    val replyerId: Long = 0,
) {
    /** 头像地址；接口返回的 portrait 可能是裸串，也可能是完整链接。 */
    val replyerPortraitUrl: String
        get() = when {
            replyerPortrait.isEmpty() -> ""
            replyerPortrait.startsWith("http", ignoreCase = true) -> replyerPortrait
            else -> "https://tb.himg.baidu.com/sys/portrait/item/" + replyerPortrait
        }

    val displayName: String get() = replyerName.ifEmpty { "贴吧用户" }
}

/**
 * 一页消息。
 *
 * 服务端把列表挂在 [MessageType.listKey] 指向的字段上（`reply_list` / `at_list`），
 * 有意思的是**两个接口的响应里两个字段都会出现**，只是非当前类型的那个是空数组，
 * 所以这里按类型取，取不到再退回「哪个非空用哪个」。
 */
data class MessageList(
    val items: List<MessageItem> = emptyList(),
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
    val errorCode: String = "",
    val errorMessage: String = "",
) {
    val isOk: Boolean get() = errorCode.isEmpty() || errorCode == "0"

    companion object {
        fun parse(json: JSONObject, type: MessageType): MessageList {
            val errorCode = json.opt("error_code")?.toString().orEmpty()
            val errorMessage = json.optString("error_msg")
            val page = json.optJSONObject("page")

            // `reply_list` / `at_list` 在空的时候服务端会下发字符串或其他非数组形态，
            // 所以统一走 optJSONArray，拿不到就当成空列表。
            val primary = json.optJSONArray(type.listKey)
            val fallbackKey = if (type == MessageType.REPLY_ME) "at_list" else "reply_list"
            val fallback = json.optJSONArray(fallbackKey)
            val array = primary ?: fallback

            val items = buildList {
                for (i in 0 until (array?.length() ?: 0)) {
                    val item = array?.optJSONObject(i) ?: continue
                    add(parseItem(item))
                }
            }

            return MessageList(
                items = items,
                currentPage = page?.optIntFromString("current_page")?.takeIf { it > 0 } ?: 1,
                hasMore = page?.opt("has_more").toBooleanFlag(),
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
        }

        private fun parseItem(json: JSONObject): MessageItem {
            val replyer = json.optJSONObject("replyer")
            val quoteUser = json.optJSONObject("quote_user")
            return MessageItem(
                title = json.optString("title"),
                content = json.optString("content"),
                quoteContent = json.optString("quote_content"),
                quoteUserName = quoteUser?.let { user ->
                    val nameShow = user.optString("name_show")
                    nameShow.ifEmpty { user.optString("name") }
                }.orEmpty(),
                forumName = json.optString("fname"),
                threadId = json.optLongOrString("thread_id"),
                postId = json.optLongOrString("post_id"),
                quotePid = json.optLongOrString("quote_pid"),
                time = json.optLongOrString("time"),
                unread = json.opt("unread").toBooleanFlag(),
                isFloorReply = json.opt("is_floor").toBooleanFlag(),
                replyerName = replyer?.let { user ->
                    val nameShow = user.optString("name_show")
                    nameShow.ifEmpty { user.optString("name") }
                }.orEmpty(),
                replyerPortrait = replyer?.optString("portrait").orEmpty(),
                replyerId = replyer?.optLongOrString("id") ?: 0L,
            )
        }
    }
}

/**
 * 「我的」页角标数量（`POST /c/s/msg?bookmark=1`）。
 *
 * 匿名时服务端返回 `{"error_code":0, ..., "message":{}}`——注意 `error_code` 是 0
 * 但 `message` 是空对象，所以判断「有没有登录」要看字段而不是错误码。
 */
data class MessageCounts(
    val replyMe: Int = 0,
    val atMe: Int = 0,
    val fans: Int = 0,
    val storeThread: Int = 0,
) {
    val hasAny: Boolean get() = replyMe > 0 || atMe > 0 || fans > 0 || storeThread > 0

    companion object {
        fun parse(json: JSONObject): MessageCounts {
            val message = json.optJSONObject("message") ?: return MessageCounts()
            fun JSONObject.count(key: String): Int = when (val raw = opt(key)) {
                null, JSONObject.NULL -> 0
                is Number -> raw.toInt()
                else -> raw.toString().toIntOrNull() ?: 0
            }
            return MessageCounts(
                replyMe = message.count("replyme"),
                atMe = message.count("atme"),
                fans = message.count("fans"),
                storeThread = message.count("storethread"),
            )
        }
    }
}
