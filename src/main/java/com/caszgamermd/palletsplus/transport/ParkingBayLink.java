package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.caszualadditions.racks.RackContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/** Two-click link tool. Computer -> bay binds a warehouse; OUT -> IN pairs bays.
 * Selection belongs to the item and is dimension-scoped. */
public final class ParkingBayLink extends Item {
    public ParkingBayLink(Properties p){super(p);}
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getLevel() instanceof ServerLevel l))return InteractionResult.SUCCESS;
        var player=c.getPlayer();if(player==null)return InteractionResult.FAIL;
        ItemStack held=c.getItemInHand();CompoundTag t=held.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        BlockPos pos=c.getClickedPos();var state=l.getBlockState(pos);
        if(player.isShiftKeyDown()){
            held.set(DataComponents.CUSTOM_DATA,CustomData.EMPTY);
            player.sendSystemMessage(Component.literal("Parking Bay Link selection cleared."));
            return InteractionResult.SUCCESS;
        }
        String dim=l.dimension().identifier().toString(),response;
        if(state.is(RackContent.RACK_COMPUTER)){
            t.putString("dimension",dim);t.putString("choose","warehouse");t.putLong("source",pos.asLong());
            response="Rack Computer selected. Click a Drone Parking Bay to attach it to this warehouse.";
        }else if(state.is(TransportContent.BAY)){
            BlockPos root=GuidewayBlock.root(pos,state);
            if(!DroneBayRoutes.valid(l,root)){response="Complete the 3x3 Drone Parking Bay first.";}
            else if(dim.equals(t.getStringOr("dimension",""))&&"warehouse".equals(t.getStringOr("choose",""))){
                response=DroneBayRoutes.warehouse(l,root,BlockPos.of(t.getLongOr("source",0)),player.getUUID().toString());
                if(response.startsWith("Bay connected"))t.remove("choose");
            }else if(dim.equals(t.getStringOr("dimension",""))&&"output".equals(t.getStringOr("choose",""))){
                response=DroneBayRoutes.link(l,BlockPos.of(t.getLongOr("source",0)),root);
                if(response.startsWith("Air route linked")){t.remove("choose");t.remove("source");}
            }else{
                var bay=DroneBayRoutes.get(l,root);
                if(bay==null||bay.input())response="Select an OUTPUT bay first; empty-hand click a bay to toggle INPUT/OUTPUT.";
                else{
                    t.putString("dimension",dim);t.putString("choose","output");t.putLong("source",root.asLong());
                    response="OUTPUT bay selected. Travel to the destination INPUT bay and click it, even when the source chunk unloads.";
                }
            }
        }else{return InteractionResult.PASS;}
        held.set(DataComponents.CUSTOM_DATA,CustomData.of(t));
        player.sendSystemMessage(Component.literal(response));
        return InteractionResult.SUCCESS;
    }
}
