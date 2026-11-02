package com.meraj.niceexcel;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONTokener;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Headless HyperFormula host. It runs in the dedicated :hyperformula process so
 * JavaScript calculation cannot stall the native spreadsheet's UI process.
 */
public final class HyperFormulaEngineService extends Service {
    public static final int MSG_RECALCULATE = 41;
    public static final int MSG_RESULT = 42;
    public static final String KEY_PAYLOAD = "payload";
    public static final String KEY_RESULT = "result";
    private static final String TAG = "NiceExcel-HyperFormula";

    private final Queue<Message> pending = new ArrayDeque<>();
    private WebView webView;
    private boolean ready;
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message message) {
            if (message.what != MSG_RECALCULATE) return;
            if (!ready || webView == null) {
                pending.add(Message.obtain(message));
                return;
            }
            evaluate(message);
        }
    });

    @Override public void onCreate() {
        super.onCreate();
        try {
            // FlaskActivity also owns a WebView in the main app process. Android P+
            // requires a distinct WebView data directory for this secondary process.
            if (Build.VERSION.SDK_INT >= 28) WebView.setDataDirectorySuffix("hyperformula");
            webView = new WebView(getApplicationContext());
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(false);
            webView.setWebViewClient(new WebViewClient() {
                @Override public void onPageFinished(WebView view, String url) {
                    if (!url.endsWith("hyperformula_runtime.html")) return;
                    ready = true;
                    Log.i(TAG, "Offline HyperFormula runtime ready (GPLv3)");
                    while (!pending.isEmpty()) evaluate(pending.remove());
                }
            });
            webView.loadUrl("file:///android_asset/hyperformula_runtime.html");
        } catch (Throwable error) {
            Log.e(TAG, "Unable to initialize HyperFormula WebView runtime", error);
        }
    }

    private void evaluate(Message request) {
        Bundle data = request.getData();
        String payload = data == null ? null : data.getString(KEY_PAYLOAD);
        if (payload == null || webView == null) {
            sendResult(request, "{\"ok\":false,\"error\":\"Missing payload or runtime\"}");
            return;
        }
        String javascript = "window.NiceExcelHF ? window.NiceExcelHF.calculate(" +
                org.json.JSONObject.quote(payload) + ") : JSON.stringify({ok:false,error:'runtime-not-ready'})";
        webView.evaluateJavascript(javascript, encoded -> {
            String result;
            try {
                Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
                result = decoded == null ? "null" : decoded.toString();
            } catch (Throwable error) {
                result = "{\"ok\":false,\"error\":" +
                        org.json.JSONObject.quote("Unable to decode HyperFormula response: " + error) + "}";
            }
            sendResult(request, result);
        });
    }

    private void sendResult(Message request, String result) {
        if (request.replyTo == null) return;
        try {
            Message response = Message.obtain(null, MSG_RESULT);
            Bundle bundle = new Bundle();
            bundle.putString(KEY_RESULT, result);
            response.setData(bundle);
            request.replyTo.send(response);
        } catch (Throwable error) {
            Log.w(TAG, "Unable to return formula results to client", error);
        }
    }

    @Override public IBinder onBind(Intent intent) { return messenger.getBinder(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        ready = false;
        pending.clear();
        if (webView != null) {
            try { webView.stopLoading(); webView.destroy(); } catch (Throwable ignored) { }
            webView = null;
        }
        super.onDestroy();
    }
}
