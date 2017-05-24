package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Created on 26.01.17.
 */
public class DarkLyricsRetriever extends LyricsRetriever {
    final static String TAG = "DLRetriever";

    final String mBaseURL = "http://www.darklyrics.com";

    public DarkLyricsRetriever(Context ctx, String bandname, String album, String song) {
        super(TAG, ctx, bandname, album, song);
    }

    @Override
    public RetrieveResult retrieveLyrics() {
        // Parse bandpage
        URL url = null;
        RetrieveResult res = new RetrieveResult(this, mBandname, mAlbum, mSong);
        Map<String, Album> albumMap = new HashMap<>();
        try {
            String bandname = URLEncoder.encode(mBandname.replace(" ", ""), "UTF-8").toLowerCase();
            url = new URL(mBaseURL + "/" + bandname.substring(0, 1) + "/" + bandname + ".html");
        } catch (MalformedURLException e) {
            Log.v("DLRetriever", "Malformed url 1" + e.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (url == null) {
            return res;
        }
        try {
            Log.v(TAG, "Retrieving lyrics from: " + url.toString());
            RetrieveHelper.StringResult result = RetrieveHelper.retrieveAsString(url, null, null, mContext);
            if (result.status() != 0) {
                Log.v(TAG, "Error: "+result.status());
                res.setStatus(RetrieveResult.Status.BAND_NOT_FOUND);
                return res;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream stream = new ByteArrayInputStream(result.result().getBytes("UTF-8"));
            InputSource source = new InputSource(stream);
            Document doc = null;
            try {
                doc = builder.parse(source);
            }  catch (SAXException e) {
                Log.v(TAG, "SAXException: "+e.toString());
            }
            if (doc == null) {
                res.setStatus(RetrieveResult.Status.PARSING_ERROR);
                return res;
            }
            XPathFactory xfactory = XPathFactory.newInstance();
            XPath xPath = xfactory.newXPath();
            NodeList albums = null;
            try {
                albums = (NodeList) xPath.evaluate("//div[@class='album']", doc, XPathConstants.NODESET);
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
            if (albums != null) {
                for (int i = 0; i < albums.getLength(); i++) {
                    String albumname;
                    try {
                        albumname = (String) xPath.evaluate("h2/strong/text()", (Element) albums.item(i), XPathConstants.STRING);
                    } catch (XPathExpressionException e) {
                        Log.v(TAG, "XPathExpressionException in albumname");
                        continue;
                    }
                    if ((albumname == null) || (albumname.equals(""))) continue;
                    albumname = albumname.substring(1, albumname.length() - 1);
                    Log.v(TAG, "Album : " + albumname);
                    Album album = new Album();
                    albumMap.put(LyricsRetrieverFrontend.normalizeAlbum(albumname).toLowerCase(), album);
                    NodeList songs = null;
                    try {
                        songs = (NodeList) xPath.evaluate("a", (Element) albums.item(i), XPathConstants.NODESET);
                    } catch (XPathExpressionException e) {
                        Log.v(TAG, "XPathExpressionException in songs");
                        continue;
                    }
                    String songname = "";
                    String link = "";
                    for (int j=0; j < songs.getLength(); j++) {
                        Element song = (Element) songs.item(j);
                        link = song.getAttribute("href");
                        songname = song.getTextContent();
                        Log.v(TAG, "HREF : "+ link + " " + songname);
                        album.Song2Href.put(LyricsRetrieverFrontend.normalizeSongname(songname), link);
                    }
                }
            }
        } catch (IOException e) {
            Log.v(TAG, "IOException:" + e.toString());
        } catch (ParserConfigurationException e) {
            Log.v(TAG, "ParserConfiguration error:" + e.toString());
        }
        String normAlbum = LyricsRetrieverFrontend.normalizeAlbum(mAlbum).toLowerCase();
        if (albumMap.containsKey(normAlbum)) {
            Map<String, String> songMap = albumMap.get(normAlbum).Song2Href;
            if (songMap.containsKey(LyricsRetrieverFrontend.normalizeSongname(mSong))) {
                String normSong = LyricsRetrieverFrontend.normalizeSongname(mSong);
                Log.v(TAG, "Song found and href is " + songMap.get(normSong));

                return getLyricsForSong(res, normSong, songMap.get(LyricsRetrieverFrontend.normalizeSongname(mSong)));
            } else {
                Log.v(TAG, "Song " + mSong + " not found");
            }
        } else {
            Log.v(TAG, "Album "+ mAlbum + " not found");
        }
        return res;
    }

    private RetrieveResult getLyricsForSong(RetrieveResult res, String song, String href) {
        URL url = null;
        try {
            String newurl = href.replace("..", this.mBaseURL);
            url = new URL(newurl);
        } catch (MalformedURLException e) {
            Log.v("DLRetriever", "Malformed url 1" + e.toString());
        }
        try {
            RetrieveHelper.StringResult result = RetrieveHelper.retrieveAsString(url, "<div class=\"lyrics\">", "</div>", mContext);
            if (result.status() != 0) {
                Log.v(TAG, "Error retrieving song: " + result.status());
                res.setStatus(RetrieveResult.Status.SONG_NOT_FOUND);
                return res;
            }
            InputStream is = new ByteArrayInputStream(result.result().getBytes());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));

            String id = href.substring(href.indexOf("#")+1);

            String line;
            boolean inSong = false;
            StringBuilder strings = new StringBuilder();
            while ((line = br.readLine()) != null ) {
                String trimmed = line.trim();
                if (trimmed.startsWith("<h3>")) {
                    if (line.contains("<a")) {
                        String name = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\""));
                        if (name.equals(id)) {
                            inSong = true;
                            continue;
                        } else {
                            if (inSong) {
                                break;
                            }
                        }
                    }
                } else if (line.contains("div class=\"thanks\"")) {
                    if (inSong) {
                        break;
                    }
                } else {
                    if (inSong) {
                        strings.append(line);
                    }
                }
            }
            res.setLyrics(strings.toString());
        } catch (IOException e) {
            Log.v(TAG, "Error IOException: ");
        }
        return res;
    }

    private class Album {
        public Map<String, String> Song2Href = new HashMap<>();
    }

    @Override
    public String getTag() {
        return TAG;
    }
}
