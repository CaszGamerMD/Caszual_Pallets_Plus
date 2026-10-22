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
    /** In Minecraft 26.2 the Rack Computer accepts all occupied-hand
     * clicks before Item.useOn runs, and the guideway's fallback click can
     * invoke its empty-hand mode switch instead. Intercept ONLY this tool
     * and these two targets before either block handles the click.
     * Returning SUCCESS also prevents duplicate server activation.
     */
    public static void registerInteractions(){
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            ItemStack stack=player.getItemInHand(hand);
            var state=world.getBlockState(hit.getBlockPos());
            if(!shouldIntercept(stack,state))return InteractionResult.PASS;
            if(!(stack.getItem() instanceof ParkingBayLink tool))return InteractionResult.PASS;
            return tool.useOn(new UseOnContext(player,hand,hit));
        });
    }
    public static boolean shouldIntercept(ItemStack held,net.minecraft.world.level.block.state.BlockState target){
        return held.getItem() instanceof ParkingBayLink &&
            (target.is(TransportContent.BAY) || target.is(RackContent.RACK_COMPUTER));
    }
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getLevel() instanceof ServerLevel l))return InteractionResult.SUCCESS;
        var player=c.getPlayer();if(player==null)return InteractionResult.FAIL;
        ItemStack held=c.getItemInHand();CompoundTag t=held.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        BlockPos pos=c.getClickedPos();var state=l.getBlockState(pos);
        if(player.isShiftKeyDown()){
            held.set(DataComponents.CUSTOM_DATA,CustomData.EMPTY);
            player.sendSystemMessage(Component.literal("Pallet Transfer Link selection cleared."));
            return InteractionResult.SUCCESS;
        }
        String dim=l.dimension().identifier().toString(),response;
        if(state.is(RackContent.RACK_COMPUTER)){
            t.putString("dimension",dim);t.putString("choose","warehouse");t.putLong("source",pos.asLong());
            response="Rack Computer selected. Click a Pallet Transfer Node to attach it to this warehouse.";
        }else if(state.is(TransportContent.BAY)){
            BlockPos root=GuidewayBlock.root(pos,state);
            if(!DroneBayRoutes.valid(l,root)){response="Complete the 3x3 Pallet Transfer Node first.";}
            else if(dim.equals(t.getStringOr("dimension",""))&&"warehouse".equals(t.getStringOr("choose",""))){
                response=DroneBayRoutes.warehouse(l,root,BlockPos.of(t.getLongOr("source",0)),player.getUUID().toString());
                if(response.startsWith("Bay connected"))t.remove("choose");
            }else if(dim.equals(t.getStringOr("dimension",""))&&"output".equals(t.getStringOr("choose",""))){
                response=DroneBayRoutes.link(l,BlockPos.of(t.getLongOr("source",0)),root);
                if(response.startsWith("Air route linked")){t.remove("choose");t.remove("source");}
            }else{
                var bay=DroneBayRoutes.get(l,root);
                if(bay==null||bay.input())response="Select an OUTPUT node first; empty-hand click a node to toggle INPUT/OUTPUT.";
                else{
                    t.putString("dimension",dim);t.putString("choose","output");t.putLong("source",root.asLong());
                    response="OUTPUT node selected. Travel to the destination INPUT node and click it, even when the source chunk unloads.";
                }
            }
        }else{return InteractionResult.PASS;}
        held.set(DataComponents.CUSTOM_DATA,CustomData.of(t));
        player.sendSystemMessage(Component.literal(response));
        return InteractionResult.SUCCESS;
    }
}
