package com.stackallocated.imageupload;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class UploadActivity extends Activity {
    private final static String TAG = "UploadActivity";

    void makeToast(String msg) {
        Toast toast = Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    // Removes URIs that are null or not openable.
    void sanitizeUris(ArrayList<Uri> uris) {
        Iterator<Uri> it = uris.iterator();
        while (it.hasNext()) {
            Uri uri = it.next();

            if (uri == null) {
                Log.d(TAG, "Removed null URI");
                it.remove();
                continue;
            }

            try {
                AssetFileDescriptor desc = getContentResolver().openAssetFileDescriptor(uri, "r");
                desc.close();
            } catch (Exception e) {
                Log.d(TAG, "Couldn't open URI '" + uri + "'");
                it.remove();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<Uri> imageUris = null;
        final Intent intent = getIntent();
        // We have an image to upload, get list of images.
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Log.v(TAG, "Got send action");

            Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                imageUris = new ArrayList<Uri>(Arrays.asList(imageUri));
            } else {
                makeToast("No image provided to upload!");
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            Log.v(TAG, "Got multiple send action");

            imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }

        // Sanitize the list of image URIs.
        sanitizeUris(imageUris);

        // Trigger the upload.
        if (imageUris != null && imageUris.size() > 0) {
            if (imageUris.size() > 1) {
                makeToast("Uploading " + imageUris.size() + " images!");
            } else{
                makeToast("Uploading image!");
            }
            for (Uri imageUri : imageUris) {
                Log.d(TAG, "Uploading image '" + imageUri + "'");
            }
        }

        // This activity never needs to show the UI.
        finish();
        return;
    }
}
