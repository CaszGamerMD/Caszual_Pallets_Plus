package com.caszgamermd.palletsplus.transport;

import java.util.LinkedHashSet;
import java.util.Set;

/** Bounded chunk-ticket footprint, independent of Minecraft world loading.
 * One set is refreshed every ten ticks, then expires shortly after a stop.
 * Ground carriers ticket forward along their current heading; a train engine
 * additionally tickets behind itself to keep a ten-car consist simulated. */
public final class TransportChunkCorridor {
    public record Chunk(int x,int z){}
    private static final double TRAIN_REAR=96,TRAIN_AHEAD=96,CONVEYOR_AHEAD=48,DRONE_AHEAD=96;
    private static final int SAMPLE_SPACING=16;

    private static Chunk chunk(double x,double z){
        return new Chunk(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4);
    }
    public static Set<Chunk> plan(int kind,double x,double z,double tx,double tz){
        Set<Chunk> result=new LinkedHashSet<>();
        if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(tx)||!Double.isFinite(tz))return result;
        result.add(chunk(x,z));
        double dx=tx-x,dz=tz-z,length=Math.hypot(dx,dz);
        if(length<=1e-8)return result;
        double vx=dx/length,vz=dz/length;
        double forward=kind==4?Math.min(DRONE_AHEAD,length):kind==1?TRAIN_AHEAD:kind==0?CONVEYOR_AHEAD:0;
        for(int distance=SAMPLE_SPACING;distance<=forward;distance+=SAMPLE_SPACING)
            result.add(chunk(x+vx*distance,z+vz*distance));
        if(kind==1)for(int distance=SAMPLE_SPACING;distance<=TRAIN_REAR;distance+=SAMPLE_SPACING)
            result.add(chunk(x-vx*distance,z-vz*distance));
        // Preserve the existing drone behavior: load the remote destination bay
        // without loading every intervening chunk along a very long flight.
        if(kind==4)result.add(chunk(tx,tz));
        return result;
    }
    private TransportChunkCorridor(){}
}
