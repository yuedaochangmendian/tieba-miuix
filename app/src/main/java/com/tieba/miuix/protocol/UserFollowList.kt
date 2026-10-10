// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import org.json.JSONObject

/**
 * 用户关注列表（`POST /c/u/follow/followList`）。
 *
 * 照 TiebaLite 的 `FollowListBean`：顶层是 `error_code` / `error_msg` 与分页字段，
 * 列表在 `follow_list` 里，每一项是 `{ id, name, name_show, portrait, intro }`。
 *
 * `uid` 留空表示查「当前登录用户」的关注，因此**查自己必须登录**；
 * 带上别人的 `uid` 时匿名也能拿到公开的关注列表。
 */
object UserFollowList {

    /** 一页的数量，服务端按自己节奏返回，这里只用来判断是否还有更多。 */
    const val PAGE_SIZE = 20

    /** 关注列表里的一个用户。 */
    data class Item(
        val id: Long = 0L,
        val name: String = "",
        val nameShow: String = "",
        val portrait: String = "",
        val intro: String = "",
    ) {
        val displayName: String get() = nameShow.ifEmpty { name }
    }

    /** 一页结果。 */
    data class Page(
        val errorCode: Int = 0,
        val errorMessage: String = "",
        val items: List<Item> = emptyList(),
        val totalFollowNum: Int = 0,
        val hasMore: Boolean = false,
        val pageNum: Int = 1,
    ) {
        val isOk: Boolean get() = errorCode == 0
    }

    fun parse(json: JSONObject): Page {
        val items = ArrayList<Item>()
        json.optJSONArray("follow_list")?.let { array ->
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                items.add(
                    Item(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name"),
                        nameShow = obj.optString("name_show"),
                        portrait = obj.optString("portrait"),
                        intro = obj.optString("intro"),
                    ),
                )
            }
        }
        return Page(
            errorCode = json.optInt("error_code", 0),
            errorMessage = json.optString("error_msg"),
            items = items,
            totalFollowNum = json.optInt("total_follow_num", 0),
            // has_more 是数字，0 表示没有下一页。
            hasMore = json.optInt("has_more", 0) != 0,
            pageNum = json.optInt("pn", 1),
        )
    }
}
