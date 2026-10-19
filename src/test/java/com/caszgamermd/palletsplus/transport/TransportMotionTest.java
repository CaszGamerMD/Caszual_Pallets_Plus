package com.caszgamermd.palletsplus.transport;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class TransportMotionTest {
    @Test void trainAcceleratesForTenSecondsThenHoldsThirtyMetresPerSecond(){
        double speed=0;
        for(int tick=1;tick<=200;tick++){
            speed=TransportMotion.accelerate(speed);
            assertEquals(Math.min(30,tick*.15),speed*20,1e-8);
        }
        for(int tick=0;tick<100;tick++)speed=TransportMotion.accelerate(speed);
        assertEquals(30,speed*20,1e-8);
    }
    @Test void substepsConsumeExactConveyorAndDroneTickBudgets(){
        for(double speed:new double[]{TransportMotion.CONVEYOR_SPEED,TransportMotion.DRONE_SPEED}){
            double travelled=0;
            for(int tick=0;tick<20;tick++){
                double remaining=speed;
                while(remaining>1e-8){double step=TransportMotion.distance(remaining,100);assertTrue(step<=.1);travelled+=step;remaining-=step;}
            }
            assertEquals(speed==TransportMotion.CONVEYOR_SPEED?8:45,travelled,1e-7);
        }
    }
    @Test void stoppingAtCentreAndIndexEndpointDoesNotOvershoot(){
        assertEquals(.003,TransportMotion.distance(1.5,.003),1e-12);
        assertEquals(0,TransportMotion.distance(1.5,0));
        double remaining=TransportMotion.spacing(3.6,6.8),travelled=0;
        while(remaining>1e-8){double step=TransportMotion.distance(TransportMotion.INDEX_SPEED,remaining);travelled+=step;remaining-=step;}
        assertEquals(5.6,travelled,1e-8);
        assertEquals(7.2,TransportMotion.spacing(6.8,6.8),1e-8);
        assertEquals(6.0,TransportMotion.spacing(6.8,4.4),1e-8);
    }
    @Test void driverAndEightPassengersHaveDistinctSeatsWithinTheirCars(){
        assertEquals(1,TransportMotion.seats(1));assertEquals(8,TransportMotion.seats(3));
        assertEquals(0,TransportMotion.seats(2));assertEquals(0,TransportMotion.seats(4));
        var seats=new HashSet<TransportMotion.Seat>();
        for(int i=0;i<8;i++){
            var seat=TransportMotion.seat(3,i);assertTrue(seats.add(seat));
            assertTrue(Math.abs(seat.x())<1);assertTrue(Math.abs(seat.z())<2.2);
        }
        assertThrows(IllegalArgumentException.class,()->TransportMotion.seat(3,8));
    }
}
