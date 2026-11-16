package com.example.playlist;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Помощник для работы с бд SQLite.
 * Создаёт таблицу tracks и при первом запуске заполняет её стартовыми песнями.
 */
public class DBHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "playlist.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE = "tracks";
    public static final String COL_ID = "_id";  // SimpleCursorAdapter требует колонку именно с таким именем
    public static final String COL_ARTIST = "artist";
    public static final String COL_TITLE = "title";
    public static final String COL_YEAR = "year";
    public static final String COL_DURATION = "duration"; // длительность в секундах

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_ARTIST + " TEXT NOT NULL, "
                + COL_TITLE + " TEXT NOT NULL, "
                + COL_YEAR + " INTEGER, "
                + COL_DURATION + " INTEGER)");

        // Стартовый плейлист
        addTrack(db, "Собачий Lie", "Рана", 2026, "2:30");
        addTrack(db, "GONE.Fludd", "Мурманск", 2026, "2:06");
        addTrack(db, "higanbanan", "Binomi", 2026, "3:05");
        addTrack(db, "Холи Полли", "Иммунитет", 2026, "2:20");
        addTrack(db, "Три дня дождя, pyrokinesis", "Негативное мышление", 2025, "3:24");
        addTrack(db, "Daniela", "Танцы с тенями", 2025, "4:05");
        addTrack(db, "FirstFeel, pyrokinesis", "Сестра", 2020, "2:42");
        addTrack(db, "pyrokinesis", "Брошен", 2023, "2:34");
        addTrack(db, "pyrokinesis", "Ума Турман", 2021, "3:16");
        addTrack(db, "pyrokinesis", "В городе где гаснут фонари", 2019, "3:29");
        addTrack(db, "Justin Bieber", "Ooo Baby Baby", 2010, "3:34");
        addTrack(db, "Кипелов", "Я свободен", 1997, "7:29");
        addTrack(db, "Кино", "Кукушка", 1991, "6:40");
        addTrack(db, "Кино", "Группа крови", 1988, "4:46");
    }

    private void addTrack(SQLiteDatabase db, String artist, String title, int year, String duration) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ARTIST, artist);
        cv.put(COL_TITLE, title);
        cv.put(COL_YEAR, year);
        cv.put(COL_DURATION, TimeUtils.parse(duration));
        db.insert(TABLE, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }
}
