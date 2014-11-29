package com.stackallocated.imageupload;

import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

public class UploadService extends Service {
    final static String TAG = "UploadService";
    private UploadServiceRunnable runnable;
    private Thread thread;

    public final static String ACTION_UPLOAD = "upload";
    public final static String ACTION_CANCEL = "cancel";
    public final static String EXTRA_URIS = "com.stackallocated.imageupload.EXTRA_URIS";

    protected BlockingQueue<Uri> pendingUris = new LinkedBlockingQueue<>();

    protected void finish() {
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Starting upload service");

        runnable = new UploadServiceRunnable(this);
        thread = new Thread(runnable);
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!thread.isAlive()) {
            Log.w(TAG, "Thread is dead in onStartCommand!");
            return START_NOT_STICKY;
        }

        switch (intent.getAction()) {
            case ACTION_UPLOAD:
                // This just shoves the URIs in the concurrent queue and lets the thread handle it.
                // The warning suppression is because only code in this app will supply this extra.
                @SuppressWarnings("unchecked")
                ArrayList<Uri> imageUris = (ArrayList<Uri>)intent.getSerializableExtra(EXTRA_URIS);
                pendingUris.addAll(imageUris);
                runnable.updateUploadProgressNotification(false);
                break;
            case ACTION_CANCEL:
                Log.i(TAG, "Cancelling!");
                runnable.abort();
                finish();
                break;
            default:
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Upload service stopped with " + pendingUris.size() + " remaining!");
    }

    // Have to override this, even though we don't use it.
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
