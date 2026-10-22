package com.caszgamermd.palletsplus.transport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PalletPlateLandingYTest {
    @Test void baseRunnersRestOnPressurePlateSurface(){
        assertEquals(PalletPlateLandingY.POWERED_PAD_TOP,
            1+PalletPlateLandingY.RUNNER_BOTTOM+PalletPlateLandingY.offset(true),1e-8);
        assertEquals(PalletPlateLandingY.IDLE_PAD_TOP,
            1+PalletPlateLandingY.RUNNER_BOTTOM+PalletPlateLandingY.offset(false),1e-8);
        assertTrue(PalletPlateLandingY.offset(true)<-.9);
    }
}