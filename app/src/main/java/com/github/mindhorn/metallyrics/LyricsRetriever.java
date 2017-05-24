package com.github.mindhorn.metallyrics;
import android.content.Context;
import android.util.Log;

/**
 * Created on 20.01.17.
 */
public abstract class LyricsRetriever {
    String mBandname;
    String mAlbum;
    String mSong;
    Context mContext;
    String mRetrieverName;

    public LyricsRetriever(String retrieverName, Context ctx, String bandname, String album, String song) {
        mBandname = bandname;
        mAlbum = album;
        mSong = song;
        mContext = ctx;
        mRetrieverName = retrieverName;
    }

    public boolean isBlacklisted() {
        BandBlacklist blacklist = new BandBlacklist(mContext, mRetrieverName);
        boolean bl = blacklist.isBlacklisted(mBandname);
        if (bl) {
            Log.v(getTag(), mBandname + " is blacklisted");
        }
        blacklist.close();
        return bl;
    }

    protected void addToBlacklist() {
        Log.v(getTag(), "Blacklisting " + mBandname);
        BandBlacklist blacklist = new BandBlacklist(mContext, mRetrieverName);
        blacklist.addToBlacklist(mBandname);
        blacklist.close();
    }

    abstract RetrieveResult retrieveLyrics();

    abstract String getTag();
}
