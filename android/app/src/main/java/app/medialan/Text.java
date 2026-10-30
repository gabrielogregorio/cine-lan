package app.medialan;

import java.text.Normalizer;
import java.util.Locale;

final class Text {
    private Text() {}

    static String normalize(String text) {
        String withoutAccent = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccent.toLowerCase(Locale.ROOT);
    }

    static String duration(int seconds) {
        int minutes = seconds / 60;
        return minutes >= 60 ? (minutes / 60) + " h " + (minutes % 60) + " min" : minutes + " min";
    }

    static String clock(long ms) {
        long total = Math.max(ms, 0) / 1000;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
