package com.caszgamermd.palletsplus.client;

/** Duck state on Caszual Additions' pallet renderer. The physical pallet
 * stays a normal ground pallet so storage golems retain direct access. */
public interface PalletPlateRenderOffset {
    double palletsplus$landingOffset();
    void palletsplus$setLandingOffset(double offset);
}