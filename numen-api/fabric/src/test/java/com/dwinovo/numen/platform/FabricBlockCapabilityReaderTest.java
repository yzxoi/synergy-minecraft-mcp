package com.dwinovo.numen.platform;

import org.junit.jupiter.api.Test;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FabricBlockCapabilityReaderTest {

    @Test
    void collectDeduplicatesOnlyByIdentityAndKeepsEverySide() {
        Map<String, List<String>> storages = new IdentityHashMap<>();
        String first = new String("same value");
        String second = new String("same value");

        FabricBlockCapabilityReader.collect(storages, first, "all");
        FabricBlockCapabilityReader.collect(storages, first, "north");
        FabricBlockCapabilityReader.collect(storages, second, "south");
        FabricBlockCapabilityReader.collect(storages, null, "west");

        assertEquals(2, storages.size());
        assertEquals(List.of("all", "north"), storages.get(first));
        assertEquals(List.of("south"), storages.get(second));
    }

    @Test
    void convertsFabricDropletsToMillibuckets() {
        assertEquals("0", FabricBlockCapabilityReader.formatMilliBuckets(0));
        assertEquals("1", FabricBlockCapabilityReader.formatMilliBuckets(81));
        assertEquals("1000", FabricBlockCapabilityReader.formatMilliBuckets(81_000));
        assertEquals("0.012346", FabricBlockCapabilityReader.formatMilliBuckets(1));
    }
}
