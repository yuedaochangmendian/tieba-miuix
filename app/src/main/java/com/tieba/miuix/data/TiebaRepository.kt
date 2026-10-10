// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import com.tieba.miuix.protocol.CommonReq
import com.tieba.miuix.protocol.ForumHome
import com.tieba.miuix.protocol.ForumSearchResult
import com.tieba.miuix.protocol.FrsPageProto
import com.tieba.miuix.protocol.ForumSearchPostResult
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
 * 凭证是**动态读取**的（[credentialsProvider]）：登录、退出、补取 tbs 之后
 * 不需要重建仓库，下一次请求自动带上新凭证。
 */
class TiebaRepository(
    private val credentialsProvider: () -> Credentials = { Credentials() },
) {

    /** 当前凭证快照。 */
    val credentials: Credentials get() = credentialsProvider()

    /** 是否已登录。 */
    val isLoggedIn: Boolean get() = credentials.isLoggedIn

    /** 加载某个吧的主题帖列表。 */
    suspend fun getThreads(
        kw: String,
        pn: Int = 1,
        rn: Int = TiebaConst.DEFAULT_THREAD_PAGE_SIZE,
        sortType: Int = ThreadSortType.REPLY,
        isGood: Boolean = false,
    ): FrsPageProto.Response = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
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
        val credentials = credentialsProvider()
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
     * 首页数据（网页端 `GET /mg/o/getForumHome`）。
     *
     * 这个接口**不需要登录**：实测匿名请求会返回 `errno = 0` 与 12 个热门吧
     * （`data.recomm_forum`）。登录后服务端会额外给出 `data.concern_forum`
     * （关注的吧），[ForumHome.parse] 两段都读。
     */
    suspend fun getForumHome(): ForumHome = withContext(Dispatchers.IO) {
        val json = WebHttp.getJson(
            path = "/mg/o/getForumHome",
            params = listOf("subapp_type" to "pc", "_client_type" to "20"),
            referer = "https://" + TiebaConst.WEB_BASE_HOST + "/",
        )
        ForumHome.parse(json)
    }

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
     * 实测响应把 `post_list` 与 `page` 平铺在顶层。
     */
    suspend fun searchPostInForum(
        keyword: String,
        forumName: String,
        pn: Int = 1,
        rn: Int = TiebaConst.DEFAULT_POST_PAGE_SIZE,
        onlyThread: Boolean = false,
        searchType: Int = 1,
    ): ForumSearchPostResult = withContext(Dispatchers.IO) {
        if (keyword.isBlank() || forumName.isBlank()) return@withContext ForumSearchPostResult()
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
        ForumSearchPostResult.parse(json)
    }

    // ---------------------------------------------------------------- 登录

    /**
     * 取 `tbs`（写操作令牌）。
     *
     * 登录后必须单独取一次：只带 BDUSS 能读不能写，发帖/回复/签到都会被拒。
     * 先试客户端通道 `/dc/common/tbs`（返回 JSON），再回落网页通道
     * `/dc/common/tbs`（返回 HTML，值是 `tbs` 的字符串），两者都解析不到就
     * 返回空串，由调用方提示用户。
     */
    suspend fun fetchTbs(): String = withContext(Dispatchers.IO) {
        val current = credentialsProvider()
        if (!current.isLoggedIn) return@withContext ""
        val cookie = "BDUSS=${current.bduss}" +
            if (current.stoken.isNotEmpty()) "; STOKEN=${current.stoken}" else ""

        val candidates = listOf(
            Triple(
                "http://${TiebaConst.APP_BASE_HOST}/dc/common/tbs?t=${System.currentTimeMillis()}",
                TiebaConst.USER_AGENT,
                null,
            ),
            Triple(
                "https://${TiebaConst.WEB_BASE_HOST}/dc/common/tbs",
                TiebaConst.WEB_USER_AGENT,
                "https://${TiebaConst.WEB_BASE_HOST}/",
            ),
        )
        for ((url, ua, referer) in candidates) {
            val text = runCatching { ProtoHttp.getText(url, cookie, ua, referer) }.getOrNull()
            val tbs = text?.let { extractTbs(it) }.orEmpty()
            if (tbs.isNotEmpty()) return@withContext tbs
        }
        ""
    }
}

/**
 * 从响应里抠出 `tbs` 的值。
 *
 * 实测两种形态都要兼容：JSON 的 `{"tbs":"..."}` 与 HTML/JS 里的 `tbs = '...'`。
 */
internal fun extractTbs(body: String): String {
    if (body.isEmpty()) return ""
    Regex("""["']tbs["']\s*[:=]\s*["']([0-9a-zA-Z]{10,})["']""")
        .find(body)
        ?.let { return it.groupValues[1] }
    Regex("""tbs\s*[:=]\s*["']([0-9a-zA-Z]{10,})["']""")
        .find(body)
        ?.let { return it.groupValues[1] }
    return ""
}