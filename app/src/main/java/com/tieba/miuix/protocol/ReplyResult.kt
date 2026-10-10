// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import org.json.JSONObject

/**
 * 发帖 / 回帖结果。
 *
 * 回帖走 `/c/c/post/add`，发主题走 `/c/c/thread/add`，两者都是表单 + APP 盐签名，
 * 成功时返回 `pid`/`tid`；失败时 `error_code` 是字符串（`"0"` 表示成功），
 * `error_msg` 里是原因。被风控拦下时 `info.need_vcode = "1"`，本工程目前只能把
 * 这句提示原样转给用户。
 */
data class ReplyResult(
    val isSuccess: Boolean = false,
    val errorCode: String = "",
    val errorMessage: String = "",
    val pid: Long = 0,
    val tid: Long = 0,
    val needVcode: Boolean = false,
) {
    /** 能给用户看的一句话；[successMessage] 用来区分「发帖成功」与「回复成功」。 */
    fun displayMessage(successMessage: String = "回复成功"): String = when {
        isSuccess -> successMessage
        needVcode -> "需要验证码，请在官方客户端完成一次验证后再试"
        errorMessage.isNotBlank() -> errorMessage
        errorCode.isNotBlank() -> "接口返回错误（error_code = " + errorCode + "）"
        else -> "发送失败"
    }

    companion object {
        fun parse(json: JSONObject): ReplyResult {
            val code = json.opt("error_code")?.toString().orEmpty()
            val message = json.optString("error_msg").ifEmpty { json.optString("msg") }
            val info = json.optJSONObject("info")
            val needVcode = info?.opt("need_vcode")?.toString() == "1"
            val pid = json.opt("pid")?.toString().orEmpty().toLongOrNull() ?: 0L
            val success = (code.isEmpty() || code == "0") && !needVcode
            return ReplyResult(
                isSuccess = success,
                errorCode = code,
                errorMessage = message,
                pid = pid,
                tid = json.opt("tid")?.toString().orEmpty().toLongOrNull() ?: 0L,
                needVcode = needVcode,
            )
        }
    }
}
