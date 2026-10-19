package com.mrfuzzihead.ic2tweaks.enet.packet;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-tick source-offer ledger shared across grids, mirroring 1.12.2's per-tile {@code Tile.amount}.
 * A 1.7.10 source tile can belong to more than one grid; the first grid's distribution reduces the
 * remaining offer and the next grid sees the reduced value.
 * <p>
 * Pure on purpose: the adapter keys it by the IC2 {@code Tile} identity, tests key it by anything.
 */
public final class PacketOfferLedger<K> {

    private final Map<K, Double> remaining = new HashMap<>();

    /** Seeds the ledger with 1.12.2's source offer cap. */
    public void seed(K key, double offer, int tier, int packets) {
        remaining.put(key, PacketMath.capOffer(offer, tier, packets));
    }

    public boolean contains(K key) {
        return remaining.containsKey(key);
    }

    public double remaining(K key) {
        Double value = remaining.get(key);
        return value == null ? 0.0D : value;
    }

    /** Stores the remainder after one grid's distribution. May be zero or negative (1.12.2 clamps on read). */
    public void update(K key, double remainingOffer) {
        remaining.put(key, remainingOffer);
    }

    public void clear() {
        remaining.clear();
    }
}
