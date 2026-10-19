package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.palletsplus.PalletsPlus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Expiring, mod-owned simulation tickets for drone, train and conveyor trips.
 * Saved active transport records re-establish tickets after a server restart.
 * Parked vehicles and stopped station cars do not keep chunks loaded.
 * Never modifies the player's /forceload list. */
public final class DroneChunkLoading {
    private static TicketType ticket;
    private static final Map<String,Integer> absentTicks=new HashMap<>();
    public static void initialize(){
        ticket=Registry.register(BuiltInRegistries.TICKET_TYPE,PalletsPlus.legacyId("pallet_drone"),
            new TicketType(60,TicketType.FLAG_LOADING|TicketType.FLAG_SIMULATION|TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%10!=0)return;
            for(var level:server.getAllLevels()){
                var data=TransitWorldData.get(level);
                for(var entry:new ArrayList<>(data.vehicles.entrySet())){
                    var record=entry.getValue();
                    if(!record.has("active")||!record.get("active").getAsBoolean()||
                        !record.has("kind")||!record.has("x")||!record.has("z")||
                        !record.has("tx")||!record.has("tz"))continue;
                    int kind=record.get("kind").getAsInt();
                    if(kind!=0&&kind!=1&&kind!=4)continue;
                    double x=record.get("x").getAsDouble(),z=record.get("z").getAsDouble();
                    double tx=record.get("tx").getAsDouble()+.5,tz=record.get("tz").getAsDouble()+.5;
                    if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(tx)||!Double.isFinite(tz))continue;
                    ChunkPos here=new ChunkPos((int)Math.floor(x)>>4,(int)Math.floor(z)>>4);
                    // Radius 1 limits the footprint of ground transport; drone
                    // flights keep their existing radius-2 destination coverage.
                    int radius=kind==4?2:1;
                    for(var chunk:TransportChunkCorridor.plan(kind,x,z,tx,tz))
                        level.getChunkSource().addTicketWithRadius(ticket,new ChunkPos(chunk.x(),chunk.z()),radius);
                    String key=level.dimension().identifier()+"/"+entry.getKey();
                    net.minecraft.world.entity.Entity e;
                    try{e=level.getEntity(UUID.fromString(entry.getKey()));}catch(IllegalArgumentException ex){data.forget(entry.getKey());continue;}
                    if(e!=null){absentTicks.remove(key);continue;}
                    // Do not expire a record while its chunk is still loading.
                    if(level.getChunkSource().hasChunk(here.x(),here.z())&&absentTicks.merge(key,10,Integer::sum)>600){data.forget(entry.getKey());absentTicks.remove(key);}
                }
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->absentTicks.clear());
    }
    private DroneChunkLoading(){}
}
