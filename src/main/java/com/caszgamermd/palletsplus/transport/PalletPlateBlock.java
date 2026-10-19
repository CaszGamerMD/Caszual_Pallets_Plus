package com.caszgamermd.palletsplus.transport;
import com.mojang.serialization.MapCodec;
import com.caszgamermd.caszualadditions.pallet.PalletBlock;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
import java.util.List;
/** A single kit occupies four blocks. Only real pallet blocks and loaded carriers activate it. */
public final class PalletPlateBlock extends Block {
    public static final IntegerProperty PART=IntegerProperty.create("part",0,3);
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    private boolean removing;
    public PalletPlateBlock(Properties p){super(p);registerDefaultState(stateDefinition.any().setValue(PART,0).setValue(POWERED,false));}
    @Override protected MapCodec<? extends Block> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState>b){b.add(PART,POWERED);}
    private static BlockPos cell(BlockPos root,int part){return root.offset(part%2,0,part/2);}
    private static BlockPos root(BlockPos p,BlockState s){return p.offset(-s.getValue(PART)%2,0,-s.getValue(PART)/2);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){for(int i=0;i<4;i++){BlockPos p=cell(c.getClickedPos(),i);if(!c.getLevel().hasChunkAt(p)||!c.getLevel().getWorldBorder().isWithinBounds(p)||c.getLevel().isOutsideBuildHeight(p)||!c.getLevel().getBlockState(p).canBeReplaced())return null;}return defaultBlockState();}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity e,ItemStack stack){if(l.isClientSide())return;for(int i=1;i<4;i++)l.setBlock(cell(p,i),s.setValue(PART,i),3);l.scheduleTick(p,this,2);}
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){if(!l.isClientSide()&&s.getValue(PART)==0)l.scheduleTick(p,this,2);}
    @Override protected void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource r){
        if(s.getValue(PART)!=0)return;
        boolean loaded=l.hasChunksAt(p,p.offset(1,2,1));
        boolean occupied=false;
        if(loaded){for(int i=0;i<4;i++)if(l.getBlockState(cell(p,i).above()).getBlock() instanceof PalletBlock)occupied=true;
            if(!occupied)occupied=l.getEntitiesOfClass(TransportVehicle.class,new AABB(p.getX(),p.getY(),p.getZ(),p.getX()+2,p.getY()+2,p.getZ()+2)).stream().anyMatch(TransportVehicle::hasCargo);}
        for(int i=0;i<4;i++){BlockPos q=cell(p,i);var a=l.getBlockState(q);if(a.is(this)&&a.getValue(POWERED)!=occupied){l.setBlock(q,a.setValue(POWERED,occupied),3);l.updateNeighborsAt(q.below(),this);}}
        l.scheduleTick(p,this,4);
    }
    @Override protected boolean isSignalSource(BlockState s){return true;}
    @Override protected int getSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return s.getValue(POWERED)?15:0;}
    @Override protected int getDirectSignal(BlockState s,BlockGetter l,BlockPos p,Direction d){return d==Direction.UP&&s.getValue(POWERED)?15:0;}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return Block.box(0,0,0,16,s.getValue(POWERED)?1:2,16);}
    private void dismantle(ServerLevel l,BlockPos p,BlockState s,boolean drop){if(removing)return;removing=true;try{BlockPos root=root(p,s);for(int i=0;i<4;i++){BlockPos q=cell(root,i);var a=l.getBlockState(q);if(a.is(this)&&a.getValue(PART)==i){l.removeBlock(q,false);l.updateNeighborsAt(q.below(),this);}}if(drop)popResource(l,root,new ItemStack(this));}finally{removing=false;}}
    @Override public BlockState playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){if(l instanceof ServerLevel server)dismantle(server,p,s,!player.getAbilities().instabuild);return super.playerWillDestroy(l,p,s,player);}
    @Override protected void affectNeighborsAfterRemoval(BlockState s,ServerLevel l,BlockPos p,boolean moved){dismantle(l,p,s,!moved);super.affectNeighborsAfterRemoval(s,l,p,moved);}
    @Override protected List<ItemStack> getDrops(BlockState s,LootParams.Builder p){return List.of();}
}
