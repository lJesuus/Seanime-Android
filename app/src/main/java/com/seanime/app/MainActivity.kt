package com.seanime.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
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
import android.provider.MediaStore
import android.provider.Settings
import android.os.Environment
import android.provider.DocumentsContract
import java.net.URISyntaxException

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var pipManager: PiPManager
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val retryCountMap = mutableMapOf<WebView, Int>()
    private val MAX_RETRIES = 5
    private val REQUEST_CODE_NOTIFICATIONS = 101
    private val REQUEST_CODE_STORAGE = 102
    private val REQUEST_CODE_FOLDER_PICKER = 103
    private val REQUEST_CODE_EXTERNAL_PLAYER = 104

    private val LOCAL_HOST = "127.0.0.1"
    
    private var currentPlayingTitle: String = ""

    inner class ExternalPlayerBridge {
        var lastUrl: String = ""
        @JavascriptInterface
        fun setUrl(url: String) {
            lastUrl = url
        }
    }

    private val externalPlayerBridge = ExternalPlayerBridge()

    private fun savePlaybackPosition(title: String, positionMs: Long) {
        if (title.isEmpty()) return
        val prefs = getSharedPreferences("seanime_playback", Context.MODE_PRIVATE)
        prefs.edit().putLong(title, positionMs).apply()
    }

    private fun getSavedPlaybackPosition(title: String): Long {
        if (title.isEmpty()) return -1L
        val prefs = getSharedPreferences("seanime_playback", Context.MODE_PRIVATE)
        return prefs.getLong(title, -1L)
    }

    private fun clearPlaybackPosition(title: String) {
        if (title.isEmpty()) return
        val prefs = getSharedPreferences("seanime_playback", Context.MODE_PRIVATE)
        prefs.edit().remove(title).apply()
    }

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

    inner class FolderPickerBridge {
        @JavascriptInterface
        fun openFolderPicker() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                startActivityForResult(intent, REQUEST_CODE_FOLDER_PICKER)
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
        
        checkAndRequestStoragePermissions()

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
                waitForServerAndLoadUrl("http://127.0.0.1:43211/entry?id=$animeId")
            }
        }
    }

    private fun waitForServerAndLoadUrl(url: String) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val checkInterval = 200L
        val maxWaitTime = 15000L // 15 seconds
        val startTime = System.currentTimeMillis()

        Thread {
            var serverReady = false
            while (System.currentTimeMillis() - startTime < maxWaitTime) {
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", 43211), 200)
                    socket.close()
                    serverReady = true
                    break
                } catch (e: Exception) {
                    Thread.sleep(checkInterval)
                }
            }

            handler.post {
                if (!serverReady) {
                    Toast.makeText(this@MainActivity, "Timeout waiting for server", Toast.LENGTH_LONG).show()
                }
                webView.loadUrl(url)
            }
        }.start()
    }

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), REQUEST_CODE_STORAGE
                )
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
        webView.addJavascriptInterface(FolderPickerBridge(), "FolderPickerBridge")

        var hasError = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                hasError = false
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false

                if (uri.scheme == "intent") {
                    val uriString = uri.toString()

                    return try {
                        val intent = Intent.parseUri(uriString, Intent.URI_INTENT_SCHEME)
                        
                        // Force ACTION_VIEW for videos to ensure standard players can handle it
                        if (intent.type?.startsWith("video/") == true || uriString.contains("video/")) {
                            intent.action = Intent.ACTION_VIEW
                            
                            val title = intent.getStringExtra("title") ?: "Seanime Stream"
                            currentPlayingTitle = title
                            
                            val savedPos = getSavedPlaybackPosition(title)
                            if (savedPos > 0) {
                                // VLC uses extra_position (long), MX Player uses position (int)
                                intent.putExtra("position", savedPos.toInt())
                                intent.putExtra("extra_position", savedPos)
                            }
                            
                            android.util.Log.d("SeanimeExternalPlayer", "Firing intent: ${intent.dataString} with title '$title' at pos $savedPos")
                            startActivityForResult(intent, REQUEST_CODE_EXTERNAL_PLAYER)
                            return true
                        }

                        android.util.Log.d("SeanimeExternalPlayer", "Firing intent: ${intent.dataString}")
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
                if (req?.isForMainFrame == true) {
                    hasError = true
                    retry(view)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (view != null) retryCountMap[view] = 0

                if (hasError) return
                
                // Do not inject patches into Chrome error pages or external links
                if (url == null || !url.startsWith("http://127.0.0.1:43211")) {
                    return
                }

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
            waitForServerAndLoadUrl("http://127.0.0.1:43211")
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_EXTERNAL_PLAYER) {
            val pos = data?.getLongExtra("extra_position", -1L) 
                   ?: data?.getIntExtra("position", -1)?.toLong() ?: -1L
            val duration = data?.getLongExtra("extra_duration", -1L) ?: -1L
            
            if (pos > 0 && currentPlayingTitle.isNotEmpty()) {
                // If user watched more than 90%, consider it finished and don't resume next time
                if (duration > 0 && pos > duration * 0.90) {
                    clearPlaybackPosition(currentPlayingTitle)
                } else {
                    savePlaybackPosition(currentPlayingTitle, pos)
                }
            }
            currentPlayingTitle = ""
        }
        
        if (requestCode == REQUEST_CODE_FOLDER_PICKER && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            val path = resolveUriToPath(uri)
            if (path != null) {
                webView.evaluateJavascript("window.onFolderSelected?.('$path')", null)
            } else {
                Toast.makeText(this, "Could not resolve path", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun resolveUriToPath(uri: Uri): String? {
        // Handle tree URI (ACTION_OPEN_DOCUMENT_TREE)
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val parts = documentId.split(":")
        if (parts.size >= 2) {
            val type = parts[0]
            val relativePath = parts[1]
            if ("primary".equals(type, ignoreCase = true)) {
                return Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
            } else {
                // Handle SD card if needed, but primary is most common
                return "/storage/$type/$relativePath"
            }
        }
        return null
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
