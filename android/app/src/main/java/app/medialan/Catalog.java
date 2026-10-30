package app.medialan;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** In-memory catalog with a disk copy, to open fast and survive a server that is off. */
final class Catalog {
    static final String SERVER_DEFAULT = BuildConfig.DEFAULT_SERVER;
    private static final int MIN_BY_GENRE = 6;
    private static final int SIZE_FEATURED = 16;
    private static final int SIZE_BEST = 30;
    private static final String TITLE_RESUME = "Continuar assistindo";
    static final String[] ORDERS = {"day", "hour", "az"};
    static final String[] NAMES_ORDERS = {"Aleatória, troca todo dia", "Aleatória, troca toda hora", "De A a Z"};

    interface Done<T> {
        void onDone(T result, Exception error);
    }

    static final class Row {
        final String title;
        final List<Media> items;

        Row(String title, List<Media> items) {
            this.title = title;
            this.items = items;
        }

        /** In this row a click goes straight back to where playback stopped. */
        boolean resumes() {
            return TITLE_RESUME.equals(title);
        }
    }

    private static final ExecutorService BACKGROUND = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static List<Media> movies = new ArrayList<>();
    static List<Media> shows = new ArrayList<>();
    /** Queue handed to the player: episodes in order, or just the movie. */
    static List<Media> queue = new ArrayList<>();
    /** Ids still on the server. Empty means "unknown": nothing is hidden then. */
    static Set<String> existing = new HashSet<>();

    private Catalog() {}

    private static SharedPreferences preferences(Context c) {
        return c.getSharedPreferences("ajustes", Context.MODE_PRIVATE);
    }

    static String server(Context c) {
        return preferences(c).getString("server", SERVER_DEFAULT);
    }

    static void setServer(Context c, String address) {
        preferences(c).edit().putString("server", address.replaceAll("/+$", "")).apply();
    }

    static String absolute(Context c, String path) {
        return path == null ? null : server(c) + path;
    }

