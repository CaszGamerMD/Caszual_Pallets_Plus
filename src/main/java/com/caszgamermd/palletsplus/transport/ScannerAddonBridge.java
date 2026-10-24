package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.caszualadditions.racks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;

/** Transport implementation for the Caszual Additions Warehouse Scanner.
 * No extra controller is needed for linking; the existing physical cargo
 * handoff and two-Golem dispatchers retain their own safety checks. */
public final class ScannerAddonBridge {
    private ScannerAddonBridge(){}
    private static CompoundTag data(ItemStack scanner){
        return scanner.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
    }
    private static void save(ItemStack scanner,CompoundTag tag){
        scanner.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    private static BlockPos warehouse(ItemStack scanner,ServerLevel level){
        var tag=data(scanner);
        if(!level.dimension().toString().equals(tag.getStringOr("warehouse_dimension",""))
                ||!tag.contains("warehouse_x"))return null;
        return new BlockPos(tag.getIntOr("warehouse_x",0),
                tag.getIntOr("warehouse_y",0),tag.getIntOr("warehouse_z",0));
    }
    public static String onBlock(ServerLevel level,ServerPlayer player,
            ItemStack scanner,BlockPos clicked,String mode){
        if(!level.hasChunkAt(clicked))return "The target is not loaded.";
        var state=level.getBlockState(clicked);
        if(!(state.getBlock() instanceof GuidewayBlock way)||!way.bay())
            return mode.equals("LINK")?"Click a Pallet Transfer Node to link it.":
                    "Click a Pallet Transfer Node beside a conveyor or stopped flatbed.";
        BlockPos node=GuidewayBlock.root(clicked,state);
        if(!DroneBayRoutes.valid(level,node))return "Complete all nine node tiles first.";
        if("LINK".equals(mode)){
            CompoundTag tag=data(scanner);
            String operation=tag.getStringOr("scanner_link_type","AIR_ROUTE");
            if("WAREHOUSE".equals(operation)){
                BlockPos computer=warehouse(scanner,level);
                if(computer==null)return "Link the scanner to a Rack Computer first.";
                return DroneBayRoutes.warehouse(level,node,computer,player.getUUID().toString());
            }
            if(tag.contains("scanner_link_source")){
                if(!level.dimension().toString().equals(tag.getStringOr("scanner_link_dimension",""))){
                    tag.remove("scanner_link_source");save(scanner,tag);
                    return "Saved OUTPUT is in another dimension. Select a local OUTPUT node.";
                }
                BlockPos source=BlockPos.of(tag.getLongOr("scanner_link_source",0));
                if(source.equals(node))return "Now click a different INPUT node.";
                String outcome=DroneBayRoutes.link(level,source,node);
                if(outcome.startsWith("Air route linked")){
                    tag.remove("scanner_link_source");
                    tag.remove("scanner_link_dimension");
                    save(scanner,tag);
                }
                return outcome;
            }
            var bay=DroneBayRoutes.get(level,node);
            if(bay==null||bay.input())return "Select an OUTPUT Pallet Transfer Node first.";
            tag.putLong("scanner_link_source",node.asLong());
            tag.putString("scanner_link_dimension",level.dimension().toString());
            save(scanner,tag);
            return "OUTPUT selected. Click the INPUT Pallet Transfer Node to link the route.";
        }
        if("LOAD_UNLOAD".equals(mode)){
            BlockPos computer=warehouse(scanner,level);
            if(computer==null)return "Link the scanner to a warehouse first.";
            var bay=DroneBayRoutes.get(level,node);
            boolean loading="LOAD".equals(data(scanner)
                    .getStringOr("scanner_load_direction","LOAD"));
            if(bay==null||bay.output()!=loading)
                return loading?"Select an OUTPUT node for loading onto transport.":
                               "Select an INPUT node for unloading transport.";
            TransportVehicle carrier=NodePushBridge.adjacentVehicle(level,node,loading);
            if(carrier==null)return "No compatible adjacent conveyor or stopped train flatbed.";
            // The warehouse's existing two-Golem dispatcher handles a genuine
            // pallet, preventing ghost cargo or a one-worker partial mission.
            return PalletNodePushCrew.request(level,player.getUUID(),
                    computer,node,carrier.getUUID(),loading);
        }
        return "Switch Scanner to LINK or LOAD / UNLOAD.";
    }
}
