package com.caszgamermd.palletsplus.mixin;

import com.caszgamermd.caszualadditions.pallet.PalletBlock;
import com.caszgamermd.caszualadditions.pallet.PalletBlockEntity;
import com.caszgamermd.caszualadditions.pallet.PalletBlockEntityRenderer;
import com.caszgamermd.caszualadditions.pallet.PalletBlockEntityRenderState;
import com.caszgamermd.palletsplus.client.PalletPlateRenderOffset;
import com.caszgamermd.palletsplus.transport.PalletPlateBlock;
import com.caszgamermd.palletsplus.transport.PalletPlateLandingY;
import com.caszgamermd.palletsplus.transport.TransportContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rendering-only lowering for pressure-plate handoffs. Minecraft cannot
 * occupy the same four blocks with both the pressure plate and the normal
 * golem-addressable ground pallet. Physically retaining the pallet at y+1
 * preserves its native controller and inventory. The Pallets Plus addon
 * visually seats the original pallet right on the activated plate. */
@Mixin(value=PalletBlockEntityRenderer.class,remap=false)
public abstract class PalletPlateRenderingMixin {
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void palletsplus$plateLanding(PalletBlockEntity blockEntity,
            PalletBlockEntityRenderState state,float partialTicks,
            Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress,
            CallbackInfo ci){
        double offset=0;
        Level level=blockEntity.getLevel();
        if(level!=null&&blockEntity.isRoot()&&
                blockEntity.getBlockState().getValue(PalletBlock.FACING)==Direction.EAST&&
                !blockEntity.getBlockState().getValue(PalletBlock.LEGACY)){
            BlockPos pad=blockEntity.getBlockPos().below();
            if(level.hasChunksAt(pad,pad.offset(1,0,1))){
                boolean complete=true;
                for(int part=0;part<4;part++){
                    BlockPos cell=pad.offset(part%2,0,part/2);
                    var tile=level.getBlockState(cell);
                    if(!tile.is(TransportContent.PLATE)||!tile.hasProperty(PalletPlateBlock.PART)||
                            tile.getValue(PalletPlateBlock.PART)!=part){
                        complete=false;break;
                    }
                }
                if(complete){
                    // Root y is exactly one block above the plate. Once the
                    // 2x2 pressure plate is powered its top is 1/16 high.
                    // Pallet model runners start at +0.035, so lower all
                    // boards, wrap and item cargo by this exact distance.
                    boolean down=level.getBlockState(pad).getValue(PalletPlateBlock.POWERED);
                    offset=PalletPlateLandingY.offset(down);
                }
            }
        }
        ((PalletPlateRenderOffset)state).palletsplus$setLandingOffset(offset);
    }
    @Inject(method="submit",at=@At("HEAD"))
    private void palletsplus$before(PalletBlockEntityRenderState state,
            PoseStack poseStack,SubmitNodeCollector collector,
            CameraRenderState camera,CallbackInfo ci){
        poseStack.pushPose();
        poseStack.translate(0,((PalletPlateRenderOffset)state).palletsplus$landingOffset(),0);
    }
    @Inject(method="submit",at=@At("TAIL"))
    private void palletsplus$after(PalletBlockEntityRenderState state,
            PoseStack poseStack,SubmitNodeCollector collector,
            CameraRenderState camera,CallbackInfo ci){
        poseStack.popPose();
    }
}