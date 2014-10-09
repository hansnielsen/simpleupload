package com.stackallocated.util;

import android.graphics.Bitmap;
import android.util.Log;

public class ImageUtils {
    private static final String TAG = "ImageUtils";

    public static Bitmap makeThumbnail(Bitmap original, int size) {
        int width = original.getWidth();
        int height = original.getHeight();
        int scaledwidth, scaledheight;
        if (width > height) {
            scaledwidth = size;
            scaledheight = height * size / width;
        } else {
            scaledwidth = width * size / height;
            scaledheight = size;
        }

        Bitmap thumbnail = Bitmap.createScaledBitmap(original, scaledwidth, scaledheight, false);
        Log.v(TAG, "Thumbnail: " + thumbnail.getWidth() + "x" + thumbnail.getHeight());
        return thumbnail;
    }
}