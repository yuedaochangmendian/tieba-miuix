// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import com.tieba.miuix.protocol.CommonReq
import com.tieba.miuix.protocol.ForumSearchResult
import com.tieba.miuix.protocol.FrsPageProto
import com.tieba.miuix.protocol.ForumSearchPostItem
import com.tieba.miuix.protocol.PbPageProto
import com.tieba.miuix.protocol.TiebaConst.PostSortType
import com.tieba.miuix.protocol.ProtoHttp
import com.tieba.miuix.protocol.ThreadSearchItem
import com.tieba.miuix.protocol.ThreadSearchResult
import com.tieba.miuix.protocol.TiebaConst.ThreadSortType
import com.tieba.miuix.protocol.TiebaConst
import com.tieba.miuix.protocol.UserSearchResult
import com.tieba.miuix.protocol.WebHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 登录凭证。匿名浏览时全部为空。 */
data class Credentials(
    val bduss: String = "",
    val stoken: String = "",
    val tbs: String = "",
) {
    val isLoggedIn: Boolean get() = bduss.isNotEmpty()
}

/**
 * 贴吧数据仓库：把 UI 的调用翻译成具体的接口请求。
 *
 * 这里只复刻了两个"只读"接口，足以支撑「浏览吧内帖子 → 查看帖子楼层」的核心流程。
 */
