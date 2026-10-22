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

    @Inject(method="tickDelivery",at=@At("HEAD"),cancellable=true)
    private void palletsplus$deliver(ServerLevel level,CallbackInfo ci){
        if(deliveryMode==8||deliveryMode==9){palletsplus$tickTransport(level);ci.cancel();}
    }
    @Inject(method="addAdditionalSaveData",at=@At("TAIL"))
    private void palletsplus$save(ValueOutput out,CallbackInfo ci){
        boolean active=deliveryMode==8||deliveryMode==9;
        out.putInt("palletsplus_transport_mode",active?deliveryMode:0);
        out.putString("palletsplus_transport_vehicle",palletsplus$deliveryTrain);
        // Removing the addon leaves a carrying crew, never a misinterpreted warehouse task.
        if(active)out.putInt("delivery_mode",0);
    }
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void palletsplus$load(ValueInput in,CallbackInfo ci){
        int mode=in.getIntOr("palletsplus_transport_mode",0);
        palletsplus$deliveryTrain=in.getStringOr("palletsplus_transport_vehicle","");
        palletsplus$trainSlot=-1;
        if((mode==8||mode==9)&&loaded()&&!delivered){
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
        palletsplus$deliveryTrain=train.getStringUUID();deliveryPosition=train.blockPosition();deliveryMode=8;palletsplus$trainSlot=-1;
        releaseTicks=0;entityData.set(RELEASE_TICKS,0);entityData.set(RACK_INSERT_ACTIVE,true);
        rackFrontRoute.clear();nextRackFrontPlan=0;deliveryFailure="MOVING TO FLATBED";
        return "Golems will carry the pallet to the stopped flatbed and load a free slot. Keep a clear three-block aisle beside the train.";
    }
    public String palletsplus$requestConveyor(ServerLevel level,BlockPos root){
        if(deliveryMode>=3&&deliveryMode<=7)return "Finish or cancel the warehouse task before loading transport.";
        if(!loaded()||delivered)return "No pallet ready to deliver.";
        if(!com.caszgamermd.palletsplus.transport.GuidewayBlock.complete(level,root))return "Choose a complete conveyor module.";
        deliveryPosition=root.immutable();deliveryMode=9;palletsplus$deliveryTrain="";palletsplus$trainSlot=-1;
        releaseTicks=0;entityData.set(RELEASE_TICKS,0);entityData.set(RACK_INSERT_ACTIVE,true);rackFrontRoute.clear();nextRackFrontPlan=0;
        return "Golems will load this conveyor when its deck is clear. Keep a supported three-block aisle beside it.";
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
        double side=(getX()-cargoTarget.x)*sideX+(getZ()-cargoTarget.z)*sideZ<0?-1:1;
        double x=cargoTarget.x+side*sideX*2.8,z=cargoTarget.z+side*sideZ*2.8;
        int y=(int)Math.floor(train.getY());
        // Search a supported aisle at rail level or one block above/below.
        BlockPos approach=null;
        for(int dy:new int[]{0,1,-1}){BlockPos candidate=BlockPos.containing(x,y+dy,z);if(StorageGolemReturnPath.walkable(level,candidate)&&level.noCollision(this,getBoundingBox().move(x-getX(),y+dy-getY(),z-getZ()))){approach=candidate;break;}}
        if(approach==null){waitForDelivery("WAITING: CLEAR SUPPORTED AISLE BESIDE FLATBED");return;}
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
