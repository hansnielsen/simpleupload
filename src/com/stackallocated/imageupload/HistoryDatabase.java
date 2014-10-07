package com.stackallocated.imageupload;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.util.Log;

public class HistoryDatabase extends SQLiteOpenHelper {
    private final static String TAG = "HistoryDatabase";

    private final static int DB_VERSION = 1;
    private final static String DB_NAME = "HistoryDatabase";

    private final static String IMAGES_TABLE_NAME = "images";
    private final static String IMAGES_COL_URL = "url";
    private final static String IMAGES_COL_UPLOADED_DATE = "uploaded_date";
    private final static String IMAGES_TABLE_CREATE = "CREATE TABLE " + IMAGES_TABLE_NAME + " (" +
            BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT" +
            "," +
            IMAGES_COL_URL + " TEXT" +
            "," +
            IMAGES_COL_UPLOADED_DATE + " INTEGER" +
            ");";

    // Singleton used to access the database.
    private static HistoryDatabase instance = null;
    public static HistoryDatabase getInstance(Context context) {
        if (instance == null) {
            instance = new HistoryDatabase(context);
        }
        return instance;
    }

    public HistoryDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(IMAGES_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.e(TAG, "Got onUpgrade from " + oldVersion + " to " + newVersion + ", ignoring");
    }

    public void insertImage(String url, long uploadedDate) {
        ContentValues values = new ContentValues();
        values.put(IMAGES_COL_URL, url);
        values.put(IMAGES_COL_UPLOADED_DATE, uploadedDate);
        long ret = getWritableDatabase().insert(IMAGES_TABLE_NAME, null, values);
        if (ret < 0) {
            Log.e(TAG, "Unable to write row to database!");
        }
    }
    
    public Cursor getImages() {
        return getReadableDatabase().rawQuery("SELECT * FROM " + IMAGES_TABLE_NAME + ";", null);
    }
}
