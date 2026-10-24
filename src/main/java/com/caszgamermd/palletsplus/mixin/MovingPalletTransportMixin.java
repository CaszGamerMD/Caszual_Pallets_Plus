package com.caszgamermd.palletsplus.mixin;

import com.caszgamermd.caszualadditions.racks.*;
import com.caszgamermd.palletsplus.integration.TransportCrew;
import net.minecraft.core.*;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** Optional transport bridge. Warehouse implementation and cargo ownership remain in Additions. */
@Mixin(value=MovingPallet.class,remap=false)
public abstract class MovingPalletTransportMixin extends PathfinderMob implements TransportCrew {
    protected MovingPalletTransportMixin(EntityType<? extends PathfinderMob> type,Level level){super(type,level);}
    @Shadow private ItemStack pallet;
    @Shadow private NonNullList<ItemStack> contents;
    @Shadow private boolean delivered;
    @Shadow private int deliveryMode,releaseTicks,nextRackFrontPlan;
    @Shadow private BlockPos deliveryPosition;
    @Shadow private String deliveryFailure;
    @Shadow @Final private ArrayDeque<BlockPos> rackFrontRoute;
    @Shadow @Final private static EntityDataAccessor<Integer> RELEASE_TICKS;
    @Shadow @Final private static EntityDataAccessor<Boolean> RACK_INSERT_ACTIVE;
    @Shadow @Final private static EntityDataAccessor<Float> RACK_INSERT_X,RACK_INSERT_Y,RACK_INSERT_Z;
    @Shadow public abstract boolean loaded();
    @Shadow private void waitForDelivery(String reason){throw new AssertionError();}
    @Shadow private void navigateToRackFront(ServerLevel level,BlockPos goal,double x,double z){throw new AssertionError();}
    @Shadow private void clearCargo(){throw new AssertionError();}
    @Shadow private boolean restoreWorkers(ServerLevel level){throw new AssertionError();}
    @Unique private String palletsplus$deliveryTrain="";
    @Unique private int palletsplus$trainSlot=-1;
    @Unique private int palletsplus$approachSide=0;

