package app.medialan;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** Everything that belongs to the user lives here, on the device: progress, favorites and buggy marks. */
final class UserStore extends SQLiteOpenHelper {
    private static final double FRACTION_WATCHED = 0.92;
    private static final long MIN_RESUME_MS = 30_000;
    private static UserStore single;
    static int version;
    private final Map<String, Progress> progress = new HashMap<>();
    private int versionRead = -1;

    static final class Progress {
        final long positionMs;
        final long durationMs;
        final boolean watched;

        Progress(long positionMs, long durationMs, boolean watched) {
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.watched = watched;
        }

        int permille() {
            return durationMs > 0 ? (int) (positionMs * 1000 / durationMs) : 0;
        }

        boolean resumable() {
            return !watched && positionMs >= MIN_RESUME_MS;
        }
    }

    static synchronized UserStore of(Context context) {
        if (single == null) {
            single = new UserStore(context.getApplicationContext());
        }

        return single;
    }

    private UserStore(Context context) {
        super(context, "user.sqlite", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE progress(id TEXT PRIMARY KEY, show_id TEXT, position INTEGER, duration INTEGER,"
                + " watched INTEGER, updated INTEGER, media TEXT)");
        database.execSQL("CREATE TABLE favorites(id TEXT PRIMARY KEY, updated INTEGER, media TEXT)");
        createBuggy(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int old, int fresh) {
        if (old < 2) {
            createBuggy(database);
        }
    }

    private static void createBuggy(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE buggy(id TEXT PRIMARY KEY, updated INTEGER, media TEXT)");
    }

    void save(Media mediaItem, long positionMs, long durationMs) {
        boolean watched = durationMs > 0 && positionMs >= durationMs * FRACTION_WATCHED;
        ContentValues values = new ContentValues();
        values.put("id", mediaItem.id);
        values.put("show_id", mediaItem.showId);
        values.put("position", positionMs);
        values.put("duration", durationMs);
        values.put("watched", watched ? 1 : 0);
        values.put("updated", System.currentTimeMillis());
        values.put("media", mediaItem.raw.toString());
        getWritableDatabase().insertWithOnConflict("progress", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        version += 1;
    }

    /** Reads everything once and keeps it: each poster asks for its own, and scrolling cannot wait for the database. */
    Progress progress(String id) {
        if (versionRead != version) {
            progress.clear();
            try (Cursor cursor = getReadableDatabase().rawQuery("SELECT id, position, duration, watched FROM progress", null)) {
                while (cursor.moveToNext()) {
                    progress.put(cursor.getString(0), new Progress(cursor.getLong(1), cursor.getLong(2), cursor.getInt(3) == 1));
                }
            }
            versionRead = version;
        }

        return progress.get(id);
    }

    /** Id of the last episode touched in the show, so the Watch button knows where it stopped. */
    String lastOfShow(String showId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM progress WHERE show_id=? ORDER BY updated DESC LIMIT 1",
                new String[] {showId})) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    List<Media> continueWatching() {
        return read("SELECT media FROM progress WHERE watched=0 AND position>=" + MIN_RESUME_MS
                + " ORDER BY updated DESC LIMIT 20");
    }

    List<Media> favorites() {
        return read("SELECT media FROM favorites ORDER BY updated DESC");
    }

    boolean favorite(String id) {
        return marked("favorites", id);
    }

    void toggleFavorite(Media mediaItem) {
        toggle("favorites", mediaItem);
    }

    List<Media> buggy() {
        return read("SELECT media FROM buggy ORDER BY updated DESC");
    }

    boolean buggy(String id) {
        return marked("buggy", id);
    }

    void toggleBuggy(Media mediaItem) {
        toggle("buggy", mediaItem);
    }

    private boolean marked(String table, String id) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT 1 FROM " + table + " WHERE id=?", new String[] {id})) {
            return cursor.moveToFirst();
        }
    }

    private void toggle(String table, Media mediaItem) {
        if (marked(table, mediaItem.id)) {
            getWritableDatabase().delete(table, "id=?", new String[] {mediaItem.id});
        } else {
            ContentValues values = new ContentValues();
            values.put("id", mediaItem.id);
            values.put("updated", System.currentTimeMillis());
            values.put("media", mediaItem.raw.toString());
            getWritableDatabase().insert(table, null, values);
        }
        version += 1;
    }

    void deleteAll() {
        getWritableDatabase().delete("progress", null, null);
        getWritableDatabase().delete("favorites", null, null);
        version += 1;
    }

    private List<Media> read(String sql) {
        List<Media> out = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                try {
                    out.add(new Media(new JSONObject(cursor.getString(0))));
                } catch (JSONException ignorada) {
                    // a corrupted row must not take down the home screen
                }
            }
        }

        return out;
    }
}
