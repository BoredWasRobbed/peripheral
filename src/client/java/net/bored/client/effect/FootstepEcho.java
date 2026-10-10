package net.bored.client.effect;

import net.bored.presence.Observer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * Someone walking in the player's footsteps a few blocks back. Each time the player takes a step, a matching step
 * plays a beat later from a spot the player walked through earlier, on whatever block is there. It stops for good
 * once the player stands still, and vanishes if they turn and look straight at where it was coming from.
 */
public final class FootstepEcho {
	public static final FootstepEcho INSTANCE = new FootstepEcho();

	/** Vanilla plays a step roughly every 1.67 blocks walked. */
	private static final double STEP_LENGTH = 1.0 / 0.6;
	private static final double FOLLOW_DISTANCE = 5.5;
	private static final int WAIT_FOR_MOVEMENT_TICKS = 30 * 20;
	private static final int STOPPED_TICKS = 10;
	private static final int PATH_LENGTH = 100;

	private final Deque<Vec3d> path = new ArrayDeque<>();
	private final List<int[]> pending = new ArrayList<>();
	private final Random random = Random.create();

	private int stepsLeft;
	private int waitTicks;
	private boolean started;
	private int stillTicks;
	private double sinceLastStep;
	private Vec3d lastPos;
	/** Mark each step with smoke and say why the echo ended, for /peripheral debug. */
	private boolean debug;

	private FootstepEcho() {
	}

	public void start(int steps, boolean debug) {
		this.debug = debug;
		stepsLeft = steps;
		waitTicks = WAIT_FOR_MOVEMENT_TICKS;
		started = false;
		stillTicks = 0;
		sinceLastStep = 0;
	}

	public void stop() {
		stepsLeft = 0;
		pending.clear();
	}

	public void reset() {
		stop();
		path.clear();
		lastPos = null;
	}

	public void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}

		Vec3d pos = player.getPos();
		double moved = lastPos == null ? 0 : Math.hypot(pos.x - lastPos.x, pos.z - lastPos.z);
		lastPos = pos;
		if (path.isEmpty() || path.peekFirst().squaredDistanceTo(pos) > 0.01) {
			path.addFirst(pos);
			if (path.size() > PATH_LENGTH) {
				path.removeLast();
			}
		}

		Iterator<int[]> it = pending.iterator();
		while (it.hasNext()) {
			int[] countdown = it.next();
			if (--countdown[0] <= 0) {
				it.remove();
				playStep(client, player);
			}
		}

		if (stepsLeft <= 0) {
			return;
		}

		boolean walking = player.isOnGround() && !player.isSneaking() && moved > 0.03;
		if (walking) {
			stillTicks = 0;
			sinceLastStep += moved;
			if (sinceLastStep >= STEP_LENGTH) {
				sinceLastStep -= STEP_LENGTH;
				started = true;
				stepsLeft--;
				pending.add(new int[] {5 + random.nextInt(4)});
			}
		} else if (started && ++stillTicks > STOPPED_TICKS) {
			// They stopped, so it stops. Any step already on its way still lands, a beat too late.
			stepsLeft = 0;
			debugMessage(player, "you stopped, so it stopped");
		}

		if (!started && --waitTicks <= 0) {
			stepsLeft = 0;
			debugMessage(player, "gave up, you never walked");
		}
		if (started && stepsLeft == 0 && stillTicks == 0) {
			debugMessage(player, "ran out of steps");
		}
	}

	private void playStep(MinecraftClient client, ClientPlayerEntity player) {
		Vec3d at = followerPosition(player);
		if (Observer.isFocusedOn(player, at.add(0, 1, 0))) {
			// Looked right at it. Nothing there, and it doesn't come back.
			stop();
			debugMessage(player, "you looked at it, so it vanished");
			return;
		}

		BlockState ground = client.world.getBlockState(BlockPos.ofFloored(at.x, at.y - 0.2, at.z));
		if (ground.isAir()) {
			ground = client.world.getBlockState(player.getBlockPos().down());
		}
		BlockSoundGroup group = ground.getSoundGroup();
		client.world.playSound(at.x, at.y, at.z, group.getStepSound(), SoundCategory.PLAYERS,
				group.getVolume() * 0.2f, group.getPitch(), false);
		if (debug) {
			for (int i = 0; i < 6; i++) {
				client.world.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.1 + i * 0.3, at.z, 0.0, 0.02, 0.0);
			}
			player.sendMessage(Text.literal(String.format("footstep_echo: step %dm behind you (%d left)",
					Math.round(at.distanceTo(player.getPos())), stepsLeft)).formatted(Formatting.GRAY), true);
		}
	}

	/** The first spot along the player's recent path that's far enough back, or straight behind them if they haven't come far. */
	private void debugMessage(ClientPlayerEntity player, String why) {
		if (debug) {
			player.sendMessage(Text.literal("[Peripheral] footstep_echo ended: " + why).formatted(Formatting.GRAY), false);
		}
	}

	private Vec3d followerPosition(ClientPlayerEntity player) {
		Vec3d now = player.getPos();
		for (Vec3d point : path) {
			if (point.squaredDistanceTo(now) >= FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
				return point;
			}
		}
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d back = new Vec3d(-look.x, 0, -look.z);
		if (back.lengthSquared() < 1.0E-4) {
			back = new Vec3d(0, 0, 1);
		}
		return now.add(back.normalize().multiply(FOLLOW_DISTANCE));
	}
}
