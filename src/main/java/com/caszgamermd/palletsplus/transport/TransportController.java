package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.caszualadditions.pallet.PalletBlock;
import com.caszgamermd.caszualadditions.racks.StorageGroundPalletTransfer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import java.util.UUID;

/** Two-click controller. Selections live on the held item and are dimension-scoped. */
public final class TransportController extends Item {
    public TransportController(Properties p){super(p);}
    private static CompoundTag data(ItemStack s){return s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
    private static void save(ItemStack s,CompoundTag t){s.set(DataComponents.CUSTOM_DATA,CustomData.of(t));}
    private static void message(Player p,String s){if(p!=null)p.sendSystemMessage(Component.literal(s));}
    private static String dimension(ServerLevel l){return l.dimension().identifier().toString();}
    private static TransportVehicle selected(ServerLevel l,CompoundTag t){
        if(!dimension(l).equals(t.getStringOr("dimension","")))return null;
        try{return l.getEntity(UUID.fromString(t.getStringOr("vehicle",""))) instanceof TransportVehicle v?v:null;}catch(IllegalArgumentException e){return null;}
    }
    public static InteractionResult useVehicle(Player player,InteractionHand hand,TransportVehicle v){
        if(!(v.level() instanceof ServerLevel l))return InteractionResult.SUCCESS;
        if(v.kind()==1&&(player.isShiftKeyDown()||player.getVehicle()==v)){
            message(player,v.toggleEngine());return InteractionResult.SUCCESS;
        }
        ItemStack stack=player.getItemInHand(hand);CompoundTag t=data(stack);
        if(v.kind()==4&&t.contains("bay")&&dimension(l).equals(t.getStringOr("dimension",""))){
            boolean ok=v.linkBay(BlockPos.of(t.getLongOr("bay",0)));message(player,ok?"Drone destination linked. Empty-hand click to depart.":"Stop the drone and choose a bay different from its home.");if(ok){t.remove("bay");save(stack,t);}return InteractionResult.SUCCESS;
        }
        if(t.contains("pallet")&&dimension(l).equals(t.getStringOr("dimension",""))){
            BlockPos p=BlockPos.of(t.getLongOr("pallet",0));
            boolean ok=p.distToCenterSqr(v.position())<=144&&v.load(l,p);
            message(player,ok?"Pallet loaded; all contents and settings preserved.":"Cannot load: use a stopped carrier with a free slot within 12 blocks of a wrapped pallet.");
            if(ok){t.remove("pallet");save(stack,t);}return InteractionResult.SUCCESS;
        }
        TransportVehicle previous=selected(l,t);
        if(previous!=null&&previous!=v){
            boolean trainPair=previous.kind()>=1&&previous.kind()<=3&&v.kind()>=1&&v.kind()<=3;
            String result=trainPair?TransportVehicle.couplePair(previous,v):(previous.transferTo(v)?"Pallet transferred.":"Pause both carriers; target needs a free pallet slot within 12 blocks.");
            message(player,result);
            if(result.startsWith("Cars coupled")||result.equals("Pallet transferred.")){t.remove("vehicle");save(stack,t);}
            return InteractionResult.SUCCESS;
        }
        t.remove("pallet");t.putString("vehicle",v.getStringUUID());t.putString("dimension",dimension(l));save(stack,t);
        message(player,"Carrier selected. Click ground to unload, a bay to link a drone, or another carrier to transfer cargo. Click another train car to couple (either selection order). Sneak-click a block to clear.");return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult interactLivingEntity(ItemStack stack,Player player,net.minecraft.world.entity.LivingEntity entity,InteractionHand hand){
        return entity instanceof TransportVehicle v?useVehicle(player,hand,v):InteractionResult.PASS;
    }
    @Override public InteractionResult use(net.minecraft.world.level.Level level,Player player,InteractionHand hand){
        if(player.getVehicle() instanceof TransportVehicle engine&&engine.kind()==1){
            if(!level.isClientSide())message(player,engine.toggleEngine());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getLevel() instanceof ServerLevel l))return InteractionResult.SUCCESS;
        Player player=c.getPlayer();if(player==null)return InteractionResult.FAIL;
        if(player.getVehicle() instanceof TransportVehicle engine&&engine.kind()==1){message(player,engine.toggleEngine());return InteractionResult.SUCCESS;}
        ItemStack held=c.getItemInHand();CompoundTag t=data(held);BlockPos p=c.getClickedPos();var state=l.getBlockState(p);
        if(player.isShiftKeyDown()){save(held,new CompoundTag());message(player,"Transport selection cleared.");return InteractionResult.SUCCESS;}
        if(state.getBlock() instanceof PalletBlock){
            var pallet=StorageGroundPalletTransfer.wrappedController(l,p);
            if(pallet==null){message(player,"Wrap this pallet before transporting it.");return InteractionResult.SUCCESS;}
            t.remove("vehicle");t.remove("bay");t.putLong("pallet",pallet.getBlockPos().asLong());t.putString("dimension",dimension(l));save(held,t);
            message(player,"Pallet selected. Click a conveyor or a stopped pallet car/drone to load.");return InteractionResult.SUCCESS;
        }
        if(state.getBlock() instanceof GuidewayBlock w){
            BlockPos root=GuidewayBlock.root(p,state);
            TransportVehicle v=selected(l,t);
            if(w.bay()&&v!=null&&v.kind()==4){message(player,v.linkBay(root)?"Drone route linked. Empty-hand click the drone to depart; click again after unloading to return.":"Stop the drone and choose a different bay.");return InteractionResult.SUCCESS;}
            if(w.bay()&&GuidewayBlock.complete(l,root)){
                t.remove("vehicle");t.remove("pallet");t.putLong("bay",root.asLong());t.putString("dimension",dimension(l));save(held,t);message(player,"Destination bay selected. Click a stopped drone in this dimension to link it, even if the bay is distant.");return InteractionResult.SUCCESS;
            }
            if(!w.rail()&&!w.bay()&&t.contains("pallet")&&dimension(l).equals(t.getStringOr("dimension",""))){
                BlockPos source=BlockPos.of(t.getLongOr("pallet",0));
                if(source.distSqr(root)>144||!GuidewayBlock.complete(l,root)){message(player,"Use a complete conveyor within 12 blocks of the pallet.");return InteractionResult.SUCCESS;}
                TransportVehicle carrier=TransportContent.VEHICLE.create(l,EntitySpawnReason.EVENT);
                if(carrier==null)return InteractionResult.FAIL;
                carrier.configure(0,root,state.getValue(GuidewayBlock.FACING));
                if(!l.noCollision(carrier)||!l.getEntitiesOfClass(TransportVehicle.class,carrier.getBoundingBox()).isEmpty()){message(player,"Conveyor is occupied.");return InteractionResult.SUCCESS;}
                if(l.addFreshEntity(carrier)){
                    if(carrier.load(l,source)){t.remove("pallet");save(held,t);message(player,"Pallet loaded onto conveyor.");}
                    else{carrier.discard();message(player,"Pallet is no longer available or wrapped.");}
                }return InteractionResult.SUCCESS;
            }
        }
        TransportVehicle v=selected(l,t);
        if(v!=null){
            BlockPos place=p.relative(c.getClickedFace());
            boolean ok=place.distToCenterSqr(v.position())<=144&&v.unload(l,place,c.getHorizontalDirection().getOpposite());
            message(player,ok?"Pallet unloaded.":"Pause the carrier and choose clear, supported 2×2 ground with three blocks of clearance within 12 blocks.");
            return InteractionResult.SUCCESS;
        }
        message(player,"Click a wrapped pallet or a carrier first.");return InteractionResult.SUCCESS;
    }
}
