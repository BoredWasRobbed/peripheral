package net.bored.effect;

import net.bored.config.PeripheralConfig;
import net.bored.presence.EffectDebug;
import net.bored.presence.Observer;
import net.bored.world.OtherPlayerState;
import net.bored.world.PeripheralWorldState;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * The other you, as a player with a life of its own. It joins the game for an hour to an hour and a half at a time,
 * and only while it's in the game does it do anything: a daily routine of gathering in the morning, mining in the
 * afternoon, cooking at dusk and sleeping at night, which leaves traces around its base. Most of the time it comes and
 * goes silently; sometimes the chat says "You joined the game".
 */
public final class OtherPlayer {
	private static final long MINUTE = 20L * 60L;
	private static final int SESSION_MIN = 60;
	private static final int SESSION_MAX = 90;
	private static final int AWAY_MIN = 20;
	private static final int AWAY_MAX = 60;

	public enum Activity {
		AWAY, GATHERING, MINING, COOKING, SLEEPING;

		public String describe() {
			return switch (this) {
				case AWAY -> "not in the game";
				case GATHERING -> "gathering wood";
				case MINING -> "mining";
				case COOKING -> "cooking";
				case SLEEPING -> "asleep";
			};
		}
	}

	private OtherPlayer() {
	}

	/** What it's doing at this time of day, if it's in the game. */
	static Activity byTime(long timeOfDay) {
		long t = Math.floorMod(timeOfDay, 24000L);
		if (t < 5000) return Activity.GATHERING;
		if (t < 10500) return Activity.MINING;
		if (t < 13000) return Activity.COOKING;
		if (t < 23000) return Activity.SLEEPING;
		return Activity.GATHERING;
	}

	public static Activity activity(MinecraftServer server) {
		if (PeripheralWorldState.get(server).otherBase() == null || !OtherPlayerState.get(server).online) {
			return Activity.AWAY;
		}
		return byTime(server.getOverworld().getTimeOfDay());
	}

	// ---- Sessions ----

	/** Comes into the game. Announced only sometimes: it stays hidden 50 to 75 percent of the time. */
	public static void join(MinecraftServer server, boolean mayAnnounce) {
		OtherPlayerState other = OtherPlayerState.get(server);
		Random random = server.getOverworld().getRandom();
		long now = server.getOverworld().getTime();
		other.online = true;
		other.sessionEnds = now + random.nextBetween(SESSION_MIN, SESSION_MAX) * MINUTE;
		other.swappedThisSession = false;
		double hiddenChance = 0.5 + random.nextDouble() * 0.25;
		other.announced = mayAnnounce && PeripheralConfig.get().otherPlayerJoinMessages && random.nextDouble() >= hiddenChance;
		other.markDirty();
		if (other.announced) {
			server.getPlayerManager().broadcast(Text.translatable("multiplayer.player.joined", "You").formatted(Formatting.YELLOW), false);
		}
	}

	public static void leave(MinecraftServer server) {
		OtherPlayerState other = OtherPlayerState.get(server);
		Random random = server.getOverworld().getRandom();
		other.online = false;
		other.nextJoin = server.getOverworld().getTime() + random.nextBetween(AWAY_MIN, AWAY_MAX) * MINUTE;
		if (other.announced) {
			server.getPlayerManager().broadcast(Text.translatable("multiplayer.player.left", "You").formatted(Formatting.YELLOW), false);
		}
		other.announced = false;
		other.markDirty();
	}

	private static void updateSession(MinecraftServer server, OtherPlayerState other, long now) {
		if (other.online) {
			if (now >= other.sessionEnds) {
				leave(server);
			}
			return;
		}
		if (other.nextJoin == 0L) {
			other.nextJoin = now + server.getOverworld().getRandom().nextBetween(AWAY_MIN, AWAY_MAX) * MINUTE;
			other.markDirty();
		} else if (now >= other.nextJoin) {
			join(server, true);
		}
	}

	// ---- The routine ----

