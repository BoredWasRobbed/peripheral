package net.bored.effect;

import net.bored.world.OtherPlayerState;
import net.bored.world.PeripheralWorldState;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * Getting further along, the way a player does: at dusk the other you smelts what it mined, makes better tools when
 * it can afford them, and dresses the armor stand in what it's wearing now. Everything is paid for from its chests.
 */
final class OtherProgress {
	private OtherProgress() {
	}

	static void cook(ServerWorld world, PeripheralWorldState base, OtherPlayerState other, OtherBaseBuilder.Layout layout,
			OtherBaseStyle style, Random random) {
		// Dinner on the go, and whatever raw ore it brought back smelted.
		if (world.getBlockEntity(layout.at(0, -2, 1)) instanceof FurnaceBlockEntity furnace && furnace.getStack(2).isEmpty()) {
			furnace.setStack(2, new ItemStack(style.cookedFood(random.nextBoolean()), 1 + random.nextInt(3)));
			furnace.markDirty();
		}
		smelt(world, layout, Items.RAW_IRON, Items.IRON_INGOT);
		smelt(world, layout, Items.RAW_GOLD, Items.GOLD_INGOT);

		// Tools: stone once it has dug some rock, iron once it has three ingots to spare.
		if (other.toolTier == 0 && other.mineDepth >= 3) {
			OtherBaseTidy.store(world, layout, List.of(new ItemStack(Items.STONE_PICKAXE), new ItemStack(Items.STONE_AXE), new ItemStack(Items.STONE_SWORD)));
			other.toolTier = 1;
			other.markDirty();
			OtherPlayer.report(world, layout.door(), "made stone tools");
		} else if (other.toolTier == 1 && takeAll(world, layout, Items.IRON_INGOT, 3)) {
			OtherBaseTidy.store(world, layout, List.of(new ItemStack(Items.IRON_PICKAXE)));
			other.toolTier = 2;
			other.markDirty();
			OtherPlayer.report(world, layout.door(), "made an iron pickaxe");
		}

		// Armor on the stand: leather once the cabin stands, iron once it can afford a chestplate.
		if (other.armorTier == 0 && base.otherBaseStage() >= 2 && other.toolTier >= 1) {
			dress(world, layout, new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.LEATHER_CHESTPLATE));
			other.armorTier = 1;
			other.markDirty();
			OtherPlayer.report(world, layout.at(1, 1, 1), "put leather armor on its armor stand");
		} else if (other.armorTier == 1 && other.toolTier >= 2 && takeAll(world, layout, Items.IRON_INGOT, 8)) {
			dress(world, layout, new ItemStack(Items.LEATHER_HELMET), new ItemStack(Items.IRON_CHESTPLATE));
			other.armorTier = 2;
			other.markDirty();
			OtherPlayer.report(world, layout.at(1, 1, 1), "upgraded its armor stand to an iron chestplate");
		}
	}

	/** Every raw item in its chests becomes the smelted one. */
	private static void smelt(ServerWorld world, OtherBaseBuilder.Layout layout, Item raw, Item smelted) {
		int count = 0;
		while (count < 64 && OtherBaseTidy.take(world, layout, raw)) {
			count++;
		}
		if (count > 0) {
			OtherBaseTidy.store(world, layout, List.of(new ItemStack(smelted, count)));
		}
	}

	/** Takes {@code count} of the item, or nothing at all if there aren't that many. */
	private static boolean takeAll(ServerWorld world, OtherBaseBuilder.Layout layout, Item item, int count) {
		int taken = 0;
		while (taken < count && OtherBaseTidy.take(world, layout, item)) {
			taken++;
		}
		if (taken < count) {
			if (taken > 0) {
				OtherBaseTidy.store(world, layout, List.of(new ItemStack(item, taken)));
			}
			return false;
		}
		return true;
	}

	/** Puts the armor on the stand in the cabin, setting one up if there isn't one. */
	private static void dress(ServerWorld world, OtherBaseBuilder.Layout layout, ItemStack head, ItemStack chest) {
		BlockPos spot = layout.at(1, 1, 1);
		List<ArmorStandEntity> stands = world.getEntitiesByClass(ArmorStandEntity.class, new Box(spot).expand(1.5), stand -> true);
		ArmorStandEntity stand;
		if (stands.isEmpty()) {
			if (!OtherBaseBuilder.isClear(world, spot) || !OtherBaseBuilder.isClear(world, spot.up())) {
				return;
			}
			stand = new ArmorStandEntity(world, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			stand.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, layout.front().asRotation(), 0.0f);
			world.spawnEntity(stand);
		} else {
			stand = stands.get(0);
		}
		stand.equipStack(EquipmentSlot.HEAD, head);
		stand.equipStack(EquipmentSlot.CHEST, chest);
	}
}
