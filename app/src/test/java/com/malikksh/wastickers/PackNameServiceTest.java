package com.malikksh.wastickers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PackNameServiceTest {
    @Test
    public void normalizationCollapsesWhitespaceAndUsesUnicodeSafeCaseKey() {
        assertEquals("Мои стикеры", PackNameService.normalizeDisplayName("  Мои\t\nстикеры  "));
        assertEquals(
                PackNameService.canonicalKey("Мои стикеры"),
                PackNameService.canonicalKey(" МОИ   СТИКЕРЫ ")
        );
        assertEquals(
                PackNameService.canonicalKey("Café"),
                PackNameService.canonicalKey("Cafe\u0301")
        );
    }

    @Test
    public void defaultGeneratorUsesFirstFreeSuffix() {
        List<PackStore.Pack> packs = Arrays.asList(
                pack("one", "Мои стикеры"),
                pack("two", "мои   стикеры 2")
        );

        assertEquals("Мои стикеры 3", PackNameService.nextAvailableName(
                packs, PackNameService.DEFAULT_NAME));
    }

    @Test
    public void duplicatesConflictAfterCaseWhitespaceAndUnicodeNormalization() {
        List<PackStore.Pack> packs = new ArrayList<>();
        packs.add(pack("one", "Мои стикеры"));
        packs.add(pack("cafe", "Café"));

        assertFalse(PackNameService.isAvailable(packs, " мои   СТИКЕРЫ ", null));
        assertFalse(PackNameService.isAvailable(packs, "Cafe\u0301", null));
        assertTrue(PackNameService.isAvailable(packs, "Другой набор", null));
    }

    @Test
    public void renameCanKeepOwnNameButCannotTakeAnotherPackName() {
        List<PackStore.Pack> packs = Arrays.asList(
                pack("one", "Первый"),
                pack("two", "Второй")
        );

        assertTrue(PackNameService.isAvailable(packs, " первый ", "one"));
        assertFalse(PackNameService.isAvailable(packs, "ВТОРОЙ", "one"));
    }

    @Test
    public void duplicateNameGetsDeterministicSuffix() {
        List<PackStore.Pack> packs = Arrays.asList(
                pack("one", "Набор"),
                pack("copy", "Набор — копия")
        );

        assertEquals("Набор — копия 2",
                PackNameService.nextAvailableName(packs, "Набор — копия"));
    }

    private static PackStore.Pack pack(String id, String name) {
        return new PackStore.Pack(id, name, 3, "1", false);
    }
}
