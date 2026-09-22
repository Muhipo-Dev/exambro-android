package com.muhipo.exambrowser.cache

import android.webkit.WebView

/**
 * Global JavaScript Engine Injector for CBT IndexedDB Caching and Auto-Release Sync.
 * Injected automatically into every loaded CBT web page to hook fetch, XMLHttpRequest,
 * and DOM form inputs.
 */
object CbtCacheScriptInjector {

    fun injectEngine(webView: WebView, host: String) {
        val cleanHost = host.lowercase().trim()
        val jsEngine = """
            (function() {
                if (window.__exambroCacheEngineInitialized) return;
                window.__exambroCacheEngineInitialized = true;

                var CURRENT_HOST = "$cleanHost";
                var DB_NAME = "ExambroCbtOfflineDB";
                var DB_VERSION = 1;
                var dbInstance = null;

                // 1. Initialize Client-Side IndexedDB
                function initIndexedDB(callback) {
                    var request = indexedDB.open(DB_NAME, DB_VERSION);
                    request.onupgradeneeded = function(e) {
                        var db = e.target.result;
                        if (!db.objectStoreNames.contains('cbt_answers')) {
                            db.createObjectStore('cbt_answers', { keyPath: 'id' });
                        }
                        if (!db.objectStoreNames.contains('cbt_pending_queue')) {
                            db.createObjectStore('cbt_pending_queue', { keyPath: 'id' });
                        }
                        if (!db.objectStoreNames.contains('cbt_session_state')) {
                            db.createObjectStore('cbt_session_state', { keyPath: 'key' });
                        }
                    };
                    request.onsuccess = function(e) {
                        dbInstance = e.target.result;
                        if (window.ExamBrowserCacheBridge) {
                            window.ExamBrowserCacheBridge.logCacheEvent("IndexedDB", "Client IndexedDB initialized successfully");
                        }
                        if (callback) callback(dbInstance);
                    };
                    request.onerror = function(e) {
                        console.error("Failed to open IndexedDB", e);
                    };
                }

                initIndexedDB(function() {
                    restoreAnswersFromNative();
                    triggerAutoReleaseSync();
                });

                // 2. Save Answer to IndexedDB & Native Bridge
                function saveAnswerToStorage(qId, val, elementType) {
                    if (!qId) return;
                    var payload = {
                        id: qId,
                        value: val,
                        type: elementType || 'input',
                        updatedAt: Date.now()
                    };

                    // Save to client IndexedDB
                    if (dbInstance) {
                        try {
                            var tx = dbInstance.transaction(['cbt_answers'], 'readwrite');
                            tx.objectStore('cbt_answers').put(payload);
                        } catch(e) {}
                    }

                    // Save to Native Android Database Bridge
                    if (window.ExamBrowserCacheBridge) {
                        window.ExamBrowserCacheBridge.saveCbtAnswer(CURRENT_HOST, qId, JSON.stringify(payload));
                    }
                }

                // 3. Monitor and Hook Form Inputs globally
                function attachFormListeners() {
                    document.addEventListener('input', function(e) {
                        var target = e.target;
                        if (!target) return;
                        var name = target.name || target.id;
                        if (name) {
                            var val = target.value;
                            if (target.type === 'checkbox') {
                                val = target.checked;
                            }
                            saveAnswerToStorage(name, val, target.type || target.tagName.toLowerCase());
                        }
                    }, true);

                    document.addEventListener('change', function(e) {
                        var target = e.target;
                        if (!target) return;
                        var name = target.name || target.id;
                        if (name) {
                            var val = target.value;
                            if (target.type === 'radio' && target.checked) {
                                val = target.value;
                            } else if (target.type === 'checkbox') {
                                val = target.checked;
                            }
                            saveAnswerToStorage(name, val, target.type || target.tagName.toLowerCase());
                        }
                    }, true);
                }

                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    attachFormListeners();
                } else {
                    document.addEventListener('DOMContentLoaded', attachFormListeners);
                }

                // 4. Restore Answers when Page Loads or App Re-opens
                function restoreAnswersFromNative() {
                    if (!window.ExamBrowserCacheBridge) return;
                    try {
                        var rawJson = window.ExamBrowserCacheBridge.getAllSavedAnswers(CURRENT_HOST);
                        if (!rawJson) return;
                        var answersMap = JSON.parse(rawJson);
                        for (var key in answersMap) {
                            try {
                                var item = JSON.parse(answersMap[key]);
                                if (!item || !item.id) continue;
                                var el = document.getElementsByName(item.id)[0] || document.getElementById(item.id);
                                if (el) {
                                    if (el.type === 'radio') {
                                        var radios = document.getElementsByName(item.id);
                                        for (var r = 0; r < radios.length; r++) {
                                            if (radios[r].value === item.value) {
                                                radios[r].checked = true;
                                            }
                                        }
                                    } else if (el.type === 'checkbox') {
                                        el.checked = (item.value === true || item.value === 'true');
                                    } else {
                                        el.value = item.value;
                                    }
                                }
                            } catch(e) {}
                        }
                    } catch(e) {}
                }

                // 5. Global Fetch & XMLHttpRequest Interceptor for Offline CBT Submission Queue
                var originalFetch = window.fetch;
                if (originalFetch) {
                    window.fetch = function() {
                        var args = arguments;
                        return originalFetch.apply(this, args).catch(function(err) {
                            try {
                                var url = typeof args[0] === 'string' ? args[0] : (args[0] ? args[0].url : '');
                                var options = args[1] || {};
                                var method = options.method || 'GET';
                                if (method.toUpperCase() === 'POST' || method.toUpperCase() === 'PUT') {
                                    var payloadStr = options.body || '';
                                    if (window.ExamBrowserCacheBridge) {
                                        window.ExamBrowserCacheBridge.queueOfflinePayload(CURRENT_HOST, url, method, payloadStr);
                                    }
                                    return Promise.resolve(new Response(JSON.stringify({
                                        status: 'offline_cached',
                                        message: 'Mode Offline : Koneksi Terputus, Jawaban aman tersimpan di cache.',
                                        cached: true
                                    }), {
                                        status: 200,
                                        headers: { 'Content-Type': 'application/json' }
                                    }));
                                }
                            } catch(e) {}
                            return Promise.reject(err);
                        });
                    };
                }

                // Hook XMLHttpRequest globally for jQuery $.ajax / $.post in Candy CBT & BeeSMART
                var XHR = XMLHttpRequest.prototype;
                var open = XHR.open;
                var send = XHR.send;

                XHR.open = function(method, url) {
                    this._method = method;
                    this._url = url;
                    return open.apply(this, arguments);
                };

                XHR.send = function(postData) {
                    var self = this;
                    var method = self._method || 'POST';
                    var url = self._url || '';

                    this.addEventListener('error', function() {
                        if (method.toUpperCase() === 'POST' || method.toUpperCase() === 'PUT') {
                            if (window.ExamBrowserCacheBridge) {
                                window.ExamBrowserCacheBridge.queueOfflinePayload(CURRENT_HOST, url, method, postData || '');
                            }
                        }
                    });

                    return send.apply(this, arguments);
                };

                // 6. Auto-Release Sync Engine on Reconnection
                function triggerAutoReleaseSync() {
                    if (!window.ExamBrowserCacheBridge) return;
                    try {
                        var rawQueue = window.ExamBrowserCacheBridge.getQueuedRequests(CURRENT_HOST);
                        if (!rawQueue) return;
                        var queue = JSON.parse(rawQueue);
                        if (!queue || queue.length === 0) return;

                        if (window.ExamBrowserCacheBridge) {
                            window.ExamBrowserCacheBridge.logCacheEvent("SyncEngine", "Reconnected! Flushing " + queue.length + " cached requests...");
                        }

                        // Process requests sequentially
                        var index = 0;
                        function sendNext() {
                            if (index >= queue.length) return;
                            var item = queue[index];
                            var fetchFunc = originalFetch || window.fetch;
                            fetchFunc(item.endpoint, {
                                method: item.method || 'POST',
                                headers: {
                                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                                    'X-Requested-With': 'XMLHttpRequest'
                                },
                                body: item.payload
                            }).then(function(res) {
                                if (res.ok || res.status < 500) {
                                    if (window.ExamBrowserCacheBridge) {
                                        window.ExamBrowserCacheBridge.notifySyncSuccess(CURRENT_HOST, item.id);
                                    }
                                    index++;
                                    sendNext();
                                }
                            }).catch(function(err) {
                                console.error("Retry sync failed for item " + item.id, err);
                            });
                        }
                        sendNext();
                    } catch(e) {
                        console.error("Error running auto-release sync", e);
                    }
                }

                window.addEventListener('online', triggerAutoReleaseSync);
                window.ExambroTriggerSyncRelease = triggerAutoReleaseSync;

            })();
        """.trimIndent()

        webView.evaluateJavascript(jsEngine, null)
    }

    /**
     * Manually triggers JavaScript sync release from Android code.
     */
    fun triggerSyncRelease(webView: WebView) {
        webView.evaluateJavascript("if (window.ExambroTriggerSyncRelease) { window.ExambroTriggerSyncRelease(); }", null)
    }
}