	/** Called every {@link OtherBaseBuilder#CHECK_INTERVAL} ticks. */
	public static void tick(MinecraftServer server) {
		PeripheralWorldState base = PeripheralWorldState.get(server);
		PeripheralConfig config = PeripheralConfig.get();
		if (base.otherBase() == null || !config.isEffectEnabled(OtherBaseEffect.NAME)) {
			return;
		}
		ServerWorld world = server.getOverworld();
		OtherPlayerState other = OtherPlayerState.get(server);
		updateSession(server, other, world.getTime());
		OtherBaseBuilder.Layout layout = OtherBaseBuilder.layoutOf(world, base);
		if (layout == null) {
			return;
		}
		if (!other.online) {
			if (world.isChunkLoaded(layout.centre())) {
				setFurnaceLit(world, layout, false);
			}
			return;
		}

		Activity activity = byTime(world.getTimeOfDay());
		if (!activity.name().equals(other.lastActivity)) {
			other.lastActivity = activity.name();
			other.markDirty();
		}
		OtherJournal.tick(server, world, other, layout);
		if (!config.allowWorldChanges) {
			return;
		}

		Random random = world.getRandom();
		OtherBaseStyle style = OtherBaseStyle.of(base);
		boolean baseUnseen = !OtherBaseBuilder.isWatched(world, layout);
		if (baseUnseen && world.isChunkLoaded(layout.centre())) {
			// Door open in the morning while it's out and about, furnace lit while it cooks.
			setDoorOpen(world, layout, activity == Activity.GATHERING);
			setFurnaceLit(world, layout, activity == Activity.COOKING);
		}
		switch (activity) {
			case GATHERING -> OtherTraces.gather(world, base, other, layout, style, random);
			case MINING -> OtherTraces.mine(world, other, layout, style, random);
			case COOKING -> {
				if (baseUnseen) {
					OtherBaseBuilder.loadArea(world, layout);
					OtherProgress.cook(world, base, other, layout, style, random);
				}
			}
			default -> {
			}
		}
		if (!other.swappedThisSession && activity != Activity.SLEEPING && random.nextInt(30) == 0) {
			if (OtherTraces.swap(server, world, layout, random)) {
				other.swappedThisSession = true;
				other.markDirty();
			}
		}
	}

	private static void setDoorOpen(ServerWorld world, OtherBaseBuilder.Layout layout, boolean open) {
		BlockPos pos = layout.door();
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof DoorBlock door && state.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER
				&& state.get(DoorBlock.OPEN) != open) {
			door.setOpen(null, world, state, pos, open);
		}
	}

	private static void setFurnaceLit(ServerWorld world, OtherBaseBuilder.Layout layout, boolean lit) {
		BlockPos pos = layout.at(0, -2, 1);
		BlockState state = world.getBlockState(pos);
		if (state.getBlock() instanceof AbstractFurnaceBlock && state.get(AbstractFurnaceBlock.LIT) != lit) {
			world.setBlockState(pos, state.with(AbstractFurnaceBlock.LIT, lit), net.minecraft.block.Block.NOTIFY_LISTENERS);
		}
	}

	// ---- Mirroring the player ----

	/** A copy of something the player was carrying turns up in the other base when they die. */
	public static void onPlayerDeath(ServerPlayerEntity player) {
		MinecraftServer server = player.getServer();
		PeripheralWorldState base = PeripheralWorldState.get(server);
		if (base.otherBase() == null || !PeripheralConfig.get().isEffectEnabled(OtherBaseEffect.NAME)) {
			return;
		}
		List<ItemStack> carried = new ArrayList<>();
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (!stack.isEmpty()) {
				carried.add(stack);
			}
		}
		if (carried.isEmpty()) {
			return;
		}
		ServerWorld world = server.getOverworld();
		OtherBaseBuilder.Layout layout = OtherBaseBuilder.layoutOf(world, base);
		if (layout == null) {
			return;
		}
		ItemStack copy = carried.get(player.getRandom().nextInt(carried.size())).copy();
		OtherBaseBuilder.loadArea(world, layout);
		OtherBaseTidy.store(world, layout, List.of(copy));
		report(world, layout.door(), "a copy of your " + copy.getName().getString() + " is in its chest now");
	}

	/** Remembers crops the player plants away from the other base; enough of them and it starts a field of its own. */
	public static void onPlanted(MinecraftServer server) {
		OtherPlayerState other = OtherPlayerState.get(server);
		other.plantingsSeen++;
		other.markDirty();
	}

	// ---- Shared helpers ----

	/** No player within {@code distance} blocks, and none near enough to have the spot on screen is looking at it. */
	static boolean unseen(ServerWorld world, BlockPos pos, double distance) {
		double viewDistance = world.getServer().getPlayerManager().getViewDistance() * 16.0 + 16.0;
		Vec3d target = Vec3d.ofCenter(pos);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			double distanceSq = player.squaredDistanceTo(target);
			if (distanceSq < distance * distance) {
				return false;
			}
			if (distanceSq < viewDistance * viewDistance && Observer.canSee(player, target)) {
				return false;
			}
		}
		return true;
	}

	/** Tells players with the debug readout what the other you just did. */
	static void report(ServerWorld world, BlockPos pos, String what) {
		for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
			if (EffectDebug.isReporting(player) && player.getWorld() == world) {
				EffectDebug.report(player, "the_other_you", pos, what);
			}
		}
	}
}
