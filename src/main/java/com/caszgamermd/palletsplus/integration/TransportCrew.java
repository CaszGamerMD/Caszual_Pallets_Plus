package com.caszgamermd.palletsplus.integration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.caszgamermd.palletsplus.transport.TransportVehicle;
public interface TransportCrew {
    String palletsplus$requestTrain(TransportVehicle train);
    String palletsplus$requestConveyor(ServerLevel level,BlockPos root);
}
