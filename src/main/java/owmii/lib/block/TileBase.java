package owmii.lib.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import owmii.lib.inventory.Inventory;

import javax.annotation.Nullable;

public class TileBase extends TileEntity implements INamedContainerProvider {
    protected final Inventory inv = Inventory.createBlank(this);
    private final LazyOptional<Inventory> invHandler = LazyOptional.of(() -> this.inv);
    public boolean isNew = true;
    protected boolean isContainerOpen;

    @Nullable
    public ITextComponent customName;
    public String publicName = "";

    public TileBase(TileEntityType<?> tileEntityType) {
        super(tileEntityType);
    }

    @Override
    public void read(CompoundNBT compound) {
        super.read(compound);
        readSync(compound);
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        CompoundNBT nbt = super.write(compound);
        return writeSync(nbt);
    }

    @Override
    public CompoundNBT getUpdateTag() {
        return write(new CompoundNBT());
    }

    @Nullable
    @Override
    public SUpdateTileEntityPacket getUpdatePacket() {
        return new SUpdateTileEntityPacket(getPos(), 3, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
        readSync(pkt.getNbtCompound());
    }

    public void readSync(CompoundNBT compound) {
        if (compound.contains("DefaultName", 8)) {
            this.publicName = compound.getString("DefaultName");
        }
        if (compound.contains("CustomName", 8)) {
            this.customName = ITextComponent.Serializer.fromJson(compound.getString("CustomName"));
        }
        if (!keepInventory()) {
            this.inv.deserializeNBT(compound);
        }
        readStorable(compound);
        this.isNew = compound.getBoolean("IsNew");
    }

    public CompoundNBT writeSync(CompoundNBT compound) {
        if (!this.publicName.isEmpty()) {
            compound.putString("DefaultName", this.publicName);
        }
        if (this.customName != null) {
            compound.putString("CustomName", ITextComponent.Serializer.toJson(this.customName));
        }
        if (!keepInventory()) {
            compound.merge(this.inv.serializeNBT());
        }
        writeStorable(compound);
        compound.putBoolean("IsNew", this.isNew);
        return compound;
    }

    public void readStorable(CompoundNBT compound) {
        if (keepInventory()) {
            this.inv.deserializeNBT(compound);
        }
    }

    public CompoundNBT writeStorable(CompoundNBT compound) {
        if (keepInventory()) {
            compound.merge(this.inv.serializeNBT());
        }
        return compound;
    }

    public boolean isNBTStorable() {
        return true;
    }

    public void markDirtyAndSync() {
        if (this.world != null) {
            if (isServerWorld()) {
                markDirty();
                BlockState state = getBlockState();
                this.world.notifyBlockUpdate(this.pos, state, state, 3);
            }
        }
    }

    public boolean isServerWorld() {
        return this.world != null && !this.world.isRemote;
    }

    public Block getBlock() {
        return getBlockState().getBlock();
    }

    public void setContainerOpen(boolean b) {
        this.isContainerOpen = b;
    }

    public boolean isContainerOpen() {
        return this.isContainerOpen;
    }

    public void onSlotChanged(int index) {
    }

    public boolean canInsert(int index, ItemStack stack) {
        return true;
    }

    public boolean canExtract(int slot, ItemStack stack) {
        return true;
    }

    public int getSlotLimit(int index) {
        return 64;
    }

    public boolean keepInventory() {
        return false;
    }

    public Inventory getInventory() {
        return this.inv;
    }

    public boolean hasCustomName() {
        return this.customName != null;
    }

    @Override
    public ITextComponent getDisplayName() {
        return getName();
    }

    protected ITextComponent getDefaultName() {
        return new TranslationTextComponent(this.publicName);
    }

    public void setDefaultName(String publicName) {
        this.publicName = publicName;
    }

    @Nullable
    public ITextComponent getCustomName() {
        return this.customName;
    }

    public ITextComponent getName() {
        return this.customName != null ? this.customName : getDefaultName();
    }

    public void setCustomName(ITextComponent displayName) {
        this.customName = displayName;
    }

    @Nullable
    @Override
    public Container createMenu(int i, PlayerInventory playerInventory, PlayerEntity playerEntity) {
        if (this.getBlock() instanceof BlockBase) {
            return ((BlockBase) getBlock()).getContainer(i, playerInventory, this);
        }
        return null;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (!this.removed && !this.inv.isBlank() && cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return this.invHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    public static class Tickable extends TileBase implements ITickableTileEntity {
        protected int syncTicks;
        public int ticks;

        public Tickable(TileEntityType<?> type) {
            super(type);
        }

        @Override
        public void tick() {
            if (this.world == null) return;
            if (doTick()) {
                if (this.ticks == 0) {
                    if (this.isNew) {
                        absFirstTick();
                    }
                    firstTick();
                }

                if (postTicks()) {
                    markDirty();
                    sync(getSyncTicks());
                }

                this.ticks++;

                if (this.syncTicks > -1) {
                    this.syncTicks--;
                }

                if (this.syncTicks == 0) {
                    markDirtyAndSync();
                }
            }
            if (this.isNew) {
                this.isNew = false;
                markDirtyAndSync();
            }
        }

        protected boolean doTick() {
            return true;
        }

        protected void firstTick() {
        }

        protected void absFirstTick() {
        }

        protected boolean postTicks() {
            return false;
        }

        public void resetTicks() {
            this.ticks = 0;
        }

        public void sync(int delay) {
            if (this.syncTicks <= 0) {
                this.syncTicks = delay;
            }
        }

        public int getSyncTicks() {
            return isContainerOpen() ? 4 : 40;
        }
    }
}
