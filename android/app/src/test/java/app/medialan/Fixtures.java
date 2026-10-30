package app.medialan;

import org.json.JSONArray;
import org.json.JSONObject;

final class Fixtures {
    private Fixtures() {}

    static Media movie(String id, String title, int year, double rating, String... genres) throws Exception {
        return new Media(new JSONObject().put("id", id).put("kind", "movie").put("title", title).put("year", year)
                .put("rating", rating).put("genres", new JSONArray(genres)));
    }

    static Media withCollection(Media mediaItem, String collection) throws Exception {
        return new Media(mediaItem.raw.put("collection", collection));
    }

    static Media withDirector(Media mediaItem, String director) throws Exception {
        return new Media(mediaItem.raw.put("directors", new JSONArray().put(director)));
    }
}
