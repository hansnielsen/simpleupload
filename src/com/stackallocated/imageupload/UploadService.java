package com.stackallocated.imageupload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;

import com.stackallocated.util.ImageUtils;
import com.stackallocated.util.ProgressHttpEntityWrapper;
import com.stackallocated.util.ProgressListener;

public class UploadService extends Service {
    private final static String TAG = "UploadService";
    private Handler handler;

    private final static int UPLOAD_IMAGE = 1;

    private class JsonUploadResponse {
        public String status = null;
        public String url = null;
        HashMap<String, String> errors = new HashMap<>();
    }

    private class UploadServiceHandler extends Handler {
        final NotificationManager nm;
        final Resources res;
        final PendingIntent historypending;
        final SharedPreferences prefs;
        final static int UPLOAD_PROGRESS_NOTIFICATION = 1;
        final static int UPLOAD_COMPLETE_NOTIFICATION = 2;

        public UploadServiceHandler(Looper looper) {
            super(looper);
            res = getResources();
            nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

            Intent historyintent = new Intent(getApplicationContext(), HistoryActivity.class);
            historypending = PendingIntent.getActivity(getApplicationContext(), 0, historyintent, 0);
        }

        JsonUploadResponse parseJsonResponse(InputStream input) throws IOException {
            JsonUploadResponse response = new JsonUploadResponse();

            InputStreamReader inputreader = new InputStreamReader(input);
            BufferedReader bufreader = new BufferedReader(inputreader);

            JsonReader reader = new JsonReader(bufreader);
            reader.setLenient(true);
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
                    case "errors": {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            reader.beginObject();
                            String error = null, message = null;
                            while (reader.hasNext()) {
                                switch (reader.nextName()) {
                                    case "error":
                                        error = reader.nextString();
                                        break;
                                    case "message":
                                        message = reader.nextString();
                                        break;
                                }
                            }
                            if (error != null && message != null) {
                                Log.v(TAG, "Got error '" + error + "': '" + message + "'");
                                response.errors.put(error, message);
                            }
                            reader.endObject();
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
            switch (msg.what) {
                case UPLOAD_IMAGE: {
                    Uri uri = (Uri)msg.obj;
                    handleUploadImage(uri);
                    Log.i(TAG, "Finished handling of '" + uri + "'");
                    break;
                }
                // If the message type is wrong, panic and kill everything.
                default:
                    Log.e(TAG, "Got message '" + msg.what + "' that wasn't UPLOAD_IMAGE!");
                    stopSelf();
                    return;
            }

            int startId = msg.arg1;
            boolean ret = stopSelfResult(startId);
            if (ret) {
                stopForeground(true);
            }
        }

        private void handleUploadImage(Uri uri) {
            final Notification.Builder notification = makeUploadProgressNotification(uri.toString());
            boolean success = false;

            try {
                HttpEntity uploadEntity = makeUploadEntity(uri, notification);
                HttpResponse response = performUploadRequest(uploadEntity);

                Log.e(TAG, "Response: " + response.getStatusLine());
                Log.e(TAG, "Len: " + response.getEntity().getContentLength());

                success = handleUploadResponse(response, notification, uri);
            } catch (Exception e) {
                showUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_generic));
                Log.e(TAG, "Something went wrong in the upload! " + e.getLocalizedMessage());
                e.printStackTrace();
            }

            // If we encounter an error, bail and cancel all pending uploads.
            if (!success) {
                stopForeground(true);
                stopSelf();
            }
        }

        private HttpEntity makeUploadEntity(Uri uri, final Notification.Builder notification) throws IOException {
            AssetFileDescriptor desc = getContentResolver().openAssetFileDescriptor(uri, "r");
            final long imageLen = desc.getLength();
            Log.v(TAG, "Image length is " + imageLen);

            MultipartEntityBuilder entity = MultipartEntityBuilder.create();
            entity.addBinaryBody("upload",
                                 desc.createInputStream(),
                                 ContentType.DEFAULT_BINARY,
                                 uri.getLastPathSegment());

            HttpEntity wrapper = new ProgressHttpEntityWrapper((HttpEntity)entity.build(), new ProgressListener() {
                @Override
                public void progress(long bytes) {
                    int percent = (int)((100 * bytes) / imageLen);
                    if (percent > 100) {
                        percent = 100;
                    }
                    notification.setProgress(100, percent, false);
                    nm.notify(UPLOAD_PROGRESS_NOTIFICATION, notification.build());
                    Log.v(TAG, "Total is " + bytes + " of " + imageLen);
                }
            });
            return wrapper;
        }

