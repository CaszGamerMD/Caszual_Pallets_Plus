package com.caszgamermd.palletsplus.transport;

import net.minecraft.core.Direction;

/** Three-way, one-way merging conveyor. The block FACING points at its stem;
 * left and right are the other arms. Exactly one arm is configured as output.
 * Incoming pallets may arrive through either of the remaining two arms. */
public final class ConveyorJunction {
    public static Direction port(Direction stem,int index){
        return switch(index){
            case 0->stem;
            case 1->stem.getClockWise();
            case 2->stem.getCounterClockWise();
            default->throw new IllegalArgumentException("Invalid T-junction port "+index);
        };
    }
    public static boolean contains(Direction stem,Direction port){
        return port==stem||port==stem.getClockWise()||port==stem.getCounterClockWise();
    }
    public static boolean accepts(Direction stem,int selectedOutput,Direction travel){
        Direction incomingPort=travel.getOpposite();
        return contains(stem,incomingPort)&&incomingPort!=port(stem,selectedOutput);
    }
    /** -2 means no valid entry; -1 left, 0 straight, +1 right. */
    public static int turn(Direction stem,int selectedOutput,Direction travel){
        if(!accepts(stem,selectedOutput,travel))return -2;
        Direction output=port(stem,selectedOutput);
        if(output==travel)return 0;
        if(output==travel.getClockWise())return 1;
        if(output==travel.getCounterClockWise())return -1;
        return -2;
    }
    private ConveyorJunction(){}
}
