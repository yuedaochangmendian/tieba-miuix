// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import java.security.MessageDigest

/**
 * 复刻 aiotieba 的表单签名算法。
 *
 * 算法（见 aiotieba `helper/crypto/sign.py`）：
 *  1. 将参数字典按 key 做升序排序；
 *  2. 依次拼接 `"{key}={value}"` 的 UTF-8 字节；
 *  3. 追加盐值字节；
 *  4. 取 MD5 的 16 进制小写摘要。
 *
 * 盐值：[TiebaConst.APP_SALT]（APP 端）、`PC_SALT`（网页端）、`MISC_SALT`（杂项）。
 */
object Sign {

    fun sign(params: Map<String, String>, salt: String = TiebaConst.APP_SALT): String {
        val md = MessageDigest.getInstance("MD5")
        for (key in params.keys.sorted()) {
            md.update("$key=${params[key]}".toByteArray(Charsets.UTF_8))
        }
        md.update(salt.toByteArray(Charsets.UTF_8))
        return md.digest().toHex()
    }

    fun md5Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}