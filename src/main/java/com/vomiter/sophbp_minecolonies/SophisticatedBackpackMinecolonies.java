package com.vomiter.sophbp_minecolonies;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(SophisticatedBackpackMinecolonies.MOD_ID)
public final class SophisticatedBackpackMinecolonies {
    public static final String MOD_ID = "sophbp_minecolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SophisticatedBackpackMinecolonies(IEventBus modEventBus) {
        modEventBus.addListener(ModNetwork::register);
    }
    public static ResourceLocation modLoc(String path){return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);};

}
