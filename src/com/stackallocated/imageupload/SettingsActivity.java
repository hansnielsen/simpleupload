package com.stackallocated.imageupload;

import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.widget.EditText;

public class SettingsActivity extends PreferenceActivity {
    public static final String KEY_UPLOAD_URL = "pref_server_upload_url";
    public static final String KEY_UPLOAD_USERNAME = "pref_server_auth_username";
    public static final String KEY_UPLOAD_PASSWORD = "pref_server_auth_password";
    public static final String KEY_ABOUT_VERSION = "pref_about_version";

    public static class SettingsFragment extends PreferenceFragment
                        implements OnSharedPreferenceChangeListener {
        Resources res;

        String getAppVersion() {
            Context context = getActivity();
            try {
                PackageManager pm = context.getPackageManager();
                PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
                return pi.versionName;
            } catch (Exception e) {
                return "unknown";
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            res = getResources();

            SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
            Map<String, ?> prefMap = prefs.getAll();
            for (String key : prefMap.keySet()) {
                onSharedPreferenceChanged(prefs, key);
            }

            Preference version = findPreference(KEY_ABOUT_VERSION);
            version.setSummary(res.getString(R.string.pref_about_version, getAppVersion()));
        }

        @Override
        public void onResume() {
            super.onResume();

            getPreferenceScreen()
                .getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen()
                .getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            Preference pref = findPreference(key);
            switch (key) {
                case KEY_UPLOAD_USERNAME: {
                    String summary = prefs.getString(key, "");
                    if ("".equals(summary)) {
                        summary = res.getString(R.string.pref_upload_username_empty);
                    }
                    pref.setSummary(summary);
                    break;
                }
                case KEY_UPLOAD_URL: {
                    String summary = prefs.getString(key, "");
                    if ("".equals(summary)) {
                        summary = res.getString(R.string.pref_upload_url_empty);
                    }
                    pref.setSummary(summary);
                    break;
                }
                case KEY_UPLOAD_PASSWORD: {
                    boolean passwordNotSet = "".equals(prefs.getString(key, ""));
                    if (passwordNotSet) {
                        pref.setSummary(res.getString(R.string.pref_upload_password_empty));
                    } else {
                        EditText editText = ((EditTextPreference)pref).getEditText();
                        CharSequence masked = editText.getTransformationMethod()
                                                        .getTransformation("********", editText);
                        pref.setSummary(masked);
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getFragmentManager().beginTransaction()
            .replace(android.R.id.content, new SettingsFragment())
            .commit();
    }
}
