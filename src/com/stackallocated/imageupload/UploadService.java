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
    private Thread thread;

    public final static String EXTRA_URIS = "com.stackallocated.imageupload.EXTRA_URIS";

    protected BlockingQueue<Uri> pendingUris = new LinkedBlockingQueue<>();

    @Override
    public void onCreate() {
        Log.i(TAG, "Starting upload service");

        thread = new Thread(new UploadServiceRunnable(this));
        thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!thread.isAlive()) {
            Log.e(TAG, "Thread is dead in onStartCommand!");
        }
        // This just shoves the URIs in the concurrent queue and lets the thread handle it.
        // The warning suppression is because only code in this app will supply this extra.
        @SuppressWarnings("unchecked")
        ArrayList<Uri> imageUris = (ArrayList<Uri>)intent.getSerializableExtra(EXTRA_URIS);
        pendingUris.addAll(imageUris);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (thread.isAlive()) {
            Log.e(TAG, "Thread is alive in onStartCommand!");
        }
        if (pendingUris.size() > 0) {
            Log.e(TAG, "Upload service stopped with " + pendingUris.size() + " remaining!");
        } else {
            Log.i(TAG, "Stopping upload service");
        }
    }

    // Have to override this, even though we don't use it.
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
