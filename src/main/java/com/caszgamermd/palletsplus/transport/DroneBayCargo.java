package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.caszualadditions.racks.StorageGroundPalletTransfer;
import com.caszgamermd.caszualadditions.pallet.PalletBlock;
import com.caszgamermd.caszualadditions.pallet.PalletBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** One physical, four-cell wrapped pallet centered inside the nine-cell bay.
 * Its only inventory and pickup address is the root of the placed pallet. */
public final class DroneBayCargo {
    /** West/north corner of the central 2x2 landing footprint. */
    public static BlockPos root(BlockPos center){return center.offset(-1,1,-1);}
    /** Detect even unwrapped pallets for the persistent redstone output.
     * A warehouse pickup still requires wrap for physical Golem safety. */
    public static boolean occupied(ServerLevel level,BlockPos center){
        BlockPos p=root(center);
        if(!level.hasChunkAt(p))return false;
        var state=level.getBlockState(p);
        return state.getBlock() instanceof PalletBlock
            &&state.getValue(PalletBlock.PART)==0
            &&level.getBlockEntity(p) instanceof PalletBlockEntity;
    }
    /** An empty conveyor carrier pulls a real wrapped pallet from the bay
     * immediately adjacent to its conveyor module. No virtual inventory. */
    public static BlockPos nextToConveyor(ServerLevel level,BlockPos center){
        for(Direction direction:Direction.Plane.HORIZONTAL){
            BlockPos bay=center.relative(direction,3);
            if(DroneBayRoutes.valid(level,bay)){
                BlockPos pallet=find(level,bay);
                if(pallet!=null)return pallet;
            }
        }
        return null;
    }
    public static BlockPos find(ServerLevel level,BlockPos center){
        if(!level.hasChunkAt(center))return null;
        var entity=StorageGroundPalletTransfer.wrappedController(level,root(center));
        return entity!=null&&entity.getBlockPos().equals(root(center))?entity.getBlockPos():null;
    }
    public static boolean deposit(ServerLevel level,BlockPos center,PalletCargo cargo){
        if(cargo.empty()||!DroneBayRoutes.valid(level,center))return false;
        var bay=DroneBayRoutes.get(level,center);
        if(bay==null||occupied(level,center))return false;
        // Preserve cargo entirely when there is an obstruction or an older
        // pallet, including where the bay is already filled by a crew.
        return cargo.place(level,root(center),Direction.EAST);
    }
    private DroneBayCargo(){}
}
