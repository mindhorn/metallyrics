package com.github.mindhorn.metallyrics;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import static android.database.DatabaseUtils.sqlEscapeString;

/**
 * Created on 22.01.17.
 */
public class BandBlacklist {
    private SQLiteDatabase mDatabase;

    public BandBlacklist(Context ctx, String site) {
        mDatabase = ctx.openOrCreateDatabase(site + "_blacklist", ctx.MODE_PRIVATE, null);
        initColumns();
    }

    private void initColumns() {
        mDatabase.execSQL("CREATE TABLE IF NOT EXISTS blacklist(" +
                "band VARCHAR, " +
                "additiontime REAL DEFAULT(datetime('now', 'localtime'))" +
        ");");
    }

    public void addToBlacklist(String band) {
        mDatabase.execSQL("INSERT INTO blacklist(band) VALUES(" +
                sqlEscapeString(band) + ");");
    }

    public boolean isBlacklisted(String band) {
        Cursor res = mDatabase.rawQuery("SELECT band FROM blacklist WHERE " +
                "band="  + sqlEscapeString(band)  + ";", null);
        if (!res.moveToFirst()) {
            return false;
        }
        return true;
    }

    public void close() {
        mDatabase.close();
        mDatabase = null;
    }
}
