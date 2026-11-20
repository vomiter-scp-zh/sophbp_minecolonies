package com.vomiter.sophbp_minecolonies.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.client.gui.modules.RequestTreeWindowModule;
import com.minecolonies.core.network.messages.server.colony.UpdateRequestStateMessage;
import com.vomiter.sophbp_minecolonies.ModNetwork;
import com.vomiter.sophbp_minecolonies.util.BPTransferRecord;
import com.vomiter.sophbp_minecolonies.util.SophisticatedUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

@Mixin(targets = "com.minecolonies.core.client.gui.citizen.RequestWindowCitizen$CitizenRequestTreeWindowModule", remap = false)
public abstract class CitizenRequestTreeMixin  extends RequestTreeWindowModule {
    @Shadow @Final private ICitizenDataView citizenDataView;
    @Shadow @Final private boolean isCreative;
    @Unique
    private long sophbp_minecolonies$nextSyncTime = -1;

    @Shadow
    @Final
    private Inventory inventory;

    ThreadLocal<Integer> amountToBeFullfilled = ThreadLocal.withInitial(() -> 0);

    public CitizenRequestTreeMixin(AbstractWindowSkeleton parent, IColonyView colony) {
        super(parent, colony);
    }

    @Inject(method = "onFulfill", at = @At("RETURN"))
    private void sophbpMinecolonies$sendMaterial(@NotNull IRequest<?> request, CallbackInfo ci){
        IRequestable var3 = request.getRequest();
        if (!(var3 instanceof IDeliverable deliverable)) return;
        InvWrapper var10000 = new InvWrapper(this.inventory);
        int amountRequested = deliverable.getCount();
        int amountInInv = InventoryUtils.getItemCountInItemHandler(var10000, deliverable::matches);
        int requestForBPTotal = amountRequested - amountInInv;
        final int[] requestForBP = {amountRequested - amountInInv};
        if (requestForBP[0] <= 0) return;
        var entries = new ArrayList<BPTransferRecord>();
        SophisticatedUtil.runOnBackpack(inventory.player, backpackWrapper -> {
            for (int i = 0; i < backpackWrapper.getInventoryHandler().getSlots(); i++) {
                var item = backpackWrapper.getInventoryHandler().getStackInSlot(i);
                if (deliverable.matches(item)){
                    var entry = new BPTransferRecord(backpackWrapper.getContentsUuid().get(), i, Math.min(requestForBP[0], item.getCount()));
                    entries.add(entry);
                    requestForBP[0] -= Math.min(requestForBP[0], item.getCount());
                }
            }
            return requestForBP[0] <= 0;
        });
        UUID[] uuids = new UUID[entries.size()];
        int[] slot = new int[entries.size()];
        int[] amountToSend = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            uuids[i] = entries.get(i).uuid();
            slot[i] = entries.get(i).slot();
            amountToSend[i] = entries.get(i).amount();
        };

        var packet = new ModNetwork.BPTransferRequest(
                Minecraft.getInstance().level.dimension(),
                citizenDataView.getColonyId(),
                citizenDataView.getId(),
                uuids,
                slot,
                amountToSend
        );
        PacketDistributor.sendToServer(packet);
        var itemStack = request.getDisplayStacks().stream().findFirst().orElse(ItemStack.EMPTY);
        var updatePacket = new UpdateRequestStateMessage(colony, request.getId(), RequestState.OVERRULED, itemStack.copyWithCount( requestForBPTotal - requestForBP[0]));
        updatePacket.sendToServer();
    }




    @WrapOperation(method = "isFulfillable", at = @At(value = "INVOKE", target =
            "Lcom/minecolonies/api/util/InventoryUtils;hasItemInItemHandler(Lnet/neoforged/neoforge/items/IItemHandler;Ljava/util/function/Predicate;)Z"))
    private boolean sophbpMinecolonies$hasMaterials(@Nullable IItemHandler itemHandler, @NotNull Predicate<ItemStack> itemStackSelectionPredicate, Operation<Boolean> original) {
        if (original.call(itemHandler, itemStackSelectionPredicate)) return true;
        AtomicReference<Boolean> booleanRef = new AtomicReference<>(false);
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return false;
        long currentTime = Minecraft.getInstance().level.getGameTime();
        if (currentTime >= sophbp_minecolonies$nextSyncTime){
            sophbp_minecolonies$nextSyncTime = currentTime + 60;
            SophisticatedUtil.requestBackpackSync(Minecraft.getInstance().player);
        }

        PlayerInventoryProvider.get().runOnBackpacks(
                Minecraft.getInstance().player,
                (backpack,
                inventoryName,
                identifier,
                backpackSlot
                ) -> {
                    if (booleanRef.get()) return true;
                    var inv = BackpackWrapper.fromStack(backpack).getInventoryHandler();
                    for (int i = 0; i < inv.getSlots(); i++) {
                        if (itemStackSelectionPredicate.test(inv.getStackInSlot(i))) {
                            booleanRef.set(true);
                            return true;
                        }
                    }
                    return false;
                }
        );
        return booleanRef.get();
    }
}
