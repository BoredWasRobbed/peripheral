package net.bored.effect;

import net.bored.presence.EffectDebug;
import net.bored.world.PeripheralWorldState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CropBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The other base is kept the way its owner left it. Anything a player places on the surface around it is taken down
 * the next time nobody is around to see, and put away in the base's chests, along with anything stored inside it.
 * When the chests are full, more appear: two double chests inside, then rows of them out the back.
 */
public final class OtherBaseTidy {
	/** How far from the cabin's centre, horizontally, placed blocks count. */
	private static final double RADIUS = 20.0;
	/** Blocks more than this far below the floor are underground, and left alone. */
	private static final int SURFACE_DEPTH = 2;
	/** How far above the floor the snapshot reaches. */
	private static final int SNAPSHOT_HEIGHT = 16;
	/** Rows behind the cabin where overflow chests go. */
	private static final int[] BACK_ROWS = {-OtherBaseBuilder.HALF - 2, -OtherBaseBuilder.HALF - 4, -OtherBaseBuilder.HALF - 6, -OtherBaseBuilder.HALF - 8};

	private OtherBaseTidy() {
	}

	/** Called whenever a player places a block anywhere; remembers it if it's at the other base. */
	public static void onPlaced(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.getRegistryKey() != World.OVERWORLD || state.isAir()) {
			return;
		}
		PeripheralWorldState worldState = PeripheralWorldState.get(world.getServer());
		BlockPos centre = worldState.otherBaseCentre();
		if (centre == null) {
			return;
		}
		boolean nearBase = Math.abs(pos.getX() - centre.getX()) <= RADIUS && Math.abs(pos.getZ() - centre.getZ()) <= RADIUS;
		if (state.getBlock() instanceof CropBlock && !nearBase) {
			// The player is farming; the other you notices.
			OtherPlayer.onPlanted(world.getServer());
		}
		double dx = pos.getX() + 0.5 - (centre.getX() + 0.5);
		double dz = pos.getZ() + 0.5 - (centre.getZ() + 0.5);
		if (dx * dx + dz * dz > RADIUS * RADIUS || pos.getY() < centre.getY() - SURFACE_DEPTH) {
			return;
		}
		String block = Registries.BLOCK.getId(state.getBlock()).toString();
		BlockState remembered = worldState.snapshotState(pos);
		if (remembered != null && remembered.isOf(state.getBlock())) {
			// The same block back where it was: as far as the owner can tell, nothing changed.
			worldState.removePlacedNearBase(pos);
			return;
		}
		worldState.addPlacedNearBase(pos, block);
	}

	/**
	 * Remembers how the surface around the base looks now: when it's started, after each stage goes up, and once for
	 * bases from before snapshots existed. Blocks still waiting to be tidied are left out, so they stay unfamiliar,
	 * and anything the old snapshot had where there's now only air is kept, so damage is never taken as the new normal.
	 */
	static void snapshot(ServerWorld world, PeripheralWorldState state, OtherBaseBuilder.Layout layout) {
		BlockPos centre = layout.centre();
		int radius = (int) Math.ceil(RADIUS);
		Map<Long, BlockState> snapshot = new HashMap<>();
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				for (int y = centre.getY() - SURFACE_DEPTH; y <= centre.getY() + SNAPSHOT_HEIGHT; y++) {
					pos.set(centre.getX() + dx, y, centre.getZ() + dz);
					BlockState block = world.getBlockState(pos);
					BlockState before = state.snapshotState(pos);
					if (before != null && OtherBaseBuilder.isClear(world, pos)) {
						snapshot.put(pos.asLong(), before);
					} else if (!block.isAir() && !state.placedNearBase().containsKey(pos)) {
						snapshot.put(pos.asLong(), block);
					}
				}
			}
		}
		state.setBaseSnapshot(snapshot);
	}

	/** Takes down every remembered block that is still there and stores it. Only called while nobody is watching. */
	static void tidy(ServerWorld world, PeripheralWorldState state, OtherBaseBuilder.Layout layout) {
		List<ItemStack> items = new ArrayList<>();
		int removed = 0;
		for (Map.Entry<BlockPos, String> entry : List.copyOf(state.placedNearBase().entrySet())) {
			BlockPos pos = entry.getKey();
			state.removePlacedNearBase(pos);
			if (!world.isChunkLoaded(pos)) {
				continue;
			}
			BlockState current = world.getBlockState(pos);
			if (current.isAir() || !Registries.BLOCK.getId(current.getBlock()).toString().equals(entry.getValue())) {
				// Already gone, or something else is there now.
				continue;
			}
			BlockEntity entity = world.getBlockEntity(pos);
			if (entity instanceof Inventory inventory) {
				for (int i = 0; i < inventory.size(); i++) {
					ItemStack stack = inventory.removeStack(i);
					if (!stack.isEmpty()) {
						items.add(stack);
					}
				}
			}
			Item item = current.getBlock().asItem();
			if (item != Items.AIR) {
				items.add(new ItemStack(item));
			}
			world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
			removed++;
		}
		if (removed == 0) {
			return;
		}

		List<ItemStack> leftover = store(world, layout, items);
		BlockPos centre = layout.at(0, 0, 1);
		for (ItemStack stack : leftover) {
			ItemScatterer.spawn(world, centre.getX() + 0.5, centre.getY(), centre.getZ() + 0.5, stack);
		}

		Random random = world.getRandom();
		BlockPos door = layout.door();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.getBlockPos().getSquaredDistance(door) < OtherBaseBuilder.HEARING * OtherBaseBuilder.HEARING) {
				OtherBaseBuilder.playBuildingSounds(player, layout, random);
			}
			if (EffectDebug.isReporting(player)) {
				EffectDebug.report(player, OtherBaseEffect.NAME, door, "took down " + removed + " placed blocks and put them in its chests");
			}
		}
	}

	/** Puts the items in the base's chests, adding chests as they fill. Returns what couldn't be stored anywhere. */
	static List<ItemStack> store(ServerWorld world, OtherBaseBuilder.Layout layout, List<ItemStack> items) {
		List<Storage> storages = storages(world, layout);
		List<ItemStack> leftover = new ArrayList<>();
		for (ItemStack stack : items) {
			for (Storage storage : storages) {
				if (stack.isEmpty()) {
					break;
				}
				if (!storage.exists(world) && !storage.create(world)) {
					continue;
				}
				for (BlockPos pos : storage.positions()) {
					if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
						insert(chest, stack);
					}
				}
			}
			if (!stack.isEmpty()) {
				leftover.add(stack);
			}
		}
		return leftover;
	}

	/** The base's original chest, then the places more chests can go, in the order they fill. */
	static List<Storage> storages(ServerWorld world, OtherBaseBuilder.Layout layout) {
		Direction front = layout.front();
		Direction right = layout.right();
		List<Storage> storages = new ArrayList<>();
		storages.add(new Storage(layout.at(2, -2, 1), front, false));
		// Inside, along the side walls.
		storages.add(new Storage(layout.at(2, -1, 1), right.getOpposite(), true));
		storages.add(new Storage(layout.at(-2, 1, 1), right, true));
		// Out the back, on the ground, facing away from the cabin.
		for (int b : BACK_ROWS) {
			for (int a : new int[] {2, -1}) {
				BlockPos column = layout.at(a, b, 0);
				int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
				storages.add(new Storage(new BlockPos(column.getX(), y, column.getZ()), front.getOpposite(), true));
			}
		}
		return storages;
	}

	/** Takes one of this item out of the base's chests. Returns false if there is none. */
	static boolean take(ServerWorld world, OtherBaseBuilder.Layout layout, Item item) {
		for (Storage storage : storages(world, layout)) {
			for (BlockPos pos : storage.positions()) {
				if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
					for (int i = 0; i < chest.size(); i++) {
						if (chest.getStack(i).isOf(item)) {
							chest.removeStack(i, 1);
							chest.markDirty();
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static void insert(Inventory inventory, ItemStack stack) {
		for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
			ItemStack slot = inventory.getStack(i);
			if (!slot.isEmpty() && ItemStack.canCombine(slot, stack)) {
				int move = Math.min(stack.getCount(), Math.min(slot.getMaxCount(), inventory.getMaxCountPerStack()) - slot.getCount());
				if (move > 0) {
					slot.increment(move);
					stack.decrement(move);
				}
			}
		}
		for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
			if (inventory.getStack(i).isEmpty()) {
				inventory.setStack(i, stack.split(Math.min(stack.getCount(), stack.getMaxCount())));
			}
		}
		inventory.markDirty();
	}

	/** One chest, or a double chest whose left half is at {@code pos} (the right half is beside it, as vanilla pairs them). */
	record Storage(BlockPos pos, Direction facing, boolean pair) {
		BlockPos other() {
			return pos.offset(facing.rotateYClockwise());
		}

		List<BlockPos> positions() {
			return pair ? List.of(pos, other()) : List.of(pos);
		}

		boolean exists(ServerWorld world) {
			return world.getBlockEntity(pos) instanceof ChestBlockEntity;
		}

		boolean create(ServerWorld world) {
			for (BlockPos at : positions()) {
				if (!world.isChunkLoaded(at) || !OtherBaseBuilder.isClear(world, at) || !OtherBaseBuilder.isSolidBelow(world, at)) {
					return false;
				}
			}
			BlockState chest = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, facing);
			if (!pair) {
				world.setBlockState(pos, chest, Block.NOTIFY_ALL);
				return true;
			}
			world.setBlockState(pos, chest.with(ChestBlock.CHEST_TYPE, ChestType.LEFT), Block.NOTIFY_LISTENERS);
			world.setBlockState(other(), chest.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT), Block.NOTIFY_LISTENERS);
			return true;
		}
	}
}
