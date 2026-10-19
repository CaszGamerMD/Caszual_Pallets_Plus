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
    private TransportVehicle engine,conveyor;
    private final List<TransportVehicle> cars=new ArrayList<>();
    private boolean conveyorSpeed,holdStop,indexed;
    private int elapsed;
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
        level.setBlock(new BlockPos(26,64,-12),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        engine=vehicle(level,1,8,0,.5);cars.add(engine);
        double z=.5;
        for(int i=0;i<10;i++){
            int kind=i==9?3:2;double length=kind==3?4.4:6.8;
            z+=TransportMotion.spacing(cars.getLast().length(),length);
            var car=vehicle(level,kind,8,(int)Math.round((z-.5)/3)*3,z);
            String result=car.couple(cars.getLast());
            if(!result.startsWith("Cars coupled"))throw new AssertionError(result);
            cars.add(car);
        }
        z+=TransportMotion.spacing(cars.getLast().length(),6.8);
        var extra=vehicle(level,2,8,(int)Math.round((z-.5)/3)*3,z);
        if(!extra.couple(cars.getLast()).startsWith("Maximum train"))throw new AssertionError("Eleventh car accepted");
        extra.discard();
        conveyor=vehicle(level,0,24,0,.5);
        engine.toggleEngine();
    }
    private void near(double actual,double expected,String name){if(Math.abs(actual-expected)>.015)throw new AssertionError(name+": expected "+expected+", got "+actual);}
    private void verify(MinecraftServer server){
        if(++elapsed>800)throw new AssertionError("Simulation did not finish");
        if(conveyor.tickCount==20){near(.5-conveyor.getZ(),8,"8 m/s conveyor");conveyorSpeed=true;}
        if(conveyor.tickCount==40){near(conveyor.getZ(),-11.5,"Hold bar centre stop");holdStop=true;}
        if(engine.tickCount==100)near(.5-engine.getZ(),37.875,"Engine acceleration with ten cars");
        if(engine.tickCount==220){
            near(engine.getZ(),-149.5,"Index rail centre stop");
            if(!engine.indexedAt(new BlockPos(8,64,-150)))throw new AssertionError("Indexer did not capture engine");
            if(!engine.advanceOneCar().startsWith("Indexing"))throw new AssertionError("Index advance rejected");
            indexed=true;
        }
        if(engine.tickCount==300){
            near(engine.getZ(),-155.1,"Index advance distance");
            near(cars.get(1).getZ(),-149.5,"First car at unloading marker");
            for(int i=1;i<cars.size();i++)near(cars.get(i).getZ()-cars.get(i-1).getZ(),TransportMotion.spacing(cars.get(i-1).length(),cars.get(i).length()),"Coupler "+i);
            if(!conveyorSpeed||!holdStop||!indexed)throw new AssertionError("Missing check");
            LoggerFactory.getLogger("PalletsPlusChecks").info("PALLETS_PLUS_MOVEMENT_PASS: acceleration, ten cars, capacity limit, 8 m/s, hold bar, exact car indexing");
            engine=null;server.halt(false);
        }
    }
    private void fail(MinecraftServer server,Throwable failure){LoggerFactory.getLogger("PalletsPlusChecks").error("PALLETS_PLUS_MOVEMENT_FAIL",failure);engine=null;server.halt(false);}
}
