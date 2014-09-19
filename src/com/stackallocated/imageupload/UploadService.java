package com.stackallocated.imageupload;

import java.io.BufferedReader;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import android.app.Service;
import android.content.Intent;
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
import android.util.Base64;
import android.util.JsonReader;
import android.util.Log;

public class UploadService extends Service {
    private final static String TAG = "UploadService";
    private Handler handler;

    private final static int UPLOAD_IMAGE = 1;

    public static interface ProgressListener {
        void progress(long bytes);
    }

    class CountedOutputStream extends FilterOutputStream {
        long bytes = 0;
        ProgressListener listener;

        public CountedOutputStream(OutputStream out, ProgressListener listener) {
            super(out);
            this.listener = listener;
        }

        @Override
        public void write(int b) throws IOException {
            byte[] arr = new byte[]{(byte) b};
            write(arr, 0, arr.length);
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            bytes += len;
            listener.progress(bytes);
        }
    }

    class ProgressHttpEntityWrapper extends HttpEntityWrapper {
        ProgressListener listener;

        public ProgressHttpEntityWrapper(final HttpEntity entity, final ProgressListener listener) {
            super(entity);
            this.listener = listener;
        }

        public void writeTo(OutputStream output) throws IOException {
            OutputStream wrapper = output;
            if (output.getClass() != CountedOutputStream.class && this.listener != null) {
                wrapper = new CountedOutputStream(output, listener);
            }
            this.wrappedEntity.writeTo(wrapper);
        }
    }

    private class UploadServiceHandler extends Handler {
        public UploadServiceHandler(Looper looper) {
            super(looper);
        }

        void parseJsonResponse(InputStream input) throws IOException {
            InputStreamReader inputreader = new InputStreamReader(input);
            BufferedReader bufreader = new BufferedReader(inputreader);
            JsonReader reader = new JsonReader(bufreader);

            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                switch (field) {
                    case "status": {
                        String status = reader.nextString();
                        Log.v(TAG, "Got status " + status);
                        break;
                    }
                    case "public_url": {
                        String url = reader.nextString();
                        Log.v(TAG, "Got URL " + url);
                        break;
                    }
                    case "errors": {
                        reader.beginArray();
                        while (reader.hasNext()) {
                            String error = reader.nextString();
                            Log.v(TAG, "Got error '" + error + "'");
                        }
                        reader.endArray();
                        break;
                    }
                }
            }
            reader.endObject();

            reader.close();
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

            try {
                // Here, we need to:
                //   a) get the HTTP endpoint / credentials
                //   b) open the image
                //   c) display a progress notification
                //   d) open an HTTP connection
                //   e) stream the image through the connection
                //   e) retrieve the resulting URL
                //   f) display a final notification with the URL

                AssetFileDescriptor desc = getContentResolver().openAssetFileDescriptor(uri, "r");
                Log.v(TAG, "Image length is " + desc.getLength());

                Resources res = getResources();

                String credentials = res.getString(R.string.server_auth_username) + ":" +
                                     res.getString(R.string.server_auth_password);
                String authHeader = "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);

                HttpEntity entity = MultipartEntityBuilder.create()
                    .addBinaryBody("upload", desc.createInputStream(), ContentType.DEFAULT_BINARY, uri.getLastPathSegment())
                    .build();

                HttpEntity wrapper = new ProgressHttpEntityWrapper(entity, new ProgressListener() {
                    @Override
                    public void progress(long bytes) {
                        Log.v(TAG, "Total is " + bytes);
                    }
                });

                HttpClient client = AndroidHttpClient.newInstance(res.getString(R.string.http_user_agent));
                HttpPost post = new HttpPost(res.getString(R.string.server_upload_address));
                post.setHeader("Authorization", authHeader);
                post.setEntity(wrapper);
                HttpResponse response = client.execute(post);
                Log.e(TAG, "Response: " + response.getStatusLine());
                Log.e(TAG, "Len: " + response.getEntity().getContentLength());

                parseJsonResponse(response.getEntity().getContent());

                desc.close();
            } catch (Exception e) {
                Log.e(TAG, "Something went wrong in the upload!");
                e.printStackTrace();
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
