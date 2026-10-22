package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.caszualadditions.pallet.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.*;

public final class TransportRenderer extends EntityRenderer<TransportVehicle,TransportRenderState> {
    private static final BlockDisplayContext DISPLAY=BlockDisplayContext.create();
    private final BlockModelResolver resolver;
    public TransportRenderer(EntityRendererProvider.Context context){super(context);resolver=context.getBlockModelResolver();shadowRadius=1.2f;}
    @Override public TransportRenderState createRenderState(){return new TransportRenderState();}
    @Override public void extractRenderState(TransportVehicle e,TransportRenderState s,float partial){
        super.extractRenderState(e,s,partial);s.kind=e.kind();s.yaw=e.getYRot();s.rotor=(e.tickCount+partial)*32;
        if(s.kind>0)resolver.update(s.chassis,TransportContent.MODELS.get(TransportContent.VEHICLES[s.kind]).defaultBlockState(),DISPLAY);
        resolver.update(s.blade,Blocks.POLISHED_DEEPSLATE.defaultBlockState(),DISPLAY);
        resolver.update(s.crate,PalletContent.CARDBOARD_RENDER_PROXY.defaultBlockState(),DISPLAY);
        resolver.update(s.glass,Blocks.GLASS.defaultBlockState(),DISPLAY);
        for(int i=0;i<3;i++){
            var deck=e.deck(i);s.loaded[i]=!deck.isEmpty();s.counts[i]=e.visible(i);if(!s.loaded[i])continue;
            for(int j=0;j<4;j++){
                Block material=Blocks.OAK_PLANKS;boolean plastic=false;
                if(deck.getItem() instanceof BlockItem bi&&bi.getBlock() instanceof PalletBlock p){
                    material=switch(p.kind()){
                        case WOOD->PalletData.wood(deck).get(j);
                        case IRON->Blocks.IRON_BLOCK;
                        case COPPER->PalletContent.copperTextureBlock(p.copperAge());
                        case PLASTIC->PalletContent.PALLET_RENDER_PROXY;
                    };plastic=p.kind()==PalletKind.PLASTIC;
                }
                resolver.update(s.boards[i][j],material.defaultBlockState(),DISPLAY);
                s.boards[i][j].tintLayers().clear();if(plastic)s.boards[i][j].tintLayers().add(PalletData.plasticColor(deck));
            }
        }
    }
    @Override public void submit(TransportRenderState s,PoseStack p,SubmitNodeCollector c,CameraRenderState camera){
        super.submit(s,p,c,camera);p.pushPose();p.mulPose(Axis.YP.rotationDegrees(180-s.yaw));
        if(s.kind>0){p.pushPose();p.scale(3,3,3);p.translate(-.5,0,-.5);s.chassis.submit(p,c,s.lightCoords,OverlayTexture.NO_OVERLAY,0);p.popPose();}
        if(s.kind==4)for(float x:new float[]{-1.08f,1.08f})for(float z:new float[]{-1.08f,1.08f}){
            p.pushPose();p.translate(x,2.48,z);p.mulPose(Axis.YP.rotationDegrees(s.rotor));piece(s.blade,s,p,c,-.48f,0,-.055f,.96f,.025f,.11f);piece(s.blade,s,p,c,-.055f,0,-.48f,.11f,.025f,.96f);p.popPose();
        }
        for(int i=0;i<3;i++)if(s.loaded[i]){
            p.pushPose();p.translate(-.9,s.kind==2?.68:s.kind==4?.20:0,s.kind==2?-3.1+i*2.2:-.9);
            for(float x:new float[]{.10f,.80f,1.50f})piece(s.boards[i][0],s,p,c,x,0,.06f,.16f,.12f,1.68f);
            for(int j=0;j<4;j++)piece(s.boards[i][j],s,p,c,.02f,.13f,.03f+j*.45f,1.76f,.12f,.38f);
            for(int n=0;n<s.counts[i];n++)piece(s.crate,s,p,c,.12f+n%3*.54f,.27f+n/9*.51f,.12f+n/3%3*.54f,.51f,.49f,.51f);
            float height=Math.max(.6f,.28f+(float)Math.ceil(s.counts[i]/9.0)*.51f);
            piece(s.glass,s,p,c,.015f,.25f,.015f,1.77f,height,.018f);
            piece(s.glass,s,p,c,.015f,.25f,1.77f,1.77f,height,.018f);
            piece(s.glass,s,p,c,.015f,.25f,.04f,.018f,height,1.70f);
            piece(s.glass,s,p,c,1.77f,.25f,.04f,.018f,height,1.70f);
            p.popPose();
        }p.popPose();
    }
    private static void piece(BlockModelRenderState b,TransportRenderState s,PoseStack p,SubmitNodeCollector c,float x,float y,float z,float a,float h,float d){p.pushPose();p.translate(x,y,z);p.scale(a,h,d);b.submit(p,c,s.lightCoords,OverlayTexture.NO_OVERLAY,0);p.popPose();}
}
