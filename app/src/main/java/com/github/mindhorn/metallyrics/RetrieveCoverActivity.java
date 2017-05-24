package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RetrieveCoverActivity extends AppCompatActivity {

    CoverRetriever mRetriever;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_retrieve_cover);
        mRetriever = new CoverRetriever();

        final LinearLayout layout = (LinearLayout) findViewById(R.id.layout_retrieve_covers);

        final Context ctx = this;

        final Map<CoverRetriever.Album, TextView> album2view = new HashMap<>();
        final List<CoverRetriever.Album> albums = new LinkedList<>();

        mRetriever.retrieveMissingCoverEntries(this, new CoverRetriever.MissingCoverHandler() {
            @Override
            public void handleCover(CoverRetriever.Album album) {
                TextView view = new TextView(ctx);
                view.setText(album.getArtist() + " - " + album.getTitle());
                album2view.put(album, view);
                albums.add(album);
                layout.addView(view);
            }
        });

        final Button button = (Button) findViewById(R.id.button_retrieve_covers);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final ProgressBar pb = (ProgressBar) findViewById(R.id.progressbar_retrieve_covers);
                pb.setVisibility(pb.VISIBLE);
                //List<CoverRetriever.Album> albums = new ArrayList<>(album2view.keySet());
                int numcovers = albums.size();
                pb.setMax(numcovers);
                mRetriever.retrieveCovers(ctx, albums, new CoverRetriever.MissingCoverHandler() {
                    @Override
                    public void handleCover(CoverRetriever.Album album) {
                        TextView tv = album2view.get(album);
                        album2view.remove(album);
                        layout.removeView(tv);
                        //pb.setProgress(++cur);
                    }
                });
            }
        });

    }


}
