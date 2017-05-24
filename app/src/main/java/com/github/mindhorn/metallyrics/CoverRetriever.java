package com.github.mindhorn.metallyrics;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * Created on 20.02.17.
 */

public class CoverRetriever {
    final static String TAG = "CoverRetriever";

    private class CoverRetrieverTask extends AsyncTask<CoverRetriever.Album, CoverRetriever.Album, Void> {
        Context mContext;
        MediaScannerConnection mScanner;
        MissingCoverHandler mHandler;

        CoverRetrieverTask(Context ctx, MissingCoverHandler handler) {
            mContext = ctx;
            mScanner = null;
            mHandler = handler;
        }

        @Override
        protected Void doInBackground(CoverRetriever.Album ... params) {
            for (int i=0;i<params.length;i++) {
                CoverRetriever.Album album = params[i];
                if (retrieveCover(album.getArtist(), album.getTitle())) {
                    publishProgress(album);
                }
            }
            return null;
        }

        protected boolean retrieveCover(String artist, String title) {
            String basepath = null, albumid = null;
            String[] retCol = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Albums.ALBUM_ID};
            String query = "trim("+MediaStore.Audio.Media.ARTIST+")=? AND trim(" + MediaStore.Audio.Media.ALBUM+")=?";
            Cursor cur = mContext.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, retCol, query, new String[] { artist, title }, null);
            if (cur.moveToFirst()) {
                basepath = (new File(cur.getString(1))).getParent();
                albumid = cur.getString(2);
            }
            cur.close();
            if (basepath != null) {
                Log.v(TAG, "basepath: " + basepath);
                String lartist = artist.toLowerCase().replace(" ", "+");
                String lalbum = title.toLowerCase().replace(" ", "+");
                try {
                    URL url = new URL("https://www.last.fm/music/"+lartist+"/"+lalbum+"/+images");
                    RetrieveHelper.StringResult doc = RetrieveHelper.retrieveAsString(url, "<ul class=\"image-list\">", "</ul>", mContext);
                    if (doc.status() == 0) {
                        List<String> coverlines = collectCoverLines(doc.result());
                        // Fetch first cover for now
                        if (coverlines.size() > 0) {
                            String filename = basepath + "/cover.jpg";
                            boolean retrieved = retrieveCoverFromURL(coverlines.get(0), filename) ;
                            if (retrieved) {
                                final String thumbpath = Environment.getExternalStorageDirectory() + "/Android/data/com.android.providers.media/albumthumbs";
                                Uri thumbs = Uri.parse("content://media/external/audio/albumart");

                                String thumbfilename = thumbpath + "/" + System.currentTimeMillis();
                                File outputfile = new File(thumbfilename);
                                File inputfile = new File(filename);
                                try {
                                    copyFile(inputfile, outputfile);
                                    ContentValues values = new ContentValues();
                                    values.put("album_id", albumid);
                                    Uri fileuri = Uri.fromFile(outputfile);
                                    values.put("_data", thumbfilename);
                                    Uri num_updates = mContext.getContentResolver().insert(Uri.parse("content://media/external/audio/albumart"), values);
                                    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
                                    boolean retrieveToMedia = sharedPref.getBoolean(SettingsActivity.KEY_PREF_RETRIEVE_TO_MEDIA_FOLDER, false);
                                    if (!retrieveToMedia) {
                                        Log.v(TAG, "Removing cover from media folder");
                                        if (!inputfile.delete()) {
                                            Log.v(TAG, "Could not remove cover from media directory");
                                        }
                                    }
                                    return true;
                                } catch (IOException e) {
                                    Log.v(TAG, "Could not move file to "+ thumbfilename);
                                }
                            }
                        }
                    } else {
                        Log.v(TAG, "Doc Status "+ doc.status());
                    }
                } catch (MalformedURLException e) {
                    Log.v(TAG, "Malformed url");
                } catch (IOException e) {
                    Log.v(TAG, "IOException");
                }
            }
            return false;
        }

        @Override
        protected void onProgressUpdate(Album... progress) {
            if (mHandler != null) {
                mHandler.handleCover(progress[0]);
            }
        }

    }

    public void copyFile(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    protected boolean retrieveCoverFromURL(String url, String filename) {
        try{
            Log.v(TAG, "Writing cover to " + filename);
            File file = new File(filename);
            if (file.exists() && file.length() > 0) {
                Log.v(TAG, "Omitting cover retrieve because " + filename + " already present");
                return false;
            }
            URLConnection uc1 = new URL(url).openConnection();
            OutputStream out = new FileOutputStream(file);

            byte[] buf = new byte[1024];
            int len;
            InputStream in = uc1.getInputStream();
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
            out.close();
            in.close();
        }
        catch( Exception e ) {
            Log.v(TAG, "Exception");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private List<String> collectCoverLines(String src) {
        InputStream is = new ByteArrayInputStream(src.getBytes());
        BufferedReader br = new BufferedReader(new InputStreamReader(is));

        List<String> res = new LinkedList<>();
        String line;
        try {
            while ((line = br.readLine()) != null ) {
                if (line.contains("image-list-link")) {
                    while ((line = br.readLine()) != null ) {
                        if (line.contains("src")) {
                            res.add(line.substring(line.indexOf("=")+2, line.lastIndexOf("\"")));
                        }
                    }
                }
            }
        } catch (IOException e) {
        }
        return res;
    }

    public void retrieveCovers(Context ctx, List<Album> albums, MissingCoverHandler handler) {
        Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
        Uri uri = ContentUris.withAppendedId(sArtworkUri, 28);
        //int deleted = ctx.getContentResolver().delete(uri , null, null);
        new CoverRetrieverTask(ctx, handler).execute(albums.toArray(new Album[albums.size()]));
    }

    public void dump(Context ctx, String bandname) {
        String[] retCol = { MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM_ART};
        //Cursor cur = ctx.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,  retCol, MediaStore.Audio.Albums.ALBUM_ART+" is null", null, null);
        Cursor cur = null;
        if (bandname != null) {
            String[] sArgs = {"%"+bandname+"%"};
            cur = ctx.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, retCol, MediaStore.Audio.Albums.ARTIST+" LIKE ?", sArgs, null);
        } else {
            cur = ctx.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, retCol, null, null, null);
        }
        if ((cur == null) || (cur.getCount() == 0)) {
            return;
        }
        while (cur.moveToNext()) {
            int id = cur.getInt(cur.getColumnIndex(MediaStore.MediaColumns._ID));
            String artist2 = cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ARTIST));
            String album2 = cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ALBUM));
            String art2 = cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART));
            Log.v(TAG, id + " Artist " + artist2 + " Album: " + album2 + " art: " + art2);
        }
        cur.close();
    }

    public void retrieveMissingCoverEntries(Context ctx, MissingCoverHandler handler) {
        String[] retCol = { MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST};
        Cursor cur = ctx.getContentResolver().query(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,  retCol, MediaStore.Audio.Albums.ALBUM_ART+" is null", null, null);
        while (cur.moveToNext()) {
            String artist = cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ARTIST)).trim();
            String album = cur.getString(cur.getColumnIndex(MediaStore.Audio.Albums.ALBUM)).trim();
            handler.handleCover(new Album(artist, album));
        }
        cur.close();
    }

    public void retrieveCover(Context ctx, Album album, MissingCoverHandler handler) {
        new CoverRetrieverTask(ctx, handler).execute(album);
    }

    public interface MissingCoverHandler {
        void handleCover(Album album);
    }

    public static class Album {
        String mArtist;
        String mTitle;


        public Album(String artist, String title) {
            mArtist = artist;
            mTitle = title;
        }

        public String getArtist() {
            return mArtist;
        }

        public String getTitle() {
            return mTitle;
        }


    }
}
