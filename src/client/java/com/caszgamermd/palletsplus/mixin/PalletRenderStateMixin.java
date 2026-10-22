package com.caszgamermd.palletsplus.mixin;

import com.caszgamermd.caszualadditions.pallet.PalletBlockEntityRenderState;
import com.caszgamermd.palletsplus.client.PalletPlateRenderOffset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value=PalletBlockEntityRenderState.class,remap=false)
public abstract class PalletRenderStateMixin implements PalletPlateRenderOffset {
    @Unique private double palletsplus$offset;
    @Override public double palletsplus$landingOffset(){return palletsplus$offset;}
    @Override public void palletsplus$setLandingOffset(double offset){palletsplus$offset=offset;}
}