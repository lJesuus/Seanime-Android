package com.seanime.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import java.net.URISyntaxException

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var pipManager: PiPManager
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val retryCountMap = mutableMapOf<WebView, Int>()
    private val MAX_RETRIES = 5
    private val REQUEST_CODE_NOTIFICATIONS = 101

    private val LOCAL_HOST = "127.0.0.1"

    inner class OrientationBridge {
        @JavascriptInterface
        fun setLandscape(landscape: Boolean) {
            runOnUiThread {
                requestedOrientation = if (landscape) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), REQUEST_CODE_NOTIFICATIONS)
        }

        setupWebView()
        startSeanimeService()
        setSystemBarsHidden(true)

        // --- ADDED: Handle deep link if app was closed ---
        handleWidgetIntent(intent)
    }

    // --- ADDED: Handle deep link if app was already in background ---
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Essential for getIntent() to return the latest data
        handleWidgetIntent(intent)
    }

    // --- ADDED: The Navigation Logic ---
    private fun handleWidgetIntent(intent: Intent?) {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == "seanime" && data.host == "entry") {
            val animeId = data.getQueryParameter("id")
            if (animeId != null) {
                // Navigate the WebView to the specific entry page
                webView.postDelayed({
                    webView.loadUrl("http://127.0.0.1:43211/entry?id=$animeId")
                }, 500) // Small delay to ensure WebView is ready
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AppUpdater.onRequestPermissionsResult(requestCode, grantResults)
        if (requestCode == REQUEST_CODE_NOTIFICATIONS &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            sendBroadcast(Intent(SeanimeService.ACTION_NOTIFICATION_PERMISSION_GRANTED))
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Performance.onTrimMemory(webView, level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Performance.onLowMemory(webView)
    }

    private fun startSeanimeService() {
        val intent = Intent(this, SeanimeService::class.java)
        startForegroundService(intent)
    }

    private fun setupWebView() {
        webView = WebView(this)
        setContentView(webView)

        Performance.init(this, webView)

        webView.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int
            val left: Int
            val right: Int

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemInsets = insets.getInsets(WindowInsets.Type.systemBars())
                top = systemInsets.top
                bottom = systemInsets.bottom
                left = systemInsets.left
                right = systemInsets.right
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
                @Suppress("DEPRECATION")
                left = insets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                right = insets.systemWindowInsetRight
            }

            val js = "window.__seanimeInsets = { top: $top, bottom: $bottom, left: $left, right: $right };"
            view.post { (view as? WebView)?.evaluateJavascript(js, null) }
            insets
        }

        pipManager = PiPManager(this, webView)
        pipManager.registerBridge()

        AppUpdater.init(this, webView)

        webView.addJavascriptInterface(OrientationBridge(), "OrientationBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false

                if (uri.scheme == "intent") {
                    return try {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        startActivity(intent)
                        true
                    } catch (e: URISyntaxException) {
                        e.printStackTrace()
                        true
                    } catch (e: ActivityNotFoundException) {
                        val packageName = intent.`package`
                        if (packageName != null) {
                            val marketIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$packageName")
                            )
                            startActivity(marketIntent)
                        } else {
                            Toast.makeText(this@MainActivity, "No app found to handle this link", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                }

                val host = uri.host ?: return false
                if (host == LOCAL_HOST) return false
                
                // --- ADDED: Ensure our custom scheme doesn't open a popup sheet ---
                if (uri.scheme == "seanime") return false

                PopupWebViewSheet.show(this@MainActivity, uri.toString())
                return true
            }

            override fun onReceivedError(view: WebView?, req: WebResourceRequest?, err: WebResourceError?) {
                if (req?.isForMainFrame == true) retry(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null) retryCountMap[view] = 0

                Performance.injectRuntimeOptimizations(webView)
                pipManager.injectHijacker()
                DualModeManager.inject(webView)
                VideoControlInjector.inject(webView)
                UIPatches.inject(webView)
                UIBottomNav.inject(webView)
                AppUpdater.inject(webView)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    onHideCustomView()
                    return
                }
                customView = view
                customViewCallback = callback
                val decor = window.decorView as FrameLayout
                decor.addView(customView, FrameLayout.LayoutParams(-1, -1))
                webView.visibility = View.GONE
                setSystemBarsHidden(true)
            }

            override fun onHideCustomView() {
                val decor = window.decorView as FrameLayout
                decor.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                webView.visibility = View.VISIBLE
                setSystemBarsHidden(true)
            }
        }

        // Only load the home page if there isn't a deep link intent pending
        if (intent?.data == null) {
            webView.postDelayed({
                webView.loadUrl("http://127.0.0.1:43211")
            }, 1000)
        }
    }

    private fun retry(view: WebView?) {
        view ?: return
        val count = retryCountMap.getOrDefault(view, 0)
        if (count >= MAX_RETRIES) return
        retryCountMap[view] = count + 1
        val delayMs = (count + 1) * 1000L
        view.postDelayed({ view.reload() }, delayMs)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipManager.onPiPModeChanged(isInPictureInPictureMode)

        if (!isInPictureInPictureMode) {
            webView.postDelayed({
                webView.setInitialScale(100)
                webView.postDelayed({
                    webView.evaluateJavascript(
                        "(function(){document.documentElement.style.zoom='1';document.body.style.zoom='1';})();",
                        null
                    )
                }, 50)
                setSystemBarsHidden(true)
            }, 100)
        }
    }

    private fun setSystemBarsHidden(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                if (hide) {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    it.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = if (hide) {
                (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            } else {
                View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    override fun onBackPressed() {
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
        } else if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
