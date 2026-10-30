package app.medialan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class OrderTest {
    private List<Media> library() throws Exception {
        List<Media> items = new ArrayList<>();
        for (int position = 0; position < 40; position += 1) {
            items.add(Fixtures.movie("id" + position, "Filme " + position, 2000, 7));
        }

        return items;
    }

    private List<String> ids(List<Media> items) {
        List<String> out = new ArrayList<>();
        for (Media mediaItem : items) {
            out.add(mediaItem.id);
        }

        return out;
    }

    @Test
    public void sameSeedGivesSameOrder() throws Exception {
        assertEquals(ids(Ordering.shuffled(library(), 123)), ids(Ordering.shuffled(library(), 123)));
    }

    @Test
    public void newSeedChangesTheOrder() throws Exception {
        assertNotEquals(ids(Ordering.shuffled(library(), 123)), ids(Ordering.shuffled(library(), 124)));
    }

    @Test
    public void neitherLosesNorDuplicatesItems() throws Exception {
        List<Media> shuffled = Ordering.shuffled(library(), 7);
        assertEquals(40, new java.util.HashSet<>(ids(shuffled)).size());
    }

    @Test
    public void leavesTheOriginalListUntouched() throws Exception {
        List<Media> original = library();
        List<String> before = ids(original);
        Ordering.shuffled(original, 9);
        assertEquals(before, ids(original));
    }

    @Test
    public void seedChangesWhenHourAndDayTurn() {
        long oneHour = 3_600_000L;
        assertEquals(Ordering.hour(0), Ordering.hour(oneHour - 1));
        assertNotEquals(Ordering.hour(0), Ordering.hour(oneHour));
        assertEquals(Ordering.day(0), Ordering.day(oneHour * 24 - 1));
        assertNotEquals(Ordering.day(0), Ordering.day(oneHour * 24));
    }
}
