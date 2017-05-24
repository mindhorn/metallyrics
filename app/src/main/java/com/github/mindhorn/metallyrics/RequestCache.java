package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * Created on 21.01.17.
 */
public class RequestCache {
    Context mContext;
    URL mUrl;
    boolean mIsFile;
    HttpURLConnection mConnection = null;

    private String mFilePrefix = "rq_";

    RequestCache(URL url, Context ctx) {
        mContext = ctx;
        mUrl = url;
        clearCache();
    }

    public InputStream getInputStream() throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(mUrl.toString().getBytes("UTF-8"), 0, mUrl.toString().length());
            String filename = mFilePrefix+new BigInteger(1, md.digest()).toString(16);
            File cacheFile = new File(mContext.getFilesDir(), filename);
            if (cacheFile.exists() && (cacheFile.length() > 0)) {
                Log.v("RequestCache", "Using cache file " + filename + " " + mContext.getFilesDir() + " size: " + cacheFile.length());
                return new FileInputStream(cacheFile);
            } else {
                Log.v("RequestCache", "No cache file found " + filename + " " + mContext.getFilesDir());
                mConnection = (HttpURLConnection) mUrl.openConnection();
                mConnection.setRequestProperty("User-Agent","Mozilla/5.0");
                return new CachedHttpURLConnectionStream(mConnection, cacheFile);
            }
        } catch (NoSuchAlgorithmException e) {
        } catch (FileNotFoundException e) {
            // Unreachable
        } catch (UnsupportedEncodingException e) {
            // Unreachable
        }
        return null;
    }

    public void disconnect() {
    }

    public HttpURLConnection getConnection() {
        return mConnection;
    }


    public void clearCache() {
        File basedir = mContext.getFilesDir();
        if (basedir.isDirectory()) {
            long cdate = new Date().getTime();
            File[] files = basedir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return (name.startsWith(mFilePrefix));
                }
            });
            for (int i=0;i<files.length;i++) {
                File file = files[i];
                if (file.isFile()) {
                    long diffhours = (cdate - file.lastModified()) / 1000 / 60 / 60;
                    //Log.v("RequestCache", "File " + file.getName() + " is " + diffhours);
                    if (diffhours > 12) {
                        file.delete();
                    }
                }
            }
        }
    }

    private class CachedHttpURLConnectionStream extends InputStream {
        HttpURLConnection mConnection;
        InputStream mInputStream;
        FileOutputStream mCacheFileStream;

        CachedHttpURLConnectionStream(HttpURLConnection connection, File cacheFile) throws IOException {
            mConnection  = connection;
            mInputStream = mConnection.getInputStream();
            mCacheFileStream = new FileOutputStream(cacheFile);
        }

        public HttpURLConnection getConnection() {
            return mConnection;
        }

        @Override
        public int read() throws IOException {
            int data = mInputStream.read();
            if (data == -1) {
                Log.v("RequestCache", "Closing file.");
                mCacheFileStream.close();
            } else {
                mCacheFileStream.write(data);
            }
            return data;
        }
    }
}
