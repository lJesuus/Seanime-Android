package com.seanime.app

import android.webkit.WebView

object UIExternalPlayerPatch {
    fun inject(webView: WebView) {
        val js = """
        (function() {
            if (window.__seanime_vlc_patch_init) return;
            window.__seanime_vlc_patch_init = true;
            
            let lastStreamUrl = "";
            const origInfo = console.info;
            console.info = function(...args) {
                try {
                    let msg = args.map(String).join(' ');
                    if (msg.indexOf('[EXTERNAL PLAYER LINK]') !== -1 && msg.indexOf('Sending URL to external player') !== -1) {
                        let parts = msg.split('external player');
                        if (parts.length > 1) {
                            lastStreamUrl = parts[1].trim();
                        }
                    }
                } catch(e) {}
                return origInfo.apply(this, args);
            };
            
            // Intercept window.open to fix formatting before Android sees it
            const origOpen = window.open;
            window.open = function(url, target, features) {
                if (typeof url === 'string') {
                    if (url.indexOf('%7Burl%7D') !== -1 || url.indexOf('{url}') !== -1) {
                        if (lastStreamUrl) {
                            let cleanUrl = lastStreamUrl.replace('http://', '').replace('https://', '');
                            url = url.replace('%7Burl%7D', cleanUrl).replace('{url}', cleanUrl);
                        }
                    }
                    if (url.startsWith('intent://http://') || url.startsWith('intent://https://')) {
                        url = url.replace('intent://http://', 'intent://').replace('intent://https://', 'intent://');
                    }
                }
                return origOpen(url, target, features);
            };
        })();
        """.trimIndent()
        
        webView.evaluateJavascript(js, null)
    }
}