        private HttpResponse performUploadRequest(HttpEntity uploadEntity) throws ClientProtocolException, IOException {
            String credentials = Uri.encode(prefs.getString(SettingsActivity.KEY_UPLOAD_USERNAME, "")) + ":" +
                                 Uri.encode(prefs.getString(SettingsActivity.KEY_UPLOAD_PASSWORD, ""));
            String authHeader = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);

            HttpClient client = AndroidHttpClient.newInstance(res.getString(R.string.http_user_agent));
            HttpPost post = new HttpPost(prefs.getString(SettingsActivity.KEY_UPLOAD_URL, ""));
            post.setHeader("Authorization", authHeader);
            post.setEntity(uploadEntity);
            return client.execute(post);
        }

        // Returns false if there was an error and further uploads should be aborted.
        private boolean handleUploadResponse(HttpResponse response, final Notification.Builder notification, Uri uri) throws IllegalStateException, IOException {
            // XXX should handle 404, 401, and things that aren't either 400 or 200 here before parsing JSON
            int statusCode = response.getStatusLine().getStatusCode();
            switch (statusCode) {
                case 401: // Unauthorized.
                    Log.e(TAG, "Unauthorized for URL");
                    showUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_unauthorized));
                    // Add intent to redirect to settings pane
                    return false;
                case 404: // Wrong URL.
                    Log.e(TAG, "Got 404 for URL");
                    showUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_wrong_url));
                    // Add intent to redirect to settings pane
                    return false;
                default: // Unknown response code.
                    Log.e(TAG, "Got unknown response code " + statusCode);
                    showUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_generic));
                    return false;
                case 200: // Success.
                case 400: // Generic error, handled in JSON.
                    break;
            }

            JsonUploadResponse json = parseJsonResponse(response.getEntity().getContent());

            if ("ok".equals(json.status) && json.url != null) {
                // Create successful upload notification.
                Bitmap original = MediaStore.Images.Media.getBitmap(getApplicationContext().getContentResolver(), uri);
                showUploadSuccessfulNotification(notification, json.url, original);

                // Store successful upload in the database.
                HistoryDatabase db = HistoryDatabase.getInstance(getApplicationContext());
                Bitmap thumbnail = ImageUtils.makeThumbnail(original, 128);
                db.insertImage(json.url, System.currentTimeMillis(), thumbnail);

                return true;
            } else {
                // Create upload failure notification.
                // Should do something better with the errors from the JSON here.
                showUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_generic));
                return false;
            }
        }

        private void showUploadFailureNotification(final Notification.Builder notification, String tag, String msg) {
            stopProgressNotification(notification);
            notification.setContentTitle(res.getString(R.string.uploader_notification_failure))
                        .setContentText(msg);

            nm.notify(tag, UPLOAD_COMPLETE_NOTIFICATION, notification.build());
        }

        private void showUploadSuccessfulNotification(final Notification.Builder notification, String url, Bitmap bitmap) {
            Intent copyintent = new Intent(getApplicationContext(), ClipboardURLReceiver.class);
            copyintent.setAction(url);
            PendingIntent copypending = PendingIntent.getBroadcast(getApplicationContext(), 0, copyintent, 0);

            Bitmap bigthumbnail = ImageUtils.makeThumbnail(bitmap, 512);

            stopProgressNotification(notification);
            notification.setContentTitle(res.getString(R.string.uploader_notification_successful))
                        .setContentText(url)
                        .setContentIntent(historypending)
                        .setAutoCancel(true)
                        .setStyle(new Notification.BigPictureStyle().bigPicture(bigthumbnail))
                        .addAction(R.drawable.ic_action_copy_dark,
                                   res.getString(R.string.action_copy_url),
                                   copypending);

            nm.notify(url, UPLOAD_COMPLETE_NOTIFICATION, notification.build());
        }

        // Builds a notification that is a progress bar.
        // This will be replaced with the final error or success notification at the end.
        private Notification.Builder makeUploadProgressNotification(String string) {
            final Notification.Builder builder = new Notification.Builder(getApplicationContext());
            builder.setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(res.getString(R.string.uploader_notification_uploading))
                    .setContentText(string)
                    .setProgress(100, 0, false)
                    .setOngoing(true);
            startForeground(UPLOAD_PROGRESS_NOTIFICATION, builder.build());
            return builder;
        }

        private void stopProgressNotification(Notification.Builder builder) {
            builder.setOngoing(false)
                   .setProgress(0, 0, false);
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
