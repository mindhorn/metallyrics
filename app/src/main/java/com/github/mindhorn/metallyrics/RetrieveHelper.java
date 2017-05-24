package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Created on 24.05.17.
 */

public class RetrieveHelper {

    public static final String TAG = "RetrieveHelper";

    public static DocumentResult retrieveAsDocument(URL url, String begin, String end, Context ctx) {
        try {
            StringResult sr = retrieveAsString(url, begin, end, ctx);
            Log.v(TAG, "Result : " + sr.status());
            if (sr.status() != 0) {
                return new DocumentResult(null, sr.status());
            }
            Log.v(TAG, "Result : " + +sr.result().length() + " : " + sr.result());
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream stream = new ByteArrayInputStream(sr.result().getBytes("UTF-8"));
            InputSource source = new InputSource(stream);
            Document doc = null;
            try {
                doc = builder.parse(source);
            }  catch (SAXException e) {
                Log.v(TAG, "SAXException: "+e.toString());
            }
            if (doc == null) {
                return new DocumentResult(null, 1);
            }
            return new DocumentResult(doc, 0);
        } catch (IOException e) {
            return new DocumentResult(null, 404);
        } catch (ParserConfigurationException e) {
            return new DocumentResult(null, 1);
        }
    }

    public static StringResult retrieveAsString(URL url, String begin, String end, Context ctx) throws IOException {
        Log.v(TAG, "Retrieving data from " + url.toString());
        StringBuilder result = new StringBuilder();
        RequestCache urlConnection = new RequestCache(url, ctx);
        int lines = 0;
        int lineNo = 0;
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            boolean skip = (begin != null) ? true : false;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.equals("")) continue;
                if (!trimmed.startsWith("<") && !trimmed.endsWith(">")) {
                    line = line.replace("&", "&#038;");
                }
                if ((begin != null) && (trimmed.startsWith(begin))) {
                    skip = false;
                } else if ((!skip) && (end != null) && (trimmed.startsWith(end))) {
                    result.append(line);
                    result.append("\n");
                    lines++;
                    break;
                }
                if (!skip) {
                    lines++;
                    result.append(line);
                    result.append("\n");
                }
            }
        } catch (IOException e) {
            Log.v(TAG, "IOException" + e.toString());
            int ec = 404;
            HttpURLConnection connection = urlConnection.getConnection();
            if (connection != null) {
                ec = connection.getResponseCode();
            }
            return new StringResult("", ec);
        } finally {
            urlConnection.disconnect();
        }
        Log.v(TAG, "Got Lines: "+lines);

        return new StringResult(result.toString(), 0);
    }

    public static class StringResult {
        public StringResult(String result, int status) {
            mResult = result;
            mStatus = status;
        }

        public int status() {
            return mStatus;
        }

        public String result() {
            return mResult;
        }

        protected int mStatus;
        protected String mResult;
    }

    public static class DocumentResult {
        public DocumentResult(Document result, int status) {
            mResult = result;
            mStatus = status;
        }

        public int status() {
            return mStatus;
        }

        public Document result() {
            return mResult;
        }

        protected int mStatus;
        protected Document mResult;
    }

}
