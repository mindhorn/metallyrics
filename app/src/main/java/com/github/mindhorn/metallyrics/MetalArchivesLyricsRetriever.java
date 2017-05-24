package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.text.TextUtils;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
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
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class MetalArchivesLyricsRetriever extends LyricsRetriever {
    final static String TAG = "MARetriever";
    
    final String mBaseURL = "http://www.metal-archives.com/";

    public MetalArchivesLyricsRetriever(Context ctx, String bandname, String album, String song) {
        super(TAG, ctx, bandname, album, song);
    }

    @Override
    public RetrieveResult retrieveLyrics() {
        URL url = null;
        RetrieveResult res = new RetrieveResult(this, mBandname, mAlbum, mSong);
        try {
            String bandname = URLEncoder.encode(mBandname.replace(" ", "_"), "UTF-8");
            String album = URLEncoder.encode(mAlbum.replace(" ", "_"), "UTF-8");
            url = new URL(mBaseURL + "albums/" + bandname + "/" + album);
        } catch (MalformedURLException e) {
            Log.v(TAG, "Malformed url 1" + e.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        try {
            Log.v(TAG, "Retrieving lyrics from: "+ url.toString());
            RetrieveHelper.StringResult result = RetrieveHelper.retrieveAsString(url, "<table class=\"display table_lyrics\"", "</table>", mContext);
            Log.v(TAG, "Result:"+result.mResult.length());
            if ((result.status() == 404) || (result.mResult.length() == 0)) {
                // Second chance -> try the search function
                String search =  "/search/ajax-advanced/searching/albums/"
                        + "?bandName=" + URLEncoder.encode(mBandname, "UTF-8") + "&"
                        + "releaseTitle=" + URLEncoder.encode(mAlbum, "UTF-8");
                url = new URL(mBaseURL + search);
                Log.v(TAG, "Second chance: "+ url.toString());
                result = RetrieveHelper.retrieveAsString(url, null, null, mContext);
                String href = null;
                if (result.status() == 0) {
                    JsonReader reader = new JsonReader(new StringReader(result.mResult));
                    reader.beginObject();
                    int records = 0;

                    while (reader.hasNext()) {
                        JsonToken t = reader.peek();
                        String name = reader.nextName();
                        Log.v(TAG, "Token:"+ t.toString() + " Name: "+name);
                        if (name.equals("iTotalRecords")) {
                            records = reader.nextInt();
                        } else if (name.equals("aaData")) {
                            if (records >= 1) {
                                reader.beginArray();
                                // 1st item
                                reader.beginArray();
                                reader.skipValue();
                                href = reader.nextString();
                                int sidx = href.indexOf("\"") + 1;
                                int eidx = href.indexOf("\"", sidx);
                                href = href.substring(sidx, eidx);
                                reader.skipValue();
                                reader.endArray();
                                for (int i = 1; i < records; i++) {
                                    reader.beginArray();
                                    reader.skipValue();
                                    reader.skipValue();
                                    reader.skipValue();
                                    reader.endArray();
                                }
                                reader.endArray();
                                break;
                            } else {
                                reader.beginArray();
                                reader.endArray();
                            }
                        } else {
                            reader.skipValue();
                        }
                    }
                    //reader.endObject();
                }
                if (href != null) {
                    url = new URL(href);
                    Log.v(TAG, "Retrieving second chance lyrics from: "+ url.toString());
                    result = RetrieveHelper.retrieveAsString(url, "<table class=\"display table_lyrics\"", "</table>", mContext);
                    if (result.status() != 0) {
                        res.setStatus(RetrieveResult.Status.BAND_NOT_FOUND);
                        return res;
                    }
                } else {
                    res.setStatus(RetrieveResult.Status.BAND_NOT_FOUND);
                    return res;
                }
            }
            if (result.status() != 0) {
                res.setStatus(RetrieveResult.Status.UNDEFINED);
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
            XPathExpression expr = xPath.compile("//table[@class='display table_lyrics']/tbody/tr");
            NodeList trs = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            Map<String, String> songname2id = new HashMap<String, String>();
            Log.v(TAG, "rows: " + trs.getLength());
            for (int i=0;i<trs.getLength();i++) {
                Element tr = (Element) trs.item(i);
                NodeList tds = (NodeList) xPath.evaluate("td", tr, XPathConstants.NODESET);
                //Log.v(TAG, "rows: " + tds.getLength());
                String songname = "";
                String id = "";
                if (tds.getLength() == 4) {
                    songname = tds.item(1).getTextContent().trim();
                    Log.v(TAG, "Songname: "+songname);
                    try {
                        id = (String) xPath.evaluate("a/@href", (Element) tds.item(3), XPathConstants.STRING);
                        id = id.substring(1);
                        if (!TextUtils.isDigitsOnly(id)) {
                            id = id.substring(0, id.length() - 1);
                        }
                    } catch (Exception e) {
                        try {
                            // Check if instrumental
                            id = (String) xPath.evaluate("em/text()", (Element) tds.item(3), XPathConstants.STRING);
                        } catch (Exception e2) {
                        }
                        // continue
                    }
                    Log.v(TAG, "ID: "+ id);
                    if ((songname.length() > 0) && (id.length() > 0)) {
                        songname2id.put(LyricsRetrieverFrontend.normalizeSongname(songname), id);
                    }
                }
            }
            String search = LyricsRetrieverFrontend.normalizeSongname(mSong);
            Log.v(TAG, "Searching for songname: "+search);
            String id = "";
            // Try to find song
            // First simple case: exact match
            if (songname2id.containsKey(search)) {
                id = songname2id.get(search);
            } else {
                for (Map.Entry<String, String> entry : songname2id.entrySet()) {
                    int dist = levenshteinDistance(entry.getKey(), search);
                    Log.v(TAG, "Comparing " + entry.getKey() + " with " + search + " dist is "+ dist);
                    if (dist < 3) {
                        Log.v(TAG, "Found songname "+ entry.getKey() + " with levenshtein distance of " + dist);
                        id = entry.getValue();
                        break;
                    }
                }
            }
            if (!id.equals("")) {
                if (!TextUtils.isDigitsOnly(id)) {
                    // Instrumental track
                    res.setLyrics(id);
                } else {
                    url = new URL(mBaseURL + "release/ajax-view-lyrics/id/" + id);
                    RetrieveHelper.StringResult lyrics = RetrieveHelper.retrieveAsString(url, null, null, mContext);
                    if (lyrics.status() != 0) {
                        res.setStatus(RetrieveResult.Status.SONG_NOT_FOUND);
                    } else {
                        res.setLyrics(lyrics.result());
                    }
                }
                return res;
            } else {
                res.setStatus(RetrieveResult.Status.SONG_NOT_FOUND);
                return res;
            }
        } catch (MalformedURLException e) {
            Log.v(TAG, "Malformed url " + e.toString());
        } catch (XPathExpressionException e) {
            Log.v(TAG ,"XPathException");
        } catch (ParserConfigurationException e) {
        } catch (IOException e) {
            Log.v(TAG, "IOException when parsing: "+e.toString());
        }
        return res;
    }

    public boolean isBlacklisted(String bandname) {
        return false;
    }

    @Override
    public String getTag() {
        return TAG;
    }

    public static int levenshteinDistance(String s0, String s1) {

        int len0 = s0.length() + 1;
        int len1 = s1.length() + 1;
        // the array of distances
        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        // initial cost of skipping prefix in String s0
        for (int i = 0; i < len0; i++)
            cost[i] = i;

        // dynamically computing the array of distances

        // transformation cost for each letter in s1
        for (int j = 1; j < len1; j++) {

            // initial cost of skipping prefix in String s1
            newcost[0] = j - 1;

            // transformation cost for each letter in s0
            for (int i = 1; i < len0; i++) {

                // matching current letters in both strings
                int match = (s0.charAt(i - 1) == s1.charAt(j - 1)) ? 0 : 1;

                // computing cost for each transformation
                int cost_replace = cost[i - 1] + match;
                int cost_insert = cost[i] + 1;
                int cost_delete = newcost[i - 1] + 1;

                // keep minimum cost
                newcost[i] = Math.min(Math.min(cost_insert, cost_delete),
                        cost_replace);
            }

            // swap cost/newcost arrays
            int[] swap = cost;
            cost = newcost;
            newcost = swap;
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1];
    }
}
