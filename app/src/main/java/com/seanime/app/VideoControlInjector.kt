package com.seanime.app

import android.webkit.WebView

object VideoControlInjector {

    fun inject(view: WebView) {
        // language=JavaScript
        val js = """
        (function() {
            if (window.__seanimeControlPatchActive) return;
            window.__seanimeControlPatchActive = true;

            var HIDE_DELAY_MS = 3000;
            var hideTimer = null;
            var patched = false;
            var observersAttached = false;

            function isEntryRoute() {
                var url = window.location.pathname + window.location.search;
                return url.indexOf('/entry') !== -1 && url.indexOf('id=') !== -1;
            }

            function getTopBar() {
                return document.querySelector('[data-vc-element="mobile-control-bar-top-section"]');
            }
            function getBottomBar() {
                return document.querySelector('[data-vc-element="mobile-control-bar-bottom-section"]');
            }

            function isHideTransform(transform) {
                if (!transform || transform === 'none' || transform === '') return false;
                var match = transform.match(/translateY\(([^)]+)\)/);
                if (!match) return false;
                return parseFloat(match[1]) !== 0;
            }

            function showBars() {
                var top = getTopBar();
                var bot = getBottomBar();
                if (!top || !bot) return;
                top.__seanimeOurControl = true;
                bot.__seanimeOurControl = true;
                top.style.transform = 'translateY(0px)';
                bot.style.transform = 'translateY(0px)';
            }

            function hideBars() {
                var top = getTopBar();
                var bot = getBottomBar();
                if (!top || !bot) return;
                top.__seanimeOurControl = true;
                bot.__seanimeOurControl = true;
                top.style.transform = 'translateY(-100%)';
                bot.style.transform = 'translateY(100%)';
            }

            function scheduleHide() {
                clearTimeout(hideTimer);
                hideTimer = setTimeout(function() {
                    hideBars();
                }, HIDE_DELAY_MS);
            }

            function patchPlayer() {
                if (!isEntryRoute()) return;
                if (patched) return;

                var topBar = getTopBar();
                var bottomBar = getBottomBar();
                if (!topBar || !bottomBar) return;

                patched = true;

                if (!observersAttached) {
                    observersAttached = true;

                    function watchBar(el, hideValue) {
                        new MutationObserver(function(mutations) {
                            mutations.forEach(function(m) {
                                if (m.attributeName !== 'style') return;
                                if (!isEntryRoute()) return;
                                // If we set this ourselves, allow it and clear the flag
                                if (el.__seanimeOurControl) {
                                    el.__seanimeOurControl = false;
                                    return;
                                }
                                // Otherwise the player tried to change visibility — revert it
                                // to whatever our current desired state is
                                el.__seanimeOurControl = true;
                                el.style.transform = el.__seanimeHidden ? hideValue : 'translateY(0px)';
                            });
                        }).observe(el, { attributes: true, attributeFilter: ['style'] });
                    }

                    watchBar(topBar, 'translateY(-100%)');
                    watchBar(bottomBar, 'translateY(100%)');
                }

                // Reflect hidden state on the element so the observer knows what to restore
                Object.defineProperty(topBar, '__seanimeHidden', { value: false, writable: true, configurable: true });
                Object.defineProperty(bottomBar, '__seanimeHidden', { value: false, writable: true, configurable: true });

                // Override hideBars/showBars to also update __seanimeHidden
                var _hideBars = hideBars;
                hideBars = function() {
                    var top = getTopBar();
                    var bot = getBottomBar();
                    if (top) top.__seanimeHidden = true;
                    if (bot) bot.__seanimeHidden = true;
                    _hideBars();
                };
                var _showBars = showBars;
                showBars = function() {
                    var top = getTopBar();
                    var bot = getBottomBar();
                    if (top) top.__seanimeHidden = false;
                    if (bot) bot.__seanimeHidden = false;
                    _showBars();
                };

                var container = document.querySelector('[data-vc-element="container"]');
                if (!container || container.__seanimeClickPatched) return;
                container.__seanimeClickPatched = true;

                container.addEventListener('click', function(e) {
                    if (!isEntryRoute()) return;

                    var top = getTopBar();
                    var bot = getBottomBar();
                    if (!top || !bot) return;

                    var currentlyVisible = !isHideTransform(top.style.transform);

                    if (currentlyVisible) {
                        scheduleHide();
                    } else {
                        setTimeout(function() {
                            showBars();
                            scheduleHide();
                        }, 100);
                    }
                }, false);

                // Start the initial hide timer
                scheduleHide();
            }

            var _pushState = history.pushState.bind(history);
            history.pushState = function() {
                _pushState.apply(history, arguments);
                patched = false;
                observersAttached = false;
                setTimeout(patchPlayer, 500);
            };
            var _replaceState = history.replaceState.bind(history);
            history.replaceState = function() {
                _replaceState.apply(history, arguments);
                patched = false;
                observersAttached = false;
                setTimeout(patchPlayer, 500);
            };
            window.addEventListener('popstate', function() {
                patched = false;
                observersAttached = false;
                setTimeout(patchPlayer, 500);
            });

            new MutationObserver(function() {
                if (!patched) patchPlayer();
            }).observe(document.body, { childList: true, subtree: true });

            patchPlayer();
        })();
        """.trimIndent()

        view.evaluateJavascript(js, null)
    }
}
