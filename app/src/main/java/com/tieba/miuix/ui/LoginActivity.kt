// Copyright 2026, tieba-miuix contributors
// SPDX-License-Identifier: Apache-2.0
package com.tieba.miuix.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.tieba.miuix.R
import com.tieba.miuix.data.AccountStore
import com.tieba.miuix.protocol.TiebaConst

/**
 * 贴吧登录页。
 *
 * 贴吧官方没有对外开放的账号密码接口，第三方客户端（含 TiebaLite）统一的
 * 做法是：内嵌 WebView 打开官方登录页，登录成功后从 Cookie 里取 `BDUSS`
 * 与 `STOKEN`。本项目沿用这个方案。
 *
 * 登录成功的判定有两个条件，满足任一即可：
 *  1. 当前 URL 落在 `tieba.baidu.com/index/tbwise` 或其移动版域名下；
 *  2. `tieba.baidu.com` 或 `tiebac.baidu.com` 域下已经能读到 `BDUSS` Cookie。
 *
 * 结果通过 `setResult` 返回 `BDUSS` / `STOKEN`，由调用方写入 [AccountStore]。
 * `tbs` 不在这里取——它需要带 Cookie 再发一次请求，交给调用方处理。
 */
class LoginActivity : Activity() {

    private lateinit var webView: WebView

    /**
     * 加载指示器。
     *
     * 名字不能叫 `progress`：`Activity` 本身有 `getProgress(): Int`，Kotlin 会
     * 把 `progress` 解析成那个合成属性（Int），和自己的字段冲突。
     */
    private lateinit var loadingBar: ProgressBar
    private lateinit var statusText: TextView
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // 跟随系统深浅色：framework 主题没有 DayNight 变体，只能自己判断。
        // setTheme 必须在 super.onCreate 之前调用才生效。
        val nightMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isNight = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        setTheme(if (isNight) R.style.Theme_TiebaMiuix_Login_Dark else R.style.Theme_TiebaMiuix_Login)

        super.onCreate(savedInstanceState)
        window.statusBarColor = if (isNight) DARK_BACKGROUND else android.graphics.Color.WHITE

        val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL).orEmpty().ifEmpty { DEFAULT_LOGIN_URL }

        // 注意初始化顺序：webView 的回调会读 progress / statusText，
        // 所以这两个必须先建好，再挂回调。
        loadingBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
        }
        statusText = TextView(this).apply {
            text = STATUS_HINT
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
        }
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = TiebaConst.WEB_USER_AGENT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean = false

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    loadingBar.visibility = View.VISIBLE
                    statusText.text = "正在加载登录页…"
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    loadingBar.visibility = View.GONE
                    if (url != null && maybeFinishLogin(url)) return
                    statusText.text = STATUS_HINT
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    // 只关心主文档的错误，子资源失败不影响登录。
                    if (request?.isForMainFrame != true) return
                    loadingBar.visibility = View.GONE
                    statusText.text = "网络出错了，请检查网络后重试。"
                }
            }
            loadUrl(loginUrl)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            addView(statusText)
            addView(
                FrameLayout(this@LoginActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f,
                    )
                    addView(webView, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ))
                    addView(loadingBar, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ))
                },
            )
        }
        setContentView(root)
    }

    /**
     * 判断当前页面是否已经登录完成；完成则回传凭证。
     *
     * 这里刻意「先看 Cookie 再看 URL」：贴吧登录成功后会跳几次，URL 命中时机
     * 不稳定，而 Cookie 一定已经落好。
     */
    private fun maybeFinishLogin(url: String): Boolean {
        if (finished) return true
        val cookies = readCookies()
        val bduss = AccountStore.cookieValue(cookies, "BDUSS")
        val urlLooksLoggedIn = LOGIN_OK_URL_MARKERS.any { url.contains(it) }
        if (bduss.isEmpty() && !urlLooksLoggedIn) return false
        if (bduss.isEmpty()) return false

        finished = true
        loadingBar.visibility = View.GONE
        val stoken = AccountStore.cookieValue(cookies, "STOKEN")
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(RESULT_BDUSS, bduss)
                putExtra(RESULT_STOKEN, stoken)
            },
        )
        finish()
        return true
    }

    /** 同时读贴吧主站与客户端接口域下的 Cookie，取到哪个算哪个。 */
    private fun readCookies(): String {
        val manager = CookieManager.getInstance()
        val pieces = COOKIE_URLS.mapNotNull { manager.getCookie(it) }.filter { it.isNotEmpty() }
        return pieces.joinToString("; ")
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        /** 官方登录页，`u` 参数决定登录成功后跳回哪里。 */
        const val DEFAULT_LOGIN_URL =
            "https://wappass.baidu.com/passport?login&u=https%3A%2F%2Ftieba.baidu.com%2Findex%2Ftbwise%2Fmine"

        const val EXTRA_LOGIN_URL = "login_url"
        const val RESULT_BDUSS = "bduss"
        const val RESULT_STOKEN = "stoken"

        /** 构造启动用的 Intent。 */
        fun intent(context: android.content.Context, loginUrl: String = DEFAULT_LOGIN_URL): Intent =
            Intent(context, LoginActivity::class.java).putExtra(EXTRA_LOGIN_URL, loginUrl)

        private const val STATUS_HINT = "登录后即可关注贴吧、收发消息与签到"

        private const val DARK_BACKGROUND = 0xFF141414.toInt()

        private val LOGIN_OK_URL_MARKERS = listOf(
            "tieba.baidu.com/index/tbwise",
            "tiebac.baidu.com/index/tbwise",
            "tieba.baidu.com/mo/q/wise",
            "tieba.baidu.com/index.html",
        )

        private val COOKIE_URLS = listOf(
            "https://tieba.baidu.com/",
            "https://tiebac.baidu.com/",
        )
    }
}
