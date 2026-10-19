package com.mrfuzzihead.ic2tweaks.enet;

/**
 * Cable metadata copied from the pinned IC2 build ({@code 2.2.828-experimental}). The values mirror
 * {@code ic2.core.block.wiring.TileEntityCable.getConductionLoss()} exactly so the tooltip and the
 * cable effects pass agree with what IC2 actually simulates.
 * <p>
 * The meta layout is the 1.7.10 item/block metadata: 0 insulated copper, 1 copper, 2 gold,
 * 3 insulated gold, 4 2x insulated gold, 5 HV, 6 insulated HV, 7 2x insulated HV, 8 3x insulated HV,
 * 9 glass fibre, 10 tin, 11 detector, 12 splitter, 13 insulated tin, 14 legacy insulated tin.
 * <p>
 * Note: {@code 0.025} for insulated tin (meta 13) is an IC2 data quirk (identical to glass fibre and
 * lower than bare tin); it is reported verbatim so the tooltip matches the simulation.
 */
public final class EnetCableData {

    private EnetCableData() {}

    /**
     * @param meta cable item/block metadata
     * @return the cable's conduction loss in EU/block (the per-node inner loss IC2 gives the cable),
     *         or {@code 0} for a non-cable metadata value
     */
    public static double conductionLoss(int meta) {
        switch (meta) {
            case 0:
                return 0.2D;
            case 1:
                return 0.3D;
            case 2:
                return 0.5D;
            case 3:
                return 0.45D;
            case 4:
                return 0.4D;
            case 5:
                return 1.0D;
            case 6:
                return 0.95D;
            case 7:
                return 0.9D;
            case 8:
                return 0.8D;
            case 9:
                return 0.025D;
            case 10:
                return 0.2D;
            case 11:
            case 12:
                return 0.5D;
            case 13:
                return 0.025D;
            case 14:
                return 0.15D;
            default:
                return 0D;
        }
    }
}
