package com.stackallocated.imageupload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import com.stackallocated.util.ProgressHttpEntityWrapper;
import com.stackallocated.util.ProgressListener;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;

public class UploadService extends Service {
    private final static String TAG = "UploadService";
    private Handler handler;

    private final static int UPLOAD_IMAGE = 1;

    private class JsonUploadResponse {
        public String status = null;
        public String url = null;
        ArrayList<String> errors = new ArrayList<String>();
    }

    private class UploadServiceHandler extends Handler {
        final NotificationManager nm;
        final Resources res;
        final static int UPLOAD_PROGRESS_NOTIFICATION = 1;
        final static int UPLOAD_COMPLETE_NOTIFICATION = 2;

        public UploadServiceHandler(Looper looper) {
            super(looper);
            res = getResources();
            nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        }

        JsonUploadResponse parseJsonResponse(InputStream input) throws IOException {
            JsonUploadResponse response = new JsonUploadResponse();

            InputStreamReader inputreader = new InputStreamReader(input);
            BufferedReader bufreader = new BufferedReader(inputreader);

            JsonReader reader = new JsonReader(bufreader);
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "status": {
                        response.status = reader.nextString();
                        Log.v(TAG, "Got status " + response.status);
                        break;
                    }
                    case "public_url": {
                        response.url = reader.nextString();
                        Log.v(TAG, "Got URL " + response.url);
                        break;
                    }
                    // XXX: This is not actually the error format, it'll fail miserably if there's an error.
                    case "errors": {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            String error = reader.nextString();
                            response.errors.add(error);
                            Log.v(TAG, "Got error '" + error + "'");
                        }
                        reader.endArray();
                        break;
                    }
                }
            }
            reader.endObject();
            reader.close();

            return response;
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

            final Notification.Builder nbuilder = new Notification.Builder(getApplicationContext());
            nbuilder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(res.getString(R.string.uploader_notification_uploading))
                    .setContentText(uri.toString())
                    .setProgress(100, 0, false)
                    .setOngoing(true);
            startForeground(UPLOAD_PROGRESS_NOTIFICATION, nbuilder.getNotification());

            // For the notification when the upload is done.
            Notification.Builder ncompletebuilder = new Notification.Builder(getApplicationContext());
            ncompletebuilder.setSmallIcon(R.drawable.ic_launcher);

            try {
                // Here, we need to:
                //   a) get the HTTP endpoint / credentials
                //   b) open the image
                //   c) display a progress notification
                //   d) open an HTTP connection
                //   e) stream the image through the connection
                //   e) retrieve the resulting URL
                //   f) display a final notification with the URL

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

                final AssetFileDescriptor desc = getContentResolver().openAssetFileDescriptor(uri, "r");
                final long imageLen = desc.getLength();
                Log.v(TAG, "Image length is " + imageLen);

                String credentials = Uri.encode(prefs.getString(SettingsActivity.KEY_UPLOAD_USERNAME, "")) + ":" +
                                     Uri.encode(prefs.getString(SettingsActivity.KEY_UPLOAD_PASSWORD, ""));
                String authHeader = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);

                HttpEntity entity = MultipartEntityBuilder.create()
                    .addBinaryBody("upload", desc.createInputStream(), ContentType.DEFAULT_BINARY, uri.getLastPathSegment())
                    .build();

                HttpEntity wrapper = new ProgressHttpEntityWrapper(entity, new ProgressListener() {
                    @Override
                    public void progress(long bytes) {
                        int percent = (int)((100 * bytes) / imageLen);
                        if (percent > 100) {
                            percent = 100;
                        }
                        nbuilder.setProgress(100, percent, false);
                        nm.notify(UPLOAD_PROGRESS_NOTIFICATION, nbuilder.getNotification());
                        Log.v(TAG, "Total is " + bytes + " of " + imageLen);
                    }
                });

                HttpClient client = AndroidHttpClient.newInstance(res.getString(R.string.http_user_agent));
                HttpPost post = new HttpPost(prefs.getString(SettingsActivity.KEY_UPLOAD_URL, ""));
                post.setHeader("Authorization", authHeader);
                post.setEntity(wrapper);
                HttpResponse response = client.execute(post);
                Log.e(TAG, "Response: " + response.getStatusLine());
                Log.e(TAG, "Len: " + response.getEntity().getContentLength());

                JsonUploadResponse json = parseJsonResponse(response.getEntity().getContent());

                if ("ok".equals(json.status) && json.url != null) {
                    // Create successful upload notification.
                    Intent intent = new Intent(getApplicationContext(), ClipboardURLReceiver.class);
                    intent.putExtra("url", json.url);
                    PendingIntent pending = PendingIntent.getBroadcast(getApplicationContext(), 0, intent, 0);

                    ncompletebuilder.setContentTitle(res.getString(R.string.uploader_notification_successful))
                                    .setContentText(json.url).setContentIntent(pending);
                    nm.notify(json.url, UPLOAD_COMPLETE_NOTIFICATION, ncompletebuilder.getNotification());
                } else {
                    // Create upload failure notification.
                    ncompletebuilder.setContentTitle(res.getString(R.string.uploader_notification_failure))
                                    .setContentText(uri.toString());
                    nm.notify(uri.toString(), UPLOAD_COMPLETE_NOTIFICATION, ncompletebuilder.getNotification());
                }

                desc.close();
            } catch (Exception e) {
                ncompletebuilder.setContentTitle(res.getString(R.string.uploader_notification_failure))
                                .setContentText(e.getLocalizedMessage());
                nm.notify(uri.toString(), UPLOAD_COMPLETE_NOTIFICATION, ncompletebuilder.getNotification());

                Log.e(TAG, "Something went wrong in the upload!");
                e.printStackTrace();
            }
            Log.i(TAG, "Finished handling of '" + uri + "'/" + startId);

            // This handler is the only place stopSelf() will be called,
            // so we can be sure that all calls will be in order.
            boolean res = stopSelfResult(startId);
            if (res) {
                // Service is stopping, remove ongoing progress notification
                stopForeground(true);
            } else {
                nbuilder.setProgress(100, 0, false)
                        .setContentText("");
                nm.notify(UPLOAD_PROGRESS_NOTIFICATION, nbuilder.getNotification());
            }
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
