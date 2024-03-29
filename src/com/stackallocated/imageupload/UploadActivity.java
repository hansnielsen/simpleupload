package com.stackallocated.imageupload;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.stackallocated.util.PreferenceUtils;

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

        if (PreferenceUtils.checkForUnsetPreferences(this)) {
            finish();
            return;
        }

        final Resources res = getResources();

        ArrayList<Uri> imageUris = new ArrayList<Uri>();
        final Intent intent = getIntent();
        // We have an image to upload, get list of images.
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            imageUris.add(imageUri);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            try {
                ArrayList<Uri> extraImageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                imageUris.addAll(extraImageUris);
            } catch (Exception e) {
                // Something broke, don't upload any images.
                Log.e(TAG, "Getting multiple images broke! " + e.getLocalizedMessage());
            }
        }

        // Sanitize the list of image URIs by discarding null or unopenable ones.
        sanitizeUris(imageUris);

        // Trigger the upload.
        int images = imageUris.size();
        makeToast(res.getQuantityString(R.plurals.uploader_uploading_toast, images, images));

        // Make the intent with a pile of URIs.
        if (imageUris.size() > 0) {
            Intent i = new Intent(this, UploadService.class);
            i.setAction(UploadService.ACTION_UPLOAD);
            i.putExtra(UploadService.EXTRA_URIS, (Serializable)imageUris);
            startService(i);
        }

        // This activity never needs to show the UI.
        finish();
        return;
    }
}
