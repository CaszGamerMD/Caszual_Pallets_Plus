package com.caszgamermd.palletsplus.transport;
import com.caszgamermd.palletsplus.PalletsPlus;
import com.google.gson.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;
/** Read-only terminal protocol: bounded work, dimension isolation and proximity checks. */
public final class TransitNet {
    public record Open(BlockPos computer) implements CustomPacketPayload {
        public static final Type<Open> TYPE=new Type<>(PalletsPlus.legacyId("transit_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Open::computer,Open::new);
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Query(BlockPos computer,BlockPos center,int scale,int page) implements CustomPacketPayload {
        public static final Type<Query> TYPE=new Type<>(PalletsPlus.legacyId("transit_query"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Query::computer,BlockPos.STREAM_CODEC,Query::center,ByteBufCodecs.INT,Query::scale,ByteBufCodecs.INT,Query::page,Query::new);
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Snapshot(BlockPos computer,String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(PalletsPlus.legacyId("transit_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,Snapshot::computer,ByteBufCodecs.stringUtf8(180000),Snapshot::json,Snapshot::new);
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static final Map<ServerPlayer,Long> LAST=new WeakHashMap<>();
    public static void initialize(){
        PayloadTypeRegistry.clientboundPlay().register(Open.TYPE,Open.CODEC);PayloadTypeRegistry.serverboundPlay().register(Query.TYPE,Query.CODEC);PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE,Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Query.TYPE,(q,c)->c.server().execute(()->answer(c.player(),q)));
    }
    private static void answer(ServerPlayer player,Query q){
        ServerLevel l=player.level();BlockPos p=q.computer();
        if(!l.hasChunkAt(p)||!l.getBlockState(p).is(TransportContent.COMPUTER)||player.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)>100)return;
        long now=l.getGameTime();if(now-LAST.getOrDefault(player,now-20)<20)return;LAST.put(player,now);
        int scale=Math.clamp(Integer.highestOneBit(Math.max(2,q.scale())),2,1048576);
        int cx=Math.clamp(q.center().getX(),-29999984,29999984),cz=Math.clamp(q.center().getZ(),-29999984,29999984);
        TransitWorldData data=TransitWorldData.get(l);JsonObject out=new JsonObject();out.addProperty("dimension",l.dimension().identifier().toString());out.addProperty("cx",cx);out.addProperty("cz",cz);out.addProperty("scale",scale);out.addProperty("time",now);
        JsonArray colors=new JsonArray();
        for(int z=0;z<64;z++)for(int x=0;x<64;x++){
            int wx=cx+(x-32)*scale,wz=cz+(z-32)*scale;Integer color=data.terrain.get(wx+","+wz);
            BlockPos probe=new BlockPos(wx,0,wz);
            if(l.hasChunkAt(probe)&&l.getWorldBorder().isWithinBounds(probe)){
                int y=l.getHeight(Heightmap.Types.WORLD_SURFACE,wx,wz)-1;BlockPos top=new BlockPos(wx,y,wz);
                color=l.getBlockState(top).getMapColor(l,top).col;data.terrain(wx,wz,color);
            }colors.add(color==null?-1:color);
        }out.add("colors",colors);
        List<JsonObject> visible=new ArrayList<>();double minX=p.getX(),maxX=minX,minZ=p.getZ(),maxZ=minZ;int count=0;
        for(var e:data.vehicles.entrySet()){
            JsonObject v=e.getValue();if(v.getAsJsonArray("pallets").isEmpty())continue;
            double x=v.get("x").getAsDouble(),z=v.get("z").getAsDouble();minX=Math.min(minX,x);maxX=Math.max(maxX,x);minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);count+=v.getAsJsonArray("pallets").size();
            if(Math.abs(x-cx)<scale*32.0&&Math.abs(z-cz)<scale*32.0){JsonObject copy=v.deepCopy();try{copy.addProperty("loaded",l.getEntity(UUID.fromString(e.getKey()))!=null);}catch(IllegalArgumentException ignored){copy.addProperty("loaded",false);}visible.add(copy);}
        }
        out.addProperty("count",count);out.addProperty("fleetX",(int)((minX+maxX)/2));out.addProperty("fleetZ",(int)((minZ+maxZ)/2));
        int fit=2;while(fit<1048576&&Math.max(maxX-minX,maxZ-minZ)>fit*56.0)fit*=2;out.addProperty("fleetScale",fit);
        int pages=Math.max(1,(visible.size()+127)/128),page=Math.clamp(q.page(),0,pages-1);out.addProperty("pages",pages);out.addProperty("page",page);
        JsonArray vehicles=new JsonArray();for(int i=page*128;i<Math.min(visible.size(),(page+1)*128);i++)vehicles.add(visible.get(i));out.add("vehicles",vehicles);
        JsonArray stations=new JsonArray();var it=data.stations.entrySet().iterator();
        while(it.hasNext()){
            JsonObject s=it.next().getValue();BlockPos pos=new BlockPos(s.get("x").getAsInt(),s.get("y").getAsInt(),s.get("z").getAsInt());
            if(l.hasChunkAt(pos)&&!l.getBlockState(pos).is(s.get("kind").getAsString().equals("bay")?TransportContent.BAY:TransportContent.STATION)){it.remove();data.setDirty();continue;}
            if(stations.size()<512&&Math.abs(pos.getX()-cx)<scale*32.0&&Math.abs(pos.getZ()-cz)<scale*32.0)stations.add(s);
        }out.add("stations",stations);out.add("bayLinks",DroneBayRoutes.links(l));ServerPlayNetworking.send(player,new Snapshot(p,out.toString()));
    }
    private TransitNet(){}
}
