package com.stackallocated.imageupload;

import java.util.Map;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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

    public static class SettingsFragment extends PreferenceFragment
                        implements OnSharedPreferenceChangeListener {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            SharedPreferences prefs = getPreferenceScreen().getSharedPreferences();
            Map<String, ?> prefMap = prefs.getAll();
            for (String key : prefMap.keySet()) {
                onSharedPreferenceChanged(prefs, key);
            }
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
                case KEY_UPLOAD_USERNAME:
                    pref.setSummary(prefs.getString(key, "No username set"));
                    break;
                case KEY_UPLOAD_URL:
                    pref.setSummary(prefs.getString(key, "No URL set"));
                    break;
                case KEY_UPLOAD_PASSWORD: {
                    boolean passwordNotSet = "".equals(prefs.getString(key, ""));
                    if (passwordNotSet) {
                        pref.setSummary("No password set");
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
