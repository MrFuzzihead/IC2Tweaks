package com.mrfuzzihead.ic2tweaks.enet.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Cross-grid source-offer sharing, mirroring 1.12.2's per-tile remaining amount. */
class PacketOfferLedgerTest {

    @Test
    void seedAppliesTierPowerCap() {
        PacketOfferLedger<String> ledger = new PacketOfferLedger<>();
        ledger.seed("a", 1000.0D, 1, 1);
        ledger.seed("b", 1000.0D, 1, 2);
        ledger.seed("c", 10.0D, 1, 1);

        assertEquals(32.0D, ledger.remaining("a"), 1e-9D);
        assertEquals(64.0D, ledger.remaining("b"), 1e-9D);
        assertEquals(10.0D, ledger.remaining("c"), 1e-9D);
        assertTrue(ledger.contains("a"));
        assertFalse(ledger.contains("missing"));
        assertEquals(0.0D, ledger.remaining("missing"), 1e-9D);
    }

    @Test
    void updateCarriesRemainderToTheNextGrid() {
        PacketOfferLedger<String> ledger = new PacketOfferLedger<>();
        ledger.seed("source", 64.0D, 1, 2);

        // first grid uses 20.4, leaving 43.6 for the second grid
        ledger.update("source", 43.6D);
        assertEquals(43.6D, ledger.remaining("source"), 1e-9D);

        // a fully exhausted source stores zero/negative without producing energy
        ledger.update("source", -0.2D);
        assertEquals(-0.2D, ledger.remaining("source"), 1e-9D);

        ledger.clear();
        assertFalse(ledger.contains("source"));
        assertEquals(0.0D, ledger.remaining("source"), 1e-9D);
    }
}
