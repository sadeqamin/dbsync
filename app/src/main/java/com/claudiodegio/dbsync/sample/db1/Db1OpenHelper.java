package com.claudiodegio.dbsync.sample.db1;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class Db1OpenHelper extends SQLiteOpenHelper {

    static final private Logger log = LoggerFactory.getLogger(Db1OpenHelper.class);

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "db1.db";

    private static final String DDL = "CREATE TABLE name (\n" +
            "    _id          INTEGER PRIMARY KEY,\n" +
            "    NAME         TEXT,\n" +
            "    DATE_CREATED INTEGER NOT NULL,\n" +
            "    LAST_UPDATED INTEGER,\n" +
            "    CLOUD_ID     TEXT    UNIQUE);";

    public Db1OpenHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        log.info("onCreate");
        db.execSQL(DDL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    public File getDatabaseLocation(){
        return new File("/data/data/com.claudiodegio.dbsync.sample/databases", DATABASE_NAME);
    }
}