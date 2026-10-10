// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.protocol

import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 网页协议（`/mo/q\/\*`、`/c/s\/\*`）的统一入口。
 *
 * 三种调用形态：
 * - [getJson]：网页 GET，`/mo/q/search\/\*` 走这条。
 * - [postFormJson]：表单 POST，按需追加 App 签名（`sign` 字段）。
 * - [encodeQuery]：把参数拼成 query string，主要给日志和 Referer 用。
 */
internal object WebHttp {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** 拼一个移动端搜索页的 Referer，服务端会校验它。 */
    fun hybridReferer(keyword: String): String =
        "https://" + TiebaConst.WEB_BASE_HOST + "/mo/q/hybrid/search" +
            "?keyword=" + urlEncode(keyword) +
            "&_webview_time=" + System.currentTimeMillis()

    /**
     * 发一个网页端 GET 请求并解析成 JSON。
     *
     * @param path 形如 `/mo/q/search/forum`（不要带前导主机名）
     * @param params 查询参数，顺序即拼进 URL 的顺序
     * @param referer 需要时传 [hybridReferer]
     * @param signSalt 需要签名的接口传对应的盐（如 [TiebaConst.PC_SALT]），
     *   签名值是拼在所有参数之后的最后一个参数
     */
    fun getJson(
        path: String,
        params: List<Pair<String, String>>,
        referer: String? = null,
        signSalt: String? = null,
    ): JSONObject {
        val all = if (signSalt != null) {
            params + ("sign" to Sign.sign(params.toMap(), signSalt))
        } else {
            params
        }
        val builder = ("https://" + TiebaConst.WEB_BASE_HOST + path).toHttpUrl().newBuilder()
        all.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .get()
            .webHeaders(referer)
            .build()
        return executeJson(request)
    }

    /**
     * 发一个表单 POST 请求并解析成 JSON。
     *
     * @param useAppSign 是否追加 App 签名（`Sign.sign` + `APP_SALT`），
     *   `c.tieba.baidu.com` 的接口基本都要
     * @param cookie 需要登录的接口必须把 `BDUSS=...; STOKEN=...` 一起带上。
     *   **服务端认的是 Cookie，不是表单里的 bduss 字段**：正文参数写对了但没带
     *   Cookie 时，收藏 / 签到 / 回帖这些接口一律返回
     *   `error_code=1 用户未登录或登录失败`。
     */
    fun postFormJson(
        host: String,
        path: String,
        params: List<Pair<String, String>>,
        useAppSign: Boolean = true,
        referer: String? = null,
        cookie: String? = null,
    ): JSONObject {
        val all: List<Pair<String, String>> = if (useAppSign) {
            params + ("sign" to Sign.sign(params.toMap(), TiebaConst.APP_SALT))
        } else {
            params
        }
        val body = FormBody.Builder().apply {
            all.forEach { (key, value) -> add(key, value) }
        }.build()
        val userAgent = if (host == TiebaConst.APP_BASE_HOST) {
            TiebaConst.APP_FORM_USER_AGENT
        } else {
            TiebaConst.WEB_USER_AGENT
        }
        val request = Request.Builder()
            .url("https://" + host + path)
            .post(body)
            .webHeaders(referer, userAgent, cookie)
            .build()
        return executeJson(request)
    }

    /** 把参数拼成 query string。 */
    fun encodeQuery(params: List<Pair<String, String>>): String =
        params.joinToString("&") { (key, value) -> key + "=" + urlEncode(value) }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun Request.Builder.webHeaders(
        referer: String?,
        userAgent: String = TiebaConst.WEB_USER_AGENT,
        cookie: String? = null,
    ): Request.Builder = apply {
        header("User-Agent", userAgent)
        header("Accept", "application/json, text/plain, */*")
        header("Accept-Encoding", "gzip, deflate")
        header("Cache-Control", "no-cache")
        header("Pragma", "no-cache")
        header("X-Requested-With", "com.baidu.tieba")
        if (!cookie.isNullOrEmpty()) {
            header("Cookie", cookie)
        }
        if (!referer.isNullOrEmpty()) {
            header("Referer", referer)
        }
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("贴吧接口返回 HTTP " + response.code)
            }
            val text = response.body?.string()
            if (text.isNullOrBlank()) {
                throw IOException("贴吧接口返回了空响应")
            }
            return JSONObject(text)
        }
    }
}
