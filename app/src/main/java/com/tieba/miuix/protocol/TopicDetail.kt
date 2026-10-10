// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import org.json.JSONObject

/**
 * 热话题详情（`GET /mo/q/newtopic/topicDetail`，JSON 通道）。
 *
 * 来源是 TiebaLite 的 `AppHybridTiebaApi.topicDetailFlow`，
 * 参数为 `topic_id` + `topic_name` + `is_new=1`，**匿名可读**（实测 41 KB）。
 *
 * 实测响应结构（只取用到的部分）：
 * ```
 * { no:0, error:"success", data:{
 *     topic_info:{ topic_id, topic_name, topic_desc, discuss_num, interaction_num_page,
 *                  topic_image, pc_hpic, is_video_topic },
 *     relate_thread:{ thread_list:[ { thread_info:{...} } ] },
 *     has_more: true } }
 * ```
 *
 * 这里的 `thread_info` 与 `/mo/q/search/thread` 的帖子项是同一套字段
 * （`tid/pid/title/abstract/media/reply_num/forum_name/author`），
 * 所以直接复用 [ThreadSearchItem.parse]。
 *
 * 注意正文里的换行是字面量 `\\n`，展示前要还原（见 [decodeTopicText]）。
 */
data class TopicDetail(
    val id: Long,
    val name: String,
    val desc: String,
    val discussNum: Int,
    val interactionNum: Int,
    val image: String,
    val bigImage: String,
    val isVideo: Boolean,
    val threads: List<ThreadSearchItem>,
    val hasMore: Boolean,
) {
    companion object {
        fun parse(json: JSONObject): TopicDetail {
            val data = json.optJSONObject("data") ?: JSONObject()
            val info = data.optJSONObject("topic_info") ?: JSONObject()
            val list = data.optJSONObject("relate_thread")
                ?.optJSONArray("thread_list")
            val threads = ArrayList<ThreadSearchItem>()
            if (list != null) {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val thread = item.optJSONObject("thread_info") ?: continue
                    threads += ThreadSearchItem.parse(thread)
                }
            }
            return TopicDetail(
                id = info.optLongOrString("topic_id"),
                name = decodeTopicText(info.optString("topic_name")),
                desc = decodeTopicText(info.optString("topic_desc")),
                discussNum = parseCount(info.optString("discuss_num")),
                interactionNum = info.optInt("interaction_num_page", 0),
                image = info.optString("topic_image"),
                bigImage = info.optString("pc_hpic").ifEmpty { info.optString("topic_image") },
                isVideo = info.optInt("is_video_topic", 0) != 0,
                threads = threads,
                hasMore = data.opt("has_more").toBooleanFlag(),
            )
        }
    }
}

/**
 * 还原话题文本里的转义与 HTML 实体。
 *
 * 实测 `topic_desc` 里换行是两个字面字符 `\` `n`，直接显示会很难看；
 * 少数话题的标题里还会夹 `&quot;` 之类的实体。
 */
internal fun decodeTopicText(raw: String): String {
    if (raw.isEmpty()) return raw
    return raw
        .replace("\\n", "\n")
        .replace("\\r", "")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .trim()
}
