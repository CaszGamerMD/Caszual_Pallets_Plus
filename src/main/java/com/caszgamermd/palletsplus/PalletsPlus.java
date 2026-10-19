package com.caszgamermd.palletsplus;

import com.caszgamermd.palletsplus.transport.TransportContent;
import com.caszgamermd.palletsplus.integration.CrewTransport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class PalletsPlus implements ModInitializer {
    public static final String MOD_ID="caszual_pallets_plus";
    // Preserve existing transport blocks, entities, cargo and map records in saves.
    // Additions main does not register these IDs; this addon is their sole owner.
    public static Identifier legacyId(String path){return Identifier.fromNamespaceAndPath("caszual_additions",path);}
    public void onInitialize(){
        TransportContent.initialize();
        CrewTransport.initialize();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,Identifier.fromNamespaceAndPath(MOD_ID,"transport"),
            FabricItemGroup.builder().title(Component.literal("Caszual Pallets Plus"))
                .icon(()->new ItemStack(TransportContent.ITEMS.get("pallet_engine")))
                .displayItems((parameters,output)->TransportContent.ITEMS.values().forEach(output::accept)).build());
    }
}
