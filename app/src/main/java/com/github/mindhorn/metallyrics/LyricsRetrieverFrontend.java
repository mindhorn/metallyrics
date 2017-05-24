package com.github.mindhorn.metallyrics;

import android.os.AsyncTask;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created on 20.01.17.
 */
public class LyricsRetrieverFrontend {
    final String TAG = "LRFrontend";

    private class LyricsReceiverTask extends AsyncTask<LyricsRetriever, Void, RetrieveResult> {
        private LyricsRetrieverFrontend mFrontend;
        private Queue<LyricsRetriever> mRetrievers;

        LyricsReceiverTask(LyricsRetrieverFrontend frontend) {
            mFrontend = frontend;
        }

        @Override
        protected RetrieveResult doInBackground(LyricsRetriever... params) {
            LyricsRetriever lr = params[0];
            if (lr != null) {
                RetrieveResult res = lr.retrieveLyrics();
                if (res.status() == RetrieveResult.Status.BAND_NOT_FOUND) {
                    lr.addToBlacklist();
                }
                return res;
            }
            return new RetrieveResult(null, "", "", "");
        }

        @Override
        protected void onPostExecute(RetrieveResult result) {
            mFrontend.processResult(result);
        }

    }

    private MainActivity mActivity;

    public LyricsRetrieverFrontend(MainActivity mainActivity) {
        mActivity = mainActivity;
    }

    private Queue<LyricsRetriever> mRetrievers = new LinkedBlockingQueue<>();

    public void retrieveLyrics(String band, String album, String songname, boolean refresh) {
        mRetrievers.clear();
        mRetrievers = new LinkedBlockingQueue<>();

        List<LyricsRetriever> rets = new LinkedList<>();
        rets.add(new MetalArchivesLyricsRetriever(mActivity, band, album, songname));
        rets.add(new DarkLyricsRetriever(mActivity, band, album, songname));
        rets.add(new MetroLyricsRetriever(mActivity, band, album, songname));

        for (LyricsRetriever retriever : rets) {
            if (!retriever.isBlacklisted() || refresh) {
                mRetrievers.add(retriever);
            }
        }

        if (mRetrievers.size() > 0) {
            new LyricsReceiverTask(this).execute(mRetrievers.poll());
        } else {
            Log.v(TAG, "Blacklisted in all retrievers. Not updating");
        }
    }

    public static String normalizeSongname(String songname) {
        return songname.toLowerCase();
    }

    public static String normalizeAlbum(String album) {
        int a1 = album.indexOf("(");
        int a2 = album.indexOf("[");
        if ((a1 == -1) && (a2 == -1)) {
            return album;
        }
        if ((a1 != -1) && (a2 != -1)) {
            a1--;
            a2--;
            if ((a1 <= 0) || (a2 <= 0)) {
                return album;
            }
            return album.substring(0, (a1 < a2) ? a1 : a2).trim();
        }
        int first = 0;
        if (a1 == -1) first = a2;
        if (a2 == -1) first = a1;
        if (first <= 0) {
            return album;
        }
        return album.substring(0, first - 1).trim();
    }

    public void processResult(RetrieveResult result) {
        if (result.status() == RetrieveResult.Status.SUCCESS) {
            mActivity.setCurrentLyrics(result);
        } else {
            LyricsRetriever lr = mRetrievers.poll();
            if (lr != null) {
                new LyricsReceiverTask(this).execute(lr);
            } else {
                Log.v(TAG, "No Retriever returned valid result");
                result.setBandBlacklisted();
                mActivity.setCurrentLyrics(result);
            }
        }
    }

    public static int getSiteIndex(LyricsRetriever retriever) {
        if (retriever instanceof MetalArchivesLyricsRetriever) {
            return 0;
        } else if (retriever instanceof DarkLyricsRetriever) {
            return 1;
        } else if (retriever instanceof MetroLyricsRetriever) {
            return 2;
        }
        return -1;
    }

    public static String getSiteNameByIndex(int idx) {
        switch (idx) {
            case 0:
                return "Ultimate Metal";
            case 1:
                return "Dark Lyrics";
            case 2:
                return "Metro Lyrics";
            default:
                return "";
        }
    }
}
