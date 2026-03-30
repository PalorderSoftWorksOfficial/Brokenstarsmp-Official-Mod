package net.minecraft.screen;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.ForgingSlotsManager;
import net.minecraft.screen.slot.Slot;
import org.jspecify.annotations.Nullable;

public abstract class ForgingScreenHandler extends ScreenHandler {
	private static final int field_41901 = 9;
	private static final int field_41902 = 3;
	private static final int field_54595 = 0;
	protected final ScreenHandlerContext context;
	protected final PlayerEntity player;
	protected final Inventory input;
	protected final CraftingResultInventory output = new CraftingResultInventory() {
		@Override
		public void markDirty() {
			ForgingScreenHandler.this.onContentChanged(this);
		}
	};
	private final int resultSlotIndex;

	protected boolean canTakeOutput(PlayerEntity playerEntity, boolean bl) {
		return true;
	}

	protected abstract void onTakeOutput(PlayerEntity playerEntity, ItemStack itemStack);

	protected abstract boolean canUse(BlockState blockState);

	public ForgingScreenHandler(
		@Nullable ScreenHandlerType<?> screenHandlerType,
		int i,
		PlayerInventory playerInventory,
		ScreenHandlerContext screenHandlerContext,
		ForgingSlotsManager forgingSlotsManager
	) {
		super(screenHandlerType, i);
		this.context = screenHandlerContext;
		this.player = playerInventory.player;
		this.input = this.createInputInventory(forgingSlotsManager.getInputSlotCount());
		this.resultSlotIndex = forgingSlotsManager.getResultSlotIndex();
		this.addInputSlots(forgingSlotsManager);
		this.addResultSlot(forgingSlotsManager);
		this.addPlayerSlots(playerInventory, 8, 84);
	}

	private void addInputSlots(ForgingSlotsManager forgingSlotsManager) {
		for (final ForgingSlotsManager.ForgingSlot forgingSlot : forgingSlotsManager.getInputSlots()) {
			this.addSlot(new Slot(this.input, forgingSlot.slotId(), forgingSlot.comp_1205(), forgingSlot.comp_1206()) {
				@Override
				public boolean canInsert(ItemStack itemStack) {
					return forgingSlot.comp_1207().test(itemStack);
				}
			});
		}
	}

	private void addResultSlot(ForgingSlotsManager forgingSlotsManager) {
		this.addSlot(
			new Slot(
				this.output, forgingSlotsManager.getResultSlot().slotId(), forgingSlotsManager.getResultSlot().comp_1205(), forgingSlotsManager.getResultSlot().comp_1206()
			) {
				@Override
				public boolean canInsert(ItemStack itemStack) {
					return false;
				}

				@Override
				public boolean canTakeItems(PlayerEntity playerEntity) {
					return ForgingScreenHandler.this.canTakeOutput(playerEntity, this.hasStack());
				}

				@Override
				public void onTakeItem(PlayerEntity playerEntity, ItemStack itemStack) {
					ForgingScreenHandler.this.onTakeOutput(playerEntity, itemStack);
				}
			}
		);
	}

	public abstract void updateResult();

	private SimpleInventory createInputInventory(int i) {
		return new SimpleInventory(i) {
			@Override
			public void markDirty() {
				super.markDirty();
				ForgingScreenHandler.this.onContentChanged(this);
			}
		};
	}

	@Override
	public void onContentChanged(Inventory inventory) {
		super.onContentChanged(inventory);
		if (inventory == this.input) {
			this.updateResult();
		}
	}

	@Override
	public void onClosed(PlayerEntity playerEntity) {
		super.onClosed(playerEntity);
		this.context.run((world, blockPos) -> this.dropInventory(playerEntity, this.input));
	}

	@Override
	public boolean canUse(PlayerEntity playerEntity) {
		return this.context.get((world, blockPos) -> !this.canUse(world.getBlockState(blockPos)) ? false : playerEntity.canInteractWithBlockAt(blockPos, 4.0), true);
	}

	@Override
	public ItemStack quickMove(PlayerEntity playerEntity, int i) {
		ItemStack itemStack = ItemStack.EMPTY;
		Slot slot = this.slots.get(i);
		if (slot != null && slot.hasStack()) {
			ItemStack itemStack2 = slot.getStack();
			itemStack = itemStack2.copy();
			int j = this.getPlayerInventoryStartIndex();
			int k = this.getPlayerHotbarEndIndex();
			if (i == this.getResultSlotIndex()) {
				if (!this.insertItem(itemStack2, j, k, true)) {
					return ItemStack.EMPTY;
				}

				slot.onQuickTransfer(itemStack2, itemStack);
			} else if (i >= 0 && i < this.getResultSlotIndex()) {
				if (!this.insertItem(itemStack2, j, k, false)) {
					return ItemStack.EMPTY;
				}
			} else if (this.isValidIngredient(itemStack2) && i >= this.getPlayerInventoryStartIndex() && i < this.getPlayerHotbarEndIndex()) {
				if (!this.insertItem(itemStack2, 0, this.getResultSlotIndex(), false)) {
					return ItemStack.EMPTY;
				}
			} else if (i >= this.getPlayerInventoryStartIndex() && i < this.getPlayerInventoryEndIndex()) {
				if (!this.insertItem(itemStack2, this.getPlayerHotbarStartIndex(), this.getPlayerHotbarEndIndex(), false)) {
					return ItemStack.EMPTY;
				}
			} else if (i >= this.getPlayerHotbarStartIndex()
				&& i < this.getPlayerHotbarEndIndex()
				&& !this.insertItem(itemStack2, this.getPlayerInventoryStartIndex(), this.getPlayerInventoryEndIndex(), false)) {
				return ItemStack.EMPTY;
			}

			if (itemStack2.isEmpty()) {
				slot.setStack(ItemStack.EMPTY);
			} else {
				slot.markDirty();
			}

			if (itemStack2.getCount() == itemStack.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTakeItem(playerEntity, itemStack2);
		}

		return itemStack;
	}

	protected boolean isValidIngredient(ItemStack itemStack) {
		return true;
	}

	public int getResultSlotIndex() {
		return this.resultSlotIndex;
	}

	private int getPlayerInventoryStartIndex() {
		return this.getResultSlotIndex() + 1;
	}

	private int getPlayerInventoryEndIndex() {
		return this.getPlayerInventoryStartIndex() + 27;
	}

	private int getPlayerHotbarStartIndex() {
		return this.getPlayerInventoryEndIndex();
	}

	private int getPlayerHotbarEndIndex() {
		return this.getPlayerHotbarStartIndex() + 9;
	}
}
