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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackStorage;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;

import java.util.*;

public class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(MBPSyncRequest.TYPE, MBPSyncRequest.CODEC, MBPSyncRequest::handle);
        registrar.playToClient(MBPSyncResponse.TYPE, MBPSyncResponse.CODEC, MBPSyncResponse::handle);
        registrar.playToServer(BPTransferRequest.TYPE, BPTransferRequest.CODEC, BPTransferRequest::handle);
    }

    public static class BPTransferRequest implements CustomPacketPayload {
        private static final Type<BPTransferRequest> TYPE = new Type<>(SophisticatedBackpackMinecolonies.modLoc("transfer_request"));
        private static final StreamCodec<FriendlyByteBuf, BPTransferRequest> CODEC = StreamCodec.ofMember(BPTransferRequest::encode, BPTransferRequest::decode);

        @Override
        public Type<BPTransferRequest> type() {
            return TYPE;
        }

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

        public static void handle(BPTransferRequest pkt, IPayloadContext ctx) {
            IColony colony = IColonyManager.getInstance().getColonyByDimension(pkt.colonyId, pkt.dimensionId);
            if (colony == null) return;
            ICitizenData citizenData = colony.getCitizenManager().getCivilian(pkt.citizenId);
            if (citizenData == null) return;
            Optional<AbstractEntityCitizen> optionalEntityCitizen = citizenData.getEntity();
            if (optionalEntityCitizen.isEmpty()) return;
            AbstractEntityCitizen citizen = optionalEntityCitizen.get();

            Player player = ctx.player();
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

    public record MBPSyncResponse(UUID uuid, CompoundTag tag) implements CustomPacketPayload {
        private static final Type<MBPSyncResponse> TYPE = new Type<>(SophisticatedBackpackMinecolonies.modLoc("sync_response"));
        private static final StreamCodec<FriendlyByteBuf, MBPSyncResponse> CODEC = StreamCodec.ofMember(MBPSyncResponse::encode, MBPSyncResponse::decode);

        @Override
        public Type<MBPSyncResponse> type() {
            return TYPE;
        }

        public static void encode(MBPSyncResponse pkt, FriendlyByteBuf buf){
            buf.writeUUID(pkt.uuid);
            buf.writeNbt(pkt.tag);
        }

        public static MBPSyncResponse decode(FriendlyByteBuf buf){
            return new MBPSyncResponse(buf.readUUID(), buf.readNbt());
        }

        public static void handle(MBPSyncResponse pkt, IPayloadContext ctx) {
            BackpackStorage.get().setBackpackContents(pkt.uuid, pkt.tag);
            ModNetworkClientHandler.handleMBPSyncResponse(pkt);

        }
    }

    public record MBPSyncRequest(UUID uuid) implements CustomPacketPayload {
        private static final Type<MBPSyncRequest> TYPE = new Type<>(SophisticatedBackpackMinecolonies.modLoc("sync_request"));
        private static final StreamCodec<FriendlyByteBuf, MBPSyncRequest> CODEC = StreamCodec.ofMember(MBPSyncRequest::encode, MBPSyncRequest::decode);

        @Override
        public Type<MBPSyncRequest> type() {
            return TYPE;
        }

        public static void encode(MBPSyncRequest pkt, FriendlyByteBuf buf) {
            buf.writeUUID(pkt.uuid);
        }
        public static MBPSyncRequest decode(FriendlyByteBuf buf) {
            return new MBPSyncRequest(buf.readUUID());
        }
        public static void handle(MBPSyncRequest pkt, IPayloadContext ctx) {
            ServerPlayer player = (ServerPlayer) ctx.player();
            var sourceContents = BackpackStorage.get(player.serverLevel())
                    .getOrCreateBackpackContents(
                            pkt.uuid
                    );

            CompoundTag responseContents =
                    copyInventoryContents(sourceContents);

            PlayerInventoryProvider.get().runOnBackpacks(player, (backpack, s, s1, i) -> {
                BackpackWrapper.fromStack(backpack).getContentsUuid().ifPresent(uuid -> {
                    if (uuid.equals(pkt.uuid)) {
                        PacketDistributor.sendToPlayer(player, new MBPSyncResponse(uuid, responseContents));
                    }
                });
                return false;
            });
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
