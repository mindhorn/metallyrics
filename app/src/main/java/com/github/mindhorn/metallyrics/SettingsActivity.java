package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Pair;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_PREF_AUTORETRIEVE_COVERS = "pref_autoDownloadCovers";
    public static final String KEY_PREF_RETRIEVE_TO_MEDIA_FOLDER = "pref_autoDownloadCoversToMediaFolder";
    public static final String KEY_PREF_RETRIEVE_COVERS_BUTTON =  "pref_retrieveCovers";
    public static final String KEY_PREF_LYRICS_TOTAL = "pref_lyricsTotal";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);

            final Context ctx = getActivity();
            Preference button = findPreference(KEY_PREF_RETRIEVE_COVERS_BUTTON);
            button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(ctx, RetrieveCoverActivity.class);
                    startActivity(intent);
                    return true;
                }
            });

            LyricsCache lc = new LyricsCache(this.getActivity().getApplication());
            final Preference lyricsTotal = findPreference(KEY_PREF_LYRICS_TOTAL);
            List<Pair<Integer, Integer>> list = lc.getNumberOfSongsPerSite();
            int total = 0;
            //final LinearLayout layout = (LinearLayout) findViewById(R.id.listLayout);
            for (Pair<Integer, Integer> item: list) {
                total += item.second;
                //TextView v = new TextView(this);
                //v.setText("  " + LyricsRetrieverFrontend.getSiteNameByIndex(item.first) +": " + item.second);
                //layout.addView(v);
            }
            lyricsTotal.setSummary(Integer.toString(total));
        }
    }
}
