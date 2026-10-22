package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.caszualadditions.racks.*;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Server-side bay controller; one outstanding warehouse request per condition.
 * A physically attached Rack Computer is required for warehouse signaling.
 * Pallets remain physical and are never copied into a virtual buffer. */
public final class DroneBayAutomation {
    public static void initialize(){
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(server.getTickCount()%10!=0)return;
            for(ServerLevel level:server.getAllLevels())tick(level);
        });
    }
    private static BlockPos bayPos(JsonObject record){
        try{return new BlockPos(record.get("x").getAsInt(),record.get("y").getAsInt(),record.get("z").getAsInt());}
        catch(RuntimeException ex){return null;}
    }
    private static boolean warehouseReady(ServerLevel level,DroneBayRoutes.Bay bay){
        BlockPos computer=bay.warehouse();
        return computer!=null&&level.hasChunkAt(computer)
            &&level.getBlockState(computer).is(RackContent.RACK_COMPUTER)
            &&!WarehouseNetwork.fromComputer(level,computer).computers().isEmpty();
    }
    private static BlockPos staged(ServerLevel level,BlockPos bay){
        if(!level.hasChunkAt(bay))return null;
        // First priority: pallet left physically ON the bay by a landed
        // drone. It is an ordinary wrapped four-cell Caszual pallet with
        // a real 216-slot root inventory, not a transport entity or item.
        BlockPos onBay=DroneBayCargo.find(level,bay);
        if(onBay!=null)return onBay;
        Direction facing=level.getBlockState(bay).getValue(GuidewayBlock.FACING);
        for(Direction d:new Direction[]{facing,facing.getClockWise(),facing.getCounterClockWise(),facing.getOpposite()})
            for(int dy:new int[]{0,1,-1}){
                BlockPos check=bay.relative(d,3).above(dy);
                if(!level.hasChunkAt(check))continue;
                var pallet=StorageGroundPalletTransfer.wrappedController(level,check);
                if(pallet!=null)return pallet.getBlockPos();
            }
        return null;
    }
    private static void signal(ServerLevel level,BlockPos root,boolean on){
        if(!DroneBayRoutes.valid(level,root))return;
        var center=level.getBlockState(root);
        Direction facing=center.getValue(GuidewayBlock.FACING);
        for(int i=0;i<9;i++){
            BlockPos tile=GuidewayBlock.cell(root,facing,i);
            var state=level.getBlockState(tile);
            if(state.getValue(GuidewayBlock.POWERED)!=on)
                level.setBlock(tile,state.setValue(GuidewayBlock.POWERED,on),3);
        }
    }
    /** Optional Additions bridge: keep the Pallets Plus addon compileable
     * against its already-published dependency. An older Additions runtime
     * must NOT silently fall back to the replacement-pallet remote exchange.
     * Inventory stays physically on the INPUT bay until the updated
     * Additions worker dispatcher is installed. */
    private static String dispatchNoReplacement(ServerLevel level,DroneBayRoutes.Bay bay,BlockPos cargo){
        try{
            UUID owner=UUID.fromString(bay.owner());
            Class<?> bridge=Class.forName(
                "com.caszgamermd.caszualadditions.racks.DroneBayNoReplacementDispatch");
            var dispatch=bridge.getMethod("request",ServerLevel.class,UUID.class,BlockPos.class,BlockPos.class);
            return (String)dispatch.invoke(null,level,owner,bay.warehouse(),cargo);
        }catch(ClassNotFoundException|NoSuchMethodException ex){
            return "WAITING: install Caszual Additions with three-Golem INPUT bay pickup; pallet held safely.";
        }catch(IllegalArgumentException ex){
            return "INVALID: warehouse bay link has no valid owner.";
        }catch(ReflectiveOperationException ex){
            return "WAITING: Additions input-crew dispatcher unavailable: "+ex.getClass().getSimpleName();
        }
    }
    private static void tick(ServerLevel level){
        var data=TransitWorldData.get(level);
        for(JsonObject record:new ArrayList<>(data.stations.values())){
            if(!record.has("kind")||!"bay".equals(record.get("kind").getAsString()))continue;
            BlockPos pos=bayPos(record);if(pos==null||!level.hasChunkAt(pos))continue;
            var bay=DroneBayRoutes.get(level,pos);
            if(bay==null||!DroneBayRoutes.valid(level,pos))continue;
            // Physical redstone presence sensor: stay powered as LONG as
            // a wrapped pallet stands on the Parking Bay, not just for a
            // single request tick. Golems and forklifts clearing it switch
            // the level back off at the next sensor poll.
            BlockPos onBay=DroneBayCargo.find(level,pos);
            signal(level,pos,onBay!=null);
            BlockPos cargo=bay.input()?onBay:staged(level,pos);
            if(bay.output()){
                if(bay.linked()!=null&&DroneBayRoutes.receiverReady(level,bay.linked())){
                    var drones=level.getEntitiesOfClass(TransportVehicle.class,
                        new AABB(pos).inflate(4,5,4),v->v.kind()==4&&v.atOutputBay(pos));
                    for(var drone:drones)drone.tickAutomatedBay(level,pos,bay.linked());
                }
                if(cargo==null&&warehouseReady(level,bay)){
                    if(!record.has("requested")||!record.get("requested").getAsBoolean()){
                        record.addProperty("requested",true);
                        record.addProperty("request","OUTPUT_AISLE_PALLET");
                        data.setDirty();
                        // A fresh warehouse request is sent only once for this
                        // empty-bay condition. The attached warehouse network
                        // may be serviced by connected automation or Golems.
                    }
                }else if(cargo!=null&&record.has("requested")&&record.get("requested").getAsBoolean()){
                    record.addProperty("requested",false);data.setDirty();
                }
            }else{
                if(cargo!=null&&warehouseReady(level,bay)){
                    if(!record.has("requested")||!record.get("requested").getAsBoolean()){
                        record.addProperty("requested",true);record.addProperty("pulse",true);
                        record.addProperty("request","THREE_GOLEM_PICKUP_NO_REPLACEMENT_TO_INPUT_RACK");
                        data.setDirty();
                    }
                    // Existing Additions REMOTE dispatch is a real four-golem
                    // crew with full pallet escrow and rack/PTP destination.
                    // Retry only while target physically exists; its dispatcher
                    // itself refuses duplicate target missions.
                    if(level.getGameTime()%40==0&&!bay.owner().isEmpty()){
                        // An INPUT bay uses three independently walking
                        // Golems. The existing four-Golem REMOTE EXCHANGE
                        // would place an unwanted replacement deck here.
                        String result=dispatchNoReplacement(level,bay,cargo);
                        String before=record.has("dispatch")?record.get("dispatch").getAsString():"";
                        if(!result.equals(before)){
                            record.addProperty("dispatch",result);
                            data.setDirty();
                        }
                    }
                }else if(cargo==null&&record.has("requested")&&record.get("requested").getAsBoolean()){
                    record.addProperty("requested",false);
                    record.remove("dispatch");
                    data.setDirty();
                }
            }
        }
    }
    private DroneBayAutomation(){}
}
