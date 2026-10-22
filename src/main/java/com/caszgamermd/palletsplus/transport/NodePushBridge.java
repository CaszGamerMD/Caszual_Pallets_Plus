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
        if(loading)return DroneBayCargo.find(level,node)!=null;
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
    private NodePushBridge(){}
}
