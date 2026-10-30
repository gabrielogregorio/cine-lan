package app.medialan;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;

/** Deterministic random order: the same seed gives the same order, with nothing stored. */
final class Ordering {
    private static final long MS_BY_HOUR = 3_600_000L;

    private Ordering() {}

    static long hour(long nowMs) {
        return nowMs / MS_BY_HOUR;
    }

    static long day(long nowMs) {
        return hour(nowMs) / 24;
    }

    private static long position(String id, long seed) {
        CRC32 crc = new CRC32();
        crc.update((id + ":" + seed).getBytes(StandardCharsets.UTF_8));

        return crc.getValue();
    }

    static List<Media> shuffled(List<Media> items, long seed) {
        List<Media> out = new ArrayList<>(items);
        out.sort(Comparator.comparingLong((Media mediaItem) -> position(mediaItem.id, seed)));

        return out;
    }
}
