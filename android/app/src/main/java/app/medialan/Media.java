package app.medialan;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Movie, show or episode, as the API returns it. */
final class Media {
    final JSONObject raw;
    final String id;
    final String kind;
    final String title;
    final String overview;
    final String url;
    final String showId;
    final String poster;
    final String fanart;
    final String logo;
    final String thumb;
    final String posterShow;
    final String fanartShow;
    final int year;
    final int durationS;
    final int season;
    final int episode;
    final int episodes;
    final double rating;
    final String collection;
    final List<String> genres = new ArrayList<>();
    final List<String> directors = new ArrayList<>();
    final List<String> actors = new ArrayList<>();

    Media(JSONObject o) {
        raw = o;
        id = o.optString("id");
        kind = o.optString("kind", "show");
        title = o.optString("title");
        overview = o.optString("overview");
        url = o.optString("url", null);
        showId = o.isNull("show_id") ? null : o.optString("show_id", null);
        year = o.optInt("year");
        durationS = o.optInt("duration_s");
        season = o.optInt("season");
        episode = o.optInt("episode");
        episodes = o.optInt("episodes");
        rating = o.optDouble("rating", 0);
        JSONObject images = o.optJSONObject("images");
        poster = image(images, "poster");
        fanart = image(images, "fanart");
        logo = image(images, "clearlogo");
        thumb = image(images, "thumb");
        posterShow = o.optString("show_poster", null);
        fanartShow = o.optString("show_fanart", null);
        collection = o.optString("collection");
        copy(o.optJSONArray("genres"), genres);
        copy(o.optJSONArray("directors"), directors);
        copy(o.optJSONArray("actors"), actors);
    }

    private static void copy(JSONArray source, List<String> target) {
        for (int i = 0; source != null && i < source.length(); i++) target.add(source.optString(i));
    }

    private static String image(JSONObject images, String kind) {
        return images == null ? null : images.optString(kind, null);
    }

    boolean isShow() {
        return "show".equals(kind);
    }

    boolean isEpisode() {
        return "episode".equals(kind);
    }

    String cover() {
        return poster != null ? poster : posterShow;
    }

    String background() {
        return fanart != null ? fanart : fanartShow;
    }

    /** Links the episode to the show's images, so it looks right in "Continuar assistindo". */
    void inheritOf(Media show) {
        try {
            raw.put("show_poster", show.poster);
            raw.put("show_fanart", show.fanart);
            raw.put("show_name", show.title);
        } catch (org.json.JSONException ignorada) {
            // key and value are strings: this cannot fail
        }
    }

    String titleFull() {
        String show = raw.optString("show_name", "");
        return isEpisode() && !show.isEmpty() ? show + " - " + labelEpisode() : title;
    }

    String labelEpisode() {
        String number = episode > 0 ? "E" + episode + " " : "";
        return "T" + season + " " + number + title;
    }

    String metadata() {
        List<String> parts = new ArrayList<>();
        if (year > 0) parts.add(String.valueOf(year));
        if (durationS > 0) parts.add(Text.duration(durationS));
        if (episodes > 0) parts.add(episodes + (episodes == 1 ? " episódio" : " episódios"));
        if (rating > 0) parts.add(String.format(java.util.Locale.ROOT, "★ %.1f", rating));
        for (int i = 0; i < genres.size() && i < 3; i++) parts.add(genres.get(i));
        return String.join("  ·  ", parts);
    }
}
