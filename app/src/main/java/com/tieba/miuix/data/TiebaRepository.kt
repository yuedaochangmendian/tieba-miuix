// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import com.tieba.miuix.protocol.CommonReq
import com.tieba.miuix.protocol.CommonResult
import com.tieba.miuix.protocol.ConcernedForums
import com.tieba.miuix.protocol.ForumDetailProto
import com.tieba.miuix.protocol.ForumHome
import com.tieba.miuix.protocol.ForumSearchResult
import com.tieba.miuix.protocol.FrsPageProto
import com.tieba.miuix.protocol.ForumSearchPostResult
import com.tieba.miuix.protocol.PbFloorProto
import com.tieba.miuix.protocol.PbPageProto
import com.tieba.miuix.protocol.ReplyResult
import com.tieba.miuix.protocol.SignResult
import com.tieba.miuix.protocol.StoredThreads
import com.tieba.miuix.protocol.TiebaError
import com.tieba.miuix.protocol.TiebaConst.PostSortType
import com.tieba.miuix.protocol.ProtoHttp
import com.tieba.miuix.protocol.ThreadSearchItem
import com.tieba.miuix.protocol.ThreadSearchResult
import com.tieba.miuix.protocol.UserCard
import com.tieba.miuix.protocol.UserPostResult
import com.tieba.miuix.protocol.TiebaConst.ThreadSortType
import com.tieba.miuix.protocol.TiebaConst
import com.tieba.miuix.protocol.UserSearchResult
import com.tieba.miuix.protocol.WebHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 登录凭证。匿名浏览时全部为空。 */
data class Credentials(
    val bduss: String = "",
    val stoken: String = "",
    val tbs: String = "",
    val uid: String = "",
    val nameShow: String = "",
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

    companion object {
        /** `opAgree` 的 `obj_type`：点的是某个楼层。 */
        const val OBJ_TYPE_POST = 1

        /** `opAgree` 的 `obj_type`：点的是主题本身。 */
        const val OBJ_TYPE_THREAD = 3
    }

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

    /**
     * 加载某一层的楼中楼（`/c/f/pb/floor`，`CMD=302002`）。
     *
     * 读取楼中楼**不需要登录**，所以这里始终按匿名请求发送（不带 BDUSS）。
     */
    suspend fun getSubPosts(
        tid: Long,
        pid: Long,
        forumId: Long = 0L,
        pn: Int = 1,
    ): PbFloorProto.Response = withContext(Dispatchers.IO) {
        if (tid <= 0L || pid <= 0L) {
            return@withContext PbFloorProto.Response(
                error = TiebaError(errno = 1, errmsg = "帖子或楼层 id 无效"),
                page = null,
                post = null,
                subPosts = emptyList(),
                thread = null,
                forum = null,
            )
        }
        val payload = PbFloorProto.pack(
            PbFloorProto.Params(
                tid = tid,
                pid = pid,
                pn = pn.coerceAtLeast(1),
                forumId = forumId,
                common = CommonReq(clientVersion = TiebaConst.LATEST_VERSION),
            ),
        )
        val url = "https://${TiebaConst.APP_BASE_HOST}/c/f/pb/floor?cmd=${TiebaConst.CMD_PB_FLOOR}"
        PbFloorProto.parse(ProtoHttp.post(url, payload))
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
     * 吧详情（App 协议 `POST /c/f/forum/getforumdetail?cmd=303021`）。
     *
     * 实测**匿名可用**：返回吧头像、会员数、帖子数、口号与分类。
     * 需要数字的吧 id（`forum_id`），调用方一般先用 [searchForum] 拿到 id。
     */
    suspend fun getForumDetail(forumId: Long): ForumDetailProto.Response = withContext(Dispatchers.IO) {
        if (forumId <= 0L) return@withContext ForumDetailProto.Response()
        val credentials = credentialsProvider()
        val bytes = ProtoHttp.post(
            url = "http://" + TiebaConst.APP_BASE_HOST +
                "/c/f/forum/getforumdetail?cmd=" + TiebaConst.CMD_FORUM_DETAIL + "&format=protobuf",
            payload = ForumDetailProto.pack(
                forumId = forumId,
                common = CommonReq(
                    clientVersion = TiebaConst.LATEST_VERSION,
                    bduss = credentials.bduss,
                    stoken = credentials.stoken,
                    tbs = credentials.tbs,
                ),
            ),
        )
        ForumDetailProto.parse(bytes)
    }

    /**
     * 用户发言流（`POST /c/u/feed/userpost`，表单）。
     *
     * 主题与回复走同一个接口，靠 [isThread] 区分；`uid` 用名片里的 `tieba_uid`。
     * 走 `APP_BASE_HOST` 时 [WebHttp.postFormJson] 会自动用 APP_SALT 签名。
     */
    suspend fun getUserPosts(
        uid: Long,
        pn: Int = 1,
        rn: Int = 20,
        isThread: Boolean = true,
    ): UserPostResult = withContext(Dispatchers.IO) {
        if (uid <= 0L) return@withContext UserPostResult()
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/u/feed/userpost",
            params = listOf(
                "_client_version" to TiebaConst.LATEST_VERSION,
                "uid" to uid.toString(),
                "pn" to pn.toString(),
                "rn" to rn.toString(),
                "is_thread" to if (isThread) "1" else "0",
                "need_content" to "1",
            ),
        )
        UserPostResult.parse(json)
    }

    /**
     * 用户名片（网页端 `GET /c/u/pc/userCard`）。
     *
     * 只要 `portrait` 就能查，**匿名可用**：昵称、吧龄、获赞/粉丝/关注数、
     * 个性签名、IP 归属地都在里面（实测响应里**没有** user_id / tieba_uid）。
     * 这个接口要按 `PC_SALT` 签名。
     *
     * 注意：接口只认裸 portrait（`tb.1.xxx`）。实测传完整 URL 或带 `?t=` 的
     * 尾巴都会返回 `error_code=210009 系统错误`，所以这里统一归一后再发。
     */
    suspend fun getUserCard(portrait: String): UserCard? = withContext(Dispatchers.IO) {
        val bare = barePortrait(portrait)
        if (bare.isEmpty()) return@withContext null
        val json = WebHttp.getJson(
            path = "/c/u/pc/userCard",
            params = listOf(
                "portrait" to bare,
                "subapp_type" to "pc",
                "_client_type" to "20",
            ),
            signSalt = TiebaConst.PC_SALT,
        )
        UserCard.parse(json)
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

    // ---------------------------------------------------------------- 写操作

    /**
     * 回复主题帖（`POST /c/c/post/add`，表单 + APP 盐签名）。
     *
     * 必须已登录且拿到 `tbs`，否则服务端直接回「用户未登录」或「验证码」。
     * 参数按 TiebaLite 的 `addPostFlow` 抄：`_client_version` 用 `LATEST_VERSION`，
     * `anonymous=1` 表示的是「用户名显示策略」而不是匿名发帖，别改。
     *
     * @param tid 主题帖 id
     * @param fid 吧 id，拿不到时传 0（服务端会用 kw 兜）
     * @param kw 吧名
     * @param content 正文
     * @param quoteId 楼中楼 id：回复某一层楼中楼时传，回复主题帖时留 0
     * @param replyUid 被回复者的 uid（回复楼中楼时一起带上）
     * @return [ReplyResult]，`isSuccess` 为 false 时看 `displayMessage`
     */
    suspend fun replyPost(
        tid: Long,
        fid: Long,
        kw: String,
        content: String,
        quoteId: Long = 0L,
        replyUid: Long = 0L,
    ): ReplyResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isLoggedIn) {
            return@withContext ReplyResult(errorMessage = "还没有登录，先登录再回复")
        }
        if (tid <= 0L) return@withContext ReplyResult(errorMessage = "帖子 id 无效")
        if (content.isBlank()) return@withContext ReplyResult(errorMessage = "回复内容不能为空")
        if (credentials.tbs.isEmpty()) {
            return@withContext ReplyResult(
                errorMessage = "缺少发帖令牌（tbs），到设置里刷新后再试",
            )
        }
        val params = listOf(
            "anonymous" to "1",
            "authsid" to "null",
            "barrage_time" to "0",
            "can_no_forum" to "0",
            "content" to content,
            "entrance_type" to "0",
            "fid" to fid.toString(),
            "from_fourm_id" to "null",
            "is_ad" to "0",
            "is_barrage" to "0",
            "is_feedback" to "0",
            "kw" to kw,
            "new_vcode" to "1",
            "post_from" to "3",
            "quote_id" to if (quoteId > 0L) quoteId.toString() else "null",
            "reply_uid" to if (replyUid > 0L) replyUid.toString() else "null",
            "repostid" to "null",
            "stoken" to credentials.stoken,
            "takephoto_num" to "0",
            "tbs" to credentials.tbs,
            "tid" to tid.toString(),
            "v_fid" to "",
            "v_fname" to "",
            "vcode_tag" to "12",
            "_client_version" to TiebaConst.LATEST_VERSION,
        )
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/c/post/add",
            params = params,
        )
        ReplyResult.parse(json)
    }

    /**
     * 发主题帖（`POST /c/c/thread/add`）。
     *
     * 字段照 TiebaLite 的 `addThreadFlow` 抄：`is_ntitle` 表示是否带标题
     * （无标题模式标题传空串），`new_vcode` 在它那里被写成 `not_see_lz`（历史
     * 命名，值固定 1）。和回帖一样必须带 `tbs`，否则服务端按未验证处理。
     */
    suspend fun addThread(
        forumName: String,
        forumId: Long,
        title: String,
        content: String,
    ): ReplyResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isLoggedIn) {
            return@withContext ReplyResult(errorMessage = "还没有登录，先登录再发帖")
        }
        if (content.isBlank()) {
            return@withContext ReplyResult(errorMessage = "正文不能为空")
        }
        if (forumName.isBlank() && forumId <= 0L) {
            return@withContext ReplyResult(errorMessage = "没有指定要发到哪个吧")
        }
        if (credentials.tbs.isEmpty()) {
            return@withContext ReplyResult(
                errorMessage = "缺少发帖令牌（tbs），到设置里刷新后再试",
            )
        }
        val hasTitle = title.isNotBlank()
        val params = listOf(
            "anonymous" to "1",
            "call_from" to "2",
            "can_no_forum" to "0",
            "content" to content,
            "cuid_gid" to "",
            "entrance_type" to "1",
            "fid" to forumId.toString(),
            "is_feedback" to "0",
            "is_hide" to "0",
            "is_ntitle" to (if (hasTitle) "1" else "0"),
            "kw" to forumName,
            "name_show" to credentials.nameShow,
            "new_vcode" to "1",
            "reply_uid" to "null",
            "stoken" to credentials.stoken,
            "takephoto_num" to "0",
            "tbs" to credentials.tbs,
            "title" to title,
            "vcode_tag" to "12",
            "z_id" to "",
            "_client_version" to TiebaConst.LATEST_VERSION,
        )
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/c/thread/add",
            params = params,
        )
        ReplyResult.parse(json)
    }

    // ------------------------------------------------------------ 收藏 / 关注

    /**
     * 我的收藏（主题帖）。
     *
     * `POST /c/f/post/threadstore`：表单字段是 `rn` + `offset` + `user_id`，
     * 页码靠偏移量推出来（offset = (pn - 1) * rn）。未登录时服务端会拒绝，
     * 这里先在本地拦一道，返回空列表 + 可读原因。
     */
    suspend fun getStoredThreads(pn: Int = 1, rn: Int = StoredThreads.PAGE_SIZE): StoredThreads =
        withContext(Dispatchers.IO) {
            val credentials = credentialsProvider()
            if (!credentials.isLoggedIn) {
                return@withContext StoredThreads(errorCode = 340006, errorMessage = "还没有登录")
            }
            val page = pn.coerceAtLeast(1)
            val size = rn.coerceIn(1, 100)
            val json = WebHttp.postFormJson(
                host = TiebaConst.APP_BASE_HOST,
                path = "/c/f/post/threadstore",
                params = listOf(
                    "rn" to size.toString(),
                    "offset" to ((page - 1) * size).toString(),
                    "user_id" to credentials.uid,
                    "stoken" to credentials.stoken,
                    "_client_version" to TiebaConst.LATEST_VERSION,
                ),
            )
            StoredThreads.parse(json)
        }

    /**
     * 我关注的吧。
     *
     * `POST /c/f/forum/getforumlist`：`/c/f/` 前缀的老通道，同样需要 BDUSS。
     */
    suspend fun getConcernedForums(): ConcernedForums = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isLoggedIn) {
            return@withContext ConcernedForums(errorCode = 340006, errorMessage = "还没有登录")
        }
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/f/forum/getforumlist",
            params = listOf(
                "stoken" to credentials.stoken,
                "_client_version" to TiebaConst.LATEST_VERSION,
            ),
        )
        ConcernedForums.parse(json)
    }

    /**
     * 收藏 / 取消收藏主题帖。
     *
     * `POST /c/c/post/addstore` 的字段是单个 `data`，值是 `[{pid,tid,status}]`
     * 的 JSON 文本（照 TiebaLite `NewCollectDataBean` 抄），`status=1` 收藏、
     * `status=0` 取消。返回体是 `error_code` + `error_msg`。
     */
    suspend fun setThreadStored(
        tid: Long,
        pid: Long,
        store: Boolean,
    ): CommonResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isLoggedIn) {
            return@withContext CommonResult(
                success = false,
                errorCode = 340006,
                errorMessage = "收藏需要先登录：到「我的 → 账号」里登录贴吧",
            )
        }
        if (credentials.tbs.isEmpty()) {
            return@withContext CommonResult(
                success = false,
                errorCode = 4,
                errorMessage = "缺少发帖令牌（tbs），到「我的 → 账号」里刷新一下",
            )
        }
        if (tid <= 0L) {
            return@withContext CommonResult(false, 0, "帖子 id 无效")
        }
        val data = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("tid", tid.toString())
                    put("pid", (if (pid > 0L) pid else tid).toString())
                    put("status", if (store) 1 else 0)
                },
            )
        }.toString()
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/c/post/addstore",
            params = listOf(
                "data" to data,
                "tbs" to credentials.tbs,
                "stoken" to credentials.stoken,
                "_client_version" to TiebaConst.LATEST_VERSION,
            ),
        )
        CommonResult.of(json)
    }

    /**
     * 关注 / 取关某个吧。
     *
     * `POST /c/c/forum/like`：`fid` 传 `null` 时服务端用 `kw` 解析吧，
     * `tbs` 必须带，否则返回「需要验证码」一类错误。
     */
    suspend fun setForumConcerned(
        forumId: Long,
        forumName: String,
        concern: Boolean,
    ): CommonResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isLoggedIn) {
            return@withContext CommonResult(
                success = false,
                errorCode = 340006,
                errorMessage = "关注需要先登录：到「我的 → 账号」里登录贴吧",
            )
        }
        if (credentials.tbs.isEmpty()) {
            return@withContext CommonResult(
                success = false,
                errorCode = 4,
                errorMessage = "缺少发帖令牌（tbs），到「我的 → 账号」里刷新一下",
            )
        }
        if (forumName.isBlank() && forumId <= 0L) {
            return@withContext CommonResult(false, 0, "吧名无效")
        }
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = if (concern) "/c/c/forum/like" else "/c/c/forum/unlike",
            params = listOf(
                "fid" to (if (forumId > 0L) forumId.toString() else "null"),
                "kw" to forumName,
                "tbs" to credentials.tbs,
                "stoken" to credentials.stoken,
                "_client_version" to TiebaConst.LATEST_VERSION,
            ),
        )
        CommonResult.of(json)
    }

    /**
     * 点赞 / 取消点赞。
     *
     * `POST /c/c/agree/opAgree`：`obj_type` 决定点的是楼层还是主题
     * （1 = 楼层，3 = 主题），`op_type` 0 = 点赞、1 = 取消赞。
     * 必须带 `tbs`，否则服务端按未验证处理。
     */
    suspend fun setPostAgree(
        threadId: Long,
        postId: Long,
        agree: Boolean,
        objType: Int = OBJ_TYPE_POST,
    ): CommonResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isLoggedIn) {
            return@withContext CommonResult(
                success = false,
                errorCode = 340006,
                errorMessage = "点赞需要先登录：到「我的 → 账号」里登录贴吧",
            )
        }
        if (credentials.tbs.isEmpty()) {
            return@withContext CommonResult(
                success = false,
                errorCode = 4,
                errorMessage = "缺少发帖令牌（tbs），到「我的 → 账号」里刷新一下",
            )
        }
        if (threadId <= 0L || postId <= 0L) {
            return@withContext CommonResult(false, 0, "帖子 id 无效")
        }
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/c/agree/opAgree",
            params = listOf(
                "post_id" to postId.toString(),
                "thread_id" to threadId.toString(),
                "cuid_gid" to "",
                "agree_type" to "2",
                "obj_type" to objType.toString(),
                "op_type" to (if (agree) "0" else "1"),
                "tbs" to credentials.tbs,
                "stoken" to credentials.stoken,
                "_client_version" to TiebaConst.LATEST_VERSION,
            ),
        )
        CommonResult.of(json)
    }

    /**
     * 关注 / 取关某个用户。
     *
     * `POST /c/c/user/follow`（取关是 `/c/c/user/unfollow`）：只认**裸 portrait**
     * （`tb.1.xxx`，不能带 `?t=` 或 `.jpg`，否则拿不到人），`tbs` + `stoken` 必带。
     * 取关接口比关注多一个 `timestamp` 字段，照 TiebaLite 的 `unfollowFlow` 带上。
     */
    suspend fun setUserFollowed(portrait: String, follow: Boolean): CommonResult =
        withContext(Dispatchers.IO) {
            val credentials = credentialsProvider()
            if (!credentials.isLoggedIn) {
                return@withContext CommonResult(
                    success = false,
                    errorCode = 340006,
                    errorMessage = "关注需要先登录：到「我的 → 账号」里登录贴吧",
                )
            }
            if (credentials.tbs.isEmpty()) {
                return@withContext CommonResult(
                    success = false,
                    errorCode = 4,
                    errorMessage = "缺少发帖令牌（tbs），到「我的 → 账号」里刷新一下",
                )
            }
            val bare = barePortrait(portrait)
            if (bare.isEmpty()) {
                return@withContext CommonResult(false, 0, "没有拿到这个用户的标识")
            }
            val base = mutableListOf(
                "portrait" to bare,
                "tbs" to credentials.tbs,
                "authsid" to "null",
                "stoken" to credentials.stoken,
                "from_type" to "2",
                "in_live" to "0",
                "_client_version" to TiebaConst.LATEST_VERSION,
            )
            if (!follow) {
                base += "timestamp" to System.currentTimeMillis().toString()
            }
            val json = WebHttp.postFormJson(
                host = TiebaConst.APP_BASE_HOST,
                path = if (follow) "/c/c/user/follow" else "/c/c/user/unfollow",
                params = base,
            )
            CommonResult.of(json)
        }

    /**
     * 单个吧签到（`POST /c/c/forum/sign`）。
     *
     * 表单只要 `kw` + `tbs`，其余（`stoken`/`_client_version`/`user_id`）在
     * TiebaLite 里是公共参数，这里显式带上更稳。
     */
    suspend fun signForum(forumName: String): SignResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isLoggedIn) {
            return@withContext SignResult(
                success = false,
                errorCode = 340006,
                errorMessage = "签到需要先登录：到「我的 → 账号」里登录贴吧",
            )
        }
        if (credentials.tbs.isEmpty()) {
            return@withContext SignResult(
                success = false,
                errorCode = 4,
                errorMessage = "缺少发帖令牌（tbs），到「我的 → 账号」里刷新一下",
            )
        }
        if (forumName.isBlank()) {
            return@withContext SignResult(false, 0, "吧名无效")
        }
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/c/forum/sign",
            params = listOf(
                "kw" to forumName,
                "tbs" to credentials.tbs,
                "stoken" to credentials.stoken,
                "user_id" to credentials.uid,
                "_client_version" to TiebaConst.LATEST_VERSION,
            ),
        )
        SignResult.parseSingle(json)
    }

    /**
     * 一键签到（`POST /c/c/forum/msign`）。
     *
     * `forum_ids` 是逗号分隔的数字 id 串，一次最多几十个吧；返回 `info` 数组里
     * 每个吧一条记录，`signed=1` 表示这个吧今天已经签过了。
     */
    suspend fun signForums(forumIds: List<Long>): SignResult = withContext(Dispatchers.IO) {
        val credentials = credentialsProvider()
        if (!credentials.isLoggedIn) {
            return@withContext SignResult(
                success = false,
                errorCode = 340006,
                errorMessage = "签到需要先登录：到「我的 → 账号」里登录贴吧",
            )
        }
        if (credentials.tbs.isEmpty()) {
            return@withContext SignResult(
                success = false,
                errorCode = 4,
                errorMessage = "缺少发帖令牌（tbs），到「我的 → 账号」里刷新一下",
            )
        }
        val ids = forumIds.filter { it > 0L }.distinct()
        if (ids.isEmpty()) {
            return@withContext SignResult(false, 0, "没有可签到的吧")
        }
        val json = WebHttp.postFormJson(
            host = TiebaConst.APP_BASE_HOST,
            path = "/c/c/forum/msign",
            params = listOf(
                "forum_ids" to ids.joinToString(","),
                "tbs" to credentials.tbs,
                "stoken" to credentials.stoken,
                "user_id" to credentials.uid,
                "authsid" to "null",
                "_client_version" to TiebaConst.LATEST_VERSION,
            ),
        )
        SignResult.parseBatch(json)
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
/**
 * 从一个 portrait 里取出裸标识（`tb.1.xxx`）。
 *
 * 这个值在工程里有四种形态：帖子里拿到的裸串、网页接口给的完整 URL、
 * 带 `?t=` 缓存时间戳的尾巴、以及搜索接口带上的 `.jpg` 后缀。用户名片接口
 * 只认纯标识，统一在这里归一。
 */
internal fun barePortrait(portrait: String): String {
    val clean = portrait.trim().substringBefore('?')
    if (clean.isEmpty()) return ""
    val marker = "/item/"
    val index = clean.lastIndexOf(marker)
    val bare = if (index >= 0) clean.substring(index + marker.length) else clean
    // 搜索接口会拼成 `tb.1.xxx.jpg`，但名片接口不认后缀。
    return bare.substringBeforeLast('.', bare)
}

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