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

    static synchronized UserStore of(Context c) {
        if (single == null) single = new UserStore(c.getApplicationContext());
        return single;
    }

    private UserStore(Context c) {
        super(c, "user.sqlite", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase b) {
        b.execSQL("CREATE TABLE progress(id TEXT PRIMARY KEY, show_id TEXT, position INTEGER, duration INTEGER,"
                + " watched INTEGER, updated INTEGER, media TEXT)");
        b.execSQL("CREATE TABLE favorites(id TEXT PRIMARY KEY, updated INTEGER, media TEXT)");
        createBuggy(b);
    }

    @Override
    public void onUpgrade(SQLiteDatabase b, int old, int fresh) {
        if (old < 2) createBuggy(b);
    }

    private static void createBuggy(SQLiteDatabase b) {
        b.execSQL("CREATE TABLE buggy(id TEXT PRIMARY KEY, updated INTEGER, media TEXT)");
    }

    void save(Media m, long positionMs, long durationMs) {
        boolean watched = durationMs > 0 && positionMs >= durationMs * FRACTION_WATCHED;
        ContentValues v = new ContentValues();
        v.put("id", m.id);
        v.put("show_id", m.showId);
        v.put("position", positionMs);
        v.put("duration", durationMs);
        v.put("watched", watched ? 1 : 0);
        v.put("updated", System.currentTimeMillis());
        v.put("media", m.raw.toString());
        getWritableDatabase().insertWithOnConflict("progress", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        version++;
    }

    /** Reads everything once and keeps it: each poster asks for its own, and scrolling cannot wait for the database. */
    Progress progress(String id) {
        if (versionRead != version) {
            progress.clear();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id, position, duration, watched FROM progress", null)) {
                while (c.moveToNext()) {
                    progress.put(c.getString(0), new Progress(c.getLong(1), c.getLong(2), c.getInt(3) == 1));
                }
            }
            versionRead = version;
        }
        return progress.get(id);
    }

    /** Id of the last episode touched in the show, so the Watch button knows where it stopped. */
    String lastOfShow(String showId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id FROM progress WHERE show_id=? ORDER BY updated DESC LIMIT 1",
                new String[] {showId})) {
            return c.moveToFirst() ? c.getString(0) : null;
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

    void toggleFavorite(Media m) {
        toggle("favorites", m);
    }

    List<Media> buggy() {
        return read("SELECT media FROM buggy ORDER BY updated DESC");
    }

    boolean buggy(String id) {
        return marked("buggy", id);
    }

    void toggleBuggy(Media m) {
        toggle("buggy", m);
    }

    private boolean marked(String table, String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM " + table + " WHERE id=?", new String[] {id})) {
            return c.moveToFirst();
        }
    }

    private void toggle(String table, Media m) {
        if (marked(table, m.id)) {
            getWritableDatabase().delete(table, "id=?", new String[] {m.id});
        } else {
            ContentValues v = new ContentValues();
            v.put("id", m.id);
            v.put("updated", System.currentTimeMillis());
            v.put("media", m.raw.toString());
            getWritableDatabase().insert(table, null, v);
        }
        version++;
    }

    void deleteAll() {
        getWritableDatabase().delete("progress", null, null);
        getWritableDatabase().delete("favorites", null, null);
        version++;
    }

    private List<Media> read(String sql) {
        List<Media> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) {
                try {
                    out.add(new Media(new JSONObject(c.getString(0))));
                } catch (JSONException ignorada) {
                    // a corrupted row must not take down the home screen
                }
            }
        }
        return out;
    }
}
