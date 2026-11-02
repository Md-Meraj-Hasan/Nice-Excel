package com.meraj.niceexcel;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Headless HyperFormula host in the dedicated :hyperformula process.
 *
 * Startup and calculation are explicitly acknowledged. A failed WebView load,
 * JavaScript bundle, renderer, or request returns a useful error immediately
 * instead of leaving Messenger clients blocked until their outer timeout.
 */
public final class HyperFormulaEngineService extends Service {
    public static final int MSG_RECALCULATE = 41;
    public static final int MSG_RESULT = 42;
    public static final String KEY_PAYLOAD_PATH = "payload_path";
    public static final String KEY_RESULT_PATH = "result_path";
    private static final String TAG = "NiceExcel-HyperFormula";
    private static final long STARTUP_TIMEOUT_MS = 20000L;
    private static final long REQUEST_TIMEOUT_MS = 30000L;
    private static final int MAX_PENDING_REQUESTS = 8;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Queue<Message> pending = new ArrayDeque<>();
    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NiceExcel-HF-IO");
        thread.setDaemon(true);
        return thread;
    });

    private WebView webView;
    private boolean ready;
    private boolean initializing = true;
    private String initializationFailure;
    private Message activeRequest;
    private Runnable activeTimeout;
    private long requestSequence;
    private int runtimeProbeAttempts;
    private Runnable runtimeProbeTask;

    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message message) {
            if (message.what != MSG_RECALCULATE) return;
            if (initializationFailure != null) {
                sendError(message, "HyperFormula runtime initialization failed: " + initializationFailure);
                return;
            }
            if (pending.size() >= MAX_PENDING_REQUESTS) {
                sendError(message, "HyperFormula request queue is full; retry after current calculation");
                return;
            }
            pending.add(Message.obtain(message));
            processNext();
        }
    });

    @Override public void onCreate() {
        super.onCreate();
        Log.i(TAG, "SERVICE_CREATE pid=" + android.os.Process.myPid()
                + " sdk=" + Build.VERSION.SDK_INT);
        try {
            // Must be configured before this process creates any WebView.
            if (Build.VERSION.SDK_INT >= 28) WebView.setDataDirectorySuffix("hyperformula");
            webView = new WebView(getApplicationContext());
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(false);
            webView.setWebChromeClient(new WebChromeClient() {
                @Override public boolean onConsoleMessage(ConsoleMessage message) {
                    Log.i(TAG, "WEB_CONSOLE " + message.messageLevel() + " "
                            + message.message() + " @ " + message.sourceId() + ":" + message.lineNumber());
                    return true;
                }
            });
            webView.setWebViewClient(new WebViewClient() {
                @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    Log.i(TAG, "WEB_PAGE_STARTED url=" + url);
                    if (url != null && url.contains("hyperformula_runtime.html")) {
                        scheduleRuntimeProbe(250L);
                    }
                }

                @Override public void onPageFinished(WebView view, String url) {
                    Log.i(TAG, "WEB_PAGE_FINISHED url=" + url);
                    if (url == null || !url.contains("hyperformula_runtime.html")) return;
                    scheduleRuntimeProbe(0L);
                }

                @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request != null && request.isForMainFrame()) {
                        String detail = "WebView main-frame load error " + error.getErrorCode()
                                + ": " + error.getDescription();
                        Log.e(TAG, detail);
                        failInitialization(detail);
                    } else if (error != null) {
                        Log.w(TAG, "Web resource load error " + error.getErrorCode()
                                + ": " + error.getDescription());
                    }
                }

                @Override public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    Log.e(TAG, "WEB_LOAD_ERROR code=" + errorCode + " description=" + description
                            + " url=" + failingUrl);
                    if (failingUrl != null && failingUrl.contains("hyperformula_runtime.html")) {
                        failInitialization("WebView load error " + errorCode + ": " + description);
                    }
                }

                @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                    String reason = "WebView renderer exited; crashed=" + detail.didCrash()
                            + " priority=" + detail.rendererPriorityAtExit();
                    Log.e(TAG, reason);
                    failInitialization(reason);
                    if (webView != null) {
                        try { webView.destroy(); } catch (Throwable ignored) { }
                        webView = null;
                    }
                    return true;
                }
            });

            main.postDelayed(() -> {
                if (!ready && initializationFailure == null) {
                    failInitialization("WebView runtime did not become ready within "
                            + STARTUP_TIMEOUT_MS + " ms");
                }
            }, STARTUP_TIMEOUT_MS);

            Log.i(TAG, "WEBVIEW_LOAD_BEGIN asset=hyperformula_runtime.html");
            webView.loadUrl("file:///android_asset/hyperformula_runtime.html");
        } catch (Throwable error) {
            Log.e(TAG, "Unable to initialize HyperFormula WebView runtime", error);
            failInitialization(error.toString());
        }
    }

    private void scheduleRuntimeProbe(long delayMs) {
        if (ready || initializationFailure != null) return;
        if (runtimeProbeTask != null) main.removeCallbacks(runtimeProbeTask);
        runtimeProbeTask = this::probeRuntime;
        main.postDelayed(runtimeProbeTask, delayMs);
    }

    private void probeRuntime() {
        if (webView == null || initializationFailure != null || ready) return;
        runtimeProbeAttempts++;
        webView.evaluateJavascript(
                "Boolean(window.HyperFormula && typeof window.HyperFormula.buildFromSheets === 'function' "
                        + "&& window.NiceExcelHF && window.NiceExcelHF.ready === true)",
                value -> {
                    Log.i(TAG, "RUNTIME_PROBE result=" + value);
                    if ("true".equals(value)) {
                        ready = true;
                        initializing = false;
                        Log.i(TAG, "HYPERFORMULA_READY engine=HyperFormula bundle=local license=gpl-v3");
                        processNext();
                    } else if (runtimeProbeAttempts < 40) {
                        Log.i(TAG, "RUNTIME_PROBE_PENDING attempt=" + runtimeProbeAttempts
                                + " value=" + value);
                        scheduleRuntimeProbe(500L);
                    } else {
                        failInitialization("HyperFormula bundle/API did not become ready after "
                                + runtimeProbeAttempts + " probes; last probe=" + value);
                    }
                });
    }

    private void failInitialization(String reason) {
        if (ready || initializationFailure != null) return;
        initializationFailure = reason;
        initializing = false;
        Log.e(TAG, "HYPERFORMULA_INIT_FAILED " + reason);
        while (!pending.isEmpty()) {
            sendError(pending.remove(), "HyperFormula runtime unavailable: " + reason);
        }
    }

    private void processNext() {
        if (!ready || webView == null || activeRequest != null || pending.isEmpty()) return;
        activeRequest = pending.remove();
        final Message request = activeRequest;
        final long requestId = ++requestSequence;
        final String payloadPath = request.getData() == null ? null
                : request.getData().getString(KEY_PAYLOAD_PATH);
        if (payloadPath == null) {
            completeRequest(request, requestId,
                    "{\"ok\":false,\"error\":\"Missing payload path\"}");
            return;
        }

        AtomicBoolean completed = new AtomicBoolean(false);
        activeTimeout = () -> {
            if (!completed.compareAndSet(false, true)) return;
            Log.e(TAG, "REQUEST_TIMEOUT id=" + requestId + " timeoutMs=" + REQUEST_TIMEOUT_MS);
            completeRequest(request, requestId,
                    "{\"ok\":false,\"error\":\"HyperFormula JavaScript calculation timed out after "
                            + REQUEST_TIMEOUT_MS + " ms\"}");
        };
        main.postDelayed(activeTimeout, REQUEST_TIMEOUT_MS);

        fileExecutor.execute(() -> {
            final String payload;
            try {
                File payloadFile = new File(payloadPath).getCanonicalFile();
                File cacheRoot = getCacheDir().getCanonicalFile();
                if (!payloadFile.getPath().startsWith(cacheRoot.getPath() + File.separator)) {
                    throw new SecurityException("Formula payload must be inside app cache directory");
                }
                payload = new String(Files.readAllBytes(payloadFile.toPath()), StandardCharsets.UTF_8);
            } catch (Throwable error) {
                main.post(() -> {
                    if (!completed.compareAndSet(false, true)) return;
                    Log.e(TAG, "PAYLOAD_READ_FAILED id=" + requestId, error);
                    completeRequest(request, requestId, errorJson("Unable to read formula payload: " + error));
                });
                return;
            }

            main.post(() -> {
                if (completed.get() || activeRequest != request) return;
                try {
                    JSONObject payloadObject = new JSONObject(payload);
                    int sheetCount = payloadObject.optJSONObject("sheets") == null ? 0
                            : payloadObject.optJSONObject("sheets").length();
                    int formulaCount = payloadObject.optJSONArray("formulas") == null ? 0
                            : payloadObject.optJSONArray("formulas").length();
                    Log.i(TAG, "REQUEST_BEGIN id=" + requestId + " payloadBytes="
                            + payload.getBytes(StandardCharsets.UTF_8).length
                            + " sheets=" + sheetCount + " formulas=" + formulaCount);
                    String javascript = "window.NiceExcelHF ? window.NiceExcelHF.calculate("
                            + JSONObject.quote(payload)
                            + ") : JSON.stringify({ok:false,error:'runtime-not-ready'})";
                    webView.evaluateJavascript(javascript, encoded -> {
                        if (!completed.compareAndSet(false, true)) return;
                        String result;
                        try {
                            Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
                            result = decoded == null ? "null" : decoded.toString();
                        } catch (Throwable error) {
                            result = errorJson("Unable to decode HyperFormula response: " + error);
                        }
                        Log.i(TAG, "REQUEST_CALLBACK id=" + requestId + " encodedChars="
                                + (encoded == null ? -1 : encoded.length()));
                        completeRequest(request, requestId, result);
                    });
                } catch (Throwable error) {
                    if (completed.compareAndSet(false, true)) {
                        Log.e(TAG, "REQUEST_DISPATCH_FAILED id=" + requestId, error);
                        completeRequest(request, requestId, errorJson(error.toString()));
                    }
                }
            });
        });
    }

    private String errorJson(String error) {
        return "{\"ok\":false,\"error\":" + JSONObject.quote(error) + "}";
    }

    private void completeRequest(Message request, long requestId, String result) {
        if (activeTimeout != null) {
            main.removeCallbacks(activeTimeout);
            activeTimeout = null;
        }
        if (activeRequest == request) activeRequest = null;
        sendResult(request, result);
        Log.i(TAG, "REQUEST_COMPLETE id=" + requestId);
        processNext();
    }

    private void sendError(Message request, String error) {
        sendResult(request, errorJson(error));
    }

    private void sendResult(Message request, String result) {
        if (request.replyTo == null) return;
        File resultFile = null;
        try {
            resultFile = File.createTempFile("niceexcel-hf-result-", ".json", getCacheDir());
            Files.write(resultFile.toPath(), result.getBytes(StandardCharsets.UTF_8));
            Message response = Message.obtain(null, MSG_RESULT);
            Bundle bundle = new Bundle();
            bundle.putString(KEY_RESULT_PATH, resultFile.getAbsolutePath());
            response.setData(bundle);
            request.replyTo.send(response);
        } catch (Throwable error) {
            if (resultFile != null) runCatchingDelete(resultFile);
            Log.e(TAG, "Unable to return formula results to client", error);
        }
    }

    private void runCatchingDelete(File file) {
        try { Files.deleteIfExists(file.toPath()); } catch (Throwable ignored) { }
    }

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        ready = false;
        initializationFailure = "HyperFormula service destroyed";
        main.removeCallbacksAndMessages(null);
        pending.clear();
        if (activeRequest != null) {
            sendError(activeRequest, initializationFailure);
            activeRequest = null;
        }
        fileExecutor.shutdownNow();
        if (webView != null) {
            try { webView.stopLoading(); webView.destroy(); } catch (Throwable ignored) { }
            webView = null;
        }
        super.onDestroy();
    }
}
