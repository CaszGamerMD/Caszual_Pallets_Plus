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
                // A 3x3 node is a TRANSPORT DESTINATION, not an ordinary
                // ground tile. Routing the empty pad through scanner.useOn
                // made the pallet adopt the player's facing (often outside
                // the node's central 2x2) and required an impossible raised
                // service aisle. Route loaded crews through a dedicated
                // node handoff; an empty crew may still retrieve a wrapped
                // pallet already sitting on the same physical node.
                if(level instanceof ServerLevel server && player instanceof ServerPlayer sp){
                    BlockPos center=GuidewayBlock.root(hit.getBlockPos(),state);
                    var mode=StorageScannerItem.mode(player.getItemInHand(hand));
                    BlockPos found=DroneBayCargo.find(server,center);
                    if(mode==StorageScannerItem.Mode.DIRECT){
                        String problem=scannerProblem(player.getItemInHand(hand),server);
                        if(!problem.isEmpty()){
                            sp.sendSystemMessage(Component.literal(problem));
                        }else{
                            var rig=moving(server,sp);
                            if(rig!=null){
                                sp.sendSystemMessage(Component.literal(
                                        ((TransportCrew)(Object)rig).palletsplus$requestNode(server,center)));
                            }else if(found!=null){
                                var destinationHit=new net.minecraft.world.phys.BlockHitResult(
                                        net.minecraft.world.phys.Vec3.atCenterOf(found),
                                        net.minecraft.core.Direction.UP,found,false);
                                player.getItemInHand(hand).getItem().useOn(
                                        new net.minecraft.world.item.context.UseOnContext(
                                                player,hand,destinationHit));
                            }else{
                                sp.sendSystemMessage(Component.literal(
                                        "DIRECT: load a wrapped pallet with four Golems first, "
                                        +"then click this empty Pallet Transfer Node."));
                            }
                        }
                    }else if("FORKLIFT".equals(mode.name())){
                        BlockPos clicked=found!=null?found:DroneBayCargo.root(center).below();
                        var destinationHit=new net.minecraft.world.phys.BlockHitResult(
                                net.minecraft.world.phys.Vec3.atCenterOf(clicked),
                                net.minecraft.core.Direction.UP,clicked,false);
                        player.getItemInHand(hand).getItem().useOn(
                                new net.minecraft.world.item.context.UseOnContext(
                                        player,hand,destinationHit));
                    }else{
                        sp.sendSystemMessage(Component.literal(
                                "Use DIRECT or FORKLIFT Scanner mode for manual "
                                +"Pallet Transfer Node deliveries."));
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
