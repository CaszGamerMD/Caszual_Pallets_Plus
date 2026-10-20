package com.caszgamermd.palletsplus.transport;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.*;
import java.util.*;

/** Nine occupied cells per module. No overhanging, non-colliding side artwork. */
public final class GuidewayBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED;
    public static final IntegerProperty PART=IntegerProperty.create("part",0,8);
    /** Only T-junctions use nonzero values; persisted on all nine cells. */
    public static final IntegerProperty OUTLET=IntegerProperty.create("outlet",0,2);
    public final String kind;
    private boolean removing;
    public GuidewayBlock(String kind,Properties properties){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,4).setValue(POWERED,false).setValue(OUTLET,0));}
    @Override protected MapCodec<? extends Block> codec(){return MapCodec.unit(this);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,PART,POWERED,OUTLET);}
    public static BlockPos cell(BlockPos root,Direction facing,int part){return root.relative(facing.getClockWise(),part%3-1).relative(facing,1-part/3);}
    public static BlockPos root(BlockPos pos,BlockState s){return pos.relative(s.getValue(FACING).getClockWise(),1-s.getValue(PART)%3).relative(s.getValue(FACING),s.getValue(PART)/3-1);}
    public boolean junction(){return kind.equals("conveyor_t_junction");}
    public boolean rail(){return kind.startsWith("pallet_rail");}
    public boolean bay(){return kind.equals("drone_parking_bay");}
    public int rise(){return kind.endsWith("_up")?1:kind.endsWith("_down")?-1:0;}
    public Direction exit(Direction entry){return kind.endsWith("_left")?entry.getCounterClockWise():kind.endsWith("_right")?entry.getClockWise():entry;}
    public static boolean complete(Level l,BlockPos p){
        if(!l.hasChunkAt(p))return false;
        BlockState s=l.getBlockState(p);
        if(!(s.getBlock() instanceof GuidewayBlock)||s.getValue(PART)!=4)return false;
        for(int i=0;i<9;i++){
            BlockPos c=cell(p,s.getValue(FACING),i);
            if(!l.hasChunkAt(c))return false;
            BlockState a=l.getBlockState(c);
            if(a.getBlock()!=s.getBlock()||a.getValue(PART)!=i||a.getValue(FACING)!=s.getValue(FACING)||
                (s.getBlock() instanceof GuidewayBlock way&&way.junction()&&a.getValue(OUTLET)!=s.getValue(OUTLET)))return false;
        }return true;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){
        Direction d=c.getHorizontalDirection();BlockPos p=c.getClickedPos();
        for(int i=0;i<9;i++){
            BlockPos q=cell(p,d,i);
            if(!c.getLevel().hasChunkAt(q)||c.getLevel().isOutsideBuildHeight(q)||!c.getLevel().getWorldBorder().isWithinBounds(q)||!c.getLevel().getBlockState(q).canBeReplaced())return null;
        }return defaultBlockState().setValue(FACING,d);
    }
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity e,ItemStack stack){
        super.setPlacedBy(l,p,s,e,stack);
        if(l instanceof ServerLevel server && bay())TransitWorldData.get(server).station(p,"bay");
        if(!l.isClientSide())for(int i=0;i<9;i++)if(i!=4)l.setBlock(cell(p,s.getValue(FACING),i),s.setValue(PART,i),3);
    }
    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos p,BlockState s){return kind.equals("conveyor_logic")&&s.getValue(PART)==4?new LogicConveyorEntity(p,s):null;}
    public static boolean powered(Level l,BlockPos r){
        var s=l.getBlockState(r);if(!(s.getBlock() instanceof GuidewayBlock))return false;
        for(int i=0;i<9;i++)if(l.hasNeighborSignal(cell(r,s.getValue(FACING),i)))return true;return false;
    }
    @Override protected void neighborChanged(BlockState s,Level l,BlockPos p,Block b,net.minecraft.world.level.redstone.Orientation o,boolean moved){
        if(l.isClientSide()||(!kind.equals("conveyor_hold")&&!kind.equals("conveyor_logic")&&!kind.equals("pallet_rail_index")))return;
        BlockPos r=root(p,s);if(!complete(l,r))return;
        boolean wasPowered=l.getBlockState(r).getValue(POWERED),power=powered(l,r);
        for(int i=0;i<9;i++){BlockPos q=cell(r,s.getValue(FACING),i);var a=l.getBlockState(q);if(a.is(this)&&a.getValue(POWERED)!=power)l.setBlock(q,a.setValue(POWERED,power),2);}
        if(kind.equals("pallet_rail_index")&&power&&!wasPowered&&l instanceof ServerLevel server)advanceIndex(server,r,null);
    }
    private static void advanceIndex(ServerLevel level,BlockPos root,net.minecraft.world.entity.player.Player player){
        String result="Stop an engine on this indexing rail first.";
        for(var engine:level.getEntitiesOfClass(TransportVehicle.class,new net.minecraft.world.phys.AABB(root).inflate(96)))
            if(engine.indexedAt(root)){result=engine.advanceOneCar();break;}
        if(player!=null)player.sendSystemMessage(net.minecraft.network.chat.Component.literal(result));
    }
    public static boolean busy(ServerLevel level,BlockPos root){
        return !level.getEntitiesOfClass(TransportVehicle.class,new net.minecraft.world.phys.AABB(root).inflate(3,3,3)).isEmpty();
    }
    /** Changes one persistent outlet without dismantling or rotating the 3x3 footprint. */
    public static String cycleJunction(ServerLevel l,BlockPos root){
        if(!complete(l,root))return "Finish the T-junction first.";
        BlockState center=l.getBlockState(root);
        if(!(center.getBlock() instanceof GuidewayBlock block)||!block.junction())return "Not a T-junction.";
        if(busy(l,root))return "Wait until all pallets clear the junction before switching.";
        int selected=(center.getValue(OUTLET)+1)%3;
        Direction stem=center.getValue(FACING);
        for(int i=0;i<9;i++){
            BlockPos cell=cell(root,stem,i);
            BlockState current=l.getBlockState(cell);
            l.setBlock(cell,current.setValue(OUTLET,selected),2);
        }
        return "T-junction OUT: "+ConveyorJunction.port(stem,selected).getName().toUpperCase(java.util.Locale.ROOT)+
            " — the other two arms are inputs.";
    }
    @Override protected net.minecraft.world.InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,net.minecraft.world.entity.player.Player player,net.minecraft.world.phys.BlockHitResult hit){
        if(bay()){
            if(l instanceof ServerLevel server)
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(DroneBayRoutes.mode(server,root(p,s))));
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if(junction()){
            if(l instanceof ServerLevel server)player.sendSystemMessage(net.minecraft.network.chat.Component.literal(cycleJunction(server,root(p,s))));
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if(kind.equals("pallet_rail_index")){
            if(l instanceof ServerLevel server)advanceIndex(server,root(p,s),player);
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if(!kind.equals("conveyor_logic"))return net.minecraft.world.InteractionResult.PASS;
        if(!l.isClientSide()&&l.getBlockEntity(root(p,s)) instanceof LogicConveyorEntity logic){logic.cycle(player.isShiftKeyDown());player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Logic conveyor: LOW = "+LogicConveyorEntity.label(logic.unpowered)+" / HIGH = "+LogicConveyorEntity.label(logic.powered)+". Click edits LOW; sneak-click edits HIGH."));}
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    private void removeModule(ServerLevel l,BlockPos p,BlockState s,boolean drop){
        if(removing)return;
        removing=true;
        try{
            BlockPos r=root(p,s);
            for(int i=0;i<9;i++){
                BlockPos q=cell(r,s.getValue(FACING),i);BlockState actual=l.getBlockState(q);
                if(actual.is(this)&&actual.getValue(PART)==i&&actual.getValue(FACING)==s.getValue(FACING))l.removeBlock(q,false);
            }
            if(drop)Block.popResource(l,r,new ItemStack(this));
        }finally{removing=false;}
    }
    @Override public BlockState playerWillDestroy(Level l,BlockPos p,BlockState s,net.minecraft.world.entity.player.Player player){
        if(l instanceof ServerLevel server)removeModule(server,p,s,!player.getAbilities().instabuild);
        return super.playerWillDestroy(l,p,s,player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState s,ServerLevel l,BlockPos p,boolean moved){
        if(!removing)removeModule(l,p,s,!moved);
        super.affectNeighborsAfterRemoval(s,l,p,moved);
    }
    @Override protected List<ItemStack> getDrops(BlockState s,LootParams.Builder loot){return List.of();}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){
        // Five small treads approximate the continuous 1:3 ramp surface.
        if(rise()==0)return Block.box(0,0,0,16,4,16);
        VoxelShape shape=Shapes.empty();
        int row=s.getValue(PART)/3;
        for(int i=0;i<8;i++){
            double h=4+rise()*((2-row)*16+(8-i)*2)/3.0;
            double z=i*2; Direction d=s.getValue(FACING);
            VoxelShape piece=switch(d){
                case NORTH->Block.box(0,Math.min(0,h-2),z,16,Math.max(1,h),z+2);
                case SOUTH->Block.box(0,Math.min(0,h-2),14-z,16,Math.max(1,h),16-z);
                case EAST->Block.box(14-z,Math.min(0,h-2),0,16-z,Math.max(1,h),16);
                default->Block.box(z,Math.min(0,h-2),0,z+2,Math.max(1,h),16);
            };shape=Shapes.or(shape,piece);
        }return shape;
    }
}
