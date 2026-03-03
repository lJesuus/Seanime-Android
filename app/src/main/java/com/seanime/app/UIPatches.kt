package com.seanime.app

import android.webkit.WebView

object UIPatches {

    fun inject(webView: WebView) {
        injectSettingsFix(webView)
        injectEntryPagePatches(webView)
        injectSettingsMenuFix(webView)
        injectMoreInfoPanel(webView)
        UIHomePatch.inject(webView)
    }

    private fun injectSettingsFix(webView: WebView) {
        val js = """
            (function() {
                var style = document.getElementById('__seanime_settings_fix');
                if (style) return;
                style = document.createElement('style');
                style.id = '__seanime_settings_fix';
                style.textContent = `
                    [data-open-issue-recorder-button="true"] {
                        transform: none !important;
                        transition: background-color 0.2s ease, box-shadow 0.2s ease !important;
                    }
                    [data-open-issue-recorder-button="true"]:hover {
                        transform: none !important;
                    }
                    .UI-Card__content .pb-3.flex.gap-2 {
                        flex-wrap: wrap !important;
                        overflow: hidden !important;
                    }
                    .UI-Card__content .pb-3.flex.gap-2 button {
                        flex-shrink: 1 !important;
                        min-width: 0 !important;
                        max-width: 100% !important;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectSettingsMenuFix(webView: WebView) {
        val js = """
            (function() {
                function isSettingsPage() {
                    return /\/settings/.test(window.location.href);
                }

                function cleanupDrawer() {
                    ['__seanime_drawer', '__seanime_backdrop', '__seanime_drawer_tab'].forEach(function(id) {
                        var el = document.getElementById(id);
                        if (el) el.remove();
                    });
                    var origNav = document.querySelector('.overflow-x-none.overflow-y-hidden');
                    if (origNav) {
                        origNav.style.removeProperty('position');
                        origNav.style.removeProperty('top');
                        origNav.style.removeProperty('left');
                        origNav.style.removeProperty('visibility');
                        origNav.style.removeProperty('pointer-events');
                    }
                    var donateHide = document.getElementById('__seanime_donate_hide');
                    if (donateHide) donateHide.remove();
                    document.querySelectorAll('[data-seanime-donate="true"]').forEach(function(btn) {
                        btn.removeAttribute('data-seanime-donate');
                        btn.style.removeProperty('display');
                    });
                }

                function hideDonateButton() {
                    if (!document.getElementById('__seanime_donate_hide')) {
                        var style = document.createElement('style');
                        style.id = '__seanime_donate_hide';
                        style.textContent = '[data-seanime-donate="true"] { display: none !important; }';
                        document.head.appendChild(style);
                    }

                    var ourDrawer = document.getElementById('__seanime_drawer');
                    document.querySelectorAll('button.UI-Button_root, button').forEach(function(btn) {
                        if (ourDrawer && ourDrawer.contains(btn)) return;
                        if (btn.dataset.seanimeOwned === 'true') return;
                        var spans = btn.querySelectorAll('span');
                        var isDonate = false;
                        spans.forEach(function(s) {
                            if (s.textContent.trim() === 'Donate') isDonate = true;
                        });
                        if (!isDonate && btn.textContent.trim() === 'Donate') isDonate = true;
                        if (isDonate) {
                            btn.setAttribute('data-seanime-donate', 'true');
                            btn.style.setProperty('display', 'none', 'important');
                        }
                    });
                }

                function buildDrawer() {
                    if (!isSettingsPage()) return;
                    if (document.getElementById('__seanime_drawer_tab')) return;

                    var triggers = document.querySelectorAll('.UI-Tabs__trigger');
                    if (triggers.length === 0) return;

                    var origNav = document.querySelector('.overflow-x-none.overflow-y-hidden');
                    if (!origNav) return;

                    origNav.style.setProperty('position', 'fixed', 'important');
                    origNav.style.setProperty('top', '-9999px', 'important');
                    origNav.style.setProperty('left', '-9999px', 'important');
                    origNav.style.setProperty('pointer-events', 'auto', 'important');
                    origNav.style.setProperty('visibility', 'hidden', 'important');

                    // Backdrop
                    var backdrop = document.createElement('div');
                    backdrop.id = '__seanime_backdrop';
                    backdrop.style.cssText = [
                        'position:fixed',
                        'inset:0',
                        'z-index:998',
                        'background:rgba(0,0,0,0.6)',
                        'opacity:0',
                        'pointer-events:none',
                        'transition:opacity 0.25s ease'
                    ].join(';');
                    document.body.appendChild(backdrop);

                    // Drawer
                    var drawer = document.createElement('div');
                    drawer.id = '__seanime_drawer';
                    drawer.style.cssText = [
                        'position:fixed',
                        'top:0',
                        'left:0',
                        'height:100%',
                        'width:75vw',
                        'max-width:300px',
                        'z-index:999',
                        'transform:translateX(-100%)',
                        'transition:transform 0.28s cubic-bezier(0.4,0,0.2,1)',
                        'background:rgba(10,10,15,0.55)',
                        'backdrop-filter:blur(24px)',
                        '-webkit-backdrop-filter:blur(24px)',
                        'border-right:1px solid rgba(255,255,255,0.07)',
                        'display:flex',
                        'flex-direction:column',
                        'overflow-y:auto',
                        'overflow-x:hidden'
                    ].join(';');

                    // Header
                    var header = document.createElement('div');
                    header.style.cssText = 'padding:1.5rem 1.25rem 1.1rem;border-bottom:1px solid rgba(255,255,255,0.07);flex-shrink:0;';
                    header.innerHTML = '<p style="margin:0;font-size:1.1rem;font-weight:700;color:white;">Settings</p>';
                    drawer.appendChild(header);

                    // Menu items
                    var groups = origNav.querySelectorAll('.UI-Card__root');
                    groups.forEach(function(group, gi) {
                        var btns = group.querySelectorAll('.UI-Tabs__trigger');
                        if (btns.length === 0) return;

                        if (gi > 0) {
                            var sep = document.createElement('div');
                            sep.style.cssText = 'height:1px;background:rgba(255,255,255,0.06);margin:0.15rem 0;flex-shrink:0;';
                            drawer.appendChild(sep);
                        }

                        btns.forEach(function(btn) {
                            var svgEl = btn.querySelector('svg');
                            var label = btn.textContent.trim();
                            var isActive = btn.getAttribute('data-state') === 'active';

                            var item = document.createElement('button');
                            item.setAttribute('data-drawer-for', btn.id);
                            item.dataset.seanimeOwned = 'true';
                            item.style.cssText = [
                                'display:flex',
                                'align-items:center',
                                'gap:0.85rem',
                                'width:100%',
                                'padding:0.8rem 1.25rem',
                                'background:' + (isActive ? 'rgba(255,255,255,0.06)' : 'transparent'),
                                'border:none',
                                'border-left:3px solid ' + (isActive ? 'var(--brand,#818cf8)' : 'transparent'),
                                'color:' + (isActive ? 'white' : 'rgba(255,255,255,0.45)'),
                                'font-size:0.92rem',
                                'font-weight:' + (isActive ? '600' : '400'),
                                'text-align:left',
                                'cursor:pointer',
                                'flex-shrink:0',
                                'box-sizing:border-box'
                            ].join(';');

                            var iconSpan = document.createElement('span');
                            iconSpan.style.cssText = 'display:flex;align-items:center;justify-content:center;flex-shrink:0;width:20px;height:20px;opacity:' + (isActive ? '1' : '0.5') + ';';
                            if (svgEl) {
                                var clonedSvg = svgEl.cloneNode(true);
                                clonedSvg.setAttribute('width', '20');
                                clonedSvg.setAttribute('height', '20');
                                clonedSvg.style.cssText = 'width:20px;height:20px;flex-shrink:0;';
                                iconSpan.appendChild(clonedSvg);
                            }

                            var labelSpan = document.createElement('span');
                            labelSpan.textContent = label;
                            labelSpan.style.cssText = 'overflow:hidden;text-overflow:ellipsis;white-space:nowrap;';

                            item.appendChild(iconSpan);
                            item.appendChild(labelSpan);

                            item.addEventListener('click', function() {
                                origNav.style.setProperty('visibility', 'visible', 'important');
                                var realBtn = document.getElementById(item.getAttribute('data-drawer-for'));
                                if (realBtn) {
                                    realBtn.dispatchEvent(new PointerEvent('pointerdown', {bubbles:true, cancelable:true}));
                                    realBtn.dispatchEvent(new MouseEvent('mousedown', {bubbles:true, cancelable:true}));
                                    realBtn.dispatchEvent(new PointerEvent('pointerup', {bubbles:true, cancelable:true}));
                                    realBtn.dispatchEvent(new MouseEvent('mouseup', {bubbles:true, cancelable:true}));
                                    realBtn.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}));
                                }
                                setTimeout(function() {
                                    origNav.style.setProperty('visibility', 'hidden', 'important');
                                }, 50);

                                drawer.querySelectorAll('[data-drawer-for]').forEach(function(i) {
                                    i.style.background = 'transparent';
                                    i.style.borderLeft = '3px solid transparent';
                                    i.style.color = 'rgba(255,255,255,0.45)';
                                    i.style.fontWeight = '400';
                                    var ico = i.querySelector('span:first-child');
                                    if (ico) ico.style.opacity = '0.5';
                                });
                                item.style.background = 'rgba(255,255,255,0.06)';
                                item.style.borderLeft = '3px solid var(--brand,#818cf8)';
                                item.style.color = 'white';
                                item.style.fontWeight = '600';
                                var ico = item.querySelector('span:first-child');
                                if (ico) ico.style.opacity = '1';
                            });

                            drawer.appendChild(item);
                        });
                    });

                    // Donate button
                    var donateWrapper = document.createElement('div');
                    donateWrapper.style.cssText = 'margin-top:1rem;padding:1.25rem;border-top:1px solid rgba(255,255,255,0.07);flex-shrink:0;';
                    var donateBtn = document.createElement('button');
                    donateBtn.dataset.seanimeOwned = 'true';
                    donateBtn.style.cssText = [
                        'display:flex',
                        'align-items:center',
                        'gap:0.75rem',
                        'color:rgba(255,255,255,0.45)',
                        'font-size:0.92rem',
                        'background:transparent',
                        'border:none',
                        'cursor:pointer',
                        'padding:0.5rem 0',
                        'width:100%'
                    ].join(';');
                    donateBtn.innerHTML = '<svg style="width:20px;height:20px;flex-shrink:0;" stroke="currentColor" fill="currentColor" stroke-width="0" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M4 21h9.62a3.995 3.995 0 0 0 3.037-1.397l5.102-5.952a1 1 0 0 0-.442-1.6l-1.968-.656a3.043 3.043 0 0 0-2.823.503l-3.185 2.547-.617-1.235A3.98 3.98 0 0 0 9.146 11H4c-1.103 0-2 .897-2 2v6c0 1.103.897 2 2 2zm0-8h5.146c.763 0 1.448.423 1.789 1.105l.447.895H7v2h6.014a.996.996 0 0 0 .442-.11l.003-.001.004-.002h.003l.002-.001h.004l.001-.001c.009.003.003-.001.003-.001.01 0 .002-.001.002-.001h.001l.002-.001.003-.001.002-.001.002-.001.003-.001.002-.001c.003 0 .001-.001.002-.001l.003-.002.002-.001.002-.001.003-.001.002-.001h.001l.002-.001h.001l.002-.001.002-.001c.009-.001.003-.001.003-.001l.002-.001a.915.915 0 0 0 .11-.078l4.146-3.317c.262-.208.623-.273.94-.167l.557.186-4.133 4.823a2.029 2.029 0 0 1-1.52.688H4v-6zM16 2h-.017c-.163.002-1.006.039-1.983.705-.951-.648-1.774-.7-1.968-.704L12.002 2h-.004c-.801 0-1.555.313-2.119.878C9.313 3.445 9 4.198 9 5s.313 1.555.861 2.104l3.414 3.586a1.006 1.006 0 0 0 1.45-.001l3.396-3.568C18.688 6.555 19 5.802 19 5s-.313-1.555-.878-2.121A2.978 2.978 0 0 0 16.002 2H16zm1 3c0 .267-.104.518-.311.725L14 8.55l-2.707-2.843C11.104 5.518 11 5.267 11 5s.104-.518.294-.708A.977.977 0 0 1 11.979 4c.025.001.502.032 1.067.485.081.065.163.139.247.222l.707.707.707-.707c.084-.083.166-.157.247-.222.529-.425.976-.478 1.052-.484a.987.987 0 0 1 .701.292c.189.189.293.44.293.707z"></path></svg><span>Donate</span>';
                    donateBtn.addEventListener('click', function() {
                        if (window.DonateBridge) {
                            window.DonateBridge.openDonate();
                        }
                    });
                    donateWrapper.appendChild(donateBtn);
                    drawer.appendChild(donateWrapper);

                    document.body.appendChild(drawer);

                    // Hide original donate button only after our drawer (with seanimeOwned flags) is in the DOM
                    hideDonateButton();

                    // Toggle tab
                    var drawerW = Math.min(window.innerWidth * 0.75, 300);
                    var isOpen = false;

                    var tab = document.createElement('button');
                    tab.id = '__seanime_drawer_tab';
                    tab.dataset.seanimeOwned = 'true';
                    tab.style.cssText = [
                        'position:fixed',
                        'left:0',
                        'top:50%',
                        'transform:translateY(-50%)',
                        'z-index:1000',
                        'width:20px',
                        'height:56px',
                        'background:rgba(255,255,255,0.07)',
                        'backdrop-filter:blur(12px)',
                        '-webkit-backdrop-filter:blur(12px)',
                        'border:1px solid rgba(255,255,255,0.10)',
                        'border-left:none',
                        'border-radius:0 8px 8px 0',
                        'color:rgba(255,255,255,0.6)',
                        'display:flex',
                        'align-items:center',
                        'justify-content:center',
                        'cursor:pointer',
                        'padding:0',
                        'transition:left 0.28s cubic-bezier(0.4,0,0.2,1)',
                        'box-sizing:border-box'
                    ].join(';');

                    function setArrow(pointRight) {
                        tab.innerHTML = pointRight
                            ? '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>'
                            : '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"></polyline></svg>';
                    }
                    setArrow(true);

                    function openDrawer() {
                        isOpen = true;
                        drawer.style.transform = 'translateX(0)';
                        backdrop.style.opacity = '1';
                        backdrop.style.pointerEvents = 'auto';
                        tab.style.left = drawerW + 'px';
                        setArrow(false);
                    }

                    function closeDrawer() {
                        isOpen = false;
                        drawer.style.transform = 'translateX(-100%)';
                        backdrop.style.opacity = '0';
                        backdrop.style.pointerEvents = 'none';
                        tab.style.left = '0';
                        setArrow(true);
                    }

                    tab.addEventListener('click', function() {
                        if (isOpen) closeDrawer(); else openDrawer();
                    });
                    backdrop.addEventListener('click', closeDrawer);

                    document.body.appendChild(tab);
                }

