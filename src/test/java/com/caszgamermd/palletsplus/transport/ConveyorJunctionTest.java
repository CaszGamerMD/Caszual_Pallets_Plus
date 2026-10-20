package com.caszgamermd.palletsplus.transport;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConveyorJunctionTest {
    @Test void allRotationsProvideTwoInputsAndOneOutput(){
        for(Direction stem:Direction.Plane.HORIZONTAL){
            for(int selected=0;selected<3;selected++){
                Direction output=ConveyorJunction.port(stem,selected);
                int accepted=0;
                for(int entrance=0;entrance<3;entrance++){
                    Direction port=ConveyorJunction.port(stem,entrance);
                    Direction travel=port.getOpposite();
                    boolean valid=ConveyorJunction.accepts(stem,selected,travel);
                    assertEquals(port!=output,valid);
                    if(!valid)continue;
                    accepted++;
                    int turn=ConveyorJunction.turn(stem,selected,travel);
                    assertTrue(turn>=-1&&turn<=1);
                    Direction result=turn<0?travel.getCounterClockWise():turn>0?travel.getClockWise():travel;
                    assertEquals(output,result,"Route must lead from selected inlet to chosen outlet");
                }
                assertEquals(2,accepted);
            }
        }
    }
    @Test void missingArmAndOutputAreNeverAcceptedAsInputs(){
        Direction stem=Direction.NORTH;
        for(int out=0;out<3;out++){
            assertEquals(-2,ConveyorJunction.turn(stem,out,Direction.NORTH)); // Missing south entry port
            assertEquals(-2,ConveyorJunction.turn(stem,out,ConveyorJunction.port(stem,out).getOpposite()));
        }
    }
    @Test void eastWestArmsMergeAsStraightLine(){
        assertEquals(0,ConveyorJunction.turn(Direction.NORTH,1,Direction.EAST));
        assertEquals(0,ConveyorJunction.turn(Direction.NORTH,2,Direction.WEST));
    }
}
