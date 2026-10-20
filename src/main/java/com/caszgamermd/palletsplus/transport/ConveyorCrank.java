package com.caszgamermd.palletsplus.transport;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;

public final class ConveyorCrank extends Item {
    public ConveyorCrank(Properties properties){super(properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var state=context.getLevel().getBlockState(context.getClickedPos());
        if(!(state.getBlock() instanceof GuidewayBlock way)||way.rail()||way.bay())return InteractionResult.PASS;
        if(!(context.getLevel() instanceof ServerLevel level))return InteractionResult.SUCCESS;
        BlockPos root=GuidewayBlock.root(context.getClickedPos(),state);
        if(!GuidewayBlock.complete(level,root)){player.sendSystemMessage(Component.literal("Complete the conveyor module first."));return InteractionResult.SUCCESS;}
        if(way.junction()&&!player.isShiftKeyDown()){
            player.sendSystemMessage(Component.literal(GuidewayBlock.cycleJunction(level,root)));
            return InteractionResult.SUCCESS;
        }
        if(!level.getEntitiesOfClass(TransportVehicle.class,new AABB(root).inflate(3,3,3)).isEmpty()){
            player.sendSystemMessage(Component.literal("Clear the pallet off this conveyor before changing its direction."));return InteractionResult.SUCCESS;
        }
        Direction facing=player.isShiftKeyDown()?state.getValue(GuidewayBlock.FACING).getClockWise():player.getDirection();
        // The footprint is a square: reassign all nine parts atomically before neighbor updates.
        for(int part=0;part<9;part++){
            BlockPos cell=GuidewayBlock.cell(root,facing,part);
            level.setBlock(cell,level.getBlockState(cell).setValue(GuidewayBlock.FACING,facing).setValue(GuidewayBlock.PART,part),2);
        }
        for(int part=0;part<9;part++)level.updateNeighborsAt(GuidewayBlock.cell(root,facing,part),way,null);
        player.sendSystemMessage(Component.literal(way.junction()?
            "T-junction rotated. OUT: "+ConveyorJunction.port(facing,state.getValue(GuidewayBlock.OUTLET)).getName()+". Click with crank or empty hand to choose another output.":
            "Conveyor entry: "+facing.getName()+"; pushes toward "+way.exit(facing).getName()+". Sneak-use turns clockwise."));
        return InteractionResult.SUCCESS;
    }
}
