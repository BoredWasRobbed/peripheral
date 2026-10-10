package net.bored.effect;

import net.bored.presence.EffectDebug;
import net.bored.presence.Home;
import net.bored.presence.Observer;
import net.bored.presence.PresenceContext;
import net.bored.presence.PresenceScheduler;
import net.bored.world.PeripheralWorldState;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Once per world, somewhere out of sight, a small cabin appears: someone's first night shelter, lived in. A bed like
 * the player's own, a furnace with food left in it, a chest of the things a player carries early on, and a campfire
 * outside whose smoke can be seen from a distance. Nobody is ever home, and it stays like any other build.
 */
public class OtherBaseEffect implements PresenceEffect {
	public static final String NAME = "the_other_base";
	private static final double MIN_DISTANCE = 48.0;
	private static final double MAX_DISTANCE = 80.0;
	private static final double MIN_HOME_DISTANCE = 96.0;
	/** The cabin is 7x7 around its centre; the porch and campfire take two more rows in front. */
	private static final int HALF = 3;
	private static final int PORCH = 2;
	private static final int ATTEMPTS = 60;

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int tier() {
		return 4;
	}

	@Override
	public int weight() {
		return 15;
	}

	@Override
	public boolean changesWorld() {
		return true;
	}

	/** It's found later, not heard now, so the game doesn't go quiet for it. */
	@Override
	public boolean hushes() {
		return false;
	}

	@Override
	public boolean canFire(ServerPlayerEntity player, PresenceContext context) {
		return context.alone() && context.skyVisible() && player.getWorld().getRegistryKey() == World.OVERWORLD
				&& PeripheralWorldState.get(player.getServer()).otherBase() == null;
	}

