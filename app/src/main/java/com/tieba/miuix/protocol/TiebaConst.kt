// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 贴吧 APP 端协议的常量定义。
 *
 * 取值来源：aiotieba（The Unlicense）的 `aiotieba/const.py` 以及
 * `api/get_threads/_api.py` / `api/get_posts/_api.py`。
 */
object TiebaConst {

    /** APP 端接口域名（`/c/f/...` 系列接口）。 */
    const val APP_BASE_HOST: String = "tiebac.baidu.com"

    /** 网页端接口域名。 */
    const val WEB_BASE_HOST: String = "tieba.baidu.com"

    /** 大多数接口使用的客户端版本。 */
    const val LATEST_VERSION: String = "22.12.1.0"

    /** 部分依赖旧版格式的接口（如 `get_threads`）使用的客户端版本。 */
    const val LEGACY_VERSION: String = "12.64.1.1"

    /** 聊天类接口使用的客户端版本。 */
    const val CHAT_VERSION: String = "12.68.1.0"

    /** APP 端表单签名盐值。 */
    const val APP_SALT: String = "tiebaclient!!!"

    /** 网页端签名盐值。 */
    const val PC_SALT: String = "36770b1f34c9bbf2e7d1a99d2b82fa9e"

    /** 杂项接口签名盐值。 */
    const val MISC_SALT: String = "0039d79dc3cc2075129745a30237a3c4"

    /** 获取贴吧首页帖子列表的 cmd。 */
    const val CMD_FRS_PAGE: Int = 301001

    /** 获取主题帖内回复的 cmd。 */
    const val CMD_PB_PAGE: Int = 302001

    /** 列表页默认每页条目数（接口限制 1..100）。 */
    const val DEFAULT_THREAD_PAGE_SIZE: Int = 30

    /** 主题帖内默认每页楼层数（接口限制 2..30）。 */
    const val DEFAULT_POST_PAGE_SIZE: Int = 30

    /** 请求使用的 User-Agent，与 aiotieba 保持一致。 */
    const val USER_AGENT: String = "aiotieba/1.0.0"

    /** 客户端类型：2 表示 Android。 */
    const val CLIENT_TYPE_ANDROID: Int = 2
}

/** 主题帖排序方式。 */
object ThreadSortType {
    /** 热门（仅对有热门分区的贴吧有效）。 */
    const val HOT: Int = 3

    /** 按发布时间。 */
    const val CREATE: Int = 1

    /** 按回复时间（默认）。 */
    const val REPLY: Int = 6

    /** 关注的人。 */
    const val FOLLOW: Int = 2
}

/** 楼层排序方式。 */
object PostSortType {
    /** 时间升序（默认）。 */
    const val ASC: Int = 0

    /** 时间倒序。 */
    const val DESC: Int = 1

    /** 热门序。 */
    const val HOT: Int = 2
}