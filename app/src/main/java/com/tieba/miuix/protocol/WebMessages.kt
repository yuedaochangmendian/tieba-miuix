// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import org.json.JSONObject

/**
 * 吧搜索结果。
 *
 * 数据来自网页端 `GET /mo/q/search/forum`，响应形如：
 * ```json
 * { "no": 0, "error": "", "data": { "pn": 1, "has_more": 0,
 *   "exact_match": { "forum_id": 1, "forum_name": "贴吧", ... },
 *   "fuzzy_match": [ { ... } ] } }
 * ```
 */
data class ForumSearchItem(
    val id: Long = 0,
    val name: String = "",
    val nameShow: String = "",
    val avatar: String = "",
    val postNum: Int = 0,
    val concernNum: Int = 0,
    val intro: String = "",
    val slogan: String = "",
    val isExact: Boolean = false,
) {
    val displayName: String get() = nameShow.ifEmpty { name }

    companion object {
        fun parse(json: JSONObject, exact: Boolean): ForumSearchItem = ForumSearchItem(
            id = json.optLongOrString("forum_id"),
            name = json.optString("forum_name"),
            nameShow = json.optString("forum_name_show"),
            avatar = json.optString("avatar"),
            postNum = json.optIntFromString("post_num"),
            concernNum = json.optIntFromString("concern_num"),
            intro = json.optString("intro"),
            slogan = json.optString("slogan"),
            isExact = exact,
        )
    }
}

/** 吧搜索的整体结果：精确匹配 + 模糊匹配。 */
data class ForumSearchResult(
    val exact: ForumSearchItem? = null,
    val fuzzy: List<ForumSearchItem> = emptyList(),
    val hasMore: Boolean = false,
) {
    val isEmpty: Boolean get() = exact == null && fuzzy.isEmpty()

    companion object {
        fun parse(data: JSONObject): ForumSearchResult {
            val exact = data.optJSONObject("exact_match")?.let { ForumSearchItem.parse(it, true) }
            val fuzzy = ArrayList<ForumSearchItem>()
            data.optJSONArray("fuzzy_match")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { fuzzy.add(ForumSearchItem.parse(it, false)) }
                }
            }
            return ForumSearchResult(
                exact = exact,
                fuzzy = fuzzy,
                hasMore = data.optInt("has_more") != 0,
            )
        }
    }
}

/**
 * 帖子搜索结果。
 *
 * 数据来自网页端 `GET /mo/q/search/thread`，字段对应
 * `data.post_list[].{tid,pid,title,content,time,post_num,like_num,forum_name,user{...}}`。
 */
data class ThreadSearchItem(
    val tid: Long = 0,
    val pid: Long = 0,
    val title: String = "",
    val content: String = "",
    val forumName: String = "",
    val authorName: String = "",
    val authorPortrait: String = "",
    val authorUid: Long = 0,
    val replyNum: Int = 0,
    val agreeNum: Int = 0,
    val time: Long = 0,
    val images: List<String> = emptyList(),
) {
    companion object {
        fun parse(json: JSONObject): ThreadSearchItem {
            val user = json.optJSONObject("user")
            val media = ArrayList<String>()
            json.optJSONArray("media")?.let { array ->
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("big_pic")
                        .ifEmpty { item.optString("water_pic") }
                        .ifEmpty { item.optString("small_pic") }
                        .ifEmpty { item.optString("src") }
                    if (url.isNotEmpty()) media.add(url)
                }
            }
            return ThreadSearchItem(
                tid = json.optLongOrString("tid"),
                pid = json.optLongOrString("pid"),
                title = json.optString("title"),
                content = json.optString("content"),
                forumName = json.optString("forum_name"),
                authorName = user?.optString("show_nickname")?.ifEmpty { user.optString("user_name") }
                    ?: "",
                authorPortrait = user?.optString("portrait").orEmpty(),
                authorUid = user?.optLongOrString("user_id") ?: 0L,
                replyNum = json.optIntFromString("post_num"),
                agreeNum = json.optIntFromString("like_num"),
                time = json.optLongOrString("time"),
                images = media,
            )
        }
    }
}

/** 帖子搜索的分页结果。 */
data class ThreadSearchResult(
    val items: List<ThreadSearchItem> = emptyList(),
    val currentPage: Int = 1,
    val hasMore: Boolean = false,
)

/**
 * 用户搜索结果。
 *
 * 数据来自网页端 `GET /mo/q/search/user`。
 */
data class UserSearchItem(
    val id: Long = 0,
    val name: String = "",
    val nameShow: String = "",
    val portrait: String = "",
    val intro: String = "",
    val fansNum: Int = 0,
    val isExact: Boolean = false,
) {
    val displayName: String get() = nameShow.ifEmpty { name }

    /** 按设置决定是否同时显示用户名与昵称。 */
    fun displayNameWith(showBoth: Boolean): String =
        if (showBoth && name.isNotEmpty() && nameShow.isNotEmpty() && name != nameShow) {
            "$name（$nameShow）"
        } else {
            displayName
        }

    val portraitUrl: String
        get() = when {
            portrait.isEmpty() -> ""
            // 实测 `/mo/q/search/user` 返回的 portrait 已经是完整的 https 链接
            // （gss0.bdstatic.com/.../sys/portrait/item/...），只有 App 协议的老接口
            // 才返回裸文件名，所以两种形态都要接住。
            portrait.startsWith("http", ignoreCase = true) -> portrait
            else -> "https://tb.himg.baidu.com/sys/portrait/item/$portrait"
        }

    companion object {
        fun parse(json: JSONObject, exact: Boolean): UserSearchItem = UserSearchItem(
            id = json.optLongOrString("id"),
            name = json.optString("name").ifEmpty { json.optString("user_nickname") },
            nameShow = json.optString("show_nickname"),
            portrait = json.optString("portrait"),
            intro = json.optString("intro"),
            fansNum = json.optIntFromString("fans_num"),
            isExact = exact,
        )
    }
}

