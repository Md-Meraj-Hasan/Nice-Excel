package com.meraj.niceexcel;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.libreoffice.kit.LibreOfficeKit;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs all LibreOfficeKit JNI work in the isolated :libreoffice process.
 *
 * Protocol:
 *   1 -> initialize; reply 2
 *   3 -> recalculate XLSX using KEY_INPUT_PATH and KEY_OUTPUT_PATH; reply 4
 */
public final class LibreOfficeEngineService extends Service {
    private static final String TAG = "NiceExcel-LO-Service";
    public static final int MSG_INITIALIZE = 1;
    public static final int MSG_INITIALIZE_RESULT = 2;
    public static final int MSG_RECALCULATE_XLSX = 3;
    public static final int MSG_RECALCULATE_XLSX_RESULT = 4;
    public static final String KEY_OK = "ok";
    public static final String KEY_DETAIL = "detail";
    public static final String KEY_INPUT_PATH = "inputPath";
    public static final String KEY_OUTPUT_PATH = "outputPath";

    private final ExecutorService engineExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NiceExcel-LO-Service-Worker");
        thread.setDaemon(true);
        return thread;
    });

    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (message.what != MSG_INITIALIZE && message.what != MSG_RECALCULATE_XLSX) return false;
        final int request = message.what;
        final Messenger replyTo = message.replyTo;
        final Bundle requestData = message.getData();
        engineExecutor.execute(() -> handleRequest(request, requestData, replyTo));
        return true;
    }));

    private void handleRequest(int request, Bundle requestData, Messenger replyTo) {
        Message reply = Message.obtain(null,
                request == MSG_INITIALIZE ? MSG_INITIALIZE_RESULT : MSG_RECALCULATE_XLSX_RESULT);
        Bundle result = new Bundle();
        try {
            if (!LibreOfficeKit.isInitialized()) {
                LibreOfficeKit.init(getApplicationContext());
            }
            if (!LibreOfficeKit.isInitialized()) {
                throw new IllegalStateException("LibreOfficeKit native initialization returned false");
            }

            if (request == MSG_RECALCULATE_XLSX) {
                String inputPath = requestData.getString(KEY_INPUT_PATH);
                String outputPath = requestData.getString(KEY_OUTPUT_PATH);
                if (inputPath == null || outputPath == null) {
                    throw new IllegalArgumentException("Input and output XLSX paths are required");
                }
                File input = new File(inputPath);
                File output = new File(outputPath);
                LibreOfficeCalcEngine.INSTANCE.recalculateXlsxFile(
                        getApplicationContext(), input, output);
                result.putString(KEY_DETAIL, "LibreOffice Calc recalculation completed");
                Log.i(TAG, "Recalculated workbook: " + output.getAbsolutePath()
                        + " (" + output.length() + " bytes)");
            } else {
                result.putString(KEY_DETAIL, "LibreOfficeKit native initialization completed");
                Log.i(TAG, result.getString(KEY_DETAIL));
            }
            result.putBoolean(KEY_OK, true);
        } catch (Throwable failure) {
            result.putBoolean(KEY_OK, false);
            result.putString(KEY_DETAIL, failure.getClass().getSimpleName() + ": "
                    + String.valueOf(failure.getMessage()));
            Log.e(TAG, "LibreOffice engine request failed", failure);
        }

        reply.setData(result);
        try {
            if (replyTo != null) replyTo.send(reply);
        } catch (RemoteException failure) {
            Log.w(TAG, "Unable to return LibreOffice engine result", failure);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        Log.i(TAG, "LibreOffice isolated process started: " + android.os.Process.myPid());
    }

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override public void onDestroy() {
        engineExecutor.shutdown();
        super.onDestroy();
    }
}
