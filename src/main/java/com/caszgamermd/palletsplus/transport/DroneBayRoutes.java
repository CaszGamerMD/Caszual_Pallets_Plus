package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.caszualadditions.racks.RackContent;
import com.caszgamermd.caszualadditions.racks.WarehouseNetwork;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Save-backed, dimension-local parking bay settings and one-way air routes.
 * A bay is a nine-cell GuidewayBlock module, addressed by its center. */
public final class DroneBayRoutes {
    public record Bay(BlockPos pos,boolean input,BlockPos linked,BlockPos warehouse,String owner){
        public boolean output(){return !input;}
    }
    private static String key(BlockPos p){return p.toShortString();}
    private static BlockPos pos(JsonObject o,String name){
        try{return o.has(name)?BlockPos.of(o.get(name).getAsLong()):null;}
        catch(RuntimeException ex){return null;}
    }
    private static String text(JsonObject o,String name,String otherwise){
        return o.has(name)?o.get(name).getAsString():otherwise;
    }
    public static boolean valid(ServerLevel level,BlockPos p){
        return level.hasChunkAt(p)&&level.getBlockState(p).is(TransportContent.BAY)&&GuidewayBlock.complete(level,p);
    }
    public static Bay get(ServerLevel level,BlockPos root){
        JsonObject o=TransitWorldData.get(level).stations.get(key(root));
        if(o==null||!"bay".equals(text(o,"kind","")))return null;
        return new Bay(root,"input".equals(text(o,"mode","output")),pos(o,"linked"),pos(o,"warehouse"),text(o,"owner",""));
    }
    private static JsonObject edit(ServerLevel level,BlockPos root){
        var data=TransitWorldData.get(level);
        JsonObject o=data.stations.computeIfAbsent(key(root),k->new JsonObject());
        o.addProperty("x",root.getX());o.addProperty("y",root.getY());o.addProperty("z",root.getZ());o.addProperty("kind","bay");
        if(!o.has("mode"))o.addProperty("mode","output");
        data.setDirty();
        return o;
    }
    public static String mode(ServerLevel level,BlockPos root){
        if(!valid(level,root))return "Finish the nine-block Drone Parking Bay first.";
        for(var other:level.getEntitiesOfClass(TransportVehicle.class,new AABB(root).inflate(3,6,3)))
            if(other.kind()==4&&other.flightActive())return "Wait until the drone finishes its trip.";
        JsonObject o=edit(level,root);boolean input="output".equals(text(o,"mode","output"));
        o.addProperty("mode",input?"input":"output");
        // A changed role invalidates existing routes to avoid reverse shipments.
        o.remove("linked");
        for(var entry:TransitWorldData.get(level).stations.values())
            if(pos(entry,"linked")!=null&&root.equals(pos(entry,"linked")))entry.remove("linked");
        TransitWorldData.get(level).setDirty();
        return "Drone Parking Bay: "+(input?"INPUT (receives pallets)":"OUTPUT (sends pallets)")+
            ". Use the Parking Bay Link tool to connect an OUTPUT to an INPUT.";
    }
    public static String warehouse(ServerLevel level,BlockPos root,BlockPos computer,String owner){
        if(!valid(level,root))return "The bay is incomplete.";
        if(!level.hasChunkAt(computer)||!level.getBlockState(computer).is(RackContent.RACK_COMPUTER))
            return "The selected warehouse Rack Computer is unavailable.";
        if(root.distSqr(computer)>128L*128L)return "Bay must be within 128 blocks of its Rack Computer.";
        var network=WarehouseNetwork.fromComputer(level,computer);
        if(network.computers().isEmpty())return "This Rack Computer is not on an active warehouse network.";
        JsonObject o=edit(level,root);o.addProperty("warehouse",computer.asLong());o.addProperty("owner",owner);
        TransitWorldData.get(level).setDirty();
        return "Bay connected to warehouse at "+computer.toShortString()+".";
    }
    public static String link(ServerLevel level,BlockPos output,BlockPos input){
        if(output.equals(input)||!valid(level,output)||!valid(level,input))return "Select two complete, different parking bays.";
        var source=get(level,output),dest=get(level,input);
        if(source==null||!source.output()||dest==null||!dest.input())
            return "First choose an OUTPUT bay, then an INPUT bay.";
        edit(level,output).addProperty("linked",input.asLong());
        TransitWorldData.get(level).setDirty();
        return "Air route linked: OUTPUT "+output.toShortString()+" to INPUT "+input.toShortString()+".";
    }
    public static boolean receiverReady(ServerLevel level,BlockPos input){
        Bay b=get(level,input);return b!=null&&b.input()&&valid(level,input);
    }
    /** Export a stable, bounded link list to the Pallet Transit Computer. */
    public static com.google.gson.JsonArray links(ServerLevel level){
        var result=new com.google.gson.JsonArray();
        for(var entry:TransitWorldData.get(level).stations.values()){
            if(!"bay".equals(text(entry,"kind",""))||!entry.has("x")||!entry.has("y")||!entry.has("z"))continue;
            BlockPos p=new BlockPos(entry.get("x").getAsInt(),entry.get("y").getAsInt(),entry.get("z").getAsInt());
            Bay bay=get(level,p);if(bay==null)continue;
            JsonObject link=new JsonObject();link.addProperty("x",p.getX());link.addProperty("y",p.getY());link.addProperty("z",p.getZ());
            link.addProperty("role",bay.input()?"INPUT":"OUTPUT");
            if(bay.linked()!=null)link.addProperty("to",bay.linked().toShortString());
            if(bay.warehouse()!=null)link.addProperty("warehouse",bay.warehouse().toShortString());
            result.add(link);if(result.size()>=512)break;
        }
        return result;
    }
    private DroneBayRoutes(){}
}
