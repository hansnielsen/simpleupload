package com.stackallocated.imageupload;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.util.Log;

public class UploadService extends Service {
    private final static String TAG = "UploadService";
    private Handler handler;

    private final static int UPLOAD_IMAGE = 1; 
    
    private class UploadServiceHandler extends Handler {
        public UploadServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            // If the message type is wrong, panic and kill everything.
            if (msg.what != UPLOAD_IMAGE) {
                Log.e(TAG, "Got message '" + msg.what + "' that wasn't UPLOAD_IMAGE!");
                stopSelf();
                return;
            }

            // arg1 is the startId, obj is the Uri.
            int startId = msg.arg1;
            Uri uri = (Uri)msg.obj;

            // Here, we need to:
            //   a) get the HTTP endpoint / credentials
            //   b) open the image
            //   c) display a progress notification
            //   d) open an HTTP connection
            //   e) stream the image through the connection
            //   e) retrieve the resulting URL
            //   f) display a final notification with the URL

            // Temporary test code that just wastes time.
            Log.i(TAG, "Starting handling of '" + uri + "'/" + startId);
            long endTime = System.currentTimeMillis() + 5*1000;
            while (System.currentTimeMillis() < endTime) {
                synchronized (this) {
                    try {
                        wait(endTime - System.currentTimeMillis());
                    } catch (Exception e) {
                    }
                }
            }
            Log.i(TAG, "Finished handling of '" + uri + "'/" + startId);

            // This handler is the only place stopSelf() will be called,
            // so we can be sure that all calls will be in order.
            stopSelf(startId);
        }
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "Starting upload service");

        HandlerThread thread = new HandlerThread("UploadServiceThread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        
        handler = new UploadServiceHandler(thread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        Message msg = handler.obtainMessage(UPLOAD_IMAGE, imageUri);
        msg.arg1 = startId;
        if (!handler.sendMessage(msg)) {
            Log.w(TAG, "Couldn't send message '" + msg + "' to handler");
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Stopping upload service");
    }

    // Have to override this, even though we don't use it.
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