                buildDrawer();

                var observer = new MutationObserver(function(mutations) {
                    var added = mutations.some(function(m) { return m.addedNodes.length > 0; });
                    if (added) {
                        buildDrawer();
                        if (isSettingsPage()) hideDonateButton();
                    }
                });
                observer.observe(document.body, { childList: true, subtree: true });

                var lastHref = window.location.href;
                setInterval(function() {
                    if (window.location.href !== lastHref) {
                        lastHref = window.location.href;
                        cleanupDrawer();
                        setTimeout(buildDrawer, 400);
                        setTimeout(buildDrawer, 900);
                    }
                }, 300);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectEntryPagePatches(webView: WebView) {
        val js = """
            (function() {
                if (window.__seanime_entry_observer) return;
                window.__seanime_entry_observer = true;

                function isEntryPage() {
                    return /\/entry\?id=/.test(window.location.href);
                }

                function injectCharacterStyles() {
                    if (document.getElementById('__seanime_characters_fix')) return;
                    var s = document.createElement('style');
                    s.id = '__seanime_characters_fix';
                    s.textContent = `
                        [data-media-entry-characters-section-grid="true"] {
                            grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
                        }
                        [data-media-entry-characters-section-grid-item-container="true"] {
                            gap: 0.5rem !important;
                            padding-top: 0.5rem !important;
                            padding-bottom: 0.5rem !important;
                            padding-right: 0.5rem !important;
                        }
                        [data-media-entry-characters-section-grid-item-image-container="true"] {
                            width: 3rem !important;
                            height: 3rem !important;
                            min-width: 3rem !important;
                            min-height: 3rem !important;
                        }
                        [data-media-entry-characters-section-grid-item-content="true"] p.text-lg {
                            font-size: 0.8rem !important;
                            line-height: 1.1rem !important;
                        }
                        [data-media-entry-characters-section-grid-item-content="true"] p.text-sm {
                            font-size: 0.7rem !important;
                        }
                        [data-media-entry-characters-section-grid-item-content="true"] p.text-xs {
                            font-size: 0.65rem !important;
                        }
                        [data-media-card-grid="true"][data-carouselized="true"] {
                            display: flex !important;
                            flex-direction: row !important;
                            flex-wrap: nowrap !important;
                            overflow-x: auto !important;
                            overflow-y: visible !important;
                            gap: 0.75rem !important;
                            padding: 0.5rem 0.25rem 0.75rem 0.25rem !important;
                            scroll-behavior: smooth !important;
                            -webkit-overflow-scrolling: touch !important;
                            scrollbar-width: none !important;
                        }
                        [data-media-card-grid="true"][data-carouselized="true"]::-webkit-scrollbar {
                            display: none !important;
                        }
                        [data-media-card-grid="true"][data-carouselized="true"] .col-span-1 {
                            flex: 0 0 auto !important;
                            width: 180px !important;
                        }
                    `;
                    document.head.appendChild(s);
                }

                function carouselizeGrids() {
                    if (!isEntryPage()) return;
                    var grids = document.querySelectorAll('[data-media-card-grid="true"]:not([data-carouselized])');
                    grids.forEach(function(grid) {
                        grid.setAttribute('data-carouselized', 'true');
                    });
                }

                function applyAll() {
                    setTimeout(function() {
                        injectCharacterStyles();
                        if (isEntryPage()) carouselizeGrids();
                    }, 1000);
                }

                applyAll();

                var observer = new MutationObserver(function(mutations) {
                    var relevant = mutations.some(function(m) { return m.addedNodes.length > 0; });
                    if (relevant) applyAll();
                });
                observer.observe(document.body, { childList: true, subtree: true });

                var lastHref = window.location.href;
                setInterval(function() {
                    if (window.location.href !== lastHref) {
                        lastHref = window.location.href;
                        setTimeout(applyAll, 300);
                        setTimeout(applyAll, 800);
                        setTimeout(applyAll, 1500);
                    }
                }, 300);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectMoreInfoPanel(webView: WebView) {
        val js = """
            (function() {
                if (window.__seanime_moreinfo_init) return;
                window.__seanime_moreinfo_init = true;

                function isEntryPage() {
                    return /\/entry\?id=/.test(window.location.href);
                }

                function injectMoreInfoStyles() {
                    if (document.getElementById('__seanime_moreinfo_styles')) return;
                    var s = document.createElement('style');
                    s.id = '__seanime_moreinfo_styles';
                    s.textContent = `
                        [data-anime-meta-section-rankings-container="true"],
                        [data-media-entry-genres-list="true"],
                        [data-media-entry-audience-score="true"],
                        [data-anime-entry-studio-badge="true"],
                        [data-media-page-header-details-description-trigger="true"] {
                            display: none !important;
                        }

                        #__seanime_moreinfo_btn {
                            display: inline-flex;
                            align-items: center;
                            gap: 0.35rem;
                            padding: 0.3rem 0;
                            font-size: 0.78rem;
                            font-weight: 600;
                            letter-spacing: 0.04em;
                            color: rgba(255,255,255,0.45);
                            background: transparent;
                            border: none;
                            cursor: pointer;
                            transition: color 0.18s;
                            user-select: none;
                            -webkit-user-select: none;
                            flex-shrink: 0;
                        }
                        #__seanime_moreinfo_btn:active {
                            color: rgba(255,255,255,0.7);
                        }
                        #__seanime_moreinfo_btn svg {
                            transition: transform 0.22s cubic-bezier(0.4,0,0.2,1);
                            flex-shrink: 0;
                        }
                        #__seanime_moreinfo_btn[data-open="true"] {
                            color: rgba(255,255,255,0.75);
                        }
                        #__seanime_moreinfo_btn[data-open="true"] svg {
                            transform: rotate(180deg);
                        }

                        #__seanime_moreinfo_panel {
                            overflow: hidden;
                            max-height: 0;
                            opacity: 0;
                            transition: max-height 0.32s cubic-bezier(0.4,0,0.2,1), opacity 0.22s ease;
                            display: flex;
                            flex-direction: column;
                            gap: 0.75rem;
                            padding: 0 0.1rem;
                            align-items: center;
                            width: 100%;
                        }
                        #__seanime_moreinfo_panel[data-open="true"] {
                            max-height: 600px;
                            opacity: 1;
                        }

                        #__seanime_moreinfo_panel .smi-description {
                            font-size: 0.82rem;
                            line-height: 1.55;
                            color: rgba(255,255,255,0.5);
                            text-align: center;
                            width: 100%;
                            max-height: 8.5rem;
                            overflow: hidden;
                            transition: max-height 0.35s cubic-bezier(0.4,0,0.2,1), color 0.2s ease;
                            cursor: pointer;
                        }
                        #__seanime_moreinfo_panel .smi-description[data-expanded="true"] {
                            max-height: 400px;
                            color: rgba(255,255,255,0.65);
                        }

                        #__seanime_moreinfo_panel .smi-row {
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            flex-wrap: wrap;
                            gap: 0.5rem;
                            width: 100%;
                        }

                        #__seanime_moreinfo_panel .smi-rankings {
                            display: flex;
                            flex-wrap: wrap;
                            justify-content: center;
                            gap: 0.4rem;
                            width: 100%;
                        }

                        #__seanime_moreinfo_panel .smi-genres {
                            display: flex;
                            flex-wrap: wrap;
                            justify-content: center;
                            gap: 0.4rem;
                            align-items: center;
                            width: 100%;
                        }

                        #__seanime_moreinfo_panel .smi-pill {
                            display: inline-flex;
                            align-items: center;
                            gap: 0.35rem;
                            padding: 0.2rem 0.65rem;
                            border-radius: 999px;
                            font-size: 0.75rem;
                            font-weight: 600;
                            letter-spacing: 0.02em;
                            border: 1px solid rgba(255,255,255,0.12);
                            background: rgba(255,255,255,0.04);
                            color: rgba(255,255,255,0.65);
                            white-space: nowrap;
                            text-decoration: none;
                        }
                        #__seanime_moreinfo_panel .smi-pill.smi-score {
                            color: #a5b4fc;
                            border-color: rgba(165,180,252,0.25);
                            background: rgba(165,180,252,0.07);
                        }
                        #__seanime_moreinfo_panel .smi-pill.smi-studio {
                            color: rgba(255,255,255,0.7);
                            border-color: rgba(255,255,255,0.15);
                            background: rgba(255,255,255,0.06);
                            cursor: pointer;
                        }
                        #__seanime_moreinfo_panel .smi-pill svg {
                            width: 12px;
                            height: 12px;
                            flex-shrink: 0;
                        }

                        #__seanime_moreinfo_panel .smi-sep {
                            height: 1px;
                            background: rgba(255,255,255,0.06);
                            margin: 0;
                        }
                    `;
                    document.head.appendChild(s);
                }

                function buildMoreInfoWidget() {
                    if (!isEntryPage()) return;
                    if (document.getElementById('__seanime_moreinfo_btn')) return;

                    var rankingsEl    = document.querySelector('[data-anime-meta-section-rankings-container="true"]');
                    var descTrigger   = document.querySelector('[data-media-page-header-details-description-trigger="true"]');
                    var audienceScore = document.querySelector('[data-media-entry-audience-score="true"]');
                    var studioEl      = document.querySelector('[data-anime-entry-studio-badge="true"]');
                    var genresEl      = document.querySelector('[data-media-entry-genres-list="true"]');

                    if (!rankingsEl && !descTrigger && !audienceScore && !studioEl && !genresEl) return;

                    var btn = document.createElement('button');
                    btn.id = '__seanime_moreinfo_btn';
                    btn.setAttribute('data-open', 'false');
                    btn.innerHTML =
                        '<span>More info</span>' +
                        '<svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">' +
                          '<polyline points="6 9 12 15 18 9"></polyline>' +
                        '</svg>';

                    var panel = document.createElement('div');
                    panel.id = '__seanime_moreinfo_panel';
                    panel.setAttribute('data-open', 'false');

                    if (descTrigger) {
                        var descText = descTrigger.textContent.trim();
                        if (descText) {
                            var descDiv = document.createElement('div');
                            descDiv.className = 'smi-description';
                            descDiv.textContent = descText;
                            descDiv.style.cursor = 'pointer';
                            descDiv.addEventListener('click', function() {
                                var expanded = descDiv.getAttribute('data-expanded') === 'true';
                                descDiv.setAttribute('data-expanded', expanded ? 'false' : 'true');
                            });
                            panel.appendChild(descDiv);
                            panel.appendChild(Object.assign(document.createElement('div'), {className: 'smi-sep'}));
                        }
                    }

                    var hasMeta = audienceScore || studioEl;
                    if (hasMeta) {
                        var metaRow = document.createElement('div');
                        metaRow.className = 'smi-row';

                        if (audienceScore) {
                            var scorePill = document.createElement('span');
                            scorePill.className = 'smi-pill smi-score';
                            scorePill.innerHTML = audienceScore.innerHTML;
                            metaRow.appendChild(scorePill);
                        }

                        if (studioEl) {
                            var studioPill = document.createElement('span');
                            studioPill.className = 'smi-pill smi-studio';
                            studioPill.textContent = studioEl.textContent.trim();
                            studioPill.addEventListener('click', function() {
                                studioEl.dispatchEvent(new MouseEvent('click', {bubbles: true, cancelable: true}));
                            });
                            metaRow.appendChild(studioPill);
                        }

                        panel.appendChild(metaRow);
                    }

                    if (rankingsEl) {
                        var rankLinks = rankingsEl.querySelectorAll('a');
                        if (rankLinks.length > 0) {
                            if (hasMeta) {
                                panel.appendChild(Object.assign(document.createElement('div'), {className: 'smi-sep'}));
                            }
                            var rankRow = document.createElement('div');
                            rankRow.className = 'smi-rankings';

                            rankLinks.forEach(function(a) {
                                var pill = document.createElement('a');
                                pill.className = 'smi-pill';
                                pill.href = a.href;
                                var iconEl = a.querySelector('svg');
                                var labelText = a.textContent.trim();
                                if (iconEl) {
                                    var iconWrap = document.createElement('span');
                                    var clonedIcon = iconEl.cloneNode(true);
                                    clonedIcon.setAttribute('width', '12');
                                    clonedIcon.setAttribute('height', '12');
                                    var iconSpanOrig = a.querySelector('.UI-Badge__icon');
                                    if (iconSpanOrig) {
                                        var color = iconSpanOrig.style.color ||
                                            (iconSpanOrig.className.indexOf('yellow') !== -1 ? '#eab308' :
                                             iconSpanOrig.className.indexOf('pink') !== -1   ? '#ec4899' : 'currentColor');
                                        iconWrap.style.color = color;
                                    }
                                    iconWrap.appendChild(clonedIcon);
                                    pill.appendChild(iconWrap);
                                }
                                pill.appendChild(document.createTextNode(labelText));
                                rankRow.appendChild(pill);
                            });

                            panel.appendChild(rankRow);
                        }
                    }

                    if (genresEl) {
                        var genreLinks = genresEl.querySelectorAll('a');
                        if (genreLinks.length > 0) {
                            panel.appendChild(Object.assign(document.createElement('div'), {className: 'smi-sep'}));
                            var genreRow = document.createElement('div');
                            genreRow.className = 'smi-genres';

                            genreLinks.forEach(function(a) {
                                var pill = document.createElement('a');
                                pill.className = 'smi-pill';
                                pill.href = a.href;
                                pill.textContent = a.textContent.trim();
                                genreRow.appendChild(pill);
                            });

                            panel.appendChild(genreRow);
                        }
                    }

                    btn.addEventListener('click', function() {
                        var isOpen = btn.getAttribute('data-open') === 'true';
                        btn.setAttribute('data-open', isOpen ? 'false' : 'true');
                        panel.setAttribute('data-open', isOpen ? 'false' : 'true');
                    });

                    var anchor = null;
                    var candidates = [
                        '[data-media-page-header-details-description-trigger="true"]',
                        '[data-anime-meta-section-rankings-container="true"]',
                        '[data-media-entry-genres-list="true"]',
                        '[data-media-entry-audience-score="true"]'
                    ];
                    for (var ci = 0; ci < candidates.length; ci++) {
                        var el = document.querySelector(candidates[ci]);
                        if (el && el.parentElement) {
                            anchor = el.parentElement;
                            break;
                        }
                    }

                    if (!anchor) return;

                    var wrapper = document.createElement('div');
                    wrapper.id = '__seanime_moreinfo_wrapper';
                    wrapper.style.cssText = 'display:flex;flex-direction:column;align-items:center;gap:0.6rem;width:100%;';
                    wrapper.appendChild(btn);
                    wrapper.appendChild(panel);

                    anchor.appendChild(wrapper);
                }

                injectMoreInfoStyles();
                buildMoreInfoWidget();

                var observer = new MutationObserver(function(mutations) {
                    var added = mutations.some(function(m) { return m.addedNodes.length > 0; });
                    if (added && isEntryPage()) buildMoreInfoWidget();
                });
                observer.observe(document.body, { childList: true, subtree: true });

                var lastHref = window.location.href;
                setInterval(function() {
                    if (window.location.href !== lastHref) {
                        lastHref = window.location.href;
                        ['__seanime_moreinfo_wrapper', '__seanime_moreinfo_styles'].forEach(function(id) {
                            var el = document.getElementById(id);
                            if (el) el.remove();
                        });
                        window.__seanime_moreinfo_init = false;
                        setTimeout(function() {
                            window.__seanime_moreinfo_init = true;
                            injectMoreInfoStyles();
                            buildMoreInfoWidget();
                        }, 400);
                        setTimeout(buildMoreInfoWidget, 900);
                        setTimeout(buildMoreInfoWidget, 1500);
                    }
                }, 300);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}