// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 贴吧协议里用到的固定常量。
 *
 * 这些值全部对齐 aiotieba / TiebaLite 的实现：版本号、盐值、cmd 一旦写错，
 * 服务端要么返回空数据，要么直接报参数错误，所以集中放在这里便于比对。
 */
object TiebaConst {

    /** App 协议（protobuf / 表单）用的主机。 */
    const val APP_BASE_HOST = "tiebac.baidu.com"

    /** 网页协议（`/mo/q\/\*`）用的主机。 */
    const val WEB_BASE_HOST = "tieba.baidu.com"

    /** 最新客户端版本号，写操作和 protobuf 通道用。 */
    const val LATEST_VERSION = "22.12.1.0"

    /** 老版本号，吧列表这类只读接口用它可以少带一些参数。 */
    const val LEGACY_VERSION = "12.64.1.1"

    /** 聊天 / 消息接口用的版本号。 */
    const val CHAT_VERSION = "12.68.1.0"

    /** App 协议签名盐值。 */
    const val APP_SALT = "tiebaclient!!!"

    /** 网页协议签名盐值。 */
    const val PC_SALT = "36770b1f34c9bbf2e7d1a99d2b82fa9e"

    /** 杂项接口签名盐值。 */
    const val MISC_SALT = "0039d79dc3cc2075129745a30237a3c4"

    /** 吧帖子列表（protobuf）。 */
    const val CMD_FRS_PAGE = 301001

    /** 帖子楼层列表（protobuf）。 */
    const val CMD_PB_PAGE = 302001

    /** 吧详情（protobuf）。 */
    const val CMD_FORUM_DETAIL = 303021

    /** 首页热门推荐流（`/c/f/forum/hotThreadList`）。 */
    const val CMD_HOT_THREAD_LIST = 309661
    /** 楼中楼（protobuf）。 */
    const val CMD_PB_FLOOR = 302002

    /** 吧列表默认每页条数。 */
    const val DEFAULT_THREAD_PAGE_SIZE = 30

    /** 楼层默认每页条数。 */
    const val DEFAULT_POST_PAGE_SIZE = 30

    /** protobuf 通道的 User-Agent。 */
    const val USER_AGENT = "aiotieba/1.0.0"

    /** Android 客户端类型。 */
    const val CLIENT_TYPE_ANDROID = 2

    /**
     * 网页协议 User-Agent。
     *
     * `/mo/q/search\/\*` 这类接口会**按 UA 决定返回 JSON 还是 HTML**，
     * 带 `tieba/` 后缀的移动端 Chrome UA 才能拿到 JSON，
     * 用 `aiotieba/1.0.0` 会拿到一整个网页。
     */
    const val WEB_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Version/4.0 Chrome/135.0.0.0 Mobile Safari/537.36 tieba/12.64.1.1 skin/default"

    /**
     * `c.tieba.baidu.com` 表单接口（`/c/s/searchpost` 等）的 User-Agent。
     *
     * 与 [WEB_USER_AGENT] 不同：这些接口是给客户端用的，UA 必须长得像 App。
     */
    const val APP_FORM_USER_AGENT = "bdtb for Android 7.2.0.0"

    /** 吧帖子列表排序方式。 */
    object ThreadSortType {
        /** 回复时间。 */
        const val REPLY = 6

        /** 发布时间。 */
        const val CREATE = 1

        /** 热度。 */
        const val HOT = 3

        /** 只看关注的人。 */
        const val FOLLOW = 2
    }

    /** 楼层排序方式。 */
    object PostSortType {
        /** 正序（按楼层从低到高）。 */
        const val ASC = 0

        /** 倒序。 */
        const val DESC = 1

        /** 热门。 */
        const val HOT = 2
    }
}
