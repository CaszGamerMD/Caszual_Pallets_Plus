package com.caszgamermd.palletsplus.transport;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
public final class TransitComputerBlock extends Block {
    public TransitComputerBlock(Properties p){super(p);}
    @Override protected MapCodec<? extends Block> codec(){return MapCodec.unit(this);}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){if(player instanceof ServerPlayer sp)ServerPlayNetworking.send(sp,new TransitNet.Open(p));return InteractionResult.SUCCESS;}
}