/** 用户搜索结果：精确匹配 + 模糊匹配。 */
data class UserSearchResult(
    val exact: UserSearchItem? = null,
    val fuzzy: List<UserSearchItem> = emptyList(),
    val hasMore: Boolean = false,
) {
    val isEmpty: Boolean get() = exact == null && fuzzy.isEmpty()

    companion object {
        fun parse(data: JSONObject): UserSearchResult {
            // 实测 `/mo/q/search/user` 返回的是 camelCase 的 exactMatch / fuzzyMatch。
            val exactJson = data.optJSONObject("exact_match") ?: data.optJSONObject("exactMatch")
            val fuzzyArray = data.optJSONArray("fuzzy_match") ?: data.optJSONArray("fuzzyMatch")
            val exact = exactJson?.let { UserSearchItem.parse(it, true) }
            val fuzzy = ArrayList<UserSearchItem>()
            fuzzyArray?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { fuzzy.add(UserSearchItem.parse(it, false)) }
                }
            }
            return UserSearchResult(
                exact = exact,
                fuzzy = fuzzy,
                hasMore = data.optInt("has_more") != 0,
            )
        }
    }
}

/** 吧内搜索（`POST /c/s/searchpost`）返回的一条帖子。 */
data class ForumSearchPostItem(
    val tid: Long = 0,
    val pid: Long = 0,
    val title: String = "",
    val content: String = "",
    val forumName: String = "",
    val authorName: String = "",
    val time: Long = 0,
) {
    companion object {
        fun parse(json: JSONObject): ForumSearchPostItem {
            val author = json.optJSONObject("author")
            return ForumSearchPostItem(
                tid = json.optLongOrString("tid"),
                pid = json.optLongOrString("pid"),
                title = json.optString("title"),
                content = json.optString("content"),
                forumName = json.optString("fname"),
                authorName = author?.optString("name_show")?.ifEmpty { author.optString("name") } ?: "",
                time = json.optLongOrString("time"),
            )
        }
    }
}

/**
 * 吧内搜索的返回。
 *
 * 实测响应结构是「顶层直接放 post_list 与 page」，与 `Page` 模型字段一致，
 * 因此这里直接复用 [Page]。
 */
data class ForumSearchPostResult(
    val items: List<ForumSearchPostItem> = emptyList(),
    val page: Page? = null,
) {
    companion object {
        fun parse(json: JSONObject): ForumSearchPostResult {
            val items = ArrayList<ForumSearchPostItem>()
            json.optJSONArray("post_list")?.let { array ->
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { items.add(ForumSearchPostItem.parse(it)) }
                }
            }
            return ForumSearchPostResult(
                items = items,
                page = json.optJSONObject("page")?.let { Page.parse(it) },
            )
        }
    }
}

/**
 * JSON 里数字既有可能是 `123` 也可能是 `"123"`，两种都要能读。
 */
internal fun JSONObject.optLongOrString(key: String): Long {
    val value = opt(key) ?: return 0L
    return when (value) {
        is Number -> value.toLong()
        is String -> parseCount(value).toLong()
        else -> 0L
    }
}

/** 同上，但用于 int。 */
internal fun JSONObject.optIntFromString(key: String): Int {
    val value = opt(key) ?: return 0
    return when (value) {
        is Number -> value.toInt()
        is String -> parseCount(value)
        else -> 0
    }
}

/**
 * 把贴吧的计数文本还原成整数。
 *
 * 接口里的 `post_num` / `concern_num` 是缩写过的人类可读文本，实测有
 * `"1289W"`、`"131W"`（W = 万）、`"1.2万"` 这几种形态，直接 `toIntOrNull`
 * 会全部变成 0。
 */
internal fun parseCount(raw: String): Int {
    val text = raw.trim()
    if (text.isEmpty()) return 0
    var multiplier = 1.0
    var number = text
    when {
        number.endsWith("万") -> {
            multiplier = 10_000.0
            number = number.dropLast(1)
        }
        number.endsWith("W", ignoreCase = true) -> {
            multiplier = 10_000.0
            number = number.dropLast(1)
        }
        number.endsWith("亿") -> {
            multiplier = 100_000_000.0
            number = number.dropLast(1)
        }
    }
    number = number.trim()
    if (number.isEmpty()) return 0
    val digits = number.filter { it.isDigit() || it == '.' }
    if (digits.isEmpty()) return 0
    val value = digits.toDoubleOrNull() ?: return 0
    return (value * multiplier).toInt()
}
