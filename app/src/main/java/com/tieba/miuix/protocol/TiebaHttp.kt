// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import okhttp3.Headers
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 贴吧 APP 端 protobuf 接口的 HTTP 客户端。
 *
 * 请求打包方式严格对齐 aiotieba `core/http.py` 的 `pack_proto_request`：
 *  - 使用 `multipart/form-data`，boundary 固定为 `-*_r1999`；
 *  - 唯一的分片 is `data`，其 `Content-Disposition` 为
 *    `form-data; name="data"; filename="file"`，且**不带** Content-Type；
 *  - 额外请求头 `x_bd_data_type: protobuf`。
 *
 * 响应默认由 OkHttp 自动处理 gzip（我们不再手工设置 `Accept-Encoding`）。
 */
internal object ProtoHttp {

    private const val BOUNDARY = "-*_r1999"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** 发送一个 protobuf 请求并返回响应体的原始字节。 */
    @Throws(IOException::class)
    fun post(url: String, payload: ByteArray, userAgent: String = TiebaConst.USER_AGENT): ByteArray {
        val body = MultipartBody.Builder(BOUNDARY)
            .setType(MultipartBody.FORM)
            .addPart(
                Headers.headersOf(
                    "Content-Disposition",
                    "form-data; name=\"data\"; filename=\"file\"",
                ),
                payload.toRequestBody(null),
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("x_bd_data_type", "protobuf")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("贴吧接口返回 HTTP ${response.code}")
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    /**
     * 取一段纯文本，用于 `/dc/common/tbs` 这类返回 JSON 或 HTML 的小接口。
     *
     * `Cookie` 头只在 [cookie] 非空时附带，方便匿名与登录两种场景复用。
     */
    @Throws(IOException::class)
    fun getText(
        url: String,
        cookie: String = "",
        userAgent: String = TiebaConst.WEB_USER_AGENT,
        referer: String? = null,
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Encoding", "gzip, deflate")
        if (cookie.isNotEmpty()) builder.header("Cookie", cookie)
        if (!referer.isNullOrEmpty()) builder.header("Referer", referer)

        client.newCall(builder.get().build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("贴吧接口返回 HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }
}