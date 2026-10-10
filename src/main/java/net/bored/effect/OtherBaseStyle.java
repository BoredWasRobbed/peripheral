package net.bored.effect;

import net.bored.world.PeripheralWorldState;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * What the other base is built from, picked from its surroundings when it's started: the wood of the nearest trees,
 * sandstone instead of cobblestone on sand, and a pen of whatever animals graze nearby instead of a wheat farm.
 *
 * @param yard the animals kept in a pen out front, or null for a wheat farm
 */
public record OtherBaseStyle(Wood wood, Block stone, EntityType<?> yard) {
	public static final OtherBaseStyle DEFAULT = new OtherBaseStyle(Wood.OAK, Blocks.COBBLESTONE, null);
	private static final int TREE_RADIUS = 48;
	private static final int ANIMAL_RADIUS = 40;
	private static final EntityType<?>[] LIVESTOCK = {EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.CHICKEN};

	public enum Wood {
		OAK(Blocks.OAK_LOG, Blocks.OAK_PLANKS, Blocks.OAK_DOOR, Blocks.OAK_FENCE, Blocks.OAK_FENCE_GATE,
				Blocks.OAK_STAIRS, Blocks.OAK_SLAB, Blocks.OAK_TRAPDOOR),
		SPRUCE(Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_DOOR, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_FENCE_GATE,
				Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_TRAPDOOR),
		BIRCH(Blocks.BIRCH_LOG, Blocks.BIRCH_PLANKS, Blocks.BIRCH_DOOR, Blocks.BIRCH_FENCE, Blocks.BIRCH_FENCE_GATE,
				Blocks.BIRCH_STAIRS, Blocks.BIRCH_SLAB, Blocks.BIRCH_TRAPDOOR),
		JUNGLE(Blocks.JUNGLE_LOG, Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_DOOR, Blocks.JUNGLE_FENCE, Blocks.JUNGLE_FENCE_GATE,
				Blocks.JUNGLE_STAIRS, Blocks.JUNGLE_SLAB, Blocks.JUNGLE_TRAPDOOR),
		ACACIA(Blocks.ACACIA_LOG, Blocks.ACACIA_PLANKS, Blocks.ACACIA_DOOR, Blocks.ACACIA_FENCE, Blocks.ACACIA_FENCE_GATE,
				Blocks.ACACIA_STAIRS, Blocks.ACACIA_SLAB, Blocks.ACACIA_TRAPDOOR),
		DARK_OAK(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_DOOR, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_FENCE_GATE,
				Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_TRAPDOOR),
		MANGROVE(Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_DOOR, Blocks.MANGROVE_FENCE, Blocks.MANGROVE_FENCE_GATE,
				Blocks.MANGROVE_STAIRS, Blocks.MANGROVE_SLAB, Blocks.MANGROVE_TRAPDOOR),
		CHERRY(Blocks.CHERRY_LOG, Blocks.CHERRY_PLANKS, Blocks.CHERRY_DOOR, Blocks.CHERRY_FENCE, Blocks.CHERRY_FENCE_GATE,
				Blocks.CHERRY_STAIRS, Blocks.CHERRY_SLAB, Blocks.CHERRY_TRAPDOOR);

		public final Block log;
		public final Block planks;
		public final Block door;
		public final Block fence;
		public final Block gate;
		public final Block stairs;
		public final Block slab;
		public final Block trapdoor;

		Wood(Block log, Block planks, Block door, Block fence, Block gate, Block stairs, Block slab, Block trapdoor) {
			this.log = log;
			this.planks = planks;
			this.door = door;
			this.fence = fence;
			this.gate = gate;
			this.stairs = stairs;
			this.slab = slab;
			this.trapdoor = trapdoor;
		}

		static Wood byLog(Block block) {
			for (Wood wood : values()) {
				if (wood.log == block) {
					return wood;
				}
			}
			return null;
		}

		static Wood byName(String name) {
			for (Wood wood : values()) {
				if (wood.name().equalsIgnoreCase(name)) {
					return wood;
				}
			}
			return OAK;
		}
	}

	/** Food the owner would have cooked, from the animals they keep (or any, with a farm). */
	Item cookedFood(boolean coinFlip) {
		if (yard == EntityType.COW) return Items.COOKED_BEEF;
		if (yard == EntityType.SHEEP) return Items.COOKED_MUTTON;
		if (yard == EntityType.PIG) return Items.COOKED_PORKCHOP;
		if (yard == EntityType.CHICKEN) return Items.COOKED_CHICKEN;
		return coinFlip ? Items.COOKED_BEEF : Items.COOKED_PORKCHOP;
	}

