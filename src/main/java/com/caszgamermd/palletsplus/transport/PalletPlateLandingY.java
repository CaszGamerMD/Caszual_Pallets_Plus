package com.caszgamermd.palletsplus.transport;

/** Pixel-accurate visual seating of a full pallet on the thin landing pad. */
public final class PalletPlateLandingY {
    public static final double RUNNER_BOTTOM=.035;
    public static final double POWERED_PAD_TOP=1.0/16.0;
    public static final double IDLE_PAD_TOP=2.0/16.0;
    /** Native PalletBlock remains at pad.y+1 to keep golem pickup possible. */
    public static double offset(boolean powered){
        return (powered?POWERED_PAD_TOP:IDLE_PAD_TOP)-1.0-RUNNER_BOTTOM;
    }
    /** A pallet on a Drone Parking Bay renders its feet on the 4/16
     * elevated deck rather than a full block above the drone pad. */
    public static double droneBayOffset(){return 4.0/16.0-1.0-RUNNER_BOTTOM;}
    private PalletPlateLandingY(){}
}