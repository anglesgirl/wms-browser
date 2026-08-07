package com.anglesgirl.wmsbrowser

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 扫码枪专用 WMS 浏览器。
 *  - 加载内网 wms.pantum.com（Infor WMS）
 *  - 兼容安卓 6（minSdk=23）
 *  - 内网自签名证书信任
 *  - 全屏 + 屏幕常亮（扫码连续作业）
 *  - 扫码枪模拟键盘输入（自动聚焦，超时回退重聚焦）
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val targetUrl = "https://wms.pantum.com"  // 内网地址（HTTPS）

    /** 扫码枪广播接收器（广播模式）：收到条码 → JS 填入当前输入框。 */
    private var scanReceiver: BroadcastReceiver? = null

    /** 常见扫码枪广播 action（不同厂商/型号不同，均注册监听） */
    private val scanActions = arrayOf(
        "android.intent.action.SCANRESULT",
        "nl.symbol.android.intent.action.SCANRESULT",   // Zebra
        "com.honeywell.decode.intent.action.SCAN_DECODING_RESULT", // Honeywell
        "android.intent.action.SCAN",                    // 通用
        "com.scanner.broadcast",                         // 国产枪通用
        "action.com.android.scanner",                    // 部分国产
        "android.intent.action.RECEIVE_SCANDATA_BROADCAST", // Newland (NLS-MT60/MT65/MT66, st6750 等)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏：隐藏状态栏
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        // 常亮：扫码枪长时间扫
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 强制软键盘默认隐藏（扫码枪扫码不弹），仅手点输入框才显示
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        setupWebView()
        setContentView(webView)
        // 禁用软键盘自动唤起：扫码枪输入时软键盘不自动弹出，只有用户手点输入框才显示
        // （API 23 WebView 需反射；API 26+ 原生支持）
        disableSoftInputOnFocus()
        registerScanReceiver()
        loadTarget()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this)
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        // 锁死字号：扫码枪屏幕小，防止页面字被系统字体放大/缩小
        s.textZoom = 100
        s.setSupportZoom(true)
        // 允许页面 JS 缩放（WMS 表格密集，用户可能放大看）
        s.cacheMode = WebSettings.LOAD_DEFAULT
        @Suppress("DEPRECATION")
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW)

        // WebChromeClient：支持 alert/prompt（WMS 弹窗）
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    // 注入 viewport 修正：小屏设备锁定视口宽度，防字放大/缩小
                    webView.evaluateJavascript(
                        "(function(){" +
                        "var m=document.querySelector('meta[name=viewport]');" +
                        "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}" +
                        "m.setAttribute('content','width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes');" +
                        "})();", null)
                    // 加载完成：自动聚焦输入框（扫码枪立刻能扫）
                    focusInput()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            // 内网自签名 https 证书信任
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                // WMS 内网自签，信任并继续（企业内网环境）
                handler.proceed()
            }
            // 拦截 <a target=_blank> 等，强制在本 WebView 打开
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }
            // 加载失败提示
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                Toast.makeText(this@MainActivity, "加载失败: $description", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTarget() {
        webView.loadUrl(targetUrl)
    }

    /** 注册扫码枪广播接收器。 */
    private fun registerScanReceiver() {
        try {
            val filter = IntentFilter()
            for (a in scanActions) filter.addAction(a)
            scanReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent == null) return
                    val barcode = extractBarcode(intent) ?: return
                    if (barcode.isNotEmpty()) injectBarcode(barcode)
                }
            }
            registerReceiver(scanReceiver, filter)
        } catch (e: Exception) {
            // 注册失败（部分系统限制）忽略
        }
    }

    override fun onDestroy() {
        try { if (scanReceiver != null) unregisterReceiver(scanReceiver) } catch (e: Exception) {}
        webView.destroy()
        super.onDestroy()
    }

    /** 从扫码广播 Intent 提取条码文本（不同厂商 key 不同，逐个尝试）。 */
    private fun extractBarcode(intent: Intent): String? {
        val keys = arrayOf(
            "android.intent.extra.SCAN_BROADCAST_DATA", // Newland
            "SCAN_RESULT", "scan_result", "barcode", "Barcode", "data", "text",
            "decoded_data", "SCAN_BARCODE1", "BARCODE", "value", "scanData", "code"
        )
        val ex = intent.extras ?: return null
        for (k in keys) {
            if (ex.containsKey(k)) {
                val v = ex.getString(k)
                if (!v.isNullOrBlank()) return v.trim()
            }
        }
        return null
    }

    /** 把条码注入 WebView 当前输入框（无焦点则聚焦第一个可见输入框），触发 input/change 事件。 */
    private fun injectBarcode(barcode: String) {
        runOnUiThread {
            val esc = barcode.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
            webView.evaluateJavascript(
                """
                (function(){
                  var el = document.activeElement;
                  if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) {
                    // 当前无输入框焦点 → 找第一个可见输入框
                    var els = document.querySelectorAll('input,textarea');
                    for (var i=0;i<els.length;i++){
                      if (els[i].offsetParent !== null) { el = els[i]; break; }
                    }
                  }
                  if (!el) return 'no-input';
                  el.focus();
                  el.value = '$esc';
                  // 触发 input + change（WMS 前端框架监听这些事件提交）
                  try { el.dispatchEvent(new Event('input', {bubbles:true})); } catch(e){}
                  try { el.dispatchEvent(new Event('change', {bubbles:true})); } catch(e){}
                  try { el.scrollIntoView({block:'center', inline:'nearest'}); } catch(e){}
                  return 'ok';
                })();
                """.trimIndent(), null)
        }
    }

    /** 禁用聚焦输入框时自动唤起软键盘（只对扫码枪模拟键盘生效，用户手动触摸仍显示）。 */
    private fun disableSoftInputOnFocus() {
        try {
            // API 26+ 原生；API23-25 需反射 —— 统一用反射调 View 的 @hide setShowSoftInputOnFocus
            val m = webView.javaClass.getMethod("setShowSoftInputOnFocus", Boolean::class.javaPrimitiveType)
            m.invoke(webView, false)
        } catch (e: Exception) {
            // 反射失败则用窗口 flag 兜底：保持无软键盘状态
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
    }

    /** 聚焦页面当前可见输入框，扫码枪才能接收；并滚动到可见位置（小屏防被键盘挡）。 */
    private fun focusInput() {
        runOnUiThread {
            webView.evaluateJavascript(
                """
                (function(){
                  var els = document.querySelectorAll('input[type=text],input[type=password],input:not([type]),textarea,select');
                  for (var i=0;i<els.length;i++){
                    var el = els[i];
                    if (el.offsetParent !== null) {  // 可见
                      el.focus();
                      if (el.setSelectionRange) el.setSelectionRange(el.value.length, el.value.length);
                      // 滚动到屏幕中部，避免被软键盘挡住（小屏扫码枪）
                      try { el.scrollIntoView({block:'center', inline:'nearest'}); } catch(e){}
                      var t = el;
                      setTimeout(function(){ try{ t.scrollIntoView(true); }catch(e){} }, 100);
                      break;
                    }
                  }
                  return 'ok';
                })();
                """.trimIndent(), null)
        }
    }

    // ---- 硬件返回键：WebView 后退 ----
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    // ---- 快捷键（扫码枪可能有物理按键映射为键码）----
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> if (webView.canGoBack()) { webView.goBack(); true } else super.onKeyDown(keyCode, event)
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }
}