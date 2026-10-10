package net.bored.effect;

import net.bored.presence.EffectDebug;
import net.bored.world.PeripheralWorldState;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * Broken parts of the other base are put back the next time nobody is around, the way its owner would: only with
 * blocks they actually have in the base's chests (crafting planks from logs when they need to), from the ground up.
 * What can't be paid for stays broken until the chests have it.
 */
public final class OtherBaseRepair {
	private static final int TOP = 5;

	private OtherBaseRepair() {
	}

	/** Whether anything in the plot is missing compared to the snapshot. Cheap; only looks at loaded chunks. */
	static boolean needsRepair(ServerWorld world, PeripheralWorldState state, OtherBaseBuilder.Layout layout) {
		if (!state.hasBaseSnapshot() || !world.isChunkLoaded(layout.centre())) {
			return false;
		}
		for (int y = 0; y <= TOP; y++) {
			for (int a = -OtherBaseBuilder.HALF; a <= OtherBaseBuilder.HALF; a++) {
				for (int b = -OtherBaseBuilder.HALF; b <= OtherBaseBuilder.PLOT_END; b++) {
					BlockPos pos = layout.at(a, b, y);
					if (world.isChunkLoaded(pos) && isBroken(world, pos, state.snapshotState(pos))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Rebuilds what it can. Only called while nobody is watching. */
	static void repair(ServerWorld world, PeripheralWorldState state, OtherBaseBuilder.Layout layout) {
		int repaired = 0;
		int unpaid = 0;
		for (int y = 0; y <= TOP; y++) {
			for (int a = -OtherBaseBuilder.HALF; a <= OtherBaseBuilder.HALF; a++) {
				for (int b = -OtherBaseBuilder.HALF; b <= OtherBaseBuilder.PLOT_END; b++) {
					BlockPos pos = layout.at(a, b, y);
					BlockState remembered = state.snapshotState(pos);
					if (!isBroken(world, pos, remembered) || isSecondHalf(remembered)) {
						continue;
					}
					BlockState target = restored(remembered);
					if (!target.canPlaceAt(world, pos)) {
						continue;
					}
					Item cost = costOf(remembered);
					if (cost == Items.AIR) {
						continue;
					}
					if (!pay(world, layout, cost)) {
						unpaid++;
						continue;
					}
					world.setBlockState(pos, target, Block.NOTIFY_LISTENERS);
					placeOtherHalf(world, state, pos, remembered);
					repaired++;
				}
			}
		}
		if (repaired == 0) {
			return;
		}

		BlockPos door = layout.door();
		String what = "repaired " + repaired + " broken blocks from its chests" + (unpaid > 0 ? ", " + unpaid + " still missing" : "");
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.getBlockPos().getSquaredDistance(door) < OtherBaseBuilder.HEARING * OtherBaseBuilder.HEARING) {
				OtherBaseBuilder.playBuildingSounds(player, layout, world.getRandom());
			}
			if (EffectDebug.isReporting(player)) {
				EffectDebug.report(player, OtherBaseEffect.NAME, door, what);
			}
		}
	}

	/** The snapshot has a block here and now there's only air or plants. */
	private static boolean isBroken(ServerWorld world, BlockPos pos, BlockState remembered) {
		return remembered != null && !remembered.isAir() && remembered.getFluidState().isEmpty()
				&& !world.getBlockState(pos).isOf(remembered.getBlock()) && OtherBaseBuilder.isClear(world, pos);
	}

	/** The top of a door or the head of a bed: paid for and placed along with its other half. */
	private static boolean isSecondHalf(BlockState state) {
		return (state.getBlock() instanceof DoorBlock && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER)
				|| (state.getBlock() instanceof BedBlock && state.get(BedBlock.PART) == BedPart.HEAD);
	}

	private static void placeOtherHalf(ServerWorld world, PeripheralWorldState state, BlockPos pos, BlockState placed) {
		BlockPos other = null;
		if (placed.getBlock() instanceof DoorBlock) {
			other = pos.up();
		} else if (placed.getBlock() instanceof BedBlock) {
			other = pos.offset(placed.get(BedBlock.FACING));
		}
		if (other == null) {
			return;
		}
		BlockState remembered = state.snapshotState(other);
		if (remembered != null && remembered.isOf(placed.getBlock()) && OtherBaseBuilder.isClear(world, other)) {
			world.setBlockState(other, remembered, Block.NOTIFY_LISTENERS);
		}
	}

	/** Crops are replanted from seed; grass comes back as dirt, as it would for a player. */
	private static BlockState restored(BlockState remembered) {
		if (remembered.getBlock() instanceof CropBlock crop) {
			return crop.getDefaultState();
		}
		if (remembered.isOf(Blocks.GRASS_BLOCK)) {
			return Blocks.DIRT.getDefaultState();
		}
		return remembered;
	}

	/** The item a player would spend to put this block back. */
	private static Item costOf(BlockState remembered) {
		if (remembered.isOf(Blocks.GRASS_BLOCK) || remembered.isOf(Blocks.FARMLAND) || remembered.isOf(Blocks.DIRT_PATH)) {
			return Items.DIRT;
		}
		return remembered.getBlock().asItem();
	}

	/** Takes the item from the base's chests, crafting planks out of a matching log if that's what's short. */
	private static boolean pay(ServerWorld world, OtherBaseBuilder.Layout layout, Item cost) {
		if (OtherBaseTidy.take(world, layout, cost)) {
			return true;
		}
		for (OtherBaseStyle.Wood wood : OtherBaseStyle.Wood.values()) {
			if (wood.planks.asItem() == cost && OtherBaseTidy.take(world, layout, wood.log.asItem())) {
				// One log makes four planks: one used now, three put away.
				OtherBaseTidy.store(world, layout, List.of(new ItemStack(cost, 3)));
				return true;
			}
		}
		return false;
	}
}
