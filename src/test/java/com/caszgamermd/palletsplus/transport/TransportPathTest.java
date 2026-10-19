package com.caszgamermd.palletsplus.transport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TransportPathTest {
    @Test void everyRotatedExitMeetsTheFollowingEntrance(){
        for(int rotation=0;rotation<4;rotation++)for(int turn=-1;turn<=1;turn++)for(int rise=-1;rise<=1;rise++){
            if(turn!=0&&rise!=0)continue;
            var end=TransportPath.sample(turn,rise,rotation,1);
            int nextRotation=Math.floorMod(rotation+turn,4);
            var start=TransportPath.sample(0,0,nextRotation,0);
            var direction=TransportPath.sample(0,0,nextRotation,1);
            assertEquals(end.x(),direction.x()*2+start.x(),1e-9);
            assertEquals(end.z(),direction.z()*2+start.z(),1e-9);
            assertEquals(end.y(),rise+start.y(),1e-9);
        }
    }
    @Test void turnsStayOnTheActualRollerArc(){
        for(int turn:new int[]{-1,1})for(int i=0;i<=100;i++){
            var p=TransportPath.sample(turn,0,0,i/100.0);
            assertEquals(1.5,Math.hypot(p.x()-turn*1.5,p.z()-1.5),1e-9);
        }
    }
    @Test void rampsMeetFlatDecksWithoutAHeightJump(){
        for(int rise:new int[]{-1,1}){
            assertEquals(.26,TransportPath.sample(0,rise,0,0).y(),1e-9);
            assertEquals(.26+rise,TransportPath.sample(0,rise,0,1).y(),1e-9);
            assertEquals(.26+rise*.5,TransportPath.sample(0,rise,0,.5).y(),1e-9);
        }
    }
}
