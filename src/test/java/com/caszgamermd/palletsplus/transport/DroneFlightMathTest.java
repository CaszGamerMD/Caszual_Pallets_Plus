package com.caszgamermd.palletsplus.transport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DroneFlightMathTest {
    @Test void flightSmoothlyTracksTerrainBetweenTwentyAndFortyBlocks(){
        assertEquals(30,DroneFlightMath.desired(18,0));
        assertEquals(30,DroneFlightMath.desired(60,0));
        assertEquals(30,DroneFlightMath.desired(30,0));
        assertEquals(45,DroneFlightMath.desired(45,15));
        assertEquals(30.18,DroneFlightMath.towards(30,40,.18),1e-8);
        assertEquals(29.82,DroneFlightMath.towards(30,20,.18),1e-8);
        assertEquals(0,DroneFlightMath.forwardBudget(30,15,2.25));
        assertEquals(2.25,DroneFlightMath.forwardBudget(40,15,2.25));
    }
    @Test void landingHasLowMaximumDownwardVelocity(){
        double y=35;
        for(int i=0;i<300&&y>10;i++){
            double next=DroneFlightMath.gentleLanding(y,10);
            assertTrue(y-next<=.12000001);
            assertTrue(next<=y);
            y=next;
        }
        assertEquals(10,y,1e-8);
    }
}