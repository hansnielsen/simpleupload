package com.stackallocated.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.stackallocated.imageupload.R;
import com.stackallocated.imageupload.SettingsActivity;

public class PreferenceUtils {
    static void makeToast(Context context, String msg) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
    }
    
    static boolean hasAllPreferences(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (prefs.getString(SettingsActivity.KEY_UPLOAD_USERNAME, "").isEmpty())
            return false;
        if (prefs.getString(SettingsActivity.KEY_UPLOAD_PASSWORD, "").isEmpty())
            return false;
        if (prefs.getString(SettingsActivity.KEY_UPLOAD_URL, "").isEmpty())
            return false;

        return true;
    }

    public static boolean checkForUnsetPreferences(Context context) {
        // Are any of the preferences not set? Redirect to settings if so.
        if (!hasAllPreferences(context)) {
            makeToast(context, context.getResources().getString(R.string.settings_must_be_filled_out));
            Intent intent = new Intent(context, SettingsActivity.class);
            context.startActivity(intent);
            return true;
        }
        return false;
    }
}
