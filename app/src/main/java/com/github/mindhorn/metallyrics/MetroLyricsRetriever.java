package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.Normalizer;

/**
 * Created on 11.02.17.
 */

public class MetroLyricsRetriever extends LyricsRetriever {
    final static String TAG = "MLRetriever";

    final String mBaseURL = "http://www.metrolyrics.com/";

    public MetroLyricsRetriever(Context ctx, String bandname, String album, String song) {
        super(TAG, ctx, bandname, album, song);
    }

    @Override
    public RetrieveResult retrieveLyrics() {
        URL url = null;
        RetrieveResult res = new RetrieveResult(this, mBandname, mAlbum, mSong);
        try {
            String bandname = URLEncoder.encode(mBandname.replace(" ", "-").toLowerCase(), "UTF-8");
            String songname = URLEncoder.encode(Normalizer.normalize(mSong, Normalizer.Form.NFKD).replace(" ", "-").toLowerCase(), "UTF-8");
            url = new URL(mBaseURL + songname + "-lyrics-" + bandname + ".html");
        } catch (MalformedURLException e) {
            Log.v(TAG, "Malformed url 1" + e.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        try {
            Log.v(TAG, "Retrieving lyrics from: " + url.toString());
            RetrieveHelper.StringResult result =
                    RetrieveHelper.retrieveAsString(url, "<div id=\"lyrics-body-text\"", "</div>", mContext);
            if (result.status() == 0) {
                Log.v(TAG, "rows: " + result.result());
                res.setLyrics(result.result());
            } else {
                Log.v(TAG, "Error retrieving document");
            }
        } catch (IOException e) {

        }
        return res;
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
