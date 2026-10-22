package com.caszgamermd.palletsplus.transport;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
public final class TransportRenderState extends EntityRenderState {
    public int kind;public float yaw,rotor;
    public final BlockModelRenderState chassis=new BlockModelRenderState();
    public final BlockModelRenderState blade=new BlockModelRenderState();
    public final BlockModelRenderState[][] boards=new BlockModelRenderState[3][4];
    public final BlockModelRenderState crate=new BlockModelRenderState(),glass=new BlockModelRenderState();
    public final boolean[] loaded=new boolean[3];public final int[] counts=new int[3];
    public TransportRenderState(){for(int i=0;i<3;i++)for(int j=0;j<4;j++)boards[i][j]=new BlockModelRenderState();}
}
