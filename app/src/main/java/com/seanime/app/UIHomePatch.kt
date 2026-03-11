package com.seanime.app

import android.webkit.WebView

object UIHomePatch {

    fun inject(webView: WebView) {
        injectHomeStyles(webView)
        injectCurrentlyWatchingCarousel(webView)
    }

    private fun injectHomeStyles(webView: WebView) {
        val js = """
        (function() {
            if (document.getElementById('__seanime_home_styles')) return;

            var s = document.createElement('style');
            s.id = '__seanime_home_styles';
            s.textContent = `
                /* ── Banner: shrink on mobile so it doesn't eat half the screen ── */
                [data-library-header-container="true"] {
                    height: 14rem !important;
                }
                /* Compensate top padding containers that assume full banner height */
                [data-library-toolbar-top-padding="true"].h-28 {
                    height: 6rem !important;
                }

                /* ── Top navbar: hide desktop nav links, keep the action icons ── */
                [data-top-menu="true"] {
                    display: none !important;
                }
                /* Remove the spacer so action icons sit flush right */
                [data-top-navbar-content-separator="true"] {
                    display: none !important;
                }

                /* ── Currently watching section: allow horizontal overflow ── */
                [data-library-collection-list-item-media-card-lazy-grid][data-list-type="CURRENT"] {
                    overflow: visible !important;
                }
                /* The page wrapper must not clip the carousel */
                [data-page-wrapper="true"] {
                    overflow: visible !important;
                }

                /* ── Continue watching carousel: compact card height ── */
                [data-episode-card="true"] [data-episode-card-image-container="true"] {
                    aspect-ratio: 16/7 !important;
                }

                /* ── Episode card subtitle: single line, smaller ── */
                [data-episode-card-subtitle="true"] {
                    font-size: 0.78rem !important;
                }
                [data-episode-card-subtitle="true"] span.flex-none {
                    font-size: 0.85rem !important;
                }
                [data-episode-card-title="true"] {
                    font-size: 0.92rem !important;
                    width: 100% !important;
                }

                /* ── Library grid: 2-col tighter gap on mobile ── */
                [data-media-card-grid="true"] {
                    gap: 0.65rem !important;
                }

                /* ── Media card: slightly reduce cover aspect so more fit ── */
                .media-entry-card__body {
                    aspect-ratio: 6/8 !important;
                }

                /* ── Card title: tighter ── */
                [data-media-entry-card-title-section="true"] {
                    padding-top: 0.3rem !important;
                }
                [data-media-entry-card-title-section-title="true"] {
                    font-size: 0.78rem !important;
                    line-height: 1.2 !important;
                }
                [data-media-entry-card-title-section-year-season="true"] {
                    font-size: 0.7rem !important;
                }

                /* ── Genre tabs: smaller pills on mobile ── */
                .UI-StaticTabs__trigger {
                    height: 2.2rem !important;
                    padding-left: 0.85rem !important;
                    padding-right: 0.85rem !important;
                    font-size: 0.82rem !important;
                }

                /* ── Section headings: tighter margin ── */
                [data-continue-watching-container="true"] h2,
                [data-library-collection-list-item-header="true"] h2 {
                    font-size: 1.1rem !important;
                }
            `;
            document.head.appendChild(s);
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectCurrentlyWatchingCarousel(webView: WebView) {
        val js = """
        (function() {
            if (window.__seanime_cw_carousel_init) return;
            window.__seanime_cw_carousel_init = true;

            function applyCarousel() {
                var section = document.querySelector('[data-library-collection-list-item-media-card-lazy-grid][data-list-type="CURRENT"]');
                if (!section || section.dataset.carouselized) return;
                section.dataset.carouselized = 'true';

                var grid = section.querySelector('[data-media-card-grid="true"]');
                if (!grid) return;

                grid.style.cssText += [
                    'display:flex',
                    'flex-direction:row',
                    'flex-wrap:nowrap',
                    'overflow-x:auto',
                    'overflow-y:visible',
                    'gap:0.65rem',
                    'padding:0.25rem 1rem 1rem 1rem',
                    'scroll-snap-type:x mandatory',
                    '-webkit-overflow-scrolling:touch',
                    'scrollbar-width:none'
                ].join(';');

                if (!document.getElementById('__seanime_cw_carousel_sb')) {
                    var sb = document.createElement('style');
                    sb.id = '__seanime_cw_carousel_sb';
                    sb.textContent = '[data-library-collection-list-item-media-card-lazy-grid][data-list-type="CURRENT"] [data-media-card-grid="true"]::-webkit-scrollbar{display:none!important;}';
                    document.head.appendChild(sb);
                }

                grid.querySelectorAll('[data-media-entry-card-container="true"]').forEach(function(card) {
                    card.style.cssText += [
                        'flex:0 0 42vw',
                        'max-width:42vw',
                        'scroll-snap-align:start',
                        'width:42vw'
                    ].join(';');
                });
            }

            applyCarousel();

            new MutationObserver(function() {
                var section = document.querySelector('[data-library-collection-list-item-media-card-lazy-grid][data-list-type="CURRENT"]');
                if (!section) return;
                if (!section.dataset.carouselized) {
                    window.__seanime_cw_carousel_init = false;
                    window.__seanime_cw_carousel_init = true;
                    applyCarousel();
                    return;
                }
                var grid = section.querySelector('[data-media-card-grid="true"]');
                if (!grid) return;
                grid.querySelectorAll('[data-media-entry-card-container="true"]').forEach(function(card) {
                    if (!card.style.flexBasis) {
                        card.style.cssText += [
                            'flex:0 0 42vw',
                            'max-width:42vw',
                            'scroll-snap-align:start',
                            'width:42vw'
                        ].join(';');
                    }
                });
            }).observe(document.body, { childList: true, subtree: true });

            var _pushState = history.pushState.bind(history);
            history.pushState = function() {
                _pushState.apply(history, arguments);
                window.__seanime_cw_carousel_init = false;
                setTimeout(function() {
                    window.__seanime_cw_carousel_init = true;
                    applyCarousel();
                }, 400);
            };
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
