package com.caszgamermd.palletsplus.transport;

/** Stable terrain-following altitude controller with bounded climb and descent. */
public final class DroneFlightMath {
    public static final double CRUISE_CLEARANCE=30;
    public static final double MIN_CLEARANCE=20;
    public static final double MAX_CLEARANCE=40;
    public static double desired(double altitude,double ground){
        if(altitude-ground<MIN_CLEARANCE)return ground+CRUISE_CLEARANCE;
        if(altitude-ground>MAX_CLEARANCE)return ground+CRUISE_CLEARANCE;
        return altitude;
    }
    public static double towards(double current,double goal,double maxStep){
        return current+Math.clamp(goal-current,-maxStep,maxStep);
    }
    /** Avoid flying horizontally into terrain while the aircraft climbs. */
    public static double forwardBudget(double altitude,double nextGround,double maximumSpeed){
        return altitude-nextGround<MIN_CLEARANCE?0:maximumSpeed;
    }
    public static double gentleLanding(double altitude,double padAltitude){
        double remaining=padAltitude-altitude;
        return towards(altitude,padAltitude,remaining<0?.12:.35);
    }
    private DroneFlightMath(){}
}