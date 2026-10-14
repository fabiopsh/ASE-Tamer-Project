package it.unipi.ase.tamers.monsters.catalog;

import static org.junit.jupiter.api.Assertions.*;

import it.unipi.ase.tamers.monsters.catalog.Paging.Page;
import it.unipi.ase.tamers.monsters.common.ApiException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PagingTest {
    private static final List<Integer> HUNDRED = IntStream.rangeClosed(1, 100).boxed().toList();

    @Test
    void defaultsAreFirstPageOfTwenty() {
        Paging p = Paging.parse(null, null);
        assertEquals(0, p.page());
        assertEquals(20, p.size());
        assertEquals(new Paging(0, 20), Paging.parse("", " "));
    }

    @Test
    void sizeIsClampedToMax() {
        assertEquals(100, Paging.parse("0", "500").size());
        assertEquals(100, Paging.parse("0", "100").size());
    }

    @Test
    void invalidValuesAreRejectedWith400() {
        for (String[] bad : new String[][] {{"-1", "10"}, {"0", "0"}, {"0", "-5"}, {"x", "10"}, {"0", "1.5"}}) {
            ApiException e = assertThrows(ApiException.class, () -> Paging.parse(bad[0], bad[1]));
            assertEquals(400, e.status());
            assertEquals("validation_error", e.error());
        }
    }

    @Test
    void totalPagesRoundsUp() {
        assertEquals(0, Paging.totalPages(0, 20));
        assertEquals(1, Paging.totalPages(1, 20));
        assertEquals(5, Paging.totalPages(100, 20));
        assertEquals(6, Paging.totalPages(101, 20));
    }

    @Test
    void sliceReturnsTheRequestedPage() {
        Page<Integer> page = new Paging(1, 20).slice(HUNDRED);
        assertEquals(20, page.items().size());
        assertEquals(21, page.items().get(0));
        assertEquals(40, page.items().get(19));
        assertEquals(100, page.total());
        assertEquals(5, page.totalPages());
        assertEquals(1, page.page());
        assertEquals(20, page.size());
    }

    @Test
    void lastPageMayBeShort() {
        Page<Integer> page = new Paging(2, 40).slice(HUNDRED);
        assertEquals(20, page.items().size());
        assertEquals(81, page.items().get(0));
        assertEquals(3, page.totalPages());
    }

    @Test
    void pageBeyondTheEndIsEmptyButKeepsTotals() {
        Page<Integer> page = new Paging(9, 20).slice(HUNDRED);
        assertTrue(page.items().isEmpty());
        assertEquals(100, page.total());
        assertEquals(5, page.totalPages());
    }

    @Test
    void emptyListHasZeroPages() {
        Page<Integer> page = new Paging(0, 20).slice(List.of());
        assertTrue(page.items().isEmpty());
        assertEquals(0, page.total());
        assertEquals(0, page.totalPages());
    }

    @Test
    void hugePageNumberDoesNotOverflow() {
        assertTrue(new Paging(Integer.MAX_VALUE, 100).slice(HUNDRED).items().isEmpty());
    }
}
