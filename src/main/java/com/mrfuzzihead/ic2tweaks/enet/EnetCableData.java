package com.mrfuzzihead.ic2tweaks.enet;

/**
 * Cable metadata for the energy-net overload system. The loss table is copied from the pinned IC2
 * build ({@code 2.2.828-experimental}) so the tooltip matches what IC2 actually simulates; the
 * absorption table ports IC2 Experimental 1.12.2's insulation-based shock protection.
 * <p>
 * The meta layout is the 1.7.10 item/block metadata: 0 insulated copper, 1 copper, 2 gold,
 * 3 insulated gold, 4 2x insulated gold, 5 HV, 6 insulated HV, 7 2x insulated HV, 8 3x insulated HV,
 * 9 glass fibre, 10 tin, 11 detector, 12 splitter, 13 insulated tin, 14 legacy insulated tin.
 */
public final class EnetCableData {

    /**
     * Absorption used for cables that have no insulation layer at all (glass fibre, detector,
     * splitter). IC2 1.12.2 uses {@code Integer.MAX_VALUE} for those, so they can never shock.
     */
    public static final double NO_SHOCK = Integer.MAX_VALUE;

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

    /**
     * Shock protection of an IC2 cable, using IC2 Experimental 1.12.2's rule:
     * {@code getPowerFromTier(insulation)} for tin and {@code getPowerFromTier(insulation + 1)} for
     * every other cable, with cables that cannot be insulated immune. This makes bare tin deliberately
     * the most sensitive common cable (8 EU/t) while bare copper/gold/HV sit at 32 EU/t.
     *
     * @param meta cable item/block metadata
     * @return the insulation energy absorption in EU/t, or {@link #NO_SHOCK}
     */
    public static double insulationEnergyAbsorption(int meta) {
        switch (meta) {
            case 9: // glass fibre
            case 11: // detector
            case 12: // splitter
                return NO_SHOCK;
            case 10: // tin, 0 layers
                return tierPower(0);
            case 13: // insulated tin
            case 14: // legacy insulated tin
                return tierPower(1);
            case 1: // copper, 0 layers
            case 2: // gold, 0 layers
            case 5: // HV, 0 layers
                return tierPower(1);
            case 0: // insulated copper
            case 3: // insulated gold
            case 6: // insulated HV
                return tierPower(2);
            case 4: // 2x insulated gold
            case 7: // 2x insulated HV
                return tierPower(3);
            case 8: // 3x insulated HV
                return tierPower(4);
            default:
                return NO_SHOCK;
        }
    }

    /** {@code EnergyNet.getPowerFromTier}, duplicated so the table stays pure and unit-testable. */
    private static double tierPower(int tier) {
        return tier < 14 ? (8 << (2 * tier)) : 8D * Math.pow(4D, tier);
    }
}
