package com.vomiter.sophbp_minecolonies;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.InventoryUtils;
import com.vomiter.sophbp_minecolonies.util.SophisticatedUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;

import java.util.*;
import java.util.function.Supplier;

public class ModNetwork {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;
    public static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL = NetworkRegistry.newSimpleChannel(
                    SophisticatedBackpackMinecolonies.modLoc("main"),
                    () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals
            );

            int id = 0;
            CHANNEL.messageBuilder(MBPSyncRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(MBPSyncRequest::encode)
                    .decoder(MBPSyncRequest::decode)
                    .consumerMainThread(MBPSyncRequest::handle)
                    .add();
            CHANNEL.messageBuilder(MBPSyncResponse.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(MBPSyncResponse::encode)
                    .decoder(MBPSyncResponse::decode)
                    .consumerMainThread(MBPSyncResponse::handle)
                    .add();
            CHANNEL.messageBuilder(BPTransferRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(BPTransferRequest::encode)
                    .decoder(BPTransferRequest::decode)
                    .consumerMainThread(BPTransferRequest::handle)
                    .add();
        });

    }

    public static class BPTransferRequest{
        private ResourceKey<Level> dimensionId;
        private int colonyId;
        private final int citizenId;
        private final UUID[] uuid;
        private final int[] slot;
        private final int[] amount;


        public BPTransferRequest(ResourceKey<Level> dimensionId, int colonyId, int citizenId, UUID[] uuid, int[] slot, int[] amount){

            this.dimensionId = dimensionId;
            this.colonyId = colonyId;
            this.citizenId = citizenId;
            this.uuid = uuid;
            this.slot = slot;
            this.amount = amount;
        }

        public static void encode(BPTransferRequest pkt, FriendlyByteBuf buf){
            buf.writeUtf(pkt.dimensionId.location().toString());
            buf.writeInt(pkt.colonyId);
            buf.writeInt(pkt.citizenId);
            buf.writeVarInt(pkt.uuid.length);
            for (int i = 0; i < pkt.uuid.length; i++) {
                buf.writeUUID(pkt.uuid[i]);
            }
            buf.writeVarIntArray(pkt.slot);
            buf.writeVarIntArray(pkt.amount);
        }

        public static BPTransferRequest decode(FriendlyByteBuf buf){
            var dimensionId = ResourceKey.create(Registries.DIMENSION, Objects.requireNonNull(ResourceLocation.tryParse(buf.readUtf(32767))));
            var colonyId = buf.readInt();
            var citizenId = buf.readInt();
            var uuids = new ArrayList<>();
            var backpackCount = buf.readVarInt();
            for (int i = 0; i < backpackCount; i++) {
                uuids.add(buf.readUUID());
            }
            return new BPTransferRequest(dimensionId, colonyId, citizenId, uuids.toArray(UUID[]::new), buf.readVarIntArray(), buf.readVarIntArray());
        }

        public static void handle(BPTransferRequest pkt, Supplier<NetworkEvent.Context> ctx) {
            IColony colony = IColonyManager.getInstance().getColonyByDimension(pkt.colonyId, pkt.dimensionId);
            if (colony == null) return;
            ICitizenData citizenData = colony.getCitizenManager().getCivilian(pkt.citizenId);
            if (citizenData == null) return;
            Optional<AbstractEntityCitizen> optionalEntityCitizen = citizenData.getEntity();
            if (optionalEntityCitizen.isEmpty()) return;
            AbstractEntityCitizen citizen = optionalEntityCitizen.get();

            Player player = ctx.get().getSender();
            for (int i = 0; i < pkt.uuid.length; i++) {
                int finalI = i;
                SophisticatedUtil.runOnBackpack(player, backpackWrapper -> {
                    boolean match = backpackWrapper.getContentsUuid().map(uuid1 -> pkt.uuid[finalI].equals(uuid1)).orElse(false);
                    if (!match) return false;
                    var extracted = backpackWrapper.getInventoryHandler().extractItem(pkt.slot[finalI], pkt.amount[finalI], false);
                    ItemStack remainingItemStack = InventoryUtils.addItemStackToItemHandlerWithResult(citizen.getInventoryCitizen(), extracted);
                    backpackWrapper.getInventoryHandler().insertItem(pkt.slot[finalI], remainingItemStack, false);
                    return true;
                });
            }
        }
    }

    public record MBPSyncResponse(UUID uuid, CompoundTag tag){
        public static void encode(MBPSyncResponse pkt, FriendlyByteBuf buf){
            buf.writeUUID(pkt.uuid);
            buf.writeNbt(pkt.tag);
        }

        public static MBPSyncResponse decode(FriendlyByteBuf buf){
            return new MBPSyncResponse(buf.readUUID(), buf.readNbt());
        }

        public static void handle(MBPSyncResponse pkt, Supplier<NetworkEvent.Context> ctx) {
            BackpackStorage.get().setBackpackContents(pkt.uuid, pkt.tag);
            ModNetworkClientHandler.handleMBPSyncResponse(pkt);
            ctx.get().setPacketHandled(true);

        }
    }

    public record MBPSyncRequest(UUID uuid){
        public static void encode(MBPSyncRequest pkt, FriendlyByteBuf buf) {
            buf.writeUUID(pkt.uuid);
        }
        public static MBPSyncRequest decode(FriendlyByteBuf buf) {
            return new MBPSyncRequest(buf.readUUID());
        }
        public static void handle(MBPSyncRequest pkt, Supplier<NetworkEvent.Context> ctx) {
            var sourceContents = BackpackStorage.get()
                    .getOrCreateBackpackContents(
                            pkt.uuid
                    );

            CompoundTag responseContents =
                    copyInventoryContents(sourceContents);

            PlayerInventoryProvider.get().runOnBackpacks(Objects.requireNonNull(ctx.get().getSender()), ((backpack, s, s1, i) -> {
                backpack.getCapability(CapabilityBackpackWrapper.getCapabilityInstance()).ifPresent(iBackpackWrapper -> {
                    iBackpackWrapper.getContentsUuid().ifPresent(uuid1 -> {
                        if (uuid1.equals(pkt.uuid)){
                            CHANNEL.send(
                                    PacketDistributor.PLAYER.with(() -> ctx.get().getSender()),
                                    new MBPSyncResponse(uuid1, responseContents)
                            );
                        }
                    });
                });
                return false;
            }));

            ctx.get().setPacketHandled(true);
        }

        private static CompoundTag copyInventoryContents(
                CompoundTag source
        ) {
            CompoundTag result = new CompoundTag();
            result.put(
                    "inventory",
                    source.contains("inventory", Tag.TAG_COMPOUND)
                            ? source.getCompound("inventory").copy()
                            : new CompoundTag()
            );

            result.put(
                    "upgradeInventory",
                    source.contains("upgradeInventory", Tag.TAG_COMPOUND)
                            ? source.getCompound("upgradeInventory").copy()
                            : new CompoundTag()
            );

            return result;
        }
    }
}