	/** What the animals leave the owner with. */
	Item animalProduct() {
		if (yard == EntityType.COW) return Items.LEATHER;
		if (yard == EntityType.SHEEP) return Items.WHITE_WOOL;
		if (yard == EntityType.PIG) return Items.PORKCHOP;
		if (yard == EntityType.CHICKEN) return Items.FEATHER;
		return Items.WHEAT;
	}

	/** Looks around a new base's plot and decides how it's built. */
	static OtherBaseStyle choose(ServerWorld world, OtherBaseBuilder.Layout layout, int floor) {
		BlockPos centre = layout.at(0, 0, 0);

		// The commonest log among the nearby trees.
		Map<Wood, Integer> logs = new EnumMap<>(Wood.class);
		for (int dx = -TREE_RADIUS; dx <= TREE_RADIUS; dx += 2) {
			for (int dz = -TREE_RADIUS; dz <= TREE_RADIUS; dz += 2) {
				int x = centre.getX() + dx;
				int z = centre.getZ() + dz;
				if (!world.isChunkLoaded(new BlockPos(x, floor, z))) {
					continue;
				}
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
				for (int y = top; y > top - 8; y--) {
					BlockState state = world.getBlockState(new BlockPos(x, y, z));
					if (state.isIn(BlockTags.LOGS)) {
						Wood wood = Wood.byLog(state.getBlock());
						if (wood != null) {
							logs.merge(wood, 1, Integer::sum);
						}
						break;
					}
				}
			}
		}
		Wood wood = logs.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Wood.OAK);

		// Sandstone on sand, cobblestone everywhere else.
		int sand = 0;
		int total = 0;
		for (int a = -OtherBaseBuilder.HALF; a <= OtherBaseBuilder.HALF; a++) {
			for (int b = -OtherBaseBuilder.HALF; b <= OtherBaseBuilder.HALF; b++) {
				BlockPos column = layout.at(a, b, 0);
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
				total++;
				if (world.getBlockState(new BlockPos(column.getX(), top, column.getZ())).isIn(BlockTags.SAND)) {
					sand++;
				}
			}
		}
		Block stone = sand * 2 > total ? Blocks.SANDSTONE : Blocks.COBBLESTONE;

		// Animals grazing nearby are kept in a pen; with none around, it's a wheat farm.
		Map<EntityType<?>, Integer> animals = new HashMap<>();
		Box area = new Box(centre.getX() - ANIMAL_RADIUS, floor - 16, centre.getZ() - ANIMAL_RADIUS,
				centre.getX() + ANIMAL_RADIUS, floor + 16, centre.getZ() + ANIMAL_RADIUS);
		for (AnimalEntity animal : world.getEntitiesByClass(AnimalEntity.class, area, entity -> true)) {
			for (EntityType<?> type : LIVESTOCK) {
				if (animal.getType() == type) {
					animals.merge(type, 1, Integer::sum);
				}
			}
		}
		EntityType<?> yard = animals.entrySet().stream().filter(entry -> entry.getValue() >= 2)
				.max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
		return new OtherBaseStyle(wood, stone, yard);
	}

	/** The style saved with the world; bases from before styles existed are oak, cobblestone and wheat. */
	static OtherBaseStyle of(PeripheralWorldState state) {
		if (state.otherBaseWood().isEmpty()) {
			return DEFAULT;
		}
		Block stone = Registries.BLOCK.get(new Identifier(state.otherBaseStone()));
		EntityType<?> yard = state.otherBaseYard().isEmpty() ? null : Registries.ENTITY_TYPE.get(new Identifier(state.otherBaseYard()));
		return new OtherBaseStyle(Wood.byName(state.otherBaseWood()), stone == Blocks.AIR ? Blocks.COBBLESTONE : stone, yard);
	}

	void save(PeripheralWorldState state) {
		state.setOtherBaseStyle(wood.name().toLowerCase(), Registries.BLOCK.getId(stone).toString(),
				yard == null ? "" : Registries.ENTITY_TYPE.getId(yard).toString());
	}

	/** A short description for debug messages, like "spruce, cobblestone, sheep pen". */
	String describe() {
		String yardName = yard == null ? "wheat farm" : Registries.ENTITY_TYPE.getId(yard).getPath() + " pen";
		return wood.name().toLowerCase() + ", " + Registries.BLOCK.getId(stone).getPath() + ", " + yardName;
	}
}
