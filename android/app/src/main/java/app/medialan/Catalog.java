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

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("ajustes", Context.MODE_PRIVATE);
    }

    static String server(Context context) {
        return preferences(context).getString("server", SERVER_DEFAULT);
    }

    static void setServer(Context context, String address) {
        preferences(context).edit().putString("server", address.replaceAll("/+$", "")).apply();
    }

    static String absolute(Context context, String path) {
        return path == null ? null : server(context) + path;
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
                for (int read; (read = entry.read(block)) != -1; ) {
                    out.write(block, 0, read);
                }
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
            } catch (Exception thrown) {
                error = thrown;
            }
            T final_ = result;
            Exception errorFinal = error;
            MAIN.post(() -> ready.onDone(final_, errorFinal));
        });
    }

    static void load(Context anyContext, Done<Boolean> ready) {
        Context context = anyContext.getApplicationContext();
        File copy = new File(context.getFilesDir(), "catalog.json");
        inBackground(() -> {
            String base = server(context);
            String text;
            try {
                JSONObject all = new JSONObject();
                all.put("movies", new JSONObject(download(base + "/api/movies")).getJSONArray("items"));
                all.put("shows", new JSONObject(download(base + "/api/shows")).getJSONArray("shows"));
                all.put("ids", new JSONObject(download(base + "/api/ids")).getJSONArray("ids"));
                text = all.toString();
                Files.write(copy.toPath(), text.getBytes(StandardCharsets.UTF_8));
            } catch (IOException | JSONException failureOfNetwork) {
                if (!copy.exists()) {
                    throw failureOfNetwork;
                }
                text = new String(Files.readAllBytes(copy.toPath()), StandardCharsets.UTF_8);
            }
            JSONObject all = new JSONObject(text);
            List<Media> newMovies = list(all.getJSONArray("movies"));
            List<Media> newShows = list(all.getJSONArray("shows"));
            Set<String> newIds = new HashSet<>();
            JSONArray ids = all.optJSONArray("ids");
            for (int position = 0; ids != null && position < ids.length(); position += 1) {
                newIds.add(ids.getString(position));
            }
            movies = newMovies;
            shows = newShows;
            existing = newIds;

            return true;
        }, ready);
    }

    private static List<Media> list(JSONArray array) throws JSONException {
        List<Media> out = new ArrayList<>(array.length());
        for (int position = 0; position < array.length(); position += 1) {
            out.add(new Media(array.getJSONObject(position)));
        }

        return out;
    }

    private static List<Media> onlyExisting(List<Media> items) {
        if (existing.isEmpty()) {
            return items;
        }
        List<Media> out = new ArrayList<>();
        for (Media mediaItem : items) {
            if (existing.contains(mediaItem.id)) {
                out.add(mediaItem);
            }
        }

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
        for (Media show : shows) {
            if (show.id.equals(id)) {
                return show;
            }
        }

        return null;
    }

    static String order(Context context) {
        return preferences(context).getString("order", ORDERS[0]);
    }

    static void setOrder(Context context, String order) {
        preferences(context).edit().putString("order", order).apply();
        UserStore.version += 1;
    }

    /** Current hour or day number: the same seed gives the same order on any device. */
    static long seed(Context context) {
        long now = System.currentTimeMillis();

        return "hour".equals(order(context)) ? Ordering.hour(now) : Ordering.day(now);
    }

    /** No shuffle in A to Z; the other modes change deterministically and store nothing. */
    static List<Media> sorted(Context context, List<Media> items) {
        return "az".equals(order(context)) ? items : Ordering.shuffled(items, seed(context));
    }

    /** Queue for random play: truly random, changes on every click. */
    static List<Media> queueRandom() {
        List<Media> out = new ArrayList<>(movies);
        Collections.shuffle(out);

        return out;
    }

    static List<Row> rows(Context context, UserStore user) {
        List<Row> out = new ArrayList<>();
        add(out, TITLE_RESUME, onlyExisting(user.continueWatching()));
        add(out, "Favoritos", favorites(user));

        List<Media> showcase = new ArrayList<>();
        for (Media mediaItem : movies) {
            if (mediaItem.fanart != null && mediaItem.logo != null && mediaItem.rating >= 6.5) {
                showcase.add(mediaItem);
            }
        }
        for (Media mediaItem : shows) {
            if (mediaItem.fanart != null && mediaItem.logo != null) {
                showcase.add(mediaItem);
            }
        }
        showcase = Ordering.shuffled(showcase, seed(context));
        add(out, "Destaques", showcase.subList(0, Math.min(SIZE_FEATURED, showcase.size())));

        add(out, "Séries", sorted(context, shows));

        List<Media> best = new ArrayList<>(movies);
        best.sort(Comparator.comparingDouble((Media mediaItem) -> -mediaItem.rating));
        add(out, "Mais bem avaliados", best.subList(0, Math.min(SIZE_BEST, best.size())));

        Map<String, List<Media>> byGenre = new HashMap<>();
        for (Media mediaItem : movies) {
            for (String genre : mediaItem.genres) {
                byGenre.computeIfAbsent(genre, unused -> new ArrayList<>()).add(mediaItem);
            }
        }
        List<Map.Entry<String, List<Media>>> genres = new ArrayList<>(byGenre.entrySet());
        genres.sort((first, second) -> second.getValue().size() - first.getValue().size());
        for (Map.Entry<String, List<Media>> genre : genres) {
            if (genre.getValue().size() >= MIN_BY_GENRE) {
                add(out, genre.getKey(), sorted(context, genre.getValue()));
            }
        }

        return out;
    }

    private static void add(List<Row> out, String title, List<Media> items) {
        if (!items.isEmpty()) {
            out.add(new Row(title, items));
        }
    }

    static List<Media> search(String term) {
        String target = Text.normalize(term);
        List<Media> out = new ArrayList<>();
        if (target.isEmpty()) {
            return out;
        }
        for (Media mediaItem : shows) {
            if (Text.normalize(mediaItem.title).contains(target)) {
                out.add(mediaItem);
            }
        }
        for (Media mediaItem : movies) {
            if (Text.normalize(mediaItem.title).contains(target)) {
                out.add(mediaItem);
            }
        }

        return out;
    }
}
