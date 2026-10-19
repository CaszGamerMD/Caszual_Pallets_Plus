package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.palletsplus.PalletsPlus;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.*;
import java.util.*;

/** Dimension-owned last-known transport positions and discovered terrain. */
public final class TransitWorldData extends SavedData {
    public static final SavedDataType<TransitWorldData> TYPE=new SavedDataType<>(
        PalletsPlus.legacyId("pallet_transit"),TransitWorldData::new,
        Codec.PASSTHROUGH.xmap(TransitWorldData::decodeDynamic,TransitWorldData::encodeDynamic),null);
    public final Map<String,JsonObject> vehicles=new LinkedHashMap<>();
    public final Map<String,JsonObject> stations=new LinkedHashMap<>();
    public final Map<String,Integer> terrain=new LinkedHashMap<>();
    public static TransitWorldData get(ServerLevel l){return l.getDataStorage().computeIfAbsent(TYPE);}
    // Store a compound, not one JSON string: large maps exceed NBT's UTF string limit.
    private static TransitWorldData decodeDynamic(Dynamic<?> value){return decode(value.convert(JsonOps.INSTANCE).getValue().toString());}
    private Dynamic<JsonElement> encodeDynamic(){return new Dynamic<>(JsonOps.INSTANCE,JsonParser.parseString(encode()));}
    private static TransitWorldData decode(String text){
        TransitWorldData d=new TransitWorldData();
        try{JsonObject o=JsonParser.parseString(text).getAsJsonObject();
            if(o.has("vehicles"))o.getAsJsonObject("vehicles").entrySet().forEach(e->{
                JsonObject vehicle=e.getValue().getAsJsonObject();
                // NBT represents booleans as bytes; restore the JSON boolean explicitly.
                JsonElement active=vehicle.get("active");
                if(active!=null&&active.isJsonPrimitive()&&active.getAsJsonPrimitive().isNumber())vehicle.addProperty("active",active.getAsInt()!=0);
                d.vehicles.put(e.getKey(),vehicle);
            });
            if(o.has("stations"))o.getAsJsonObject("stations").entrySet().forEach(e->d.stations.put(e.getKey(),e.getValue().getAsJsonObject()));
            if(o.has("terrain"))o.getAsJsonObject("terrain").entrySet().forEach(e->d.terrain.put(e.getKey(),e.getValue().getAsInt()));
        }catch(RuntimeException ignored){}return d;
    }
    private String encode(){JsonObject o=new JsonObject(),v=new JsonObject(),s=new JsonObject(),t=new JsonObject();vehicles.forEach(v::add);stations.forEach(s::add);terrain.forEach(t::addProperty);o.add("vehicles",v);o.add("stations",s);o.add("terrain",t);return o.toString();}
    public void record(TransportVehicle v){
        JsonObject o=new JsonObject();o.addProperty("id",v.getStringUUID());o.addProperty("kind",v.kind());
        o.addProperty("x",v.getX());o.addProperty("y",v.getY());o.addProperty("z",v.getZ());o.addProperty("yaw",v.getYRot());
        // Keep saved drone destination fields compatible with existing worlds.
        // Ground transports record a short projected heading, not a global route:
        // tickets follow turns as each loaded vehicle advances.
        net.minecraft.core.BlockPos target=v.kind()==4?v.flightDestination():
            v.blockPosition().relative(v.getDirection(),v.kind()==1?96:48);
        o.addProperty("active",v.transportChunksActive());o.addProperty("tx",target.getX());o.addProperty("tz",target.getZ());
        o.addProperty("status",v.transitStatus());o.addProperty("seen",v.level().getGameTime());
        if(v.hasFollowingCars()){
            JsonArray trailers=new JsonArray();
            var positions=v.trailingCarPositions();
            for(var position:positions){
                JsonObject car=new JsonObject();
                car.addProperty("x",position.x);car.addProperty("z",position.z);
                trailers.add(car);
            }
            // After a world restart, the engine may load before all the
            // trailers. Retain the last valid positions so the tickets can
            // bring those cars back instead of discarding their corridor.
            if(trailers.isEmpty()){
                JsonObject previous=vehicles.get(v.getStringUUID());
                if(previous!=null&&previous.has("trailers")&&previous.get("trailers").isJsonArray())
                    trailers=previous.getAsJsonArray("trailers").deepCopy();
            }
            o.add("trailers",trailers);
        }
        JsonArray pallets=new JsonArray();for(int i=0;i<v.capacity();i++)if(!v.cargo[i].empty()){JsonObject p=new JsonObject();p.addProperty("slot",i+1);String name=v.cargo[i].deck.getHoverName().getString();p.addProperty("name",name.substring(0,Math.min(80,name.length())));p.addProperty("stacks",v.cargo[i].items.stream().filter(s->!s.isEmpty()).count());pallets.add(p);}
        o.add("pallets",pallets);vehicles.put(v.getStringUUID(),o);setDirty();
    }
    public void forget(String id){if(vehicles.remove(id)!=null)setDirty();}
    public void station(net.minecraft.core.BlockPos pos,String kind){String id=pos.toShortString();JsonObject o=new JsonObject();o.addProperty("x",pos.getX());o.addProperty("y",pos.getY());o.addProperty("z",pos.getZ());o.addProperty("kind",kind);if(!o.equals(stations.put(id,o)))setDirty();}
    public void terrain(int x,int z,int color){String key=x+","+z;if(terrain.size()>=65536&&!terrain.containsKey(key))terrain.remove(terrain.keySet().iterator().next());if(!java.util.Objects.equals(terrain.put(key,color),color))setDirty();}
}
