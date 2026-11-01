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

/**
 * Hosts LibreOfficeKit in a separate process. A native SIGABRT during
 * initialization must not terminate the NiceExcel UI process.
 *
 * Message protocol:
 *   what=1: initialize; reply what=2 with Bundle keys "ok" and "detail".
 */
public final class LibreOfficeEngineService extends Service {
    private static final String TAG = "NiceExcel-LO-Service";
    public static final int MSG_INITIALIZE = 1;
    public static final int MSG_INITIALIZE_RESULT = 2;
    public static final String KEY_OK = "ok";
    public static final String KEY_DETAIL = "detail";

    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (message.what != MSG_INITIALIZE) return false;
        Message reply = Message.obtain(null, MSG_INITIALIZE_RESULT);
        Bundle result = new Bundle();
        try {
            LibreOfficeKit.init(getApplicationContext());
            boolean ready = LibreOfficeKit.isInitialized();
            result.putBoolean(KEY_OK, ready);
            result.putString(KEY_DETAIL, ready
                    ? "LibreOfficeKit native initialization completed"
                    : "LibreOfficeKit native initialization returned false");
            Log.i(TAG, result.getString(KEY_DETAIL));
        } catch (Throwable failure) {
            result.putBoolean(KEY_OK, false);
            result.putString(KEY_DETAIL, failure.getClass().getSimpleName() + ": "
                    + String.valueOf(failure.getMessage()));
            Log.e(TAG, "LibreOfficeKit initialization failed", failure);
        }
        reply.setData(result);
        try {
            if (message.replyTo != null) message.replyTo.send(reply);
        } catch (RemoteException failure) {
            Log.w(TAG, "Unable to return initialization result", failure);
        }
        return true;
    }));

    @Override public void onCreate() {
        super.onCreate();
        Log.i(TAG, "LibreOffice isolated process started: " + android.os.Process.myPid());
    }

    @Override public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }
}
