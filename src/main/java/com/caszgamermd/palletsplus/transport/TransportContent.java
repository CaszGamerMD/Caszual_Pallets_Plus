package com.caszgamermd.palletsplus.transport;

import com.caszgamermd.palletsplus.PalletsPlus;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import java.util.*;

public final class TransportContent {
    public static final Map<String,GuidewayBlock> WAYS=new LinkedHashMap<>();
    public static final Map<String,Block> MODELS=new LinkedHashMap<>();
    public static final Map<String,Item> ITEMS=new LinkedHashMap<>();
    public static EntityType<TransportVehicle> VEHICLE;
    public static Block BAY,STATION,PLATE,COMPUTER;
    public static net.minecraft.world.level.block.entity.BlockEntityType<LogicConveyorEntity> LOGIC_ENTITY;
    public static Item CONTROLLER;
    public static final String[] VEHICLES={"conveyor_pallet","pallet_engine","pallet_car","passenger_car","pallet_drone"};
    public static void initialize(){
        for(String name:List.of("conveyor_hold","conveyor_logic","conveyor_straight","conveyor_left","conveyor_right","conveyor_t_junction","conveyor_up","conveyor_down","pallet_rail","pallet_rail_index","pallet_rail_left","pallet_rail_right")){
            var key=ResourceKey.create(Registries.BLOCK,PalletsPlus.legacyId(name));
            var block=new GuidewayBlock(name,BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion().setId(key));
            Registry.register(BuiltInRegistries.BLOCK,key,block); WAYS.put(name,block);
            var ik=ResourceKey.create(Registries.ITEM,PalletsPlus.legacyId(name));
            ITEMS.put(name,Registry.register(BuiltInRegistries.ITEM,ik,new BlockItem(block,new Item.Properties().setId(ik).useBlockDescriptionPrefix())));
        }
        // Save-compatible legacy ID stays registered. A native addon item ID
        // additionally lets players discover the T-junction under
        // @caszual_pallets_plus in recipe browsers and creative search.
        var tKey=ResourceKey.create(Registries.ITEM,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(PalletsPlus.MOD_ID,"conveyor_t_junction"));
        ITEMS.put("junction_search",Registry.register(BuiltInRegistries.ITEM,tKey,
            new BlockItem(WAYS.get("conveyor_t_junction"),
                new Item.Properties().setId(tKey).useBlockDescriptionPrefix())));
        LOGIC_ENTITY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,PalletsPlus.legacyId("logic_conveyor"),net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(LogicConveyorEntity::new,WAYS.get("conveyor_logic")).build());
        STATION=block("train_station_post",StationPostBlock::new);
        // Only retain the BLOCK registry ID for existing world saves.
        // There is intentionally no pressure-plate item registration,
        // creative entry, JEI recipe or pallet handoff behavior.
        var legacyPlateKey=ResourceKey.create(Registries.BLOCK,PalletsPlus.legacyId("pallet_pressure_plate"));
        PLATE=Registry.register(BuiltInRegistries.BLOCK,legacyPlateKey,
            new PalletPlateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                .strength(4).noOcclusion()
                .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)
                .setId(legacyPlateKey)));
        COMPUTER=block("pallet_transit_computer",TransitComputerBlock::new);
        DroneChunkLoading.initialize();DroneBayAutomation.initialize();TransitNet.initialize();
        var bayKey=ResourceKey.create(Registries.BLOCK,PalletsPlus.legacyId("drone_parking_bay"));
        BAY=Registry.register(BuiltInRegistries.BLOCK,bayKey,new GuidewayBlock("drone_parking_bay",BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4).pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).noOcclusion().setId(bayKey)));
        var bik=ResourceKey.create(Registries.ITEM,PalletsPlus.legacyId("drone_parking_bay"));
        // Keep the legacy item ID loadable for old saves; creative and
        // recipes expose only the renamed addon-native Pallet Transfer Node.
        Registry.register(BuiltInRegistries.ITEM,bik,
            new BlockItem(BAY,new Item.Properties().setId(bik).useBlockDescriptionPrefix()));
        var nodeKey=ResourceKey.create(Registries.ITEM,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(PalletsPlus.MOD_ID,"pallet_transfer_node"));
        ITEMS.put("pallet_transfer_node",Registry.register(BuiltInRegistries.ITEM,nodeKey,
            new BlockItem(BAY,new Item.Properties().setId(nodeKey).useBlockDescriptionPrefix())));
        for(int k=1;k<VEHICLES.length;k++){
            String name=VEHICLES[k];
            var key=ResourceKey.create(Registries.BLOCK,PalletsPlus.legacyId(name+"_model"));
            MODELS.put(name,Registry.register(BuiltInRegistries.BLOCK,key,new Block(BlockBehaviour.Properties.of().noCollision().noOcclusion().setId(key))));
            var ik=ResourceKey.create(Registries.ITEM,PalletsPlus.legacyId(name));
            ITEMS.put(name,Registry.register(BuiltInRegistries.ITEM,ik,new VehicleItem(k,new Item.Properties().stacksTo(1).setId(ik))));
        }
        var ck=ResourceKey.create(Registries.ITEM,PalletsPlus.legacyId("transport_controller"));
        CONTROLLER=Registry.register(BuiltInRegistries.ITEM,ck,new TransportController(new Item.Properties().stacksTo(1).setId(ck)));
        ITEMS.put("transport_controller",CONTROLLER);
        var crankKey=ResourceKey.create(Registries.ITEM,PalletsPlus.legacyId("conveyor_crank"));
        ITEMS.put("conveyor_crank",Registry.register(BuiltInRegistries.ITEM,crankKey,new ConveyorCrank(new Item.Properties().stacksTo(1).setId(crankKey))));
        var linkKey=ResourceKey.create(Registries.ITEM,PalletsPlus.legacyId("parking_bay_link"));
        ITEMS.put("parking_bay_link",Registry.register(BuiltInRegistries.ITEM,linkKey,
            new ParkingBayLink(new Item.Properties().stacksTo(1).setId(linkKey))));
        ParkingBayLink.registerInteractions();
        var ek=ResourceKey.create(Registries.ENTITY_TYPE,PalletsPlus.legacyId("pallet_transport"));
        VEHICLE=Registry.register(BuiltInRegistries.ENTITY_TYPE,ek,EntityType.Builder.<TransportVehicle>of(TransportVehicle::new,MobCategory.MISC).sized(2.4f,2.5f).build(ek));
        FabricDefaultAttributeRegistry.register(VEHICLE,TransportVehicle.attributes());
    }
    private static Block block(String name,java.util.function.Function<BlockBehaviour.Properties,Block> factory){
        var key=ResourceKey.create(Registries.BLOCK,PalletsPlus.legacyId(name));var b=Registry.register(BuiltInRegistries.BLOCK,key,factory.apply(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).strength(4).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK).setId(key)));
        var ik=ResourceKey.create(Registries.ITEM,PalletsPlus.legacyId(name));ITEMS.put(name,Registry.register(BuiltInRegistries.ITEM,ik,new BlockItem(b,new Item.Properties().setId(ik).useBlockDescriptionPrefix())));return b;
    }
    private TransportContent(){}
}