    static String download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(20000);
        try {
            if (connection.getResponseCode() != 200) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] block = new byte[16 * 1024];
            try (InputStream entry = connection.getInputStream()) {
                for (int read; (read = entry.read(block)) != -1; ) out.write(block, 0, read);
            }
            return out.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }

    static <T> void inBackground(java.util.concurrent.Callable<T> task, Done<T> ready) {
        BACKGROUND.execute(() -> {
            T result = null;
            Exception error = null;
            try {
                result = task.call();
            } catch (Exception e) {
                error = e;
            }
            T final_ = result;
            Exception errorFinal = error;
            MAIN.post(() -> ready.onDone(final_, errorFinal));
        });
    }

    static void load(Context context, Done<Boolean> ready) {
        Context c = context.getApplicationContext();
        File copy = new File(c.getFilesDir(), "catalog.json");
        inBackground(() -> {
            String base = server(c);
            String text;
            try {
                JSONObject all = new JSONObject();
                all.put("movies", new JSONObject(download(base + "/api/movies")).getJSONArray("items"));
                all.put("shows", new JSONObject(download(base + "/api/shows")).getJSONArray("shows"));
                all.put("ids", new JSONObject(download(base + "/api/ids")).getJSONArray("ids"));
                text = all.toString();
                Files.write(copy.toPath(), text.getBytes(StandardCharsets.UTF_8));
            } catch (IOException | JSONException failureOfNetwork) {
                if (!copy.exists()) throw failureOfNetwork;
                text = new String(Files.readAllBytes(copy.toPath()), StandardCharsets.UTF_8);
            }
            JSONObject all = new JSONObject(text);
            List<Media> newMovies = list(all.getJSONArray("movies"));
            List<Media> newShows = list(all.getJSONArray("shows"));
            Set<String> newIds = new HashSet<>();
            JSONArray ids = all.optJSONArray("ids");
            for (int i = 0; ids != null && i < ids.length(); i++) newIds.add(ids.getString(i));
            movies = newMovies;
            shows = newShows;
            existing = newIds;
            return true;
        }, ready);
    }

    private static List<Media> list(JSONArray a) throws JSONException {
        List<Media> out = new ArrayList<>(a.length());
        for (int i = 0; i < a.length(); i++) out.add(new Media(a.getJSONObject(i)));
        return out;
    }

    private static List<Media> onlyExisting(List<Media> items) {
        if (existing.isEmpty()) return items;
        List<Media> out = new ArrayList<>();
        for (Media m : items) if (existing.contains(m.id)) out.add(m);
        return out;
    }

    static List<Media> favorites(UserStore user) {
        return onlyExisting(user.favorites());
    }

    static List<Media> buggy(UserStore user) {
        return onlyExisting(user.buggy());
    }

    static List<Media> library() {
        List<Media> out = new ArrayList<>(movies);
        out.addAll(shows);
        return out;
    }

    static Media findShow(String id) {
        for (Media s : shows) if (s.id.equals(id)) return s;
        return null;
    }

    static String order(Context c) {
        return preferences(c).getString("order", ORDERS[0]);
    }

    static void setOrder(Context c, String order) {
        preferences(c).edit().putString("order", order).apply();
        UserStore.version++;
    }

    /** Current hour or day number: the same seed gives the same order on any device. */
    static long seed(Context c) {
        long now = System.currentTimeMillis();
        return "hour".equals(order(c)) ? Ordering.hour(now) : Ordering.day(now);
    }

    /** No shuffle in A to Z; the other modes change deterministically and store nothing. */
    static List<Media> sorted(Context c, List<Media> items) {
        return "az".equals(order(c)) ? items : Ordering.shuffled(items, seed(c));
    }

    /** Queue for random play: truly random, changes on every click. */
    static List<Media> queueRandom() {
        List<Media> out = new ArrayList<>(movies);
        Collections.shuffle(out);
        return out;
    }

    static List<Row> rows(Context c, UserStore user) {
        List<Row> out = new ArrayList<>();
        add(out, TITLE_RESUME, onlyExisting(user.continueWatching()));
        add(out, "Favoritos", favorites(user));

        List<Media> showcase = new ArrayList<>();
        for (Media m : movies) if (m.fanart != null && m.logo != null && m.rating >= 6.5) showcase.add(m);
        for (Media m : shows) if (m.fanart != null && m.logo != null) showcase.add(m);
        showcase = Ordering.shuffled(showcase, seed(c));
        add(out, "Destaques", showcase.subList(0, Math.min(SIZE_FEATURED, showcase.size())));

        add(out, "Séries", sorted(c, shows));

        List<Media> best = new ArrayList<>(movies);
        best.sort(Comparator.comparingDouble((Media m) -> -m.rating));
        add(out, "Mais bem avaliados", best.subList(0, Math.min(SIZE_BEST, best.size())));

        Map<String, List<Media>> byGenre = new HashMap<>();
        for (Media m : movies) {
            for (String g : m.genres) byGenre.computeIfAbsent(g, k -> new ArrayList<>()).add(m);
        }
        List<Map.Entry<String, List<Media>>> genres = new ArrayList<>(byGenre.entrySet());
        genres.sort((a, b) -> b.getValue().size() - a.getValue().size());
        for (Map.Entry<String, List<Media>> g : genres) {
            if (g.getValue().size() >= MIN_BY_GENRE) add(out, g.getKey(), sorted(c, g.getValue()));
        }
        return out;
    }

    private static void add(List<Row> out, String title, List<Media> items) {
        if (!items.isEmpty()) out.add(new Row(title, items));
    }

    static List<Media> search(String term) {
        String target = Text.normalize(term);
        List<Media> out = new ArrayList<>();
        if (target.isEmpty()) return out;
        for (Media m : shows) if (Text.normalize(m.title).contains(target)) out.add(m);
        for (Media m : movies) if (Text.normalize(m.title).contains(target)) out.add(m);
        return out;
    }
}
