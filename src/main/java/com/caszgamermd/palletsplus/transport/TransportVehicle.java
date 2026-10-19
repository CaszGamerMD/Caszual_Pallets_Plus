package com.caszgamermd.palletsplus.transport;

import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Server-authoritative carrier. Closed/unloaded routes stop with cargo retained. */
public final class TransportVehicle extends PathfinderMob {
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(TransportVehicle.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> DECK0=SynchedEntityData.defineId(TransportVehicle.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DECK1=SynchedEntityData.defineId(TransportVehicle.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DECK2=SynchedEntityData.defineId(TransportVehicle.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> LOAD=SynchedEntityData.defineId(TransportVehicle.class,EntityDataSerializers.INT);
    public final PalletCargo[] cargo={new PalletCargo(),new PalletCargo(),new PalletCargo()};
    private BlockPos node=BlockPos.ZERO,target=BlockPos.ZERO,home=BlockPos.ZERO,destination=BlockPos.ZERO;
    private Direction heading=Direction.NORTH;
    private boolean running,traveling,linkedBay,returning,dropsReleased,awaitingBayUnload;
    private int flightStage,latchedRoute=-1;
    private static final BlockPos NO_STATION=new BlockPos(0,Integer.MIN_VALUE,0);
    private BlockPos stationPassed=NO_STATION;
    private int lastMoveTick=-100;
    private double progress=.5,trainSpeed,indexRemaining=-1;
    private BlockPos indexRail=NO_STATION;
    private int indexedCar;
    private String ahead="",behind="",status="Parked";
    private final Deque<Vec3> trail=new ArrayDeque<>();
    public TransportVehicle(EntityType<? extends TransportVehicle> type,Level level){super(type,level);setPersistenceRequired();setNoGravity(true);}
    public static AttributeSupplier.Builder attributes(){return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH,80).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(KIND,0);b.define(DECK0,ItemStack.EMPTY);b.define(DECK1,ItemStack.EMPTY);b.define(DECK2,ItemStack.EMPTY);b.define(LOAD,0);}
    public int kind(){return entityData.get(KIND);}
    @Override protected AABB makeBoundingBox(Vec3 pos){
        // Entity's default square only covered the center of a long flatbed.
        // During the superclass constructor synchronized data is not ready yet.
        if(entityData==null)return super.makeBoundingBox(pos);
        double yaw=Math.toRadians(getYRot()),w=kind()==4?1.66:1.2,h=length()*.5;
        double x=Math.abs(Math.cos(yaw))*w+Math.abs(Math.sin(yaw))*h;
        double z=Math.abs(Math.sin(yaw))*w+Math.abs(Math.cos(yaw))*h;
        return new AABB(pos.x-x,pos.y,pos.z-z,pos.x+x,pos.y+2.8,pos.z+z);
    }
    public int capacity(){return kind()==2?3:kind()==0||kind()==4?1:0;}
    public ItemStack deck(int i){return entityData.get(i==0?DECK0:i==1?DECK1:DECK2);}
    public int visible(int i){return (entityData.get(LOAD)>>(i*5))&31;}
    public void sync(){entityData.set(DECK0,cargo[0].deck.copy());entityData.set(DECK1,cargo[1].deck.copy());entityData.set(DECK2,cargo[2].deck.copy());int n=0;for(int i=0;i<3;i++)n|=cargo[i].visible()<<(i*5);entityData.set(LOAD,n);if(level() instanceof ServerLevel l)TransitWorldData.get(l).record(this);}
    public void configure(int kind,BlockPos p,Direction d){
        entityData.set(KIND,kind);node=p;target=p;home=p;heading=d;
        setYRot(d.toYRot());setYBodyRot(getYRot());running=kind==0;
        double x=0,y=.26,z=0;
        var state=level().getBlockState(p);
        if(state.getBlock() instanceof GuidewayBlock way&&!way.bay()){
            int turn=way.kind.endsWith("_left")?-1:way.kind.endsWith("_right")?1:0;
            if(way.kind.equals("conveyor_logic"))progress=0;
            int rotation=switch(d){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
            var point=TransportPath.sample(turn,way.rise(),rotation,progress);
            x=point.x();y=point.y();z=point.z();
        }
        setPos(p.getX()+.5+x,p.getY()+y,p.getZ()+.5+z);
    }
    public double length(){return switch(kind()){case 1->3.6;case 2->6.8;case 3->4.4;case 4->3.3;default->1.85;};}
    private double spacing(TransportVehicle leader){return TransportMotion.spacing(leader.length(),length());}
    private boolean stationary(){
        if(running||tickCount-lastMoveTick<10)return false;
        TransportVehicle head=this;
        for(int i=0;i<16&&!head.ahead.isEmpty();i++){
            head=find(head.ahead);if(head==null||head.running||head.tickCount-head.lastMoveTick<10)return false;
        }
        return true;
    }
    public boolean load(ServerLevel l,BlockPos p){if(kind()!=0&&!stationary())return false;for(int i=0;i<capacity();i++)if(cargo[i].empty()&&cargo[i].lift(l,p)){sync();return true;}return false;}
    public boolean unload(ServerLevel l,BlockPos p,Direction d){if(!stationary())return false;for(int i=0;i<capacity();i++)if(!cargo[i].empty()){boolean ok=cargo[i].place(l,p,d);sync();return ok;}return false;}
    public boolean transferTo(TransportVehicle other){
        if(other==this||!stationary()||!other.stationary()||distanceToSqr(other)>144)return false;
        for(int source=0;source<capacity();source++)if(!cargo[source].empty()){
            for(int dest=0;dest<other.capacity();dest++)if(other.cargo[dest].empty()){
                other.cargo[dest]=cargo[source];cargo[source]=new PalletCargo();sync();other.sync();return true;
            }
        }
        return false;
    }
    public int freePalletSlot(){if(kind()!=0&&(kind()!=2||!stationary()))return -1;for(int i=0;i<capacity();i++)if(cargo[i].empty())return i;return -1;}
    public Vec3 palletSlotPosition(int slot){if(kind()==0)return position();double yaw=Math.toRadians(getYRot()),offset=(slot-1)*2.2;return position().add(Math.sin(yaw)*offset,.68,-Math.cos(yaw)*offset);}
    /** Called on the server only; caller relinquishes its shipment immediately on success. */
    public boolean acceptCrewPallet(ItemStack deck,List<ItemStack> contents){
        int slot=freePalletSlot();if(slot<0||deck.isEmpty()||contents.size()!=216)return false;
        PalletCargo load=new PalletCargo();load.deck=deck.copy();for(int i=0;i<216;i++)load.items.set(i,contents.get(i).copy());cargo[slot]=load;sync();return true;
    }
    public static String couplePair(TransportVehicle a,TransportVehicle b){
        if(a==b)return "Select a different train car.";
        if(a.kind()<1||a.kind()>3||b.kind()<1||b.kind()>3)return "Only train cars can couple.";
        // Infer front/rear from track direction, so selection order is irrelevant.
        Direction d=a.getDirection();
        double forward=(b.getX()-a.getX())*d.getStepX()+(b.getZ()-a.getZ())*d.getStepZ();
        return forward>0?a.couple(b):b.couple(a);
    }
    public boolean hasCargo(){return Arrays.stream(cargo).anyMatch(c->!c.empty());}
    public boolean linkBay(BlockPos p){if(kind()!=4||!stationary()||p.equals(home))return false;destination=p;linkedBay=true;flightStage=0;returning=false;return true;}
    public boolean flightActive(){return kind()==4&&linkedBay&&(running||traveling||awaitingBayUnload);}
    public BlockPos flightDestination(){return awaitingBayUnload?node:returning?home:destination;}
    public String transitStatus(){return status;}
    public void releaseStation(){if(kind()==1&&!running&&status.startsWith("Station")){running=true;status="Departing station";}}
    @Override public void remove(Entity.RemovalReason reason){if(reason.shouldDestroy()&&level() instanceof ServerLevel l)TransitWorldData.get(l).forget(getStringUUID());super.remove(reason);}
    public String couple(TransportVehicle leader){
        if(kind()!=2&&kind()!=3)return "Select a pallet or passenger car as the rear car.";
        if(leader==this||leader.kind()<1||leader.kind()>3||!ahead.isEmpty()||!leader.behind.isEmpty()||distanceToSqr(leader)>100)return "Coupling unavailable: stop and bring free couplers within 10 blocks.";
        Set<String> members=new HashSet<>();
        TransportVehicle scan=leader;
        while(scan!=null){
            if(!members.add(scan.getStringUUID()))return "Coupling loop detected.";
            if(scan.ahead.isEmpty())break;
            scan=find(scan.ahead);if(scan==null)return "Lead car is unloaded.";
        }
        scan=this;
        while(scan!=null){
            if(!members.add(scan.getStringUUID()))return "That would form a coupling loop.";
            if(scan.behind.isEmpty())break;
            scan=find(scan.behind);if(scan==null)return "Rear car is unloaded.";
        }
        if(members.size()>TransportMotion.MAX_CARS+1)return "Maximum train: one engine and ten cars.";
        if(!leader.stationary()||!stationary())return "Stop the entire train before coupling.";
        if(getDirection()!=leader.getDirection())return "Place both cars facing the same track direction.";
        // Coupling is established on a straight, fully loaded stretch of warehouse rail.
        Vec3 offset=position().subtract(leader.position());
        if(Math.abs(offset.y)>.1||(Math.abs(offset.x)>.1&&Math.abs(offset.z)>.1))return "Align both cars on a straight rail before coupling.";
        if(offset.length()<spacing(leader)-.3)return "Leave room between the cars for their full chassis.";
        if(!(level() instanceof ServerLevel server))return "Server unavailable.";
        int samples=(int)Math.ceil(offset.length()*10);
        leader.trail.clear();
        for(int i=0;i<=samples;i++){
            Vec3 point=position().lerp(leader.position(),i/(double)samples);
            BlockPos floor=BlockPos.containing(point.x,point.y-.20,point.z);
            if(!server.hasChunkAt(floor)||!(server.getBlockState(floor).getBlock() instanceof GuidewayBlock way)||!way.rail()){
                leader.trail.clear();return "Couplers need continuous warehouse rail between the cars.";
            }
            leader.trail.addLast(point);
        }
        ahead=leader.getStringUUID();leader.behind=getStringUUID();return "Cars coupled. Board the engine and use the Transport Controller to start.";
    }
    private TransportVehicle find(String id){if(!(level() instanceof ServerLevel l)||id.isEmpty())return null;try{return l.getEntity(UUID.fromString(id)) instanceof TransportVehicle v?v:null;}catch(IllegalArgumentException e){return null;}}
    /** Engine ticks the complete consist in small steps; entity tick order cannot stretch couplers. */
    private List<TransportVehicle> consist(){
        List<TransportVehicle> cars=new ArrayList<>();Set<String> ids=new HashSet<>();
        TransportVehicle v=this;
        while(v!=null){
            if(!ids.add(v.getStringUUID())||cars.size()>TransportMotion.MAX_CARS)return null;
            cars.add(v);if(v.behind.isEmpty())return cars;
            TransportVehicle next=find(v.behind);
            if(next==null||!next.ahead.equals(v.getStringUUID()))return null;
            v=next;
        }return null;
    }
    public String toggleEngine(){
        if(kind()!=1||!ahead.isEmpty())return "Use the lead engine.";
        running=!running;traveling=false;trainSpeed=0;
        if(running){indexRemaining=-1;indexRail=NO_STATION;indexedCar=0;}
        status=running?"Engine started — accelerating to 30 m/s":"Engine stopped";return status;
    }
    public boolean indexedAt(BlockPos rail){return kind()==1&&indexRail.equals(rail);}
    public String advanceOneCar(){
        if(!indexedAt(indexRail)||indexRail.equals(NO_STATION))return "No train at this indexing rail.";
        if(running)return "Train is already advancing.";
        var cars=consist();if(cars==null)return "Wait for all coupled cars to load.";
        if(indexRemaining<=0){
            if(cars.size()==1){indexRemaining=length();}
            else{
                if(indexedCar>=cars.size()-1)return "All cars indexed. Start the engine to depart.";
                indexRemaining=cars.get(indexedCar+1).spacing(cars.get(indexedCar));indexedCar++;
            }
        }
        running=true;trainSpeed=0;status="Indexing next car at 2 m/s";return status;
    }
    @Override public void tick(){
        super.tick();setBoundingBox(makeBoundingBox(position()));setDeltaMovement(Vec3.ZERO);
        if(!(level() instanceof ServerLevel l)||!isAlive())return;
        if(tickCount%10==0)TransitWorldData.get(l).record(this);
        if(!ahead.isEmpty()){running=false;return;}
        if(kind()==4&&awaitingBayUnload&&!running&&tickCount%20==0){
            if(!hasCargo()||serviceBay(l,false)){awaitingBayUnload=false;status="Unloaded at bay — click to collect next pallet and depart";}else status="Waiting for clear pallet space beside bay";
        }
        if(!running){trainSpeed=0;if(kind()==1){var parked=consist();if(parked!=null)for(int i=1;i<parked.size();i++)if(!parked.get(i).follow(l,.25))break;}return;}
        if(kind()==4){fly(l);return;}
        if(kind()==2||kind()==3){status="Needs an engine";return;}
        var cars=kind()==1?consist():List.of(this);
        if(cars==null){trainSpeed=0;status="Waiting for all coupled cars";return;}
        for(int i=1;i<cars.size();i++)if(cars.get(i-1).distanceToSqr(cars.get(i))>Math.pow(cars.get(i).spacing(cars.get(i-1))+2,2)){
            trainSpeed=0;status="Waiting for coupled car";return;
        }
        // Settle placement slack while the engine waits, before accelerating the consist.
        for(int i=1;i<cars.size();i++)if(!cars.get(i).follow(l,.25)){trainSpeed=0;status="Aligning coupled cars";return;}
        double budget=kind()==0?TransportMotion.CONVEYOR_SPEED:indexRemaining>0?TransportMotion.INDEX_SPEED:(trainSpeed=TransportMotion.accelerate(trainSpeed));
        while(budget>1e-8&&running){
            double moved=advanceGuideway(l,Math.min(budget,TransportMotion.STEP));
            if(moved<=1e-8){trainSpeed=0;break;}
            budget-=moved;
            if(indexRemaining>0){
                indexRemaining=Math.max(0,indexRemaining-moved);
                if(indexRemaining<1e-7){running=false;traveling=false;trainSpeed=0;status="Index stop — click indexing rail for next car";}
            }
            boolean followersReady=true;
            for(int i=1;i<cars.size();i++)if(!cars.get(i).follow(l,TransportMotion.STEP+.002)){followersReady=false;break;}
            if(!followersReady){trainSpeed=0;status="Waiting for coupled car route";break;}

        }
    }
    private double advanceGuideway(ServerLevel l,double budget){
        if(!GuidewayBlock.complete(l,node)){traveling=false;status="Guideway missing";return 0;}
        var state=l.getBlockState(node);var way=(GuidewayBlock)state.getBlock();
        boolean power=GuidewayBlock.powered(l,node);
        boolean indexStop=kind()==1&&way.kind.equals("pallet_rail_index")&&indexRemaining<0&&!node.equals(stationPassed);
        boolean stationStop=kind()==1&&!node.equals(stationPassed)&&StationPostBlock.at(l,node,state.getValue(GuidewayBlock.FACING));
        boolean hold=way.kind.equals("conveyor_hold")&&power;
        if(progress>=.5-1e-8&&(indexStop||stationStop||hold)){
            traveling=false;
            if(indexStop){indexRail=node;indexedCar=0;indexRemaining=0;stationPassed=node;running=false;status="Index stop — click rail or pulse redstone for next car";}
            else if(stationStop){stationPassed=node;running=false;status="Station stop — use controller or post to depart";}
            else status="Hold bar / redstone stop";
            return 0;
        }
        if(way.rail()&&!way.kind.equals("pallet_rail_index")&&power){traveling=false;status="Redstone stop";return 0;}
        int turn=way.kind.endsWith("_left")?-1:way.kind.endsWith("_right")?1:0;
        if(way.kind.equals("conveyor_logic")){
            if(latchedRoute<0&&l.getBlockEntity(node) instanceof LogicConveyorEntity logic){int selected=logic.route(power);if(selected==3){traveling=false;status="Logic conveyor holding";return 0;}latchedRoute=selected;}
            turn=latchedRoute==1?-1:latchedRoute==2?1:0;
        }
        Direction entry=state.getValue(GuidewayBlock.FACING);
        Direction out=turn<0?entry.getCounterClockWise():turn>0?entry.getClockWise():entry;
        BlockPos next=node.relative(out,3).above(way.rise());
        double length=TransportPath.length(turn,way.rise()),limit=(indexStop||stationStop||hold)&&progress<.5?.5:1;
        double distance=TransportMotion.distance(budget,(limit-progress)*length);
        if(indexRemaining>0)distance=Math.min(distance,indexRemaining);
        double nextProgress=Math.min(limit,progress+distance/length);
        if(nextProgress>=1-1e-8){
            // Reach the end of this module and retain cargo there until an exit is available.
            nextProgress=1;
        }
        int rotation=switch(entry){case EAST->1;case SOUTH->2;case WEST->3;default->0;};
        var point=TransportPath.sample(turn,way.rise(),rotation,nextProgress);
        Vec3 goal=new Vec3(node.getX()+.5+point.x(),node.getY()+point.y(),node.getZ()+.5+point.z());
        traveling=true;
        if(!step(l,goal,distance+.00001))return 0;
        progress=nextProgress;
        if(progress>=1-1e-8){
            if(!GuidewayBlock.complete(l,next)){traveling=false;status="End of route";return distance;}
            var ns=l.getBlockState(next);var nw=(GuidewayBlock)ns.getBlock();
            if(nw.rail()!=(kind()!=0)||nw.bay()||ns.getValue(GuidewayBlock.FACING)!=out){traveling=false;status="Incompatible guideway direction";return distance;}
            node=next;target=next;progress=0;latchedRoute=-1;stationPassed=NO_STATION;
        }
        return distance;
    }
    private void record(){trail.addLast(position());while(trail.size()>400)trail.removeFirst();}
    private boolean follow(ServerLevel l,double budget){
        TransportVehicle leader=find(ahead);
        if(leader==null||!leader.behind.equals(getStringUUID())){status="Waiting for lead car";return false;}
        double spacing=spacing(leader),distance=0;Vec3 previous=leader.position(),goal=null;
        var it=leader.trail.descendingIterator();
        while(it.hasNext()){
            Vec3 point=it.next();double segment=previous.distanceTo(point);
            if(segment>1e-9&&distance+segment>=spacing){goal=previous.lerp(point,(spacing-distance)/segment);break;}
            distance+=segment;previous=point;
        }
        if(goal==null){status="Taking up coupling slack";return false;}
        BlockPos floor=BlockPos.containing(goal.x,goal.y-.20,goal.z);
        if(!l.hasChunkAt(floor)||!(l.getBlockState(floor).getBlock() instanceof GuidewayBlock way)||!way.rail()){status="Trailing rail missing";return false;}
        running=false;return step(l,goal,budget);
    }
    private boolean sameTrain(TransportVehicle other){
        for(int side=0;side<2;side++){
            TransportVehicle v=this;
            for(int i=0;i<=TransportMotion.MAX_CARS&&v!=null;i++){
                if(v==other)return true;v=find(side==0?v.ahead:v.behind);
            }
        }return false;
    }
    private boolean step(ServerLevel l,Vec3 goal,double speed){
        double remaining=Math.min(speed,position().distanceTo(goal));
        while(remaining>1e-8){
            double amount=Math.min(remaining,TransportMotion.STEP);
            Vec3 delta=goal.subtract(position());
            Vec3 next=position().add(delta.scale(Math.min(amount,delta.length())/delta.length()));
            if(!moveSegment(l,next,amount+.000001))return false;
            remaining-=amount;
        }
        return position().distanceTo(goal)<1e-6;
    }
    private boolean moveSegment(ServerLevel l,Vec3 goal,double speed){
        Vec3 delta=goal.subtract(position());double distance=delta.length();
        if(distance<1e-8)return true;
        Vec3 change=delta.scale(Math.min(speed,distance)/distance);
        double yaw=change.horizontalDistanceSqr()>1e-10?Math.atan2(-change.x,change.z):Math.toRadians(getYRot());
        double halfWidth=kind()==4?1.66:1.20,halfLength=length()*.5;
        double ex=Math.abs(Math.cos(yaw))*halfWidth+Math.abs(Math.sin(yaw))*halfLength;
        double ez=Math.abs(Math.sin(yaw))*halfWidth+Math.abs(Math.cos(yaw))*halfLength;
        AABB next=new AABB(getX()-ex,getY(),getZ()-ez,getX()+ex,getY()+2.8,getZ()+ez).move(change).minmax(getBoundingBox());
        BlockPos min=BlockPos.containing(next.minX,next.minY,next.minZ),max=BlockPos.containing(next.maxX,next.maxY,next.maxZ);
        if(l.isOutsideBuildHeight(min)||l.isOutsideBuildHeight(max)||!l.hasChunksAt(min,max)||!l.getWorldBorder().isWithinBounds(min)||!l.getWorldBorder().isWithinBounds(max)){status="Waiting for loaded route";return false;}
        // Guideways form the supporting floor; all other collision must remain clear.
        for(BlockPos p:BlockPos.betweenClosed(min,max)){
            var s=l.getBlockState(p);
            if(s.getBlock() instanceof GuidewayBlock&&p.getY()<=getY()+.5)continue;
            var shape=s.getCollisionShape(l,p);
            if(!shape.isEmpty()&&shape.toAabbs().stream().anyMatch(b->b.move(p).intersects(next))){status="Obstructed";return false;}
        }
        for(TransportVehicle other:l.getEntitiesOfClass(TransportVehicle.class,next)){
            if(other==this||(kind()>=1&&kind()<=3&&sameTrain(other)))continue;
            status="Waiting for transport ahead";return false;
        }
        setPos(getX()+change.x,getY()+change.y,getZ()+change.z);
        if(change.horizontalDistanceSqr()>.00001){setYRot((float)Math.toDegrees(Math.atan2(-change.x,change.z)));setYBodyRot(getYRot());}
        lastMoveTick=tickCount;record();status="Moving";return distance<=speed;
    }
    /** Dock transfer uses real pallet blocks beside the bay, never loose item drops. */
    private boolean serviceBay(ServerLevel l,boolean pickup){
        if(!GuidewayBlock.complete(l,node)||!l.getBlockState(node).is(TransportContent.BAY))return false;
        Direction front=l.getBlockState(node).getValue(GuidewayBlock.FACING);
        for(Direction d:new Direction[]{front,front.getClockWise(),front.getCounterClockWise(),front.getOpposite()})for(int dy:new int[]{0,1,-1}){
            BlockPos p=node.relative(d,3).above(dy);
            if(!l.hasChunksAt(p.offset(-2,-1,-2),p.offset(2,3,2)))continue;
            if(pickup){if(cargo[0].empty()&&cargo[0].lift(l,p)){sync();return true;}}
            else if(!cargo[0].empty()){
                // Do not place a solid pallet through a player, Golem or vehicle.
                var area=new AABB(p).expandTowards(d.getStepX()+d.getClockWise().getStepX(),2,d.getStepZ()+d.getClockWise().getStepZ());
                if(!l.getEntities(this,area,e->e.isAlive()&&!e.isSpectator()).isEmpty())continue;
                if(cargo[0].place(l,p,d)){sync();return true;}
            }
        }return false;
    }
    private void fly(ServerLevel l){
        if(!linkedBay){status="Link a destination parking bay";return;}
        BlockPos end=returning?home:destination;
        if(!l.hasChunkAt(end)||!l.getBlockState(end).is(TransportContent.BAY)||!GuidewayBlock.complete(l,end)){status="Waiting for destination bay";return;}
        double altitude=Math.max(home.getY(),destination.getY())+7;
        Vec3 goal=switch(flightStage){
            case 0->new Vec3(getX(),altitude,getZ());
            case 1->new Vec3(end.getX()+.5,altitude,end.getZ()+.5);
            default->new Vec3(end.getX()+.5,end.getY()+.26,end.getZ()+.5);
        };
        traveling=true;
        if(step(l,goal,TransportMotion.DRONE_SPEED)){
            if(flightStage<2)flightStage++;
            else{node=end;flightStage=0;returning=!returning;traveling=false;running=false;awaitingBayUnload=hasCargo();status="Docked — unloading pallet beside bay";}
        }
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand){
        if(level().isClientSide())return InteractionResult.SUCCESS;
        if(player.getItemInHand(hand).is(com.caszgamermd.caszualadditions.racks.RackContent.STORAGE_SCANNER_ITEM)&&player instanceof net.minecraft.server.level.ServerPlayer sp){
            String problem=com.caszgamermd.palletsplus.integration.CrewTransport.scannerProblem(player.getItemInHand(hand),(ServerLevel)level());
            if(!problem.isEmpty()){sp.sendSystemMessage(Component.literal(problem));return InteractionResult.SUCCESS;}
            sp.sendSystemMessage(Component.literal(com.caszgamermd.palletsplus.integration.CrewTransport.deliverTrain((ServerLevel)level(),sp,this)));return InteractionResult.SUCCESS;
        }
        if(player.getItemInHand(hand).is(TransportContent.CONTROLLER))return TransportController.useVehicle(player,hand,this);
        if(!player.getItemInHand(hand).isEmpty())return InteractionResult.PASS;
        if(player.isShiftKeyDown()){
            if(!ahead.isEmpty()&&!stationary()){player.sendSystemMessage(Component.literal("Stop the engine before uncoupling."));return InteractionResult.SUCCESS;}
            if(running||traveling){running=false;traveling=false;status="Stopped";}
            else if(!ahead.isEmpty()||!behind.isEmpty()){
                TransportVehicle a=find(ahead),b=find(behind);if(a!=null)a.behind="";if(b!=null)b.ahead="";ahead="";behind="";status="Uncoupled";
            }else if(Arrays.stream(cargo).allMatch(PalletCargo::empty)&&getPassengers().isEmpty()){
                if(kind()>0)player.getInventory().placeItemBackInInventory(new ItemStack(TransportContent.ITEMS.get(TransportContent.VEHICLES[kind()])));discard();return InteractionResult.SUCCESS;
            }
        }else if(kind()==1||kind()==3){
            if(!canAddPassenger(player)){player.sendSystemMessage(Component.literal("All seats are occupied."));return InteractionResult.SUCCESS;}
            player.startRiding(this,false,false);
            if(kind()==1)player.sendSystemMessage(Component.literal("Use the Transport Controller to start/stop the engine. Sneak to dismount."));
            return InteractionResult.SUCCESS;
        }
        else{
            if(kind()==4&&!running){
                if(awaitingBayUnload&&hasCargo()){player.sendSystemMessage(Component.literal("Clear a supported 2x2 pallet area beside the bay; the drone is waiting to unload."));return InteractionResult.SUCCESS;}
                if(!hasCargo())serviceBay((ServerLevel)level(),true);
            }
            running=!running;if(!running)traveling=false;status=running?"Started":"Paused";
        }
        player.sendSystemMessage(Component.literal(status));return InteractionResult.SUCCESS;
    }
    @Override protected boolean canAddPassenger(Entity passenger){return passenger instanceof Player&&getPassengers().size()<TransportMotion.seats(kind());}
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger,EntityDimensions dimensions,float scale){
        int index=getPassengers().indexOf(passenger);
        if(index<0||index>=TransportMotion.seats(kind()))return new Vec3(0,1.1,0);
        var seat=TransportMotion.seat(kind(),index);
        return new Vec3(seat.x(),seat.y(),seat.z()).yRot((float)Math.toRadians(180-getYRot()));
    }
    @Override public void die(net.minecraft.world.damagesource.DamageSource cause){
        if(level() instanceof ServerLevel l&&!dropsReleased){dropsReleased=true;for(var c:cargo)c.drop(l,getX(),getY(),getZ());if(kind()>0)Containers.dropItemStack(l,getX(),getY(),getZ(),new ItemStack(TransportContent.ITEMS.get(TransportContent.VEHICLES[kind()])));}
        super.die(cause);
    }
    @Override protected void addAdditionalSaveData(ValueOutput out){
        super.addAdditionalSaveData(out);out.putDouble("train_speed",trainSpeed);out.putDouble("index_remaining",indexRemaining);out.store("index_rail",BlockPos.CODEC,indexRail);out.putInt("indexed_car",indexedCar);out.putString("transit_status",status);out.putInt("logic_route",latchedRoute);out.store("station_passed",BlockPos.CODEC,stationPassed);out.putInt("kind",kind());out.putDouble("progress",progress);out.store("node",BlockPos.CODEC,node);out.store("target",BlockPos.CODEC,target);out.store("home",BlockPos.CODEC,home);out.store("destination",BlockPos.CODEC,destination);
        out.putBoolean("awaiting_bay_unload",awaitingBayUnload);out.putBoolean("running",running);out.putBoolean("traveling",traveling);out.putBoolean("linked_bay",linkedBay);out.putBoolean("returning",returning);out.putInt("flight_stage",flightStage);out.putString("ahead",ahead);out.putString("behind",behind);out.putBoolean("drops_released",dropsReleased);
        out.store("trail",Vec3.CODEC.listOf(),List.copyOf(trail));for(int i=0;i<3;i++)cargo[i].save(out,i);
    }
    @Override protected void readAdditionalSaveData(ValueInput in){
        super.readAdditionalSaveData(in);trainSpeed=0;indexRemaining=Math.clamp(in.getDoubleOr("index_remaining",-1),-1,16);indexRail=in.read("index_rail",BlockPos.CODEC).orElse(NO_STATION);indexedCar=Math.clamp(in.getIntOr("indexed_car",0),0,TransportMotion.MAX_CARS);status=in.getStringOr("transit_status","Parked");latchedRoute=Math.clamp(in.getIntOr("logic_route",-1),-1,2);stationPassed=in.read("station_passed",BlockPos.CODEC).orElse(NO_STATION);entityData.set(KIND,Math.clamp(in.getIntOr("kind",0),0,4));progress=Math.clamp(in.getDoubleOr("progress",.5),0,1);node=in.read("node",BlockPos.CODEC).orElse(blockPosition());target=in.read("target",BlockPos.CODEC).orElse(node);home=in.read("home",BlockPos.CODEC).orElse(node);destination=in.read("destination",BlockPos.CODEC).orElse(home);
        awaitingBayUnload=in.getBooleanOr("awaiting_bay_unload",false);running=in.getBooleanOr("running",false);traveling=in.getBooleanOr("traveling",false);linkedBay=in.getBooleanOr("linked_bay",false);returning=in.getBooleanOr("returning",false);flightStage=Math.clamp(in.getIntOr("flight_stage",0),0,2);ahead=in.getStringOr("ahead","");behind=in.getStringOr("behind","");dropsReleased=in.getBooleanOr("drops_released",false);
        trail.clear();var saved=in.read("trail",Vec3.CODEC.listOf()).orElse(List.of());trail.addAll(saved.subList(Math.max(0,saved.size()-400),saved.size()));for(int i=0;i<3;i++)cargo[i].load(in,i);sync();setNoGravity(true);
    }
}
