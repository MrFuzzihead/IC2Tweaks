package com.mrfuzzihead.ic2tweaks.enet.packet;

import java.util.Map;

/**
 * Contract implemented by the {@code ic2.core.energy.Grid} mixin so the live adapter (which lives in
 * IC2's package) can attach packet-model state to a grid and read its node map.
 * <p>
 * The raw {@code Map} return type is deliberate: {@code Grid.nodes} is a {@code Map<Integer, Node>} and
 * {@code Node} is package-private in {@code ic2.core.energy}. Mixin classes in the mod's package cannot
 * name that type, but a raw map descriptor matches and the IC2-package adapter casts internally.
 */
public interface PacketGridStateHolder {

    Object ic2tweaks$getPacketState();

    void ic2tweaks$setPacketState(Object state);

    Map ic2tweaks$rawNodes();
}
