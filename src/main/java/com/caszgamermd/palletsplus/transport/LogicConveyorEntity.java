package com.caszgamermd.palletsplus.transport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
public final class LogicConveyorEntity extends BlockEntity {
    // 0 straight, 1 left, 2 right, 3 hold.
    public int unpowered=0,powered=1;
    public LogicConveyorEntity(BlockPos p,BlockState s){super(TransportContent.LOGIC_ENTITY,p,s);}
    public int route(boolean power){return power?powered:unpowered;}
    public void cycle(boolean high){if(high)powered=(powered+1)%4;else unpowered=(unpowered+1)%4;setChanged();}
    public static String label(int n){return switch(n){case 1->"LEFT";case 2->"RIGHT";case 3->"HOLD";default->"STRAIGHT";};}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.putInt("low",unpowered);out.putInt("high",powered);}
    @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);unpowered=Math.clamp(in.getIntOr("low",0),0,3);powered=Math.clamp(in.getIntOr("high",1),0,3);}
}
