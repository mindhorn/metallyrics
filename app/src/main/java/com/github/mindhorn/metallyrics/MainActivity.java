package com.github.mindhorn.metallyrics;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    String mCurrent = new String();

    String mCurrentArtist = null;
    String mCurrentAlbum = null;
    String mCurrentTrack = null;

    boolean mIsVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter iF = new IntentFilter();
        iF.addAction("com.android.music.metachanged");
        //iF.addAction("com.android.music.playstatechanged");
        //iF.addAction("com.android.music.playbackcomplete");
        //iF.addAction("com.android.music.queuechanged");

        mLyricsCache = new LyricsCache(this.getApplication());
        mFrontend = new LyricsRetrieverFrontend(this);

        registerReceiver(mReceiver, iF);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);
        PreferenceManager.setDefaultValues(this, R.xml.settings, false);

        setLyrics("");
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsVisible = true;
        updateUI();
    }

    @Override
    public void onPause() {
        super.onPause();
        mIsVisible = false;
    }

    private void updateUI() {
        String artist = mCurrentArtist;
        String album = mCurrentAlbum;
        String track = mCurrentTrack;
        final WebView wv = (WebView) findViewById(R.id.webView);
        if ((artist == null) || (album == null) || (track == null)) {
            wv.loadData("", "text/html; charset=utf-8", "UTF-8");
            return;
        }
        String newSong = artist + ":" + album + ":" + track;
        if (newSong.equals(mCurrent)) {
            return;
        }
        mCurrent = newSong;
        setDetails(artist, album, track);
        // Rule out some obvious non scrapeable artists
        if (artist.toLowerCase().equals("unknown artist")) {
            return;
        }
        LyricsCache.CachedLyricsResult lyrics = mLyricsCache.getLyrics(artist, album, track);
        if (lyrics.getSiteIndex() != -1) {
            Log.v("LyricsCache", "Setting lyrics");
            setLyrics(lyrics.getLyrics());
        } else {
            if (isNetworkAvailable()) {
                setLyrics("");
                setLyricsPending(true);
                mFrontend.retrieveLyrics(artist, album, track, false);
            } else {
                setLyrics(getString(R.string.network_unavailable));
            }
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            String artist = intent.getStringExtra("artist");
            String album = intent.getStringExtra("album");
            String track = intent.getStringExtra("track");

            if (artist != null) artist = artist.trim();
            if (album  != null) album  = LyricsRetrieverFrontend.normalizeAlbum(album);
            if (track  != null) track  = track.trim();

            mCurrentArtist = artist;
            mCurrentAlbum = album;
            mCurrentTrack = track;
            if (mIsVisible) {
                updateUI();
            }
        }
    };

    public void setLyrics(String lyrics) {
        final WebView wv = (WebView) findViewById(R.id.webView);
        wv.setBackgroundColor(Color.BLACK);
        if (lyrics == null) {
            lyrics = getString(R.string.lyrics_not_found);
        }
        wv.loadDataWithBaseURL(null, "<body style=\"color: #fff\">" + lyrics + "</body>", "text/html", "utf-8", null);
    }

    public void retrieveCover(String artist, String title) {
        CoverRetriever rt = new CoverRetriever();
        rt.retrieveCover(this, new CoverRetriever.Album(artist, title), new CoverRetriever.MissingCoverHandler() {
            @Override
            public void handleCover(CoverRetriever.Album album) {

            }
        });
    }

    public void setCurrentLyrics(RetrieveResult res) {
        setLyrics(res.getLyrics());
        setDetails(res.getArtist(), res.getAlbum(), res.getSongtitle());
        setLyricsPending(false);
        if (res.status() == RetrieveResult.Status.SUCCESS) {
            int siteIdx = mFrontend.getSiteIndex(res.getSource());
            mLyricsCache.addOrUpdateEntry(res.getArtist(), res.getAlbum(), res.getSongtitle(), res.getLyrics(), siteIdx);
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean retrieveCovers = sharedPref.getBoolean(SettingsActivity.KEY_PREF_AUTORETRIEVE_COVERS, false);
        if (retrieveCovers) {
            if (isNetworkAvailable()) {
                retrieveCover(res.getArtist(), res.getAlbum());
            } else {
                Log.v("CoverRetriever", "No network connection available");
            }
        } else {
            Log.v("CoverRetriever", "Cover retrieval disabled");
        }
    }

    public void setDetails(String artist, String album, String song) {
        final TextView artistname = (TextView) findViewById(R.id.artistname);
        artistname.setText(artist + " - " + song);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager conMan = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = conMan.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private ColorStateList TextColors = null;

    public void setLyricsPending(boolean pending) {
        final TextView artistname = (TextView) findViewById(R.id.artistname);
        if (TextColors == null) TextColors = artistname.getTextColors();
        if (pending) {
            artistname.setTextColor(Color.RED);
        } else {
            artistname.setTextColor(TextColors);
        }
    }

    private LyricsCache mLyricsCache = null;
    private LyricsRetrieverFrontend mFrontend = null;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_refresh:
                if ((mCurrentArtist != null) && (mCurrentAlbum != null) && (mCurrentTrack != null)) {
                    setLyricsPending(true);
                    mFrontend.retrieveLyrics(mCurrentArtist, mCurrentAlbum, mCurrentTrack, true);
                    return true;
                }
            case R.id.action_settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }





    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }
}
