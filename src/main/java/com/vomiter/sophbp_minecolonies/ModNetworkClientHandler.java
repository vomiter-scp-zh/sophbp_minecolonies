package com.vomiter.sophbp_minecolonies;

import net.minecraft.client.Minecraft;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;

public class ModNetworkClientHandler {
    static void handleMBPSyncResponse(ModNetwork.MBPSyncResponse response){
        assert Minecraft.getInstance().player != null;
        PlayerInventoryProvider.get().runOnBackpacks(
                Minecraft.getInstance().player,
                ((backpack, s, s1, i) -> {
                    BackpackWrapper.fromStack(backpack).onContentsNbtUpdated();
                    return true;
        }));
    }

}
