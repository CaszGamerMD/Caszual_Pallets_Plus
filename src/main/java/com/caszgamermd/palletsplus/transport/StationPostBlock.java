package com.caszgamermd.palletsplus.transport;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
public final class StationPostBlock extends Block {
    public StationPostBlock(Properties p){super(p);}
    @Override protected MapCodec<? extends Block> codec(){return MapCodec.unit(this);}
    public static boolean at(ServerLevel l,BlockPos node,Direction facing){
        for(int side:new int[]{-2,2})for(int y=0;y<=1;y++){
            BlockPos p=node.relative(facing.getClockWise(),side).above(y);
            if(l.hasChunkAt(p)&&l.getBlockState(p).is(TransportContent.STATION)){TransitWorldData.get(l).station(p,"station");return true;}
        }return false;
    }
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){if(l instanceof ServerLevel server)TransitWorldData.get(server).station(p,"station");}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(l instanceof ServerLevel server)for(var v:server.getEntitiesOfClass(TransportVehicle.class,new AABB(p).inflate(4)))v.releaseStation();
        return InteractionResult.SUCCESS;
    }
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Shapes.or(Block.box(2,0,2,14,2,14),Block.box(6,2,6,10,24,10),Block.box(2,17,5,14,29,11));}
}
