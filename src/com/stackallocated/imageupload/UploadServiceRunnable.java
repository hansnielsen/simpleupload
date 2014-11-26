package com.stackallocated.imageupload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;

import com.stackallocated.util.ImageUtils;
import com.stackallocated.util.ProgressHttpEntityWrapper;
import com.stackallocated.util.ProgressListener;

class UploadServiceRunnable implements Runnable {
    private final UploadService service;

    final NotificationManager nm;
    final Resources res;
    final PendingIntent historyPending, settingsPending;
    final SharedPreferences prefs;
    final static int UPLOAD_PROGRESS_NOTIFICATION = 1;
    final static int UPLOAD_COMPLETE_NOTIFICATION = 2;
    final static String TAG = "UploadServiceRunnable";

    private class JsonUploadResponse {
        public String status = null;
        public String url = null;
        HashMap<String, String> errors = new HashMap<>();
    }

    public UploadServiceRunnable(UploadService uploadService) {
        this.service = uploadService;

        res = service.getResources();
        nm = (NotificationManager)service.getSystemService(UploadService.NOTIFICATION_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(service);

        Intent historyIntent = new Intent(service, HistoryActivity.class);
        historyPending = PendingIntent.getActivity(service, 0, historyIntent, 0);

        Intent settingsIntent = new Intent(service, SettingsActivity.class);
        settingsPending = PendingIntent.getActivity(service, 0, settingsIntent, 0);
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
                    Log.v(UploadService.TAG, "Got status " + response.status);
                    break;
                }
                case "public_url": {
                    response.url = reader.nextString();
                    Log.v(UploadService.TAG, "Got URL " + response.url);
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
                            Log.v(UploadService.TAG, "Got error '" + error + "': '" + message + "'");
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

    public void run() {
        // Block on the first URI.
        Uri uri = null;
        try {
            uri = service.pendingUris.take();
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for a URI, bailing");
        }
        while (uri != null) {
            handleUploadImage(uri);
            Log.i(TAG, "Finished handling of '" + uri + "'");
            uri = service.pendingUris.poll();
        }
        Log.i(TAG, "Done processing URIs");
        service.finish();
    }

    private void handleUploadImage(Uri uri) {
        final Notification.Builder notification = makeUploadProgressNotification(uri.toString());
        boolean success = false;

        try {
            HttpEntity uploadEntity = makeUploadEntity(uri, notification);
            HttpResponse response = performUploadRequest(uploadEntity);

            Log.e(UploadService.TAG, "Response: " + response.getStatusLine());
            Log.e(UploadService.TAG, "Len: " + response.getEntity().getContentLength());

            nm.cancel(UPLOAD_PROGRESS_NOTIFICATION);

            success = handleUploadResponse(response, uri);
        } catch (Exception e) {
            makeUploadFailureNotification(uri.toString(), res.getString(R.string.uploader_failed_generic), false);
            Log.e(UploadService.TAG, "Something went wrong in the upload! " + e.getLocalizedMessage());
            e.printStackTrace();
        }

        // If we encounter an error, bail and cancel all pending uploads.
        if (!success) {
            Log.v(UploadService.TAG, "Killing service due to error");
            service.finish();
        }
    }

    private HttpEntity makeUploadEntity(Uri uri, final Notification.Builder notification) throws IOException {
        AssetFileDescriptor desc = service.getContentResolver().openAssetFileDescriptor(uri, "r");
        final long imageLen = desc.getLength();
        Log.v(UploadService.TAG, "Image length is " + imageLen);

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
                Log.v(UploadService.TAG, "Total is " + bytes + " of " + imageLen);
            }
        });
        return wrapper;
    }

    private HttpResponse performUploadRequest(HttpEntity uploadEntity) throws ClientProtocolException, IOException {
        String credentials = Uri.encode(prefs.getString(SettingsActivity.KEY_UPLOAD_USERNAME, "")) + ":" +
                             Uri.encode(prefs.getString(SettingsActivity.KEY_UPLOAD_PASSWORD, ""));
        String authHeader = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);

        AndroidHttpClient client = AndroidHttpClient.newInstance(res.getString(R.string.http_user_agent));
        HttpPost post = new HttpPost(prefs.getString(SettingsActivity.KEY_UPLOAD_URL, ""));
        post.setHeader("Authorization", authHeader);
        post.setEntity(uploadEntity);
        HttpResponse response = client.execute(post);
        client.close();
        
        return response;
    }

    // Returns false if there was an error and further uploads should be aborted.
    private boolean handleUploadResponse(HttpResponse response, Uri uri) throws IllegalStateException, IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        switch (statusCode) {
            case 401: // Unauthorized.
                Log.e(UploadService.TAG, "Unauthorized for URL");
                makeUploadFailureNotification(uri.toString(), res.getString(R.string.uploader_failed_unauthorized), true);
                return false;
            case 404: // Wrong URL.
                Log.e(UploadService.TAG, "Got 404 for URL");
                makeUploadFailureNotification(uri.toString(), res.getString(R.string.uploader_failed_wrong_url), true);
                return false;
            default: // Unknown response code.
                Log.e(UploadService.TAG, "Got unknown response code " + statusCode);
                makeUploadFailureNotification(uri.toString(), res.getString(R.string.uploader_failed_generic), false);
                return false;
            case 200: // Success.
            case 400: // Generic error, handled in JSON.
                break;
        }

        JsonUploadResponse json = parseJsonResponse(response.getEntity().getContent());

        if ("ok".equals(json.status) && json.url != null) {
            // Create successful upload notification.
            Bitmap original = MediaStore.Images.Media.getBitmap(service.getContentResolver(), uri);
            makeUploadSuccessfulNotification(json.url, original);

            // Store successful upload in the database.
            HistoryDatabase db = HistoryDatabase.getInstance(service);
            Bitmap thumbnail = ImageUtils.makeThumbnail(original, 128);
            db.insertImage(json.url, System.currentTimeMillis(), thumbnail);

            // Update the history activity if it's visible.
            Intent updateHistory = new Intent(HistoryActivity.UPDATE_HISTORY);
            LocalBroadcastManager.getInstance(service).sendBroadcast(updateHistory);

            return true;
        } else {
            // Create upload failure notification.
            // Should do something better with the errors from the JSON here.
            makeUploadFailureNotification(uri.toString(), res.getString(R.string.uploader_failed_generic), false);
            return false;
        }
    }

    private void makeUploadFailureNotification(String tag, String msg, boolean settings) {
        final Notification.Builder notification = new Notification.Builder(service);
        notification.setContentTitle(res.getString(R.string.uploader_notification_failure))
                    .setContentText(msg)
                    .setSmallIcon(R.drawable.ic_launcher);
        if (settings) {
            notification.setContentIntent(settingsPending);
        }

        nm.notify(tag, UPLOAD_COMPLETE_NOTIFICATION, notification.build());
    }

    private void makeUploadSuccessfulNotification(String url, Bitmap bitmap) {
        Intent copyintent = new Intent(service, ClipboardURLReceiver.class);
        copyintent.setAction(url);
        PendingIntent copypending = PendingIntent.getBroadcast(service, 0, copyintent, 0);

        Bitmap bigthumbnail = ImageUtils.makeThumbnail(bitmap, 512);

        final Notification.Builder notification = new Notification.Builder(service);
        notification.setContentTitle(res.getString(R.string.uploader_notification_successful))
                    .setContentText(url)
                    .setContentIntent(historyPending)
                    .setSmallIcon(R.drawable.ic_launcher)
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
        final Notification.Builder builder = new Notification.Builder(service);
        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(res.getString(R.string.uploader_notification_uploading))
                .setContentText(string)
                .setProgress(100, 0, false)
                .setOngoing(true);
        service.startForeground(UPLOAD_PROGRESS_NOTIFICATION, builder.build());
        return builder;
    }
}