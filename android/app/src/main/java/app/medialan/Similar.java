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

    static List<Media> neighbors(Media target, List<Media> library, int limit) {
        Set<String> wordsTarget = words(target.title);
        List<Scored> candidates = new ArrayList<>();
        for (Media mediaItem : library) {
            if (!mediaItem.id.equals(target.id)) {
                candidates.add(new Scored(mediaItem, distance(target, wordsTarget, mediaItem)));
            }
        }
        candidates.sort((first, second) -> Double.compare(second.points, first.points));
        List<Media> out = new ArrayList<>();
        for (int position = 0; position < candidates.size() && position < limit; position += 1) {
            out.add(candidates.get(position).media);
        }

        return out;
    }

    private static double distance(Media first, Set<String> wordsA, Media second) {
        double points = WEIGHT_GENRE * jaccard(first.genres, second.genres);
        if (!first.collection.isEmpty() && first.collection.equals(second.collection)) {
            points += WEIGHT_COLLECTION;
        }
        points += WEIGHT_DIRECTOR * Math.min(common(first.directors, second.directors), 1);
        points += WEIGHT_ACTOR * Math.min(common(first.actors, second.actors), MAX_ACTORS);
        points += WEIGHT_WORD * Math.min(common(wordsA, words(second.title)), MAX_WORDS);
        if (first.year > 0 && second.year > 0) {
            points += WEIGHT_YEAR * Math.exp(-Math.abs(first.year - second.year) / HALF_LIFE_YEARS);
        }
        if (first.rating > 0 && second.rating > 0) {
            points += WEIGHT_RATING * (1 - Math.abs(first.rating - second.rating) / 10);
        }
        if (first.isShow() == second.isShow()) {
            points += BONUS_SAME_KIND;
        }

        return points;
    }

    private static double jaccard(List<String> first, List<String> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return 0;
        }
        Set<String> union = new HashSet<>(first);
        union.addAll(second);

        return (double) common(first, second) / union.size();
    }

    private static int common(Collection<String> first, Collection<String> second) {
        int total = 0;
        for (String word : first) {
            if (second.contains(word)) {
                total += 1;
            }
        }

        return total;
    }

    private static Set<String> words(String title) {
        Set<String> out = new HashSet<>();
        Collections.addAll(out, Text.normalize(title).split("[^a-z0-9]+"));
        out.removeIf(scored -> scored.length() < 3 || STOP_WORDS.contains(scored));

        return out;
    }
}
