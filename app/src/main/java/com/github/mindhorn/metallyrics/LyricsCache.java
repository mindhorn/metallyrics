package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

import static android.database.DatabaseUtils.sqlEscapeString;

/**
 * Created on 20.01.17.
 */
public class LyricsCache {
    private SQLiteDatabase mDatabase;

    private final String TAG = "LyricsCache";

    public class CachedLyricsResult {
        String mLyrics;
        int mSiteIndex;

        /*
        siteIdx -1 means not in cache
         */
        public CachedLyricsResult(String lyrics, int siteIdx) {
            mLyrics = lyrics;
            mSiteIndex = siteIdx;
        }

        public int getSiteIndex() {
            return mSiteIndex;
        }

        public String getLyrics() {
            return mLyrics;
        }
    }

    public LyricsCache(Context ctx) {
        mDatabase = ctx.openOrCreateDatabase("lyrics", Context.MODE_PRIVATE, null);
        initColumns();
    }

    private void initColumns() {
        mDatabase.execSQL("CREATE TABLE IF NOT EXISTS lyrics(band VARCHAR, album VARCHAR, song VARCHAR, lyrics VARCHAR, siteindex TINYINT(4));");
    }

    public void addOrUpdateEntry(String band, String album, String song, String lyrics, int siteIdx) {
        Cursor res = mDatabase.rawQuery("SELECT EXISTS(SELECT 1 FROM lyrics WHERE " +
                "band=" + sqlEscapeString(band) + " AND " +
                "album=" + sqlEscapeString(album) + " AND " +
                "song=" + sqlEscapeString(song) + ");", null);
        boolean exists = false;
        if (res.moveToFirst()) {
            exists = res.getInt(0) > 0;
        }
        if (exists) {
            Log.v(TAG, "Song already existed");
            mDatabase.delete("lyrics", "band=? AND album=? AND song=?", new String[] {band, album, song});
        }
        mDatabase.execSQL("INSERT INTO lyrics VALUES(" +
                sqlEscapeString(band)   + "," +
                sqlEscapeString(album)  + "," +
                sqlEscapeString(song)   + "," +
                sqlEscapeString(lyrics) + "," +
                String.valueOf(siteIdx) +");");
    }

    public CachedLyricsResult getLyrics(String band, String album, String song) {
        Log.v(TAG, "Checking for cached lyrics for "+band+" "+album +" " + song);
        Cursor res = mDatabase.rawQuery("SELECT lyrics, siteindex FROM lyrics WHERE " +
                "band="  + sqlEscapeString(band)  + " AND " +
                "album=" + sqlEscapeString(album) + " AND " +
                "song="  + sqlEscapeString(song)  + ";", null);
        if (!res.moveToFirst()) {
            Log.v(TAG, "Not in database");
            return new CachedLyricsResult("", -1);
        }
        Log.v(TAG, "Lyrics in cache");
        return new CachedLyricsResult(res.getString(0), res.getInt(1));
    }

    public List<Pair<Integer, Integer>> getNumberOfSongsPerSite() {
        Cursor res = mDatabase.rawQuery("SELECT siteindex, COUNT(*) FROM lyrics GROUP BY siteindex", null);
        List<Pair<Integer, Integer>> result = new ArrayList<>();
        try {
            while (res.moveToNext()) {
                result.add(new Pair<>(res.getInt(0), res.getInt(1)));
            }
        } finally {
            res.close();
        }
        return result;
    }



}
