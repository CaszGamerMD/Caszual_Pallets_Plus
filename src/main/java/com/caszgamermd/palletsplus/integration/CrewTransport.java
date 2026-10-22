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
    public static String scannerProblem(net.minecraft.world.item.ItemStack scanner,ServerLevel level){
        if(StorageScannerItem.mode(scanner)!=StorageScannerItem.Mode.DIRECT)
            return "Switch the Warehouse Scanner to DIRECT mode to load transport.";
        var data=scanner.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if(data==null)return "Link the scanner to your Rack Computer first.";
        var tag=data.copyTag();
        if(!tag.getStringOr("warehouse_dimension","").equals(level.dimension().toString())
                ||!tag.contains("warehouse_x")||!tag.contains("warehouse_y")||!tag.contains("warehouse_z"))
            return "Link the scanner to a Rack Computer in this dimension first.";
        return "";
    }
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
            if(!(state.getBlock() instanceof GuidewayBlock way)||way.rail())return InteractionResult.PASS;
            if(way.bay()){
                // The actual normal ground pallet sits one block ABOVE the
                // 3x3 parking pad. Scanner users often click the pad itself:
                // forward that click to the real pallet root for DIRECT
                // Golem retrieval and FORKLIFT source selection. When
                // empty, FORKLIFT/Direct target the bay's central 2x2
                // placement footprint, not an adjacent random block.
                if(level instanceof ServerLevel server && player instanceof ServerPlayer sp){
                    BlockPos center=GuidewayBlock.root(hit.getBlockPos(),state);
                    BlockPos found=DroneBayCargo.find(server,center);
                    BlockPos clicked=found!=null?found:DroneBayCargo.root(center).below();
                    if(StorageScannerItem.mode(player.getItemInHand(hand))==
                            StorageScannerItem.Mode.REMOTE){
                        sp.sendSystemMessage(Component.literal(
                            "INPUT Parking Bay uses automatic THREE-Golem pickup without an empty replacement. Use DIRECT or FORKLIFT mode for manual transfer."));
                    }else{
                        var destinationHit=new net.minecraft.world.phys.BlockHitResult(
                            new net.minecraft.world.phys.Vec3(clicked.getX()+.5,
                                clicked.getY()+.5,clicked.getZ()+.5),
                            net.minecraft.core.Direction.UP,clicked,false);
                        var context=new net.minecraft.world.item.context.UseOnContext(
                            player,hand,destinationHit);
                        player.getItemInHand(hand).getItem().useOn(context);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            if(player instanceof ServerPlayer sp&&level instanceof ServerLevel server){
                String problem=scannerProblem(player.getItemInHand(hand),server);
                if(!problem.isEmpty()){sp.sendSystemMessage(Component.literal(problem));return InteractionResult.SUCCESS;}
                var rig=moving(server,sp);
                BlockPos root=GuidewayBlock.root(hit.getBlockPos(),state);
                sp.sendSystemMessage(Component.literal(rig==null?"Wait for your four Golems to finish lifting the pallet.":((TransportCrew)(Object)rig).palletsplus$requestConveyor(server,root)));
            }
            return InteractionResult.SUCCESS;
        });
    }
    private CrewTransport(){}
}
