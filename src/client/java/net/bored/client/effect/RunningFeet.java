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

/**
 * Someone sprinting at the player from behind, one step at a time on whatever ground is there, until the steps
 * stop just behind them. Turning around to face it while it's still coming makes it stop dead.
 */
public final class RunningFeet {
	public static final RunningFeet INSTANCE = new RunningFeet();

	private static final double START_DISTANCE = 20.0;
	private static final double STOP_DISTANCE = 1.8;
	/** Sprinting speed, in blocks per tick. */
	private static final double SPEED = 5.6 / 20.0;
	private static final int STEP_TICKS = 6;
	private static final int MAX_TICKS = 10 * 20;
	/** Turning to within this many degrees of it, while it's this close, stops it. */
	private static final double CAUGHT_DEGREES = 40.0;
	private static final double CAUGHT_DISTANCE = 16.0;

	private Vec3d pos;
	private int age;
	private boolean debug;

	private RunningFeet() {
	}

	public void start(MinecraftClient client, boolean debug) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		this.debug = debug;
		this.age = 0;
		this.pos = player.getPos().add(behind(player).multiply(START_DISTANCE));
	}

	public void stop() {
		pos = null;
	}

	public void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (pos == null) {
			return;
		}
		if (player == null || client.world == null) {
			stop();
			return;
		}

		Vec3d target = player.getPos();
		Vec3d toPlayer = new Vec3d(target.x - pos.x, 0, target.z - pos.z);
		double distance = toPlayer.length();
		if (distance <= STOP_DISTANCE) {
			end(player, "stopped right behind you");
			return;
		}
		if (distance < CAUGHT_DISTANCE && Observer.angleTo(player, pos.add(0, 1, 0)) < CAUGHT_DEGREES) {
			end(player, "you turned around");
			return;
		}
		if (++age > MAX_TICKS) {
			end(player, "never caught up");
			return;
		}

		double move = Math.min(SPEED, distance - STOP_DISTANCE);
		pos = new Vec3d(pos.x + toPlayer.x / distance * move, target.y, pos.z + toPlayer.z / distance * move);
		if (age % STEP_TICKS == 0) {
			playStep(client, player);
		}
	}

	private void playStep(MinecraftClient client, ClientPlayerEntity player) {
		BlockState ground = client.world.getBlockState(BlockPos.ofFloored(pos.x, pos.y - 0.2, pos.z));
		if (ground.isAir()) {
			ground = client.world.getBlockState(player.getBlockPos().down());
		}
		BlockSoundGroup group = ground.getSoundGroup();
		client.world.playSound(pos.x, pos.y, pos.z, group.getStepSound(), SoundCategory.PLAYERS,
				group.getVolume() * 0.35f, group.getPitch(), false);
		if (debug) {
			for (int i = 0; i < 4; i++) {
				client.world.addParticle(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.1 + i * 0.3, pos.z, 0.0, 0.02, 0.0);
			}
		}
	}

	private void end(ClientPlayerEntity player, String why) {
		if (debug) {
			player.sendMessage(Text.literal("[Peripheral] running_feet ended: " + why).formatted(Formatting.GRAY), false);
		}
		stop();
	}

	private static Vec3d behind(ClientPlayerEntity player) {
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d back = new Vec3d(-look.x, 0, -look.z);
		if (back.lengthSquared() < 1.0E-4) {
			back = Vec3d.of(player.getHorizontalFacing().getOpposite().getVector());
		}
		return back.normalize();
	}
}
