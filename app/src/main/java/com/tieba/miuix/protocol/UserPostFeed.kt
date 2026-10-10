// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户主页里的一条发言（主题或回复）。
 *
 * 数据来自 `POST /c/u/feed/userpost`（表单，字段
 * `uid` / `pn` / `rn` / `is_thread` / `need_content`）。
 * 主题与回复共用同一张 `post_list`，靠 `is_thread` 区分。
 *
 * 回复的妙处在于：`content[0].post_content[0].text` 才是回复正文，
 * 顶层的 `title` 是所在主题的标题。两种形态都往 [title] / [content] 里归一。
 */
data class UserPostItem(
    val threadId: Long = 0,
    val postId: Long = 0,
    val forumName: String = "",
    val title: String = "",
    val content: String = "",
    val createTime: Long = 0,
    val replyNum: Int = 0,
    val agreeNum: Int = 0,
    val isThread: Boolean = false,
    val isDeleted: Boolean = false,
) {
    /** 列表里显示的主文案。 */
    val displayTitle: String
        get() = title.ifBlank { content.lineSequence().firstOrNull()?.take(60).orEmpty() }

    companion object {
        /** 数字/字符串两态读值。 */
        private fun JSONObject.intOf(key: String): Int {
            val raw = opt(key) ?: return 0
            return when (raw) {
                is Number -> raw.toInt()
                is String -> parseCount(raw)
                else -> 0
            }
        }

        private fun JSONObject.longOf(key: String): Long {
            val raw = opt(key) ?: return 0L
            return when (raw) {
                is Number -> raw.toLong()
                is String -> raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                else -> 0L
            }
        }

        private fun JSONObject.flagOf(key: String): Boolean {
            val raw = opt(key) ?: return false
            return when (raw) {
                is Boolean -> raw
                is Number -> raw.toInt() != 0
                else -> raw.toString() == "1" || raw.toString().equals("true", ignoreCase = true)
            }
        }

        /**
         * 正文位置实测（`/c/u/feed/userpost`，匿名可用）：
         * - 主题（`is_thread=1`）：`abstract[].text` 是正文，顶层 `content` 是纯字符串；
         * - 回复（`is_thread=0`）：`content[0].post_content[].text` 是回复内容。
         *
         * 三种形态按可靠性依次回退。
         */
        private fun extractText(json: JSONObject): String {
            val builder = StringBuilder()
            appendContents(builder, json.optJSONArray("abstract"))
            if (builder.isBlank()) {
                json.optJSONArray("content")?.let { array ->
                    for (i in 0 until array.length()) {
                        val node = array.optJSONObject(i) ?: continue
                        appendContents(builder, node.optJSONArray("post_content"))
                    }
                }
            }
            if (builder.isBlank()) {
                // 实测 `content` 是普通字符串（不是数组），兜住这一形态。
                val flat = json.opt("content")
                if (flat is String) builder.append(flat)
            }
            return builder.toString().trim()
        }

        private fun appendContents(builder: StringBuilder, array: JSONArray?) {
            if (array == null) return
            for (i in 0 until array.length()) {
                val node = array.optJSONObject(i) ?: continue
                val type = node.optString("type", "0")
                val text = node.optString("text", "")
                when (type) {
                    "0", "" -> builder.append(text)
                    "4" -> builder.append(node.optString("link", text))
                    "3", "20" -> builder.append("[图片]")
                    "10" -> builder.append("[语音]")
                    "1" -> builder.append(text.ifBlank { "[视频]" })
                }
            }
        }

        fun parse(json: JSONObject): UserPostItem = UserPostItem(
            threadId = json.longOf("thread_id"),
            postId = json.longOf("post_id"),
            forumName = json.optString("forum_name", ""),
            title = json.optString("title", ""),
            content = extractText(json),
            createTime = json.longOf("create_time"),
            replyNum = json.intOf("reply_num"),
            agreeNum = json.optJSONObject("agree")?.intOf("agree_num") ?: 0,
            isThread = json.flagOf("is_thread"),
            isDeleted = json.flagOf("is_post_deleted"),
        )
    }
}

/**
 * 用户发言流的一页。
 *
 * 响应顶层直接平铺 `post_list`，没有 `data` 包装：
 * ```json
 * { "error_code": "0", "error_msg": "", "post_list": [ { ... } ] }
 * ```
 */
data class UserPostResult(
    val items: List<UserPostItem> = emptyList(),
    val hasMore: Boolean = false,
    val hidePost: Boolean = false,
) {
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        fun parse(json: JSONObject): UserPostResult {
            val items = ArrayList<UserPostItem>()
            json.optJSONArray("post_list")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { items.add(UserPostItem.parse(it)) }
                }
            }
            val hide = json.opt("hide_post").toBooleanFlag()
            // 服务端不回 has_more，只能按「这一页是否装满」推断。
            return UserPostResult(
                items = items,
                hasMore = !hide && items.size >= 20,
                hidePost = hide,
            )
        }
    }
}
