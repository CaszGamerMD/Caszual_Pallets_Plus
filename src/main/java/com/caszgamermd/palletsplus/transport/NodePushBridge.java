package com.caszgamermd.palletsplus.transport;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Server-authoritative two-Golem handoff between a real Pallet Transfer Node
 * and an ADJACENT conveyor carrier or stopped train flatbed. Only this addon
 * knows its vehicle type; Caszual Additions workers call complete by bridge. */
public final class NodePushBridge {
    private static boolean adjacent(BlockPos node,TransportVehicle vehicle){
        double dx=Math.abs(vehicle.getX()-node.getX()-.5);
        double dz=Math.abs(vehicle.getZ()-node.getZ()-.5);
        return Math.abs(vehicle.getY()-node.getY())<3.5 &&
            ((dx>=1.6&&dx<=5.5&&dz<=2.2)||(dz>=1.6&&dz<=5.5&&dx<=2.2));
    }
    public static boolean eligible(ServerLevel level,BlockPos node,
            TransportVehicle vehicle,boolean loading){
        if(vehicle==null||!vehicle.isAlive()||!DroneBayRoutes.valid(level,node)
                ||!adjacent(node,vehicle)||!vehicle.canNodeTransfer(loading))
            return false;
        var bay=DroneBayRoutes.get(level,node);
        if(bay==null||(loading&&!bay.output())||(!loading&&!bay.input()))return false;
        if(loading){
            if(DroneBayCargo.find(level,node)!=null)return true;
            // Empty OUTPUT node can request an original pallet from a
            // connected warehouse's loaded OUTPUT aisle rack. This avoids
            // the previous deadlock where no two-Golem crew was dispatched
            // until the player manually placed a pallet on the node.
            // The Additions dispatcher checks the OUTPUT-aisle stock
            // BEFORE reserving or spawning workers. Keeping that lookup
            // there preserves compilation against older declared Additions
            // APIs; Pallets Plus only asserts that transport is adjacent.
            return !DroneBayCargo.occupied(level,node)&&bay.warehouse()!=null;
        }
        return !DroneBayCargo.occupied(level,node);
    }
    public static TransportVehicle adjacentVehicle(ServerLevel level,BlockPos node,boolean loading){
        TransportVehicle closest=null;double distance=Double.POSITIVE_INFINITY;
        for(TransportVehicle vehicle:level.getEntitiesOfClass(TransportVehicle.class,
                new AABB(node).inflate(6,4,6))){
            if(!eligible(level,node,vehicle,loading))continue;
            double d=vehicle.distanceToSqr(node.getX()+.5,node.getY()+.5,node.getZ()+.5);
            if(d<distance){distance=d;closest=vehicle;}
        }
        return closest;
    }
    /** Invoked only after TWO independently walking Golems reach their
     * separate push positions. The ground pallet has exactly one owner
     * before and after the atomic transfer. No replacement is created. */
    public static boolean complete(ServerLevel level,BlockPos node,UUID vehicleId,boolean loading){
        TransportVehicle vehicle=level.getEntity(vehicleId) instanceof TransportVehicle result?result:null;
        // Newly spawned/migrated entities can be visible in a chunk before
        // the UUID lookup resolves. The short loaded-area scan never reaches
        // into other chunks or matches a different transport.
        if(vehicle==null)
            for(var candidate:level.getEntitiesOfClass(TransportVehicle.class,
                    new AABB(node).inflate(6,4,6)))
                if(candidate.getUUID().equals(vehicleId)){vehicle=candidate;break;}
        if(!eligible(level,node,vehicle,loading))return false;
        boolean done=loading?vehicle.load(level,DroneBayCargo.root(node)):
                vehicle.unloadOntoNode(level,node);
        if(!done)org.slf4j.LoggerFactory.getLogger("PalletNodePush").warn(
                "Transfer deferred: loading={} source={} vehicle={} stationaryReady={} sourceWrapped={}",
                loading,node,vehicleId,vehicle.canNodeTransfer(loading),
                DroneBayCargo.find(level,node));
        return done;
    }
    /** The golem team retrieves one physical pallet from a warehouse
     * OUTPUT aisle rack, then walks it to the node and finally loads the
     * actually adjacent carriage. The leader retains all contents if the
     * vehicle departed, became full or changed direction meanwhile. */
    public static boolean completeFromCrew(ServerLevel level,BlockPos node,UUID vehicleId,
            com.caszgamermd.caszualadditions.racks.StorageGolem leader){
        if(!DroneBayRoutes.valid(level,node)||DroneBayCargo.occupied(level,node)
                ||leader==null||!leader.holdsPallet())return false;
        var bay=DroneBayRoutes.get(level,node);
        if(bay==null||!bay.output())return false;
        TransportVehicle vehicle=level.getEntity(vehicleId) instanceof TransportVehicle v?v:null;
        if(vehicle==null)
            for(var v:level.getEntitiesOfClass(TransportVehicle.class,
                    new AABB(node).inflate(6,4,6)))
                if(v.getUUID().equals(vehicleId)){vehicle=v;break;}
        if(vehicle==null||!adjacent(node,vehicle)||!vehicle.canNodeTransfer(true))
            return false;
        return vehicle.loadFromNodeGolem(level,leader);
    }
    private NodePushBridge(){}
}
