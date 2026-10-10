// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.data

import com.tieba.miuix.protocol.CommonReq
import com.tieba.miuix.protocol.FrsPageProto
import com.tieba.miuix.protocol.PbPageProto
import com.tieba.miuix.protocol.PostSortType
import com.tieba.miuix.protocol.ProtoHttp
import com.tieba.miuix.protocol.ThreadSortType
import com.tieba.miuix.protocol.TiebaConst
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
}