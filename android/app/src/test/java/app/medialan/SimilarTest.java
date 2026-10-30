package app.medialan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SimilarTest {
    @Test
    public void neverSuggestsTheTitleItself() throws Exception {
        Media target = Fixtures.movie("a", "Alvo", 2000, 7, "Ação");
        List<Media> found = Similar.neighbors(target, Arrays.asList(target, Fixtures.movie("b", "Outro", 2000, 7, "Ação")), 5);
        assertEquals(1, found.size());
        assertEquals("b", found.get(0).id);
    }

    @Test
    public void sharedGenreBeatsDifferentGenre() throws Exception {
        Media target = Fixtures.movie("a", "Alvo", 2000, 7, "Animação", "Família");
        Media equal = Fixtures.movie("b", "Bichos", 2000, 7, "Animação", "Família");
        Media other = Fixtures.movie("c", "Tiros", 2000, 7, "Terror");
        assertEquals("b", Similar.neighbors(target, Arrays.asList(other, equal), 1).get(0).id);
    }

    @Test
    public void sameCollectionComesFirst() throws Exception {
        Media target = Fixtures.comCollection(Fixtures.movie("a", "Parte Um", 2000, 7, "Drama"), "Saga");
        Media sibling = Fixtures.comCollection(Fixtures.movie("b", "Parte Dois", 1980, 4, "Comédia"), "Saga");
        Media similar = Fixtures.movie("c", "Outro Drama", 2000, 7, "Drama");
        assertEquals("b", Similar.neighbors(target, Arrays.asList(similar, sibling), 1).get(0).id);
    }

    @Test
    public void titleWordFindsContinuation() throws Exception {
        Media target = Fixtures.movie("a", "Patrulha-Lunar e a Praia", 2000, 7, "Animação");
        Media sibling = Fixtures.movie("b", "Patrulha-Lunar no Espaço", 2000, 7, "Animação");
        Media other = Fixtures.movie("c", "Rei Leão", 2000, 7, "Animação");
        assertEquals("b", Similar.neighbors(target, Arrays.asList(other, sibling), 1).get(0).id);
    }

    @Test
    public void sameDirectorBreaksTies() throws Exception {
        Media target = Fixtures.comDirector(Fixtures.movie("a", "Alvo", 2000, 7, "Drama"), "Fulana");
        Media same = Fixtures.comDirector(Fixtures.movie("b", "Bravo", 2000, 7, "Drama"), "Fulana");
        Media other = Fixtures.movie("c", "Certo", 2000, 7, "Drama");
        assertEquals("b", Similar.neighbors(target, Arrays.asList(other, same), 1).get(0).id);
    }

    @Test
    public void respectsKAndHandlesEmptyData() throws Exception {
        Media target = Fixtures.movie("a", "", 0, 0);
        List<Media> library = Arrays.asList(Fixtures.movie("b", "", 0, 0), Fixtures.movie("c", "", 0, 0), Fixtures.movie("d", "", 0, 0));
        assertEquals(2, Similar.neighbors(target, library, 2).size());
        assertTrue(Similar.neighbors(target, Arrays.asList(target), 2).isEmpty());
        assertFalse(Similar.neighbors(target, library, 9).isEmpty());
    }
}
