package com.caszgamermd.palletsplus.integration;
import com.caszgamermd.caszualadditions.racks.*;
import com.caszgamermd.palletsplus.transport.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;

public final class CrewTransport {
    private static MovingPallet moving(ServerLevel level,ServerPlayer player){
        var rigs=level.getEntitiesOfClass(MovingPallet.class,new AABB(player.blockPosition()).inflate(96),rig->rig.owns(player.getUUID()));
        return rigs.isEmpty()?null:rigs.getFirst();
    }
    public static String deliverTrain(ServerLevel level,ServerPlayer player,TransportVehicle train){
        if(train.kind()!=2)return "Choose a pallet flatbed car.";
        var rig=moving(level,player);
        return rig==null?"Wait for your four Golems to finish lifting the pallet.":((TransportCrew)(Object)rig).palletsplus$requestTrain(train);
    }
    public static void initialize(){
        UseBlockCallback.EVENT.register((player,level,hand,hit)->{
            if(!player.getItemInHand(hand).is(RackContent.STORAGE_SCANNER_ITEM))return InteractionResult.PASS;
            var state=level.getBlockState(hit.getBlockPos());
            if(!(state.getBlock() instanceof GuidewayBlock way)||way.rail()||way.bay())return InteractionResult.PASS;
            if(player instanceof ServerPlayer sp&&level instanceof ServerLevel server){
                var rig=moving(server,sp);
                BlockPos root=GuidewayBlock.root(hit.getBlockPos(),state);
                sp.sendSystemMessage(Component.literal(rig==null?"Wait for your four Golems to finish lifting the pallet.":((TransportCrew)(Object)rig).palletsplus$requestConveyor(server,root)));
            }
            return InteractionResult.SUCCESS;
        });
    }
    private CrewTransport(){}
}
