package com.caszgamermd.palletsplus.transport;
import com.google.gson.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
/** Terrain is sampled only in loaded chunks, with a persistent chart cache. */
public final class TransitComputerClient {
    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(TransitNet.Open.TYPE,(p,c)->c.client().execute(()->Minecraft.getInstance().gui.setScreen(new Terminal(p.computer()))));
        ClientPlayNetworking.registerGlobalReceiver(TransitNet.Snapshot.TYPE,(p,c)->c.client().execute(()->{if(Minecraft.getInstance().gui.screen() instanceof Terminal t&&t.computer.equals(p.computer()))t.accept(p.json());}));
    }
    private static final class Terminal extends Screen {
        final BlockPos computer;int cx,cz,scale=8,page,ticks=20;JsonObject data;int mapX,mapY,size;
        Terminal(BlockPos p){super(Component.literal("Pallet transit"));computer=p;cx=p.getX();cz=p.getZ();}
        void accept(String json){data=JsonParser.parseString(json).getAsJsonObject();}
        void request(){ClientPlayNetworking.send(new TransitNet.Query(computer,new BlockPos(cx,0,cz),scale,page));}
        void pan(int x,int z){cx=Math.clamp(cx+x*scale*24,-29999984,29999984);cz=Math.clamp(cz+z*scale*24,-29999984,29999984);page=0;}
        void button(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
        @Override protected void init(){
            mapX=12;mapY=45;size=Math.max(64,Math.min(height-105,width-180));
            int x=mapX+size+12,y=mapY;
            button("N",x+35,y,32,()->pan(0,-1));button("W",x,y+23,32,()->pan(-1,0));button("E",x+70,y+23,32,()->pan(1,0));button("S",x+35,y+46,32,()->pan(0,1));
            button("+",x,y+74,48,()->{scale=Math.max(2,scale/2);page=0;});button("-",x+54,y+74,48,()->{scale=Math.min(1048576,scale*2);page=0;});
            button("Terminal",x,y+98,102,()->{cx=computer.getX();cz=computer.getZ();scale=8;page=0;});
            button("Fit pallets",x,y+122,102,()->{if(data!=null){cx=data.get("fleetX").getAsInt();cz=data.get("fleetZ").getAsInt();scale=data.get("fleetScale").getAsInt();page=0;}});
            button("Next page",x,y+146,102,()->{if(data!=null)page=(page+1)%data.get("pages").getAsInt();});
            button("Close",width-66,10,54,this::onClose);request();
        }
        @Override public void tick(){if(++ticks>=20){ticks=0;request();}}
        @Override public boolean isPauseScreen(){return false;}
        private void text(GuiGraphicsExtractor g,String s,int x,int y,int color){g.text(font,Component.literal(s),x,y,color,false);}
        private int mx(double x){return mapX+(int)((x-data.get("cx").getAsDouble())/data.get("scale").getAsDouble()*size/64+size/2.0);}
        private int mz(double z){return mapY+(int)((z-data.get("cz").getAsDouble())/data.get("scale").getAsDouble()*size/64+size/2.0);}
        @Override public void extractBackground(GuiGraphicsExtractor g,int mouseX,int mouseY,float partial){
            super.extractBackground(g,mouseX,mouseY,partial);g.fill(0,0,width,height,0xff101922);
            text(g,"PALLET TRANSIT / LIVE NETWORK",12,13,0xffe9f1ed);text(g,data==null?"Connecting…":data.get("dimension").getAsString()+"  •  "+data.get("count").getAsInt()+" pallets",12,28,0xff8da7aa);
            g.fill(mapX-1,mapY-1,mapX+size+1,mapY+size+1,0xff4c646b);g.fill(mapX,mapY,mapX+size,mapY+size,0xff192a35);
            if(data==null)return;
            JsonArray colors=data.getAsJsonArray("colors");
            for(int z=0;z<64;z++)for(int x=0;x<64;x++){int c=colors.get(z*64+x).getAsInt();g.fill(mapX+x*size/64,mapY+z*size/64,mapX+(x+1)*size/64,mapY+(z+1)*size/64,c==-1?(((x/4+z/4)%2==0)?0xff192a35:0xff1d303c):0xff000000|c);}
            for(var e:data.getAsJsonArray("stations")){var s=e.getAsJsonObject();int x=mx(s.get("x").getAsDouble()),y=mz(s.get("z").getAsDouble());int color=s.get("kind").getAsString().equals("bay")?0xff67c9ff:0xffee776b;g.fill(x-3,y-3,x+3,y+3,color);g.fill(x-1,y-1,x+1,y+1,0xff101922);}
            String detail="Hover a pallet or bay for carrier and route status.";String detail2="";
            JsonArray bayLinks=data.has("bayLinks")?data.getAsJsonArray("bayLinks"):new JsonArray();
            int listed=0;
            for(var entry:bayLinks){
                var bay=entry.getAsJsonObject();
                int bx=mx(bay.get("x").getAsDouble()),by=mz(bay.get("z").getAsDouble());
                boolean input="INPUT".equals(bay.get("role").getAsString());
                int color=input?0xff78dc99:0xffffa85a;
                if(bx>=mapX&&bx<=mapX+size&&by>=mapY&&by<=mapY+size){
                    g.fill(bx-4,by-4,bx+5,by+5,0xff101922);
                    g.fill(bx-3,by-3,bx+4,by+4,color);
                    if(Math.abs(mouseX-bx)<=6&&Math.abs(mouseY-by)<=6){
                        detail=(input?"INPUT":"OUTPUT")+" Drone Parking Bay @ "+bay.get("x").getAsInt()+","+
                            bay.get("y").getAsInt()+","+bay.get("z").getAsInt();
                        detail2=(bay.has("to")?"Linked to "+bay.get("to").getAsString()+"  ":"")+
                            (bay.has("warehouse")?"Warehouse "+bay.get("warehouse").getAsString():"Not connected to warehouse");
                    }
                }
                if(listed<Math.max(0,(height-270)/12)&&listed<18){
                    int sx=mapX+size+12;
                    text(g,(input?"IN  ":"OUT ")+bay.get("x").getAsInt()+","+bay.get("z").getAsInt(),sx,mapY+196+listed*12,color);
                    listed++;
                }
            }
            for(var e:data.getAsJsonArray("vehicles")){
                var v=e.getAsJsonObject();var pallets=v.getAsJsonArray("pallets");int baseX=mx(v.get("x").getAsDouble()),baseY=mz(v.get("z").getAsDouble());
                for(int i=0;i<pallets.size();i++){
                    int x=Math.clamp(baseX+i*7-(pallets.size()-1)*3,mapX+3,mapX+size-4),y=Math.clamp(baseY,mapY+3,mapY+size-4);boolean live=v.get("loaded").getAsBoolean();
                    g.fill(x-3,y-3,x+4,y+4,0xff132029);g.fill(x-2,y-2,x+3,y+2,live?0xffffce66:0xff8b969b);g.fill(x-2,y+3,x+3,y+4,0xffe5bd81);
                    if(Math.abs(mouseX-x)<=4&&Math.abs(mouseY-y)<=4){var p=pallets.get(i).getAsJsonObject();String type=switch(v.get("kind").getAsInt()){case 2->"Train";case 4->"Drone";default->"Conveyor";};detail=type+" / pallet "+p.get("slot").getAsInt()+" / "+p.get("stacks").getAsInt()+" stacks / "+v.get("status").getAsString();detail2="XYZ "+v.get("x").getAsInt()+", "+v.get("y").getAsInt()+", "+v.get("z").getAsInt()+(live?" • Live":" • Last known (unloaded)");}
                }
            }
            text(g,"Gold: pallet   Green: IN bay   Orange: OUT bay   Red: station",12,mapY+size+7,0xffb8c9ca);
            text(g,"N ↑   Center "+data.get("cx")+", "+data.get("cz")+"   "+data.get("scale")+" blocks/pixel   Page "+(data.get("page").getAsInt()+1)+"/"+data.get("pages"),12,mapY+size+19,0xff8da7aa);
            text(g,detail,12,height-25,0xffffce66);text(g,detail2.isEmpty()?"Dark squares: uncharted terrain. Map refreshes each second.":detail2,12,height-13,0xffb8c9ca);
        }
    }
    private TransitComputerClient(){}
}
