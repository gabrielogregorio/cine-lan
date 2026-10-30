package app.medialan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public class MediaTest {
    @Test
    public void readsFullMovie() throws Exception {
        Media mediaItem = new Media(new JSONObject("{\"id\":\"1\",\"kind\":\"movie\",\"title\":\"Toy\",\"year\":2019,"
                + "\"rating\":7.5,\"duration_s\":6000,\"genres\":[\"Família\"],\"show_id\":null,"
                + "\"images\":{\"poster\":\"/p\",\"fanart\":\"/f\",\"clearlogo\":\"/l\"}}"));
        assertEquals("/p", mediaItem.cover());
        assertEquals("/f", mediaItem.background());
        assertEquals("/l", mediaItem.logo);
        assertNull(mediaItem.showId);
        assertFalse(mediaItem.isShow());
        assertEquals("2019  ·  1 h 40 min  ·  ★ 7.5  ·  Família", mediaItem.metadata());
    }

    @Test
    public void rawFileWithNoDataDoesNotBreak() throws Exception {
        Media mediaItem = new Media(new JSONObject("{\"id\":\"2\",\"kind\":\"movie\",\"title\":\"cru.mkv\",\"images\":{}}"));
        assertNull(mediaItem.cover());
        assertNull(mediaItem.background());
        assertEquals("", mediaItem.metadata());
    }

    @Test
    public void noKindMeansShow() throws Exception {
        assertTrue(new Media(new JSONObject("{\"id\":\"3\",\"title\":\"Show\",\"episodes\":2}")).isShow());
    }

    @Test
    public void episodeInheritsShowImages() throws Exception {
        Media show = new Media(new JSONObject("{\"id\":\"s\",\"title\":\"Show\",\"images\":{\"poster\":\"/sp\",\"fanart\":\"/sf\"}}"));
        Media episode = new Media(new JSONObject("{\"id\":\"e\",\"kind\":\"episode\",\"title\":\"Piloto\",\"season\":2,\"episode\":5}"));
        episode.inheritOf(show);
        Media kept = new Media(new JSONObject(episode.raw.toString()));
        assertEquals("/sp", kept.cover());
        assertEquals("/sf", kept.background());
        assertEquals("Show - T2 E5 Piloto", kept.titleFull());
    }
}
