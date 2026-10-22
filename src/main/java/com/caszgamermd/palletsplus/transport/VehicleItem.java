package com.caszgamermd.palletsplus.transport;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
public final class VehicleItem extends Item {
    private final int kind;
    public VehicleItem(int kind,Properties p){super(p);this.kind=kind;}
    @Override public InteractionResult useOn(UseOnContext c){
        if(!(c.getLevel() instanceof ServerLevel l))return InteractionResult.SUCCESS;
        var s=l.getBlockState(c.getClickedPos());
        if(!(s.getBlock() instanceof GuidewayBlock w)||!(kind==4?w.bay():w.rail()))return InteractionResult.FAIL;
        BlockPos p=GuidewayBlock.root(c.getClickedPos(),s);
        if(!GuidewayBlock.complete(l,p))return InteractionResult.FAIL;
        var v=TransportContent.VEHICLE.create(l,EntitySpawnReason.EVENT);if(v==null)return InteractionResult.FAIL;
        v.configure(kind,p,s.getValue(GuidewayBlock.FACING));
        if(!l.noCollision(v)||!l.getEntitiesOfClass(TransportVehicle.class,v.getBoundingBox()).isEmpty()){
            if(c.getPlayer()!=null)c.getPlayer().sendSystemMessage(Component.literal("Transport space is obstructed."));return InteractionResult.FAIL;
        }
        if(l.addFreshEntity(v)){if(c.getPlayer()==null||!c.getPlayer().getAbilities().instabuild)c.getItemInHand().shrink(1);return InteractionResult.SUCCESS;}
        return InteractionResult.FAIL;
    }
}