    @Inject(method="tickDelivery",at=@At("HEAD"),cancellable=true)
    private void palletsplus$deliver(ServerLevel level,CallbackInfo ci){
        if(deliveryMode==8||deliveryMode==9){palletsplus$tickTransport(level);ci.cancel();}
        else if(deliveryMode==10){palletsplus$tickNode(level);ci.cancel();}
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void palletsplus$save(ValueOutput out,CallbackInfo ci){
        boolean active=deliveryMode==8||deliveryMode==9||deliveryMode==10;
        out.putInt("palletsplus_transport_mode",active?deliveryMode:0);
        out.putString("palletsplus_transport_vehicle",palletsplus$deliveryTrain);
        out.putInt("palletsplus_transport_side",palletsplus$approachSide);
        // Removing the addon leaves a carrying crew, never a misinterpreted warehouse task.
        if(active)out.putInt("delivery_mode",0);
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void palletsplus$load(ValueInput in,CallbackInfo ci){
        int mode=in.getIntOr("palletsplus_transport_mode",0);
        palletsplus$deliveryTrain=in.getStringOr("palletsplus_transport_vehicle","");
        palletsplus$trainSlot=-1;
        palletsplus$approachSide=in.getIntOr("palletsplus_transport_side",0);
        if((mode==8||mode==9||mode==10)&&loaded()&&!delivered){
            deliveryMode=mode;
            releaseTicks=0;
            entityData.set(RELEASE_TICKS,0);
            entityData.set(RACK_INSERT_ACTIVE,true);
        }
    }
    public String palletsplus$requestTrain(com.caszgamermd.palletsplus.transport.TransportVehicle train){
        if(deliveryMode>=3&&deliveryMode<=7)return "Finish or cancel the warehouse task before loading transport.";
        if(!loaded()||delivered)return "No pallet ready to deliver.";
        if(train.freePalletSlot()<0)return "Train must be stopped with a free pallet slot.";
        palletsplus$deliveryTrain=train.getStringUUID();deliveryPosition=train.blockPosition();deliveryMode=8;palletsplus$trainSlot=-1;palletsplus$approachSide=0;
        releaseTicks=0;entityData.set(RELEASE_TICKS,0);entityData.set(RACK_INSERT_ACTIVE,true);
        rackFrontRoute.clear();nextRackFrontPlan=0;deliveryFailure="MOVING TO FLATBED";
        return "Golems will carry the pallet to the stopped flatbed and load a free slot. Keep a clear three-block aisle beside the train.";
    }
    public String palletsplus$requestConveyor(ServerLevel level,BlockPos root){
        if(deliveryMode>=3&&deliveryMode<=7)return "Finish or cancel the warehouse task before loading transport.";
        if(!loaded()||delivered)return "No pallet ready to deliver.";
        if(!com.caszgamermd.palletsplus.transport.GuidewayBlock.complete(level,root))return "Choose a complete conveyor module.";
        var conveyorState=level.getBlockState(root);
        if(!(conveyorState.getBlock() instanceof com.caszgamermd.palletsplus.transport.GuidewayBlock way)
                ||way.rail()||way.bay())
            return "Select a conveyor, not a rail or Pallet Transfer Node.";
        deliveryPosition=root.immutable();deliveryMode=9;palletsplus$deliveryTrain="";palletsplus$trainSlot=-1;palletsplus$approachSide=0;
        releaseTicks=0;entityData.set(RELEASE_TICKS,0);entityData.set(RACK_INSERT_ACTIVE,true);rackFrontRoute.clear();nextRackFrontPlan=0;
        return "Golems will load this conveyor when its deck is clear. Keep a supported three-block aisle beside it.";
    }
    /** A Pallet Transfer Node receives one REAL 2x2x3 wrapped pallet
     * centered above its 3x3 pad. No Golem stands on the node deck.
     * The pallet stays aboard until the last atomic physical placement. */
    @Override public String palletsplus$requestNode(ServerLevel level,BlockPos root){
        if(deliveryMode>=3&&deliveryMode<=7)
            return "Finish the warehouse delivery before loading transport.";
        if(!loaded()||delivered)return "No wrapped pallet carried by these four Golems.";
        if(!com.caszgamermd.palletsplus.transport.DroneBayRoutes.valid(level,root))
            return "This Pallet Transfer Node is incomplete or unloaded.";
        if(com.caszgamermd.palletsplus.transport.DroneBayCargo.occupied(level,root))
            return "Pallet Transfer Node occupied; carried pallet remains with the Golems.";
        deliveryPosition=root.immutable();
        deliveryMode=10;
        palletsplus$deliveryTrain="";
        palletsplus$trainSlot=-1;
        palletsplus$approachSide=0;
        releaseTicks=0;
        entityData.set(RELEASE_TICKS,0);
        entityData.set(RACK_INSERT_ACTIVE,true);
        rackFrontRoute.clear();
        nextRackFrontPlan=0;
        deliveryFailure="MOVING TO PALLET TRANSFER NODE";
        return "Four Golems delivering pallet to the central 2x2 of the "
                +"Pallet Transfer Node. Cargo will remain on the crew until placed.";
    }

    @Unique private void palletsplus$tickNode(ServerLevel level){
        BlockPos center=deliveryPosition;
        if(!com.caszgamermd.palletsplus.transport.DroneBayRoutes.valid(level,center)){
            waitForDelivery("WAITING: TRANSFER NODE MISSING OR UNLOADED");
            return;
        }
        if(com.caszgamermd.palletsplus.transport.DroneBayCargo.occupied(level,center)){
            waitForDelivery("WAITING: TRANSFER NODE OCCUPIED; CARGO RETAINED");
            return;
        }
        // Stand on real ground alongside the 3x3 pad. The node top is
        // 0.25 blocks high, but the pallet root is ONE BLOCK above it:
        // using ground-delivery AI would seek a nonexistent raised aisle.
        BlockPos approach=null;
        double best=Double.POSITIVE_INFINITY;
        for(Direction side:Direction.Plane.HORIZONTAL){
            for(int distance=3;distance<=5;distance++){
                BlockPos trial=center.relative(side,distance);
                if(!StorageGolemReturnPath.walkable(level,trial))continue;
                double x=trial.getX()+.5,z=trial.getZ()+.5;
                if(!level.noCollision(this,getBoundingBox()
                        .move(x-getX(),trial.getY()-getY(),z-getZ())))continue;
                double d=distanceToSqr(x,trial.getY(),z);
                if(d<best){best=d;approach=trial;}
            }
        }
        if(approach==null){
            waitForDelivery("WAITING: NEED CLEAR GROUND AISLE BESIDE TRANSFER NODE");
            return;
        }
        double x=approach.getX()+.5,z=approach.getZ()+.5;
        if(Math.hypot(x-getX(),z-getZ())>.55
                ||Math.abs(getY()-approach.getY())>.65){
            releaseTicks=0;
            entityData.set(RELEASE_TICKS,0);
            navigateToRackFront(level,approach,x,z);
            deliveryFailure="MOVING TO PALLET TRANSFER NODE";
            return;
        }
        getNavigation().stop();
        BlockPos padRoot=com.caszgamermd.palletsplus.transport.DroneBayCargo.root(center);
        entityData.set(RACK_INSERT_ACTIVE,true);
        entityData.set(RACK_INSERT_X,(float)(padRoot.getX()+1-getX()));
        entityData.set(RACK_INSERT_Y,(float)(padRoot.getY()-(getY()+1.48)));
        entityData.set(RACK_INSERT_Z,(float)(padRoot.getZ()+1-getZ()));
        deliveryFailure="LOWERING PALLET ONTO TRANSFER NODE";
        if(releaseTicks<RackPickupPlan.PULL_TICKS){
            entityData.set(RELEASE_TICKS,++releaseTicks);
            return;
        }
        // Copy into the tentative placement payload; ONLY after all four
        // real pallet parts plus the 216-slot controller have been written
        // do we relinquish the MovingPallet's sole stored inventory.
        var staged=new com.caszgamermd.palletsplus.transport.PalletCargo();
        staged.deck=pallet.copyWithCount(1);
        for(int i=0;i<216;i++)staged.items.set(i,contents.get(i).copy());
        if(!staged.place(level,padRoot,Direction.EAST)){
            waitForDelivery("WAITING: NODE 2x2 LANDING BLOCKED; CARGO RETAINED");
            return;
        }
        clearCargo();
        deliveryMode=0;
        palletsplus$deliveryTrain="";
        deliveryFailure="";
        entityData.set(RACK_INSERT_ACTIVE,false);
        restoreWorkers(level);
    }

    @Unique private void palletsplus$tickTransport(ServerLevel level){
        com.caszgamermd.palletsplus.transport.TransportVehicle train=null;
        try{if(level.getEntity(UUID.fromString(palletsplus$deliveryTrain)) instanceof com.caszgamermd.palletsplus.transport.TransportVehicle v)train=v;}catch(IllegalArgumentException ignored){}
        boolean conveyor=deliveryMode==9;
        if(conveyor){
            if(!com.caszgamermd.palletsplus.transport.GuidewayBlock.complete(level,deliveryPosition)){waitForDelivery("WAITING: CONVEYOR MISSING OR UNLOADED");return;}
            var state=level.getBlockState(deliveryPosition);var way=(com.caszgamermd.palletsplus.transport.GuidewayBlock)state.getBlock();
            if(way.rail()||way.bay()){waitForDelivery("WAITING: CONVEYOR REPLACED");return;}
            if(!level.getEntitiesOfClass(com.caszgamermd.palletsplus.transport.TransportVehicle.class,new net.minecraft.world.phys.AABB(deliveryPosition).inflate(1.5,2,1.5)).isEmpty()){waitForDelivery("WAITING: CONVEYOR OCCUPIED");return;}
            // An unspawned staging carrier computes the exact turn/ramp pose.
            train=com.caszgamermd.palletsplus.transport.TransportContent.VEHICLE.create(level,EntitySpawnReason.EVENT);
            if(train==null)return;
            train.configure(0,deliveryPosition,state.getValue(com.caszgamermd.palletsplus.transport.GuidewayBlock.FACING));
        }
        if(train==null||!train.isAlive()){waitForDelivery("WAITING: TRANSPORT UNLOADED OR REMOVED");return;}
        int slot=train.freePalletSlot();
        if(slot<0){waitForDelivery("WAITING: STOP TRAIN / FREE A PALLET SLOT");return;}
        Vec3 cargoTarget=train.palletSlotPosition(slot);
        double yaw=Math.toRadians(train.getYRot()),sideX=Math.cos(yaw),sideZ=Math.sin(yaw);
        int y=(int)Math.floor(train.getY());
        // Find a real supported aisle on either side of the transport.
        // Once chosen, KEEP that side for this order; previously the target
        // switched sides as the Golem rig moved, resulting in oscillation.
        BlockPos approach=null;
        double x=0,z=0;
        int selected=0;
        double best=Double.POSITIVE_INFINITY;
        for(int sideSign:new int[]{palletsplus$approachSide==0?1:palletsplus$approachSide,
                palletsplus$approachSide==0?-1:-palletsplus$approachSide}){
            if(selected!=0)break;
            double testX=cargoTarget.x+sideSign*sideX*2.8;
            double testZ=cargoTarget.z+sideSign*sideZ*2.8;
            for(int dy:new int[]{0,1,-1}){
                BlockPos candidate=BlockPos.containing(testX,y+dy,testZ);
                if(!StorageGolemReturnPath.walkable(level,candidate)
                        ||!level.noCollision(this,getBoundingBox().move(
                                testX-getX(),candidate.getY()-getY(),testZ-getZ())))
                    continue;
                double metric=distanceToSqr(testX,candidate.getY(),testZ);
                if(metric<best){
                    best=metric;approach=candidate;selected=sideSign;
                    x=testX;z=testZ;
                }
            }
        }
        if(approach==null){waitForDelivery(conveyor
                ?"WAITING: CLEAR SUPPORTED AISLE BESIDE CONVEYOR"
                :"WAITING: CLEAR SUPPORTED AISLE BESIDE FLATBED");return;}
        palletsplus$approachSide=selected;
        double distance=Math.hypot(x-getX(),z-getZ());
        if(palletsplus$trainSlot!=slot){palletsplus$trainSlot=slot;releaseTicks=0;entityData.set(RELEASE_TICKS,0);}
        if(distance>.5||Math.abs(getY()-approach.getY())>.65){
            releaseTicks=0;entityData.set(RELEASE_TICKS,0);
            navigateToRackFront(level,approach,x,z);deliveryFailure=conveyor?"MOVING TO CONVEYOR":"MOVING TO FLATBED";return;
        }
        getNavigation().stop();entityData.set(RACK_INSERT_ACTIVE,true);
        entityData.set(RACK_INSERT_X,(float)(cargoTarget.x-getX()));entityData.set(RACK_INSERT_Z,(float)(cargoTarget.z-getZ()));entityData.set(RACK_INSERT_Y,(float)(cargoTarget.y-(getY()+1.48)));
        deliveryFailure=conveyor?"LOADING CONVEYOR":"LOADING FLATBED";
        if(releaseTicks<RackPickupPlan.PULL_TICKS){entityData.set(RELEASE_TICKS,++releaseTicks);return;}
        if(conveyor&&(!level.noCollision(train)||!level.addFreshEntity(train))){waitForDelivery("WAITING: CONVEYOR BLOCKED");return;}
        if(!train.acceptCrewPallet(pallet,contents)){if(conveyor)train.discard();waitForDelivery("WAITING: FLATBED NO LONGER AVAILABLE");return;}
        clearCargo();deliveryMode=0;palletsplus$deliveryTrain="";deliveryFailure="";entityData.set(RACK_INSERT_ACTIVE,false);restoreWorkers(level);
    }
}
