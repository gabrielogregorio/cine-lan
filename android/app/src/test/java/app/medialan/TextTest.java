package app.medialan;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TextTest {
    @Test
    public void normalizeStripsAccentsAndCase() {
        assertEquals("cocorico acao", Text.normalize("Cocoricó AÇÃO"));
    }

    @Test
    public void durationInHoursAndMinutes() {
        assertEquals("1 h 40 min", Text.duration(6000));
        assertEquals("30 min", Text.duration(1800));
    }

    @Test
    public void clockWithAndWithoutHours() {
        assertEquals("4:41", Text.clock(281_000));
        assertEquals("1:40:07", Text.clock(6_007_000));
        assertEquals("0:00", Text.clock(-5));
    }
}
