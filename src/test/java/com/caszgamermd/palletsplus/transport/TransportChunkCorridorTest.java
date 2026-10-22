package com.caszgamermd.palletsplus.transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransportChunkCorridorTest {
    @Test void fullTenCarTrainStaysLoadedBehindEngineAndAlongTrack(){
        var chunks=TransportChunkCorridor.plan(1,8.5,8.5,8.5,-87.5);
        assertTrue(chunks.contains(new TransportChunkCorridor.Chunk(0,0)));
        assertTrue(chunks.contains(new TransportChunkCorridor.Chunk(0,-6)),"Ahead for a 30 m/s engine");
        assertTrue(chunks.contains(new TransportChunkCorridor.Chunk(0,6)),"Rear covers all ten cars");
        assertTrue(chunks.size()<=14,"Planner remains bounded");
    }
    @Test void conveyorLooksAheadButDoesNotKeepAnEntireNetworkLoaded(){
        var chunks=TransportChunkCorridor.plan(0,8.5,8.5,56.5,8.5);
        assertTrue(chunks.contains(new TransportChunkCorridor.Chunk(3,0)));
        assertFalse(chunks.contains(new TransportChunkCorridor.Chunk(-1,0)));
        assertTrue(chunks.size()<=4);
    }
    @Test void droneKeepsBothItsLocalCorridorAndRemoteLandingBay(){
        var chunks=TransportChunkCorridor.plan(4,8.5,8.5,1500.5,1500.5);
        assertTrue(chunks.contains(new TransportChunkCorridor.Chunk(93,93)));
        assertTrue(chunks.contains(new TransportChunkCorridor.Chunk(0,0)));
        assertTrue(chunks.size()<=9,"Only corridor samples and destination, not the full flight path");
    }
    @Test void negativeCoordinatesAndCorruptInputsStaySafe(){
        var chunks=TransportChunkCorridor.plan(0,-.25,-16.1,47.75,-16.1);
        assertTrue(chunks.contains(new TransportChunkCorridor.Chunk(-1,-2)));
        assertEquals(0,TransportChunkCorridor.plan(1,Double.NaN,0,96,0).size());
        assertEquals(0,TransportChunkCorridor.plan(4,0,0,Double.POSITIVE_INFINITY,0).size());
    }
    @Test void parkedPassengerCarsNeverRequestRemoteCorridors(){
        var chunks=TransportChunkCorridor.plan(3,8.5,8.5,800,8.5);
        assertEquals(1,chunks.size());
    }
}