	@Override
	public boolean fire(ServerPlayerEntity player, PresenceContext context, PresenceScheduler scheduler) {
		ServerWorld world = player.getServerWorld();
		PeripheralWorldState state = PeripheralWorldState.get(player.getServer());
		if (state.otherBase() != null) {
			return false;
		}
		BlockPos home = Home.of(player);
		Random random = player.getRandom();
		for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
			// Somewhere behind the player, so it is never built in front of them.
			float yaw = player.getYaw() + 180.0f + (random.nextFloat() - 0.5f) * 140.0f;
			double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
			int x = (int) Math.floor(player.getX() - Math.sin(Math.toRadians(yaw)) * distance);
			int z = (int) Math.floor(player.getZ() + Math.cos(Math.toRadians(yaw)) * distance);
			if (home != null && Math.hypot(home.getX() - x, home.getZ() - z) < MIN_HOME_DISTANCE) {
				continue;
			}
			// The door faces back toward the player.
			Direction front = Direction.getFacing(player.getX() - x, 0, player.getZ() - z);
			if (front.getAxis() == Direction.Axis.Y) {
				continue;
			}
			Layout layout = new Layout(new BlockPos(x, 0, z), front);
			Integer floor = findFloor(world, layout);
			if (floor == null) {
				continue;
			}
			layout = new Layout(new BlockPos(x, floor, z), front);
			if (isSeen(player, layout)) {
				continue;
			}
			build(world, layout, bedFor(world, home), random);
			BlockPos door = layout.at(0, HALF, 1);
			state.setOtherBase(door);
			EffectDebug.report(player, NAME, door, "built a cabin with a bed, furnace, chest and campfire");
			return true;
		}
		return false;
	}

	/** The floor height if the ground under the whole plot is flat to within a block, dry, and open above; else null. */
	private static Integer findFloor(ServerWorld world, Layout layout) {
		BlockPos centre = layout.at(0, 0, 0);
		if (!world.isChunkLoaded(centre)) {
			return null;
		}
		int floor = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ()) - 1;
		if (floor <= world.getBottomY() + 2 || floor + 6 >= world.getTopY()) {
			return null;
		}
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF + PORCH; b++) {
				BlockPos column = layout.at(a, b, 0);
				if (!world.isChunkLoaded(column)) {
					return null;
				}
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1;
				if (Math.abs(top - floor) > 1) {
					return null;
				}
				BlockPos ground = new BlockPos(column.getX(), top, column.getZ());
				BlockState groundState = world.getBlockState(ground);
				if (!world.getFluidState(ground).isEmpty() || !groundState.isSideSolidFullSquare(world, ground, Direction.UP)) {
					return null;
				}
				for (int y = floor + 1; y <= floor + 5; y++) {
					BlockPos above = new BlockPos(column.getX(), y, column.getZ());
					if (y > top && !isClear(world, above)) {
						return null;
					}
				}
			}
		}
		return floor;
	}

	private static boolean isClear(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return world.getFluidState(pos).isEmpty() && (state.isAir() || state.isReplaceable());
	}

	private static boolean isSeen(ServerPlayerEntity player, Layout layout) {
		for (int a = -HALF; a <= HALF; a += HALF) {
			for (int b = -HALF; b <= HALF + PORCH; b += HALF) {
				for (int y = 1; y <= 4; y += 3) {
					if (Observer.canSee(player, Vec3d.ofCenter(layout.at(a, b, y)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** The same kind of bed the player sleeps in, if their bed is loaded; otherwise a red one. */
	private static BlockState bedFor(ServerWorld world, BlockPos home) {
		if (home != null && world.isChunkLoaded(home) && world.getBlockState(home).getBlock() instanceof BedBlock bed) {
			return bed.getDefaultState();
		}
		return Blocks.RED_BED.getDefaultState();
	}

	private static void build(ServerWorld world, Layout layout, BlockState bed, Random random) {
		Direction front = layout.front;
		Direction right = layout.right;

		// Foundation and floor, then walls and roof, with the inside cleared.
		for (int a = -HALF; a <= HALF; a++) {
			for (int b = -HALF; b <= HALF; b++) {
				boolean edge = Math.abs(a) == HALF || Math.abs(b) == HALF;
				boolean corner = Math.abs(a) == HALF && Math.abs(b) == HALF;
				for (int y = -3; y < 0; y++) {
					BlockPos below = layout.at(a, b, y);
					if (isClear(world, below) || !world.getFluidState(below).isEmpty()) {
						set(world, below, Blocks.COBBLESTONE.getDefaultState());
					}
				}
				set(world, layout.at(a, b, 0), (edge ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS).getDefaultState());
				for (int y = 1; y <= 3; y++) {
					BlockState wall = corner ? Blocks.OAK_LOG.getDefaultState() : edge ? Blocks.OAK_PLANKS.getDefaultState() : Blocks.AIR.getDefaultState();
					set(world, layout.at(a, b, y), wall);
				}
				set(world, layout.at(a, b, 4), Blocks.OAK_PLANKS.getDefaultState());
				set(world, layout.at(a, b, 5), Blocks.AIR.getDefaultState());
			}
		}

		// Windows on the sides and back.
		set(world, layout.at(-HALF, 0, 2), Blocks.GLASS.getDefaultState());
		set(world, layout.at(HALF, 0, 2), Blocks.GLASS.getDefaultState());
		set(world, layout.at(0, -HALF, 2), Blocks.GLASS.getDefaultState());

		// The door, with a clear step in front of it.
		for (int y = 1; y <= 2; y++) {
			set(world, layout.at(0, HALF + 1, y), Blocks.AIR.getDefaultState());
		}
		BlockState door = Blocks.OAK_DOOR.getDefaultState().with(DoorBlock.FACING, front.getOpposite());
		set(world, layout.at(0, HALF, 1), door.with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
		set(world, layout.at(0, HALF, 2), door.with(DoorBlock.HALF, DoubleBlockHalf.UPPER));

		// Inside: bed in the back corner, furnace, crafting table and chest along the back wall, a torch on the wall.
		BlockState bedState = bed.with(BedBlock.FACING, front.getOpposite());
		set(world, layout.at(-2, -1, 1), bedState.with(BedBlock.PART, BedPart.FOOT));
		set(world, layout.at(-2, -2, 1), bedState.with(BedBlock.PART, BedPart.HEAD));
		set(world, layout.at(0, -2, 1), Blocks.FURNACE.getDefaultState().with(AbstractFurnaceBlock.FACING, front));
		set(world, layout.at(1, -2, 1), Blocks.CRAFTING_TABLE.getDefaultState());
		set(world, layout.at(2, -2, 1), Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, front));
		set(world, layout.at(2, 1, 2), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, right.getOpposite()));

		// Outside: a torch by the door and a campfire, its smoke the thing that gives the place away.
		set(world, layout.at(1, HALF + 1, 2), Blocks.WALL_TORCH.getDefaultState().with(WallTorchBlock.FACING, front));
		BlockPos campfireColumn = layout.at(-2, HALF + 2, 0);
		int campfireY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, campfireColumn.getX(), campfireColumn.getZ());
		set(world, new BlockPos(campfireColumn.getX(), campfireY, campfireColumn.getZ()),
				Blocks.CAMPFIRE.getDefaultState().with(CampfireBlock.FACING, right).with(CampfireBlock.LIT, true));

		fill(world, layout, random);
	}

	private static void fill(ServerWorld world, Layout layout, Random random) {
		BlockEntity furnace = world.getBlockEntity(layout.at(0, -2, 1));
		if (furnace instanceof FurnaceBlockEntity entity) {
			entity.setStack(2, new ItemStack(random.nextBoolean() ? Items.COOKED_BEEF : Items.COOKED_PORKCHOP, 2 + random.nextInt(3)));
			entity.markDirty();
		}
		BlockEntity chest = world.getBlockEntity(layout.at(2, -2, 1));
		if (chest instanceof ChestBlockEntity entity) {
			ItemStack pickaxe = new ItemStack(Items.STONE_PICKAXE);
			pickaxe.setDamage(40 + random.nextInt(60));
			ItemStack[] items = {
					new ItemStack(Items.BREAD, 3 + random.nextInt(5)),
					new ItemStack(Items.COAL, 4 + random.nextInt(9)),
					new ItemStack(Items.TORCH, 6 + random.nextInt(10)),
					new ItemStack(Items.COBBLESTONE, 20 + random.nextInt(40)),
					new ItemStack(Items.RAW_IRON, 2 + random.nextInt(4)),
					new ItemStack(Items.OAK_LOG, 3 + random.nextInt(8)),
					new ItemStack(Items.WHEAT_SEEDS, 2 + random.nextInt(10)),
					pickaxe,
			};
			for (ItemStack item : items) {
				int slot = random.nextInt(entity.size());
				while (!entity.getStack(slot).isEmpty()) {
					slot = (slot + 1) % entity.size();
				}
				entity.setStack(slot, item);
			}
			entity.markDirty();
		}
	}

	/** Only listeners are told, so nothing pops off while the cabin goes up out of order. */
	private static void set(ServerWorld world, BlockPos pos, BlockState state) {
		world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
	}

	/** Positions relative to the cabin's centre: {@code a} to the right, {@code b} toward the door, {@code y} above the floor. */
	private record Layout(BlockPos centre, Direction front, Direction right) {
		Layout(BlockPos centre, Direction front) {
			this(centre, front, front.rotateYClockwise());
		}

		BlockPos at(int a, int b, int y) {
			return centre.offset(right, a).offset(front, b).up(y);
		}
	}
}