class TiebaRepository(
    private val credentials: Credentials = Credentials(),
) {

    /** 加载某个吧的主题帖列表。 */
    suspend fun getThreads(
        kw: String,
        pn: Int = 1,
        rn: Int = TiebaConst.DEFAULT_THREAD_PAGE_SIZE,
        sortType: Int = ThreadSortType.REPLY,
        isGood: Boolean = false,
    ): FrsPageProto.Response = withContext(Dispatchers.IO) {
        val safeRn = rn.coerceIn(1, 100)
        val common = CommonReq(
            clientVersion = TiebaConst.LEGACY_VERSION,
            bduss = credentials.bduss,
            stoken = credentials.stoken,
            tbs = credentials.tbs,
        )
        val payload = FrsPageProto.pack(
            FrsPageProto.Params(
                kw = kw,
                pn = pn,
                rn = safeRn,
                rnNeed = safeRn + 5,
                isGood = isGood,
                sortType = sortType,
                common = common,
            ),
        )
        val url = "http://${TiebaConst.APP_BASE_HOST}/c/f/frs/page?cmd=${TiebaConst.CMD_FRS_PAGE}"
        FrsPageProto.parse(ProtoHttp.post(url, payload))
    }

    /** 加载主题帖内的楼层。 */
    suspend fun getPosts(
        tid: Long,
        pn: Int = 1,
        rn: Int = TiebaConst.DEFAULT_POST_PAGE_SIZE,
        sortType: Int = PostSortType.ASC,
        onlyThreadAuthor: Boolean = false,
        withComments: Boolean = false,
    ): PbPageProto.Response = withContext(Dispatchers.IO) {
        val safeRn = rn.coerceIn(2, 30)
        val common = CommonReq(
            clientVersion = TiebaConst.LATEST_VERSION,
            bduss = if (withComments) credentials.bduss else "",
            stoken = if (withComments) credentials.stoken else "",
        )
        val payload = PbPageProto.pack(
            PbPageProto.Params(
                tid = tid,
                pn = pn,
                rn = safeRn,
                sortType = sortType,
                onlyThreadAuthor = onlyThreadAuthor,
                withComments = withComments,
                commentSortByAgree = true,
                commentRn = 4,
                common = common,
            ),
        )
        val url = "https://${TiebaConst.APP_BASE_HOST}/c/f/pb/page?cmd=${TiebaConst.CMD_PB_PAGE}"
        PbPageProto.parse(ProtoHttp.post(url, payload))
    }

    // ---------------------------------------------------------------- 搜索

    /**
     * 搜索吧（网页端 `GET /mo/q/search/forum`）。
     *
     * 返回精确匹配与模糊匹配两部分；关键词为空时直接返回空结果。
     */
    suspend fun searchForum(keyword: String): ForumSearchResult = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext ForumSearchResult()
        val json = WebHttp.getJson(
            path = "/mo/q/search/forum",
            params = listOf("word" to keyword),
            referer = WebHttp.hybridReferer(keyword),
        )
        val data = json.optJSONObject("data") ?: return@withContext ForumSearchResult()
        ForumSearchResult.parse(data)
    }

    /**
     * 搜索帖子（网页端 `GET /mo/q/search/thread`）。
     *
     * @param sort 排序：0 综合、1 最新、2 热门（对应接口的 `st`）
     * @param onlyTitle 只看标题（对应接口的 `tt`）
     * @param forumName 非空时限定在该吧内搜索（对应接口的 `fname`）
     */
    suspend fun searchThread(
        keyword: String,
        pn: Int = 1,
        sort: Int = 0,
        onlyTitle: Boolean = false,
        forumName: String? = null,
        rn: Int = TiebaConst.DEFAULT_THREAD_PAGE_SIZE,
    ): ThreadSearchResult = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext ThreadSearchResult()
        val params = buildList {
            add("word" to keyword)
            add("pn" to pn.toString())
            add("st" to sort.toString())
            add("tt" to if (onlyTitle) "1" else "0")
            add("rn" to rn.coerceIn(1, 100).toString())
            add("ct" to "1")
            add("is_use_zonghe" to "1")
            add("cv" to "99.9.101")
            if (!forumName.isNullOrBlank()) add("fname" to forumName)
        }
        val json = WebHttp.getJson(
            path = "/mo/q/search/thread",
            params = params,
            referer = WebHttp.hybridReferer(keyword),
        )
        val data = json.optJSONObject("data") ?: return@withContext ThreadSearchResult()
        val items = ArrayList<ThreadSearchItem>()
        data.optJSONArray("post_list")?.let { array ->
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { items.add(ThreadSearchItem.parse(it)) }
            }
        }
        ThreadSearchResult(
            items = items,
            currentPage = data.optInt("current_page", pn),
            hasMore = data.optInt("has_more") != 0,
        )
    }

    /** 搜索用户（网页端 `GET /mo/q/search/user`）。 */
    suspend fun searchUser(keyword: String): UserSearchResult = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext UserSearchResult()
        val json = WebHttp.getJson(
            path = "/mo/q/search/user",
            params = listOf("word" to keyword),
            referer = WebHttp.hybridReferer(keyword),
        )
        val data = json.optJSONObject("data") ?: return@withContext UserSearchResult()
        UserSearchResult.parse(data)
    }

    /**
     * 吧内搜索（APP 表单端 `POST /c/s/searchpost`）。
     *
     * 与全吧搜索不同，这个接口返回的是帖子（楼层），字段形态也不同。
     */
    suspend fun searchPostInForum(
        keyword: String,
        forumName: String,
        pn: Int = 1,
        rn: Int = TiebaConst.DEFAULT_POST_PAGE_SIZE,
        onlyThread: Boolean = false,
        searchType: Int = 1,
    ): List<ForumSearchPostItem> = withContext(Dispatchers.IO) {
        if (keyword.isBlank() || forumName.isBlank()) return@withContext emptyList()
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/s/searchpost",
            params = listOf(
                "_client_version" to TiebaConst.LATEST_VERSION,
                "kw" to forumName,
                "only_thread" to if (onlyThread) "1" else "0",
                "pn" to pn.toString(),
                "rn" to rn.coerceIn(1, 100).toString(),
                "sm" to searchType.toString(),
                "word" to keyword,
            ),
        )
        val items = ArrayList<ForumSearchPostItem>()
        json.optJSONArray("post_list")?.let { array ->
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { items.add(ForumSearchPostItem.parse(it)) }
            }
        }
        items
    }
}