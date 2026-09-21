package com.malikksh.wastickers;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PackNamesTest {
    @Test public void normalizationRejectsCaseSpaceAndUnicodeVariants() {
        assertEquals(PackNames.key("Мои стикеры"), PackNames.key("  МОИ\u00a0  стикеры  "));
        assertEquals(PackNames.key("Café"), PackNames.key("Cafe\u0301"));
        assertEquals(PackNames.key("Pack 2"), PackNames.key("Ｐａｃｋ ２"));
    }
    @Test public void defaultFillsFirstAvailableSuffix() {
        assertEquals("Мои стикеры 3", PackNames.next("Мои стикеры",
                Arrays.asList(" МОИ СТИКЕРЫ ", "Мои стикеры 2", "Мои стикеры 4")));
        assertEquals("Мои стикеры", PackNames.next("Мои стикеры", Arrays.asList("Другой")));
    }
    @Test public void validationAndVersionAreDeterministic() {
        assertFalse(PackNames.valid("\u00a0  "));
        assertFalse(PackNames.valid("x".repeat(61)));
        assertTrue(PackNames.valid("Набор 🐈"));
        assertEquals("2", PackNames.nextVersion("1"));
        assertEquals("1789000000001", PackNames.nextVersion("1789000000000"));
    }
}
