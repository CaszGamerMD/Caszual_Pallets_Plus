package com.caszgamermd.palletsplus.transport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DroneBayRoutesTest {
    @Test void bayModesCannotSelfLinkAndDroneClearanceIsBounded(){
        assertEquals(30,DroneFlightMath.CRUISE_CLEARANCE);
        assertTrue(DroneFlightMath.MIN_CLEARANCE<DroneFlightMath.CRUISE_CLEARANCE);
        assertTrue(DroneFlightMath.CRUISE_CLEARANCE<DroneFlightMath.MAX_CLEARANCE);
        double height=20;
        for(int i=0;i<40;i++){
            double next=DroneFlightMath.towards(height,50,.35);
            assertTrue(next-height<=.3500001);
            height=next;
        }
        assertEquals(34,height,.00001);
    }
}
