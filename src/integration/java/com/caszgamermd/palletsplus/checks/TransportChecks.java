package com.caszgamermd.palletsplus.checks;

import com.caszgamermd.palletsplus.transport.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.LoggerFactory;
import java.util.*;

/** Real simulation checks in a disposable CI world, enabled only by the dedicated run configuration. */
public final class TransportChecks implements ModInitializer {
    private TransportVehicle engine,conveyor,extra,endOfLine;
    private final List<TransportVehicle> cars=new ArrayList<>();
    private boolean conveyorSpeed,holdStop,indexed,coupled;
    private int elapsed,coupledTick,nextCouple=1,couplingTicks;
    public void onInitialize(){
        if(!Boolean.getBoolean("palletsplus.integration"))return;
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try{setup(server);}catch(Throwable ex){fail(server,ex);}});
        ServerTickEvents.END_SERVER_TICK.register(server->{if(engine==null)return;try{verify(server);}catch(Throwable ex){fail(server,ex);}});
    }
    private void module(ServerLevel level,BlockPos root,String name){
        var block=TransportContent.WAYS.get(name);
        for(int part=0;part<9;part++)level.setBlock(GuidewayBlock.cell(root,Direction.NORTH,part),block.defaultBlockState().setValue(GuidewayBlock.FACING,Direction.NORTH).setValue(GuidewayBlock.PART,part),2);
    }
    private TransportVehicle vehicle(ServerLevel level,int kind,int x,int z,double exactZ){
        var entity=TransportContent.VEHICLE.create(level,EntitySpawnReason.EVENT);
        if(entity==null)throw new AssertionError("Cannot create vehicle");
        entity.configure(kind,new BlockPos(x,64,z),Direction.NORTH);entity.setPos(x+.5,64.26,exactZ);
        if(!level.addFreshEntity(entity))throw new AssertionError("Cannot spawn vehicle");
        return entity;
    }
    private void setup(MinecraftServer server){
        var level=server.overworld();
        for(int x=0;x<=1;x++)for(int z=-14;z<=6;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        for(int z=84;z>=-210;z-=3)module(level,new BlockPos(8,64,z),z==-150?"pallet_rail_index":"pallet_rail");
        for(int z=0;z>=-36;z-=3)module(level,new BlockPos(24,64,z),z==-12?"conveyor_hold":"conveyor_straight");
        module(level,new BlockPos(16,64,0),"conveyor_straight");
        level.setBlock(new BlockPos(26,64,-12),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        engine=vehicle(level,1,8,0,.5);cars.add(engine);
        double z=.5;
        for(int i=0;i<10;i++){
            int kind=i==9?3:2;double length=kind==3?4.4:6.8;
            z+=TransportMotion.spacing(cars.getLast().length(),length);
            var car=vehicle(level,kind,8,(int)Math.round((z-.5)/3)*3,z);
            cars.add(car);
        }
        z+=TransportMotion.spacing(cars.getLast().length(),6.8);
        extra=vehicle(level,2,8,(int)Math.round((z-.5)/3)*3,z);
        conveyor=vehicle(level,0,24,0,.5);
        endOfLine=vehicle(level,0,16,0,.5);
        // Defer coupling until the first server tick: addFreshEntity() has queued UUID
        // registration, so a same-tick level.getEntity(uuid) may not find a new car.
    }
    private void near(double actual,double expected,String name){if(Math.abs(actual-expected)>.015)throw new AssertionError(name+": expected "+expected+", got "+actual);}
    private void verify(MinecraftServer server){
        if(!coupled){
            if(++couplingTicks>300)throw new AssertionError("Timed out resolving the newly spawned train's entity UUIDs at car "+nextCouple);
            // Freshly spawned entities can enter the server's UUID lookup on
            // different ticks. Couple one car per tick, retrying if a lead
            // entity is still being registered. Never bypass actual checks.
            if(nextCouple<cars.size()){
                String result=cars.get(nextCouple).couple(cars.get(nextCouple-1));
                if(result.startsWith("Cars coupled"))nextCouple++;
                else if(!result.equals("Lead car is unloaded."))throw new AssertionError("Car "+nextCouple+": "+result);
                return;
            }
            String tooMany=extra.couple(cars.getLast());
            if(tooMany.equals("Lead car is unloaded."))return;
            if(!tooMany.startsWith("Maximum train"))throw new AssertionError("Eleventh car accepted: "+tooMany);
            extra.discard();
            coupled=true;coupledTick=engine.tickCount;
            engine.toggleEngine();
            return;
        }
        if(++elapsed>800)throw new AssertionError("Simulation did not finish");
        int moveTicks=engine.tickCount-coupledTick;
        if(conveyor.tickCount==20){near(.5-conveyor.getZ(),8,"8 m/s conveyor");conveyorSpeed=true;}
        if(conveyor.tickCount==40){
            near(conveyor.getZ(),-11.5,"Hold bar centre stop");holdStop=true;
            var endRecord=TransitWorldData.get(server.overworld()).vehicles.get(endOfLine.getStringUUID());
            if(endRecord==null||endRecord.get("active").getAsBoolean()||!endOfLine.transitStatus().startsWith("End of route"))
                throw new AssertionError("Broken conveyor end must safely stop and release chunk tickets");
        }
        if(moveTicks==100){
            near(.5-engine.getZ(),37.875,"Engine acceleration with ten cars (status: "+engine.transitStatus()+", engineZ: "+engine.getZ()+", carZ: "+cars.get(1).getZ()+")");
            var record=TransitWorldData.get(server.overworld()).vehicles.get(engine.getStringUUID());
            if(record==null||!record.get("active").getAsBoolean())
                throw new AssertionError("Moving engine must publish an active chunk-ticket record");
            if(!record.has("trailers")||record.getAsJsonArray("trailers").size()!=10)
                throw new AssertionError("Active engine must publish all ten trailer coordinates");
        }
        if(moveTicks==220){
            near(engine.getZ(),-149.5,"Index rail centre stop");
            if(!engine.indexedAt(new BlockPos(8,64,-150)))throw new AssertionError("Indexer did not capture engine");
            if(!engine.advanceOneCar().startsWith("Indexing"))throw new AssertionError("Index advance rejected");
            indexed=true;
        }
        if(moveTicks==300){
            near(engine.getZ(),-155.1,"Index advance distance");
            near(cars.get(1).getZ(),-149.5,"First car at unloading marker");
            for(int i=1;i<cars.size();i++)near(cars.get(i).getZ()-cars.get(i-1).getZ(),TransportMotion.spacing(cars.get(i-1).length(),cars.get(i).length()),"Coupler "+i);
            var record=TransitWorldData.get(server.overworld()).vehicles.get(engine.getStringUUID());
            if(record==null||record.get("active").getAsBoolean())
                throw new AssertionError("Stopped indexing engine must release its chunk tickets");
            if(!conveyorSpeed||!holdStop||!indexed)throw new AssertionError("Missing check");
            LoggerFactory.getLogger("PalletsPlusChecks").info("PALLETS_PLUS_MOVEMENT_PASS: acceleration, ten cars, capacity limit, 8 m/s, hold bar, exact car indexing");
            engine=null;server.halt(false);
        }
    }
    private void fail(MinecraftServer server,Throwable failure){LoggerFactory.getLogger("PalletsPlusChecks").error("PALLETS_PLUS_MOVEMENT_FAIL",failure);engine=null;server.halt(false);}
}
