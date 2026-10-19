package com.caszgamermd.palletsplus.transport;
import com.google.gson.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
class TransitWorldDataTest {
    @Test void fullTerrainCacheAndActiveFlightSurviveBinarySave() throws IOException {
        var original=new TransitWorldData();
        for(int i=0;i<65536;i++)original.terrain(i*8,-i*8,0x456789);
        JsonObject flight=new JsonObject();flight.addProperty("active",true);flight.addProperty("x",-4000.5);flight.addProperty("tx",32000);flight.addProperty("status","Moving");flight.addProperty("seen",9000000000L);
        JsonArray pallets=new JsonArray();for(int i=1;i<=3;i++){JsonObject p=new JsonObject();p.addProperty("slot",i);p.addProperty("stacks",216);pallets.add(p);}flight.add("pallets",pallets);original.vehicles.put("test-flight",flight);
        var tag=TransitWorldData.TYPE.codec().encodeStart(NbtOps.INSTANCE,original).getOrThrow();
        assertInstanceOf(CompoundTag.class,tag);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.write((CompoundTag)tag,new DataOutputStream(bytes));
        assertTrue(bytes.size()>65535,"Exercise the NBT UTF string size regression");
        var saved=NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        var restored=TransitWorldData.TYPE.codec().parse(NbtOps.INSTANCE,saved).getOrThrow();
        assertEquals(original.terrain,restored.terrain);assertEquals(flight,restored.vehicles.get("test-flight"));
    }
    @Test void mapCacheEvictsOldestSampleAndKeepsRecentUpdates(){
        var data=new TransitWorldData();for(int i=0;i<=65536;i++)data.terrain(i,0,i);
        assertEquals(65536,data.terrain.size());assertFalse(data.terrain.containsKey("0,0"));
        data.terrain(65536,0,42);assertEquals(42,data.terrain.get("65536,0"));assertEquals(65536,data.terrain.size());
    }
}
