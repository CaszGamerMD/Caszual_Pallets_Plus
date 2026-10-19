package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.palletsplus.PalletsPlus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Expiring, mod-owned simulation tickets. No /forceload state is changed.
 * Saved flight records re-establish tickets after a server restart. */
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
                    var record=entry.getValue();if(!record.get("active").getAsBoolean())continue;
                    double x=record.get("x").getAsDouble(),z=record.get("z").getAsDouble();
                    double tx=record.get("tx").getAsDouble()+.5,tz=record.get("tz").getAsDouble()+.5;
                    double dx=tx-x,dz=tz-z,len=Math.hypot(dx,dz);
                    ChunkPos here=new ChunkPos((int)Math.floor(x)>>4,(int)Math.floor(z)>>4);
                    Set<ChunkPos> chunks=new HashSet<>();chunks.add(here);
                    // Continuous two-second corridor at 45 m/s, with margin for saved-position refresh.
                    for(double ahead=0;ahead<=Math.min(96,len);ahead+=16)
                        chunks.add(new ChunkPos((int)Math.floor(x+(len==0?0:dx/len*ahead))>>4,(int)Math.floor(z+(len==0?0:dz/len*ahead))>>4));
                    chunks.add(new ChunkPos((int)Math.floor(tx)>>4,(int)Math.floor(tz)>>4));
                    for(var chunk:chunks)level.getChunkSource().addTicketWithRadius(ticket,chunk,2);
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
