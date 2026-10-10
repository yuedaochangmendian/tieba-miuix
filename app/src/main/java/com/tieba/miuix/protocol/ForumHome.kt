// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import org.json.JSONArray
import org.json.JSONObject

/**
 * 首页里的一个吧。
 *
 * 数据来自网页端 `GET /mg/o/getForumHome`，未登录时只有 `data.recomm_forum`
 * （12 个热门吧），登录后还会多出 `data.concern_forum`（关注的吧）。
 */
data class HomeForum(
    val id: Long = 0,
    val name: String = "",
    val avatar: String = "",
    val hotNum: Int = 0,
    val isConcerned: Boolean = false,
) {
    /** 实测 avatar 已经是完整链接，裸文件名才补前缀。 */
    val avatarUrl: String
        get() = when {
            avatar.isEmpty() -> ""
            avatar.startsWith("http", ignoreCase = true) -> avatar
            else -> "https://tb.himg.baidu.com/forum/image/$avatar"
        }

    companion object {
        fun parse(json: JSONObject, concerned: Boolean): HomeForum = HomeForum(
            id = json.optLongOrString("forum_id"),
            name = json.optString("forum_name"),
            avatar = json.optString("avatar"),
            hotNum = json.optIntFromString("hot_num"),
            isConcerned = concerned,
        )

        fun parseArray(array: JSONArray?, concerned: Boolean): List<HomeForum> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                parse(item, concerned).takeIf { it.name.isNotEmpty() }
            }
        }
    }
}

/**
 * 首页数据。
 *
 * 注意：`/mg/o/getForumHome` **不要求登录**（响应里 `data.user_info.is_login = 0`），
 * 但未登录时 [concerned] 一定是空的，UI 要能容忍。
 */
data class ForumHome(
    val concerned: List<HomeForum> = emptyList(),
    val recommended: List<HomeForum> = emptyList(),
) {
    val isEmpty: Boolean get() = concerned.isEmpty() && recommended.isEmpty()

    /** 优先展示关注的吧，没有就退回推荐。 */
    val display: List<HomeForum> get() = concerned.ifEmpty { recommended }

    companion object {
        fun parse(json: JSONObject): ForumHome {
            val data = json.optJSONObject("data") ?: JSONObject()
            return ForumHome(
                concerned = HomeForum.parseArray(
                    // 未登录时服务端直接不给这个字段，用 optJSONArray 而不是 getJSONArray。
                    data.optJSONArray("concern_forum"),
                    concerned = true,
                ),
                recommended = HomeForum.parseArray(
                    data.optJSONArray("recomm_forum"),
                    concerned = false,
                ),
            )
        }
    }
}
