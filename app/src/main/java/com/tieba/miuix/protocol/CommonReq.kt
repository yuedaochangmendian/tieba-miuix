// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

/**
 * 贴吧接口的 `common` 字段（`CommonReq`）。
 *
 * 字段编号来自 aiotieba 的 `_protobuf/CommonReq.proto`：
 * `_client_type=1`、`_client_version=2`、`_client_id=3`、`_phone_imei=5`、
 * `_from=6`、`cuid=7`、`_timestamp=8`、`model=9`、`BDUSS=10`、`tbs=11`、
 * `net_type=12`、`pversion=24`、`_os_version=25`、`brand=26`、
 * `stoken=30`、`z_id=31`、`cuid_galaxy2=32`、`cuid_gid=33`、`scr_w=37`、
 * `scr_h=38`、`scr_dip=39`、`q_type=40`、`sdk_ver=42`、`android_id=54`、
 * `cmode=55`、`start_scheme=56`、`start_type=57`、`user_agent=62`。
 *
 * 真实客户端会填满几乎所有字段，但服务端只强校验其中少数几项。
 * 本项目仅填写访问公开数据所必需的字段，其余留空。
 */
data class CommonReq(
    /** `_client_type`：2 表示 Android。 */
    var clientType: Int = TiebaConst.CLIENT_TYPE_ANDROID,
    /** `_client_version`：不同接口使用的版本号不同。 */
    var clientVersion: String = TiebaConst.LATEST_VERSION,
    /** `BDUSS`：登录凭证，匿名浏览时为空。 */
    var bduss: String = "",
    /** `stoken`：与 BDUSS 配套。 */
    var stoken: String = "",
    /** `tbs`：发帖/回复所需的令牌。 */
    var tbs: String = "",
    /** `user_agent`。 */
    var userAgent: String = TiebaConst.USER_AGENT,
    /** `_timestamp`：秒级时间戳。 */
    var timestamp: Long = 0L,
) {
    fun writeTo(w: ProtoWriter) {
        w.int32(1, clientType)
        w.string(2, clientVersion)
        if (timestamp != 0L) w.int64(8, timestamp)
        if (bduss.isNotEmpty()) w.string(10, bduss)
        if (tbs.isNotEmpty()) w.string(11, tbs)
        if (stoken.isNotEmpty()) w.string(30, stoken)
        if (userAgent.isNotEmpty()) w.string(62, userAgent)
    }
}
