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
    private TransportVehicle engine,conveyor,extra,endOfLine,supportedRamp,blockedRamp,tMergeNorth,tMergeWest,diagonalCorner,drone,recoveredConveyor,plateArrival,blockedPlateArrival;
    private final List<TransportVehicle> cars=new ArrayList<>();
    private boolean conveyorSpeed,holdStop,indexed,coupled,tNorthDone,tWestDone,cornerDone;
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
    private void moduleFacing(ServerLevel level,BlockPos root,String name,Direction facing){
        var block=TransportContent.WAYS.get(name);
        for(int part=0;part<9;part++)
            level.setBlock(GuidewayBlock.cell(root,facing,part),
                block.defaultBlockState().setValue(GuidewayBlock.FACING,facing).setValue(GuidewayBlock.PART,part),2);
    }
    private void plate(ServerLevel level,BlockPos root){
        var block=(PalletPlateBlock)TransportContent.PLATE;
        for(int part=0;part<4;part++)level.setBlock(
            root.offset(part%2,0,part/2),
            block.defaultBlockState().setValue(PalletPlateBlock.PART,part),2);
        if(!PalletPlateBlock.complete(level,root))
            throw new AssertionError("Pressure plate 2x2 is incomplete");
    }
    private void bay(ServerLevel level,BlockPos center){
        var block=TransportContent.BAY;
        for(int part=0;part<9;part++)
            level.setBlock(GuidewayBlock.cell(center,Direction.NORTH,part),
                block.defaultBlockState().setValue(GuidewayBlock.FACING,Direction.NORTH).setValue(GuidewayBlock.PART,part),2);
        TransitWorldData.get(level).station(center,"bay");
    }
    private TransportVehicle vehicle(ServerLevel level,int kind,int x,int z,double exactZ){
        var entity=TransportContent.VEHICLE.create(level,EntitySpawnReason.EVENT);
        if(entity==null)throw new AssertionError("Cannot create vehicle");
        entity.configure(kind,new BlockPos(x,64,z),Direction.NORTH);entity.setPos(x+.5,64.26,exactZ);
        if(!level.addFreshEntity(entity))throw new AssertionError("Cannot spawn vehicle");
        return entity;
    }
    private void setup(MinecraftServer server){
        var legacy=net.minecraft.resources.Identifier.fromNamespaceAndPath("caszual_additions","conveyor_t_junction");
        var nativeId=net.minecraft.resources.Identifier.fromNamespaceAndPath("caszual_pallets_plus","conveyor_t_junction");
        var oldItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(legacy);
        var newItem=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(nativeId);
        if(!(oldItem instanceof net.minecraft.world.item.BlockItem oldBlock)||
           !(newItem instanceof net.minecraft.world.item.BlockItem newBlock)||
           oldBlock.getBlock()!=TransportContent.WAYS.get("conveyor_t_junction")||
           newBlock.getBlock()!=TransportContent.WAYS.get("conveyor_t_junction"))
            throw new AssertionError("Both visible T-junction item IDs must place the same registered guideway.");
        var level=server.overworld();
        for(int x=0;x<=1;x++)for(int z=-14;z<=6;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
        for(int z=84;z>=-210;z-=3)module(level,new BlockPos(8,64,z),z==-150?"pallet_rail_index":"pallet_rail");
        for(int z=0;z>=-36;z-=3)module(level,new BlockPos(24,64,z),z==-12?"conveyor_hold":"conveyor_straight");
        module(level,new BlockPos(16,64,0),"conveyor_straight");
        // Realistic downhill installation: solid ground directly under the
        // high ramp and its lower exit, plus a separate lane with an actual
        // obstacle ABOVE the receiving conveyor.
        for(int x:new int[]{20,28}){
            BlockPos high=new BlockPos(x,64,0),low=new BlockPos(x,63,-3);
            module(level,high,"conveyor_down");
            module(level,low,"conveyor_straight");
            for(int part=0;part<9;part++){
                level.setBlock(GuidewayBlock.cell(high,Direction.NORTH,part).below(),Blocks.STONE.defaultBlockState(),2);
                level.setBlock(GuidewayBlock.cell(low,Direction.NORTH,part).below(),Blocks.STONE.defaultBlockState(),2);
            }
        }
        level.setBlock(new BlockPos(28,64,-4),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(new BlockPos(26,64,-12),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
        // The north inlet is fed through a real 90-degree corner; the
        // west inlet is straight. Both must feed the user-selected east OUT.
        for(int x=2;x<=4;x++)for(int z=-1;z<=1;z++){
            level.setChunkForced(x,z,true);level.getChunk(x,z);
        }
        moduleFacing(level,new BlockPos(45,64,-3),"conveyor_straight",Direction.EAST);
        moduleFacing(level,new BlockPos(48,64,-3),"conveyor_right",Direction.EAST);
        moduleFacing(level,new BlockPos(45,64,0),"conveyor_straight",Direction.EAST);
        BlockPos tRoot=new BlockPos(48,64,0);
        moduleFacing(level,tRoot,"conveyor_t_junction",Direction.NORTH);
        String selected=GuidewayBlock.cycleJunction(level,tRoot);
        if(!selected.contains("EAST"))throw new AssertionError("Could not set east T-junction output: "+selected);
        moduleFacing(level,new BlockPos(51,64,0),"conveyor_straight",Direction.EAST);
        moduleFacing(level,new BlockPos(54,64,0),"conveyor_straight",Direction.EAST);
        // A curve physically touches another curve by exactly one corner:
        // east-facing exit, diagonal north-east entry, then northbound exit.
        moduleFacing(level,new BlockPos(60,64,0),"conveyor_right",Direction.NORTH);
        moduleFacing(level,new BlockPos(63,64,-3),"conveyor_left",Direction.EAST);
        moduleFacing(level,new BlockPos(63,64,-6),"conveyor_straight",Direction.NORTH);
        // Real stuck-carrier regression: deliberately bump the physical
        // entity away from its saved path, then recover without losing cargo.
        module(level,new BlockPos(36,64,0),"conveyor_straight");
        module(level,new BlockPos(36,64,-3),"conveyor_straight");
        module(level,new BlockPos(36,64,-6),"conveyor_straight");
        recoveredConveyor=vehicle(level,0,36,0,.5);
        recoveredConveyor.cargo[0].deck=new net.minecraft.world.item.ItemStack(
            com.caszgamermd.caszualadditions.pallet.PalletContent.WOODEN_PALLET_ITEM);
        recoveredConveyor.cargo[0].items.set(0,new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.DIAMOND,17));
        recoveredConveyor.sync();
        recoveredConveyor.setPos(37.3,64.26,.5);
        String recovery=recoveredConveyor.restartConveyor(level);
        if(!recovery.startsWith("Pallet re-centered")||Math.abs(recoveredConveyor.getX()-36.5)>.001||
                recoveredConveyor.cargo[0].items.get(0).getCount()!=17)
            throw new AssertionError("Bumped conveyor pallet failed no-loss restart: "+recovery);
        // Emergency salvage must physically rebuild the original pallet and
        // its 216-slot cargo, removing the carrier only AFTER ground commit.
        module(level,new BlockPos(39,64,18),"conveyor_straight");
        for(int gx=40;gx<=56;gx++)for(int gz=12;gz<=26;gz++)
            level.setBlock(new BlockPos(gx,63,gz),Blocks.STONE.defaultBlockState(),2);
        TransportVehicle salvage=vehicle(level,0,39,18,18.5);
        salvage.cargo[0].deck=com.caszgamermd.caszualadditions.pallet.GlassPalletWrap.withWrap(
            new net.minecraft.world.item.ItemStack(
                com.caszgamermd.caszualadditions.pallet.PalletContent.WOODEN_PALLET_ITEM),true);
        salvage.cargo[0].items.set(0,new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.DIAMOND,23));
        salvage.cargo[0].items.set(215,new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.EMERALD,9));
        salvage.sync();
        String saved=salvage.salvageConveyor(level,new BlockPos(46,64,18));
        if(!saved.startsWith("Recovered the wrapped pallet"))
            throw new AssertionError("Emergency physical pallet recovery failed: "+saved+
                " probe: "+level.getBlockState(new BlockPos(44,63,16))+
                " above: "+level.getBlockState(new BlockPos(44,64,16))+
                " floor2: "+level.getBlockState(new BlockPos(45,63,15))+
                " loaded: "+level.hasChunksAt(new BlockPos(43,63,15),new BlockPos(46,67,18)));
        BlockPos recoveredAt=null;
        for(int gx=40;gx<=56;gx++)for(int gz=12;gz<=26;gz++){
            var pallet=com.caszgamermd.caszualadditions.racks.StorageGroundPalletTransfer
                .wrappedController(level,new BlockPos(gx,64,gz));
            if(pallet!=null&&pallet.getItem(0).getCount()==23&&pallet.getItem(215).getCount()==9)
                recoveredAt=pallet.getBlockPos();
        }
        if(recoveredAt==null)throw new AssertionError("Emergency salvage lost pallet contents");
        // Terminal conveyor unloads to an adjacent 2x2 pressure plate,
        // using a REAL wrapped Caszual pallet and its full shared inventory.
        BlockPos goodPad=new BlockPos(71,64,-3);
        module(level,new BlockPos(72,64,0),"conveyor_straight");
        plate(level,goodPad);
        plateArrival=vehicle(level,0,72,0,.5);
        plateArrival.cargo[0].deck=
            com.caszgamermd.caszualadditions.pallet.GlassPalletWrap.withWrap(
                new net.minecraft.world.item.ItemStack(
                    com.caszgamermd.caszualadditions.pallet.PalletContent.WOODEN_PALLET_ITEM),true);
        plateArrival.cargo[0].items.set(0,new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.DIAMOND,31));
        plateArrival.cargo[0].items.set(215,new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.EMERALD,12));
        plateArrival.sync();
        if(!goodPad.equals(PalletPlateBlock.ahead(level,
                new net.minecraft.world.phys.Vec3(72.5,64.26,-1),Direction.NORTH)))
            throw new AssertionError("Last 3x3 conveyor cannot find adjacent 2x2 landing pad");
        // If someone has already placed a pallet on the plate, the carrier
        // must stop and retain its original contents with no duplicate.
        BlockPos blockedPad=new BlockPos(77,64,-3);
        module(level,new BlockPos(78,64,0),"conveyor_straight");
        plate(level,blockedPad);
        level.setBlock(blockedPad.above(),Blocks.STONE.defaultBlockState(),2);
        blockedPlateArrival=vehicle(level,0,78,0,.5);
        blockedPlateArrival.cargo[0].deck=
            com.caszgamermd.caszualadditions.pallet.GlassPalletWrap.withWrap(
                new net.minecraft.world.item.ItemStack(
                    com.caszgamermd.caszualadditions.pallet.PalletContent.WOODEN_PALLET_ITEM),true);
        blockedPlateArrival.cargo[0].items.set(0,new net.minecraft.world.item.ItemStack(
            net.minecraft.world.item.Items.GOLD_INGOT,19));
        blockedPlateArrival.sync();
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
        supportedRamp=vehicle(level,0,20,0,.5);supportedRamp.setPos(20.5,63.76,.5);
        blockedRamp=vehicle(level,0,28,0,.5);blockedRamp.setPos(28.5,63.76,.5);
        tMergeNorth=vehicle(level,0,45,-3,-2.5);tMergeNorth.setPos(45.5,64.26,-2.5);
        BlockPos send=new BlockPos(12,64,9),receive=new BlockPos(12,64,-9);
        bay(level,send);bay(level,receive);
        String mode=DroneBayRoutes.mode(level,receive);
        if(!mode.contains("INPUT"))throw new AssertionError("Input bay configuration: "+mode);
        // Link tool must win both over the bay's empty-hand mode toggle and
        // Additions Rack Computer's unconditional occupied-hand terminal GUI.
        var linkingTool=new net.minecraft.world.item.ItemStack(
            TransportContent.ITEMS.get("parking_bay_link"));
        if(!ParkingBayLink.shouldIntercept(linkingTool,level.getBlockState(receive))
            ||ParkingBayLink.shouldIntercept(net.minecraft.world.item.ItemStack.EMPTY,
                level.getBlockState(receive)))
            throw new AssertionError("Parking Bay Link interaction priority is incorrect");
        BlockPos rackComputer=new BlockPos(18,64,12);
        level.setBlock(rackComputer,
            com.caszgamermd.caszualadditions.racks.RackContent.RACK_COMPUTER.defaultBlockState(),2);
        if(!ParkingBayLink.shouldIntercept(linkingTool,level.getBlockState(rackComputer)))
            throw new AssertionError("Warehouse Rack Computer will consume Parking Bay Link");
        if(!DroneBayRoutes.get(level,receive).input())
            throw new AssertionError("Link tool setup unexpectedly changed INPUT bay mode");
        String linked=DroneBayRoutes.link(level,send,receive);
        if(!linked.startsWith("Air route linked"))throw new AssertionError(linked);
        // Reproduce the real player workflow: after choosing a distant
        // OUTPUT, its chunk unloads while the player clicks the INPUT.
        // Previously the second click failed because it tried to validate
        // both physical bays as loaded at the same time.
        BlockPos distantOutput=new BlockPos(20012,64,20012);
        if(level.hasChunkAt(distantOutput))
            throw new AssertionError("Remote bay chunk unexpectedly loaded during test");
        TransitWorldData.get(level).station(distantOutput,"bay"); // previously discovered OUTPUT
        String remoteLink=DroneBayRoutes.link(level,distantOutput,receive);
        if(!remoteLink.startsWith("Air route linked"))
            throw new AssertionError("Unloaded source parking bay could not link: "+remoteLink);
        if(!receive.equals(DroneBayRoutes.get(level,distantOutput).linked()))
            throw new AssertionError("Remote bay route was not saved to the source record");
        if(!DroneBayRoutes.link(level,receive,send).startsWith("Selected source bay is INPUT"))
            throw new AssertionError("Reverse INPUT-to-OUTPUT routing was not rejected");
        drone=vehicle(level,4,12,9,9.5);
        drone.cargo[0].deck=new net.minecraft.world.item.ItemStack(Blocks.CHEST.asItem());
        drone.sync();
        drone.tickAutomatedBay(level,send,receive);
        if(!drone.flightActive())throw new AssertionError("Loaded automated drone did not depart from OUTPUT bay.");
        diagonalCorner=vehicle(level,0,60,0,.5);
        var startCorner=TransportPath.sample(1,0,0,.5);
        diagonalCorner.setPos(60.5+startCorner.x(),64+startCorner.y(),.5+startCorner.z());
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
            var received=com.caszgamermd.caszualadditions.racks.StorageGroundPalletTransfer
                .wrappedController(server.overworld(),new BlockPos(71,65,-3));
            if(received==null||received.getItem(0).getCount()!=31||
                    received.getItem(215).getCount()!=12||plateArrival.isAlive())
                throw new AssertionError("Pressure plate did not receive a centered, physical, golem-pickable pallet with its full inventory");
            if(!server.overworld().getBlockState(new BlockPos(71,64,-3))
                    .getValue(PalletPlateBlock.POWERED))
                throw new AssertionError("Physical pallet should keep its pressure plate powered");
            if(!blockedPlateArrival.isAlive()||!blockedPlateArrival.hasCargo()||
                    blockedPlateArrival.cargo[0].items.get(0).getCount()!=19)
                throw new AssertionError("Occupied pressure plate consumed or duplicated its transport cargo");
            if(recoveredConveyor.getZ()>-6.9||
                !recoveredConveyor.cargo[0].items.get(0).is(net.minecraft.world.item.Items.DIAMOND))
                throw new AssertionError("Recovered conveyor failed to resume moving with intact cargo");
            if(diagonalCorner.getZ()>-6.9||!diagonalCorner.transitStatus().startsWith("End of route"))
                throw new AssertionError("Diagonally touching conveyor corners did not join: "+diagonalCorner.transitStatus()+" x="+diagonalCorner.getX()+" z="+diagonalCorner.getZ());
            cornerDone=true;diagonalCorner.discard();
            if(tMergeNorth.getX()<55.4||!tMergeNorth.transitStatus().startsWith("End of route"))
                throw new AssertionError("T merge from corner-fed north inlet failed: "+tMergeNorth.transitStatus()+" x="+tMergeNorth.getX()+" z="+tMergeNorth.getZ());
            tNorthDone=true;tMergeNorth.discard();
            tMergeWest=vehicle(server.overworld(),0,45,0,.5);
            if(supportedRamp.getZ()>-3.5 || !supportedRamp.transitStatus().startsWith("End of route"))
                throw new AssertionError("Supported downhill conveyor jammed: "+supportedRamp.transitStatus()+" at z="+supportedRamp.getZ()+", y="+supportedRamp.getY());
            if(!blockedRamp.transitStatus().equals("Obstructed"))
                throw new AssertionError("Obstacle above lowered conveyor must still block cargo: "+blockedRamp.transitStatus());
            near(conveyor.getZ(),-11.5,"Hold bar centre stop");holdStop=true;
            var endRecord=TransitWorldData.get(server.overworld()).vehicles.get(endOfLine.getStringUUID());
            if(endRecord==null||endRecord.get("active").getAsBoolean()||!endOfLine.transitStatus().startsWith("End of route"))
                throw new AssertionError("Broken conveyor end must safely stop and release chunk tickets");
        }
        if(conveyor.tickCount==100){
            if(tMergeWest==null||tMergeWest.getX()<55.4||!tMergeWest.transitStatus().startsWith("End of route"))
                throw new AssertionError("T merge from west inlet failed: "+(tMergeWest==null?"missing":tMergeWest.transitStatus()+" x="+tMergeWest.getX()+" z="+tMergeWest.getZ()));
            tWestDone=true;tMergeWest.discard();
        }
        if(moveTicks==100){
            if(drone.getY()<75||drone.getZ()>-4||!drone.flightActive())
                throw new AssertionError("Auto-routed drone did not climb and cross to INPUT: "+drone.transitStatus()+
                    " xyz="+drone.position());
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
            if(!conveyorSpeed||!holdStop||!indexed||!tNorthDone||!tWestDone||!cornerDone)throw new AssertionError("Missing check");
            LoggerFactory.getLogger("PalletsPlusChecks").info("PALLETS_PLUS_MOVEMENT_PASS: acceleration, ten cars, capacity limit, 8 m/s, hold bar, exact car indexing, downhill conveyor supports and overhead obstruction, corner-fed T merge, straight T merge, and diagonal corner-to-corner transfer, automated drone departure and terrain-clear takeoff, bump recovery and lossless emergency salvage, physical pressure plate pallet handoff and occupied-pad protection");
            engine=null;server.halt(false);
        }
    }
    private void fail(MinecraftServer server,Throwable failure){LoggerFactory.getLogger("PalletsPlusChecks").error("PALLETS_PLUS_MOVEMENT_FAIL",failure);engine=null;server.halt(false);}
}
