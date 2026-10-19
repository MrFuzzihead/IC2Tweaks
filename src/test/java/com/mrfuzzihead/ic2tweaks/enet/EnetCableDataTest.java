package com.mrfuzzihead.ic2tweaks.enet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins the cable metadata table to the values in the pinned IC2 build, so a tooltip (or a future
 * absorption override) cannot silently drift from the values IC2 actually simulates.
 */
class EnetCableDataTest {

    @Test
    void conductionLossMatchesIc2CableTable() {
        assertEquals(0.2D, EnetCableData.conductionLoss(0)); // insulated copper
        assertEquals(0.3D, EnetCableData.conductionLoss(1)); // copper
        assertEquals(0.5D, EnetCableData.conductionLoss(2)); // gold
        assertEquals(0.45D, EnetCableData.conductionLoss(3)); // insulated gold
        assertEquals(0.4D, EnetCableData.conductionLoss(4)); // 2x insulated gold
        assertEquals(1.0D, EnetCableData.conductionLoss(5)); // HV
        assertEquals(0.95D, EnetCableData.conductionLoss(6)); // insulated HV
        assertEquals(0.9D, EnetCableData.conductionLoss(7)); // 2x insulated HV
        assertEquals(0.8D, EnetCableData.conductionLoss(8)); // 3x insulated HV
        assertEquals(0.025D, EnetCableData.conductionLoss(9)); // glass fibre
        assertEquals(0.2D, EnetCableData.conductionLoss(10)); // tin
        assertEquals(0.5D, EnetCableData.conductionLoss(11)); // detector
        assertEquals(0.5D, EnetCableData.conductionLoss(12)); // splitter
        // IC2 quirk: insulated tin reports the glass-fibre loss, lower than bare tin. Reported verbatim.
        assertEquals(0.025D, EnetCableData.conductionLoss(13)); // insulated tin
        assertEquals(0.15D, EnetCableData.conductionLoss(14)); // legacy insulated tin
    }

    @Test
    void nonCableMetadataHasNoLoss() {
        assertEquals(0D, EnetCableData.conductionLoss(-1));
        assertEquals(0D, EnetCableData.conductionLoss(15));
        assertEquals(0D, EnetCableData.conductionLoss(Integer.MAX_VALUE));
    }
}
