package com.stackallocated.imageupload;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import android.app.Notification;
import android.app.Notification.Builder;
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
    private AbortableHttpRequest abortableRequest = null;
    private boolean aborted = false;
    private int uploaded = 0;

    final NotificationManager nm;
    final Resources res;
    final PendingIntent historyPending, settingsPending, cancelPending;
    final SharedPreferences prefs;
    final Builder progressNotification;
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

        Intent cancelIntent = new Intent(service, UploadService.class);
        cancelIntent.setAction(UploadService.ACTION_CANCEL);
        cancelPending = PendingIntent.getService(service, 0, cancelIntent, 0);

        progressNotification = makeUploadProgressNotification();
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

        service.startForeground(UPLOAD_PROGRESS_NOTIFICATION, progressNotification.build());

        while (uri != null) {
            uploaded++;

            updateUploadProgressNotification(true);
            nm.notify(UPLOAD_PROGRESS_NOTIFICATION, progressNotification.build());

            boolean status = handleUploadImage(uri, progressNotification);
            Log.d(TAG, "Finished handling of '" + uri + "'");
            if (!status || aborted) {
                break;
            }

            uri = service.pendingUris.poll();
        }
        nm.cancel(UPLOAD_PROGRESS_NOTIFICATION);

        Log.i(TAG, "Done processing URIs");
        service.finish();
    }

    private boolean handleUploadImage(Uri uri, Builder progressNotification) {
        boolean success = false;
        Builder notification = makeUploadFinishedNotification();

        try {
            HttpEntity uploadEntity = makeUploadEntity(uri, progressNotification);
            HttpResponse response = performUploadRequest(uploadEntity);

            Log.v(UploadService.TAG, "Response: " + response.getStatusLine());
            Log.v(UploadService.TAG, "Len: " + response.getEntity().getContentLength());

            success = handleUploadResponse(response, uri, notification);
        } catch (Exception e) {
            makeUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_generic), false);
            if (!aborted) {
                Log.e(UploadService.TAG, "Something went wrong in the upload! " + e.getLocalizedMessage());
                e.printStackTrace();
            }
        }

        // Only show failures if the upload wasn't aborted.
        if (!aborted || success) {
            nm.notify(uri.toString(), UPLOAD_COMPLETE_NOTIFICATION, notification.build());
        }

        return success;
    }

    private HttpEntity makeUploadEntity(Uri uri, final Builder notification) throws IOException {
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
            }
        });
        return wrapper;
    }

    public void abort() {
        aborted = true;
        if (abortableRequest != null) {
            Log.i(TAG, "Aborting request!");
            abortableRequest.abort();
        } else {
            Log.e(TAG, "Request aborted, but no current request?");
        }
    }

    private HttpResponse performUploadRequest(HttpEntity uploadEntity) throws ClientProtocolException, IOException {
        String credentials = Uri.encode(prefs.getString(SettingsActivity.KEY_UPLOAD_USERNAME, "")) + ":" +
                             Uri.encode(prefs.getString(SettingsActivity.KEY_UPLOAD_PASSWORD, ""));
        String authHeader = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);

        AndroidHttpClient client = AndroidHttpClient.newInstance(res.getString(R.string.http_user_agent));
        HttpPost post = new HttpPost(prefs.getString(SettingsActivity.KEY_UPLOAD_URL, ""));
        post.setHeader("Authorization", authHeader);
        post.setEntity(uploadEntity);
        abortableRequest = post;
        if (aborted) {
            Log.i(TAG, "Not executing upload since upload was aborted");
            return null;
        }
        HttpResponse response = client.execute(post);
        // Note that even though the request could have been aborted, we don't bother
        // handling that at all after this point.
        abortableRequest = null;
        client.close();
        
        return response;
    }

    // Returns false if there was an error and further uploads should be aborted.
    private boolean handleUploadResponse(HttpResponse response, Uri uri, Builder notification) throws IllegalStateException, IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == 200 || statusCode == 400) {
            // Success or JSON error.
            // Don't do anything.
        } else if (statusCode == 401) {
            // Unauthorized.
            Log.e(UploadService.TAG, "Unauthorized for URL");
            makeUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_unauthorized), true);
            return false;
        } else if (statusCode >= 500 && statusCode <= 599) {
            // Some kind of weird server error. Probably transient.
            Log.e(UploadService.TAG, "Got 500 response code " + statusCode);
            makeUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_generic), false);
            return false;
        } else {
            if (statusCode == 404) {
                // Wrong URL.
                Log.e(UploadService.TAG, "Got 404 for URL");
            } else {
                // Garbage status code. URL must be wrong.
                Log.e(UploadService.TAG, "Got unknown response code " + statusCode);
            }
            makeUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_wrong_url, statusCode), true);
            return false;
        }

        JsonUploadResponse json = parseJsonResponse(response.getEntity().getContent());

        if ("ok".equals(json.status) && json.url != null) {
            // Build successful upload notification.
            Bitmap original = MediaStore.Images.Media.getBitmap(service.getContentResolver(), uri);
            Uri result = Uri.parse(json.url);
            makeUploadSuccessfulNotification(notification, result.getLastPathSegment(), original);

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
            makeUploadFailureNotification(notification, uri.toString(), res.getString(R.string.uploader_failed_generic), false);
            return false;
        }
    }

    private Builder makeUploadFinishedNotification() {
        final Builder notification = new Builder(service);
        notification.setSmallIcon(R.drawable.ic_launcher)
                    .setContentIntent(historyPending)
                    .setAutoCancel(true);
        return notification;
    }

    private void makeUploadFailureNotification(Builder notification, String tag, String msg, boolean settings) {
        notification.setContentTitle(res.getString(R.string.uploader_notification_failure))
                    .setContentText(msg);
        if (settings) {
            notification.setContentIntent(settingsPending);
        }
    }

    private void makeUploadSuccessfulNotification(Builder notification, String url, Bitmap bitmap) {
        Intent copyintent = new Intent(service, ClipboardURLReceiver.class);
        copyintent.setAction(url);
        PendingIntent copypending = PendingIntent.getBroadcast(service, 0, copyintent, 0);

        Bitmap bigthumbnail = ImageUtils.makeThumbnail(bitmap, 512);

        notification.setContentTitle(res.getString(R.string.uploader_notification_successful))
                    .setContentText(url)
                    .setStyle(new Notification.BigPictureStyle().bigPicture(bigthumbnail))
                    .addAction(R.drawable.ic_action_copy_dark,
                               res.getString(R.string.action_copy_url),
                               copypending);
    }

    // Builds a notification that is a progress bar.
    // This will be replaced with the final error or success notification at the end.
    private Builder makeUploadProgressNotification() {
        final Builder builder = new Builder(service);
        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(res.getString(R.string.uploader_notification_uploading))
                .setOngoing(true)
                .addAction(R.drawable.ic_action_cancel_dark,
                           res.getString(R.string.action_cancel_upload),
                           cancelPending);
        return builder;
    }

    synchronized void updateUploadProgressNotification(boolean reset) {
        if (reset) {
            progressNotification.setProgress(100, 0, false);
        }

        int total = service.pendingUris.size() + uploaded;
        if (total > 1) {
            progressNotification.setContentText(res.getString(R.string.uploader_notification_quantity, uploaded, total));
        }
    }
}