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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ArrayList<Uri> imageUris = null;
        final Intent intent = getIntent();
        // We have an image to upload, get list of images.
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Log.i(TAG, "Got send action");

            Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                imageUris = new ArrayList<Uri>(Arrays.asList(imageUri));
            } else {
                makeToast("No image provided to upload!");
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            Log.i(TAG, "Got multiple send action");

            imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        }

        // Sanitize the list of image URIs.
        Iterator<Uri> it = imageUris.iterator();
        while (it.hasNext()) {
            Uri imageUri = it.next();

            if (imageUri == null) {
                Log.d(TAG, "Removed null URI");
                it.remove();
                continue;
            }

            try {
                AssetFileDescriptor desc = getContentResolver().openAssetFileDescriptor(imageUri, "r");
                desc.close();
            } catch (Exception e) {
                Log.d(TAG, "Couldn't open URI '" + imageUri + "'");
                it.remove();
            }
        }

        // Trigger the upload.
        if (imageUris != null && imageUris.size() > 0) {
            if (imageUris.size() > 1) {
                makeToast("Uploading " + imageUris.size() + " images!");
            } else{
                makeToast("Uploading image!");
            }
            for (Uri imageUri : imageUris) {
                Log.i(TAG, "Uploading image '" + imageUri + "'");
            }
        }

        // This activity never needs to show the UI.
        finish();
        return;
    }
}
