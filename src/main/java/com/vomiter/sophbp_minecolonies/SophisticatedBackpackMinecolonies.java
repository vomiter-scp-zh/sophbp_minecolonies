package com.vomiter.sophbp_minecolonies;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(SophisticatedBackpackMinecolonies.MOD_ID)
public final class SophisticatedBackpackMinecolonies {
    public static final String MOD_ID = "sophbp_minecolonies";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SophisticatedBackpackMinecolonies() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(ModNetwork::onCommonSetup);
    }
    public static ResourceLocation modLoc(String path){return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);};

}
