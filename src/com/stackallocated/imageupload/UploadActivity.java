package com.stackallocated.imageupload;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class UploadActivity extends Activity {
    private final static String TAG = "UploadActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        // We have an image to upload.
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Log.i(TAG, "Got send action");
            Toast toast = Toast.makeText(getApplicationContext(), "Uploading image!", Toast.LENGTH_SHORT);
            toast.show();
        }

        // This activity never needs to show the UI.
        finish();
        return;
    }
}
