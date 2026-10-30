package app.medialan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rule-based KNN: scores every library title against the target and keeps the best k.
 * No model: only genre, collection, people, title words, year and rating, all already in the catalog.
 */
final class Similar {
    private static final double WEIGHT_GENRE = 4;
    private static final double WEIGHT_COLLECTION = 8;
    private static final double WEIGHT_DIRECTOR = 2;
    private static final double WEIGHT_ACTOR = 0.7;
    private static final int MAX_ACTORS = 3;
    private static final double WEIGHT_WORD = 1.5;
    private static final int MAX_WORDS = 2;
    private static final double WEIGHT_YEAR = 1.5;
    private static final double HALF_LIFE_YEARS = 12;
    private static final double WEIGHT_RATING = 0.5;
    private static final double BONUS_SAME_KIND = 0.5;
    private static final Set<String> STOP_WORDS = Set.of(
            "para", "como", "uma", "com", "dos", "das", "nos", "nas", "que", "the", "and", "aventura", "aventuras",
            "filme", "serie", "historia", "parte", "volume");

    private Similar() {}

    private static final class Scored {
        final Media media;
        final double points;

        Scored(Media media, double points) {
            this.media = media;
            this.points = points;
        }
    }

    static List<Media> neighbors(Media target, List<Media> library, int k) {
        Set<String> wordsTarget = words(target.title);
        List<Scored> candidates = new ArrayList<>();
        for (Media m : library) {
            if (!m.id.equals(target.id)) candidates.add(new Scored(m, distance(target, wordsTarget, m)));
        }
        candidates.sort((a, b) -> Double.compare(b.points, a.points));
        List<Media> out = new ArrayList<>();
        for (int i = 0; i < candidates.size() && i < k; i++) out.add(candidates.get(i).media);
        return out;
    }

    private static double distance(Media a, Set<String> wordsA, Media b) {
        double points = WEIGHT_GENRE * jaccard(a.genres, b.genres);
        if (!a.collection.isEmpty() && a.collection.equals(b.collection)) points += WEIGHT_COLLECTION;
        points += WEIGHT_DIRECTOR * Math.min(common(a.directors, b.directors), 1);
        points += WEIGHT_ACTOR * Math.min(common(a.actors, b.actors), MAX_ACTORS);
        points += WEIGHT_WORD * Math.min(common(wordsA, words(b.title)), MAX_WORDS);
        if (a.year > 0 && b.year > 0) points += WEIGHT_YEAR * Math.exp(-Math.abs(a.year - b.year) / HALF_LIFE_YEARS);
        if (a.rating > 0 && b.rating > 0) points += WEIGHT_RATING * (1 - Math.abs(a.rating - b.rating) / 10);
        if (a.isShow() == b.isShow()) points += BONUS_SAME_KIND;
        return points;
    }

    private static double jaccard(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) common(a, b) / union.size();
    }

    private static int common(Collection<String> a, Collection<String> b) {
        int total = 0;
        for (String x : a) if (b.contains(x)) total++;
        return total;
    }

    private static Set<String> words(String title) {
        Set<String> out = new HashSet<>();
        Collections.addAll(out, Text.normalize(title).split("[^a-z0-9]+"));
        out.removeIf(p -> p.length() < 3 || STOP_WORDS.contains(p));
        return out;
    }
}
