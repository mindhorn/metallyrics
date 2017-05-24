package com.github.mindhorn.metallyrics;

/**
 * Created on 21.01.17.
 */
public class RetrieveResult {
    public enum Status {
        SUCCESS,
        UNDEFINED,
        BAND_NOT_FOUND,
        SONG_NOT_FOUND,
        PARSING_ERROR
    }

    public RetrieveResult(LyricsRetriever source, String artist, String album, String songtitle) {
        mArtist = artist;
        mAlbum = album;
        mSongtitle = songtitle;
        mStatus = Status.UNDEFINED;
        mIsBandBlacklisted = false;
        mSourceRetriever = source;
    }

    public void setLyrics(String lyrics) {
        mLyrics = lyrics;
        mStatus = Status.SUCCESS;
    }

    public String getLyrics() {
        return mLyrics;
    }

    public String getArtist() {
        return mArtist;
    }

    public String getAlbum() {
        return mAlbum;
    }

    public String getSongtitle() {
        return mSongtitle;
    }

    public void setStatus(Status status) {
        mStatus = status;
    }

    public Status status() {
        return mStatus;
    }

    public void setBandBlacklisted() {
        mIsBandBlacklisted = true;
    }

    public boolean isBandBlacklisted() {
        return mIsBandBlacklisted;
    }

    public LyricsRetriever getSource() { return mSourceRetriever; }

    private Status mStatus;
    private String mLyrics;
    private String mArtist;
    private String mAlbum;
    private String mSongtitle;
    private boolean mIsBandBlacklisted;
    private LyricsRetriever mSourceRetriever;
}
