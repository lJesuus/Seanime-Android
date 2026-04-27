package com.seanime.app

import android.webkit.WebView

object UIDirectorySelectorPatch {

    fun inject(webView: WebView) {
        val js = """
            (function() {
                if (window.__seanime_directory_patch_init) return;
                window.__seanime_directory_patch_init = true;

                // Global callback for the Android bridge
                window.onFolderSelected = function(path) {
                    console.log("Folder selected: " + path);
                    
                    let targetInput = window._activeDirectoryInput;
                    
                    if (!targetInput) {
                        const activeElement = document.activeElement;
                        if (activeElement && activeElement.tagName === 'INPUT') {
                            targetInput = activeElement;
                        } else {
                            const modal = document.querySelector('[role="dialog"]');
                            if (modal) {
                                targetInput = modal.querySelector('input');
                            }
                        }
                    }

                    if (targetInput) {
                        const nativeInputValueSetter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value").set;
                        nativeInputValueSetter.call(targetInput, path);
                        targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                        targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                        targetInput.blur();
                    }
                };

                // Watch for the folder icon clicks
                document.addEventListener('click', function(e) {
                    let target = e.target;
                    while (target && target !== document.body && target !== document) {
                        let className = typeof target.className === 'string' ? target.className : (target.className?.baseVal || "");
                        
                        // Check if it's the folder icon container or the SVG itself
                        let isFolderIcon = className.includes('text-2xl cursor-pointer');
                        let parentHasClasses = target.parentElement && typeof target.parentElement.className === 'string' && 
                                               target.parentElement.className.includes('absolute') && 
                                               target.parentElement.className.includes('top-0') && 
                                               target.parentElement.className.includes('right-0');
                                               
                        if (isFolderIcon || parentHasClasses) {
                            // Find the closest input
                            const wrapper = target.closest('.space-y-1') || target.closest('.relative');
                            if (wrapper) {
                                const input = wrapper.querySelector('input');
                                window._activeDirectoryInput = input;
                                
                                console.log("Directory selector clicked, triggering native picker");
                                
                                if (window.FolderPickerBridge) {
                                    e.stopPropagation();
                                    e.preventDefault();
                                    window.FolderPickerBridge.openFolderPicker();
                                }
                                break;
                            }
                        }
                        target = target.parentElement;
                    }
                }, true); // use capture phase to intercept before React
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }
}
