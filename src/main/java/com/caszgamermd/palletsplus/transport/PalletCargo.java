package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.caszualadditions.pallet.*;
import com.caszgamermd.caszualadditions.racks.StorageGroundPalletTransfer;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.*;

/** One owner per payload; all 216 slots persist individually, never byte-indexed. */
public final class PalletCargo {
    public ItemStack deck=ItemStack.EMPTY;
    public final List<ItemStack> items=new ArrayList<>(Collections.nCopies(216,ItemStack.EMPTY));
    public boolean empty(){return deck.isEmpty();}
    public int visible(){int n=0;for(var s:items)if(!s.isEmpty())n++;return Math.min(27,n);}
    public boolean lift(ServerLevel level,BlockPos pos){
        if(!empty())return false;
        PalletBlockEntity source=StorageGroundPalletTransfer.wrappedController(level,pos);
        if(source==null)return false;
        BlockPos root=source.getBlockPos();BlockState state=source.getBlockState();
        deck=source.createDropStack();
        for(int i=0;i<216;i++)items.set(i,source.getItem(i).copy());
        source.clearContent();
        PalletCargoCollisionBlock.clear(level,root,state);
        for(int i=1;i<4;i++)level.removeBlock(PalletBlock.partPos(root,i,state),false);
        level.removeBlock(root,false);
        return true;
    }
    public boolean place(ServerLevel level,BlockPos pos,Direction facing){
        if(!(deck.getItem() instanceof BlockItem bi)||!(bi.getBlock() instanceof PalletBlock block))return false;
        BlockState root=block.defaultBlockState().setValue(PalletBlock.PART,0).setValue(PalletBlock.FACING,facing).setValue(PalletBlock.LEGACY,false);
        for(int i=0;i<4;i++){
            BlockPos p=PalletBlock.partPos(pos,i,root);
            if(!level.hasChunkAt(p)||level.isOutsideBuildHeight(p.above(2))||!level.getWorldBorder().isWithinBounds(p)
                ||level.getBlockState(p.below()).getCollisionShape(level,p.below()).isEmpty())return false;
            for(int h=0;h<3;h++)if(!level.getBlockState(p.above(h)).canBeReplaced())return false;
        }
        // Suppress neighbor updates until every part exists. Roll back any partial placement.
        List<BlockPos> placed=new ArrayList<>();boolean ok=true;
        for(int i=0;i<4;i++){
            BlockPos p=PalletBlock.partPos(pos,i,root);
            if(!level.setBlock(p,root.setValue(PalletBlock.PART,i),2)){ok=false;break;}
            placed.add(p);
            if(level.getBlockEntity(p) instanceof PalletBlockEntity be)be.initialize(pos,i,deck);else{ok=false;break;}
        }
        if(!ok){for(BlockPos p:placed)level.removeBlock(p,false);return false;}
        PalletBlockEntity dest=(PalletBlockEntity)level.getBlockEntity(pos);
        for(int i=0;i<216;i++)dest.setItem(i,items.get(i).copy());
        clear();
        for(BlockPos p:placed)level.updateNeighborsAt(p,block);
        return true;
    }
    public void clear(){deck=ItemStack.EMPTY;Collections.fill(items,ItemStack.EMPTY);}
    public void drop(ServerLevel level,double x,double y,double z){
        if(!deck.isEmpty())Containers.dropItemStack(level,x,y,z,deck.copy());
        for(ItemStack s:items)if(!s.isEmpty())Containers.dropItemStack(level,x,y,z,s.copy());
        clear();
    }
    public void save(ValueOutput out,int index){
        out.store("deck_"+index,ItemStack.OPTIONAL_CODEC,deck);
        out.store("load_"+index,ItemStack.OPTIONAL_CODEC.listOf(),items);
    }
    public void load(ValueInput in,int index){
        clear();deck=in.read("deck_"+index,ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        var saved=in.read("load_"+index,ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for(int i=0;i<Math.min(216,saved.size());i++)items.set(i,saved.get(i));
    }
}
