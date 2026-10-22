package com.caszgamermd.palletsplus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;
import com.caszgamermd.palletsplus.transport.*;
public final class PalletsPlusClient implements ClientModInitializer {
    public void onInitializeClient(){
        TransitComputerClient.initialize();
        EntityRenderers.register(TransportContent.VEHICLE,TransportRenderer::new);
    }
}
