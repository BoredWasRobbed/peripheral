package net.bored.presence;

import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Shows a debugging player where an effect just happened: a chat line with the coordinates, distance and
 * direction (click it to teleport there), and a column of particles at the spot that only they can see.
 * Does nothing unless the player has the debug readout on or fired the effect with the trigger command.
 */
public final class EffectDebug {
	private static final int BEAM_TICKS = 30 * 20;
	private static final int BEAM_INTERVAL = 10;

	private EffectDebug() {
	}

	/**
	 * @param what short description of what happened there, for example "opened oak door"
	 */
	public static void report(ServerPlayerEntity player, String effect, BlockPos pos, String what) {
		report(player, effect, pos, what, true);
	}

	/** Same, optionally without the particle column (for effects that mark themselves, like the footstep echo). */
	public static void report(ServerPlayerEntity player, String effect, BlockPos pos, String what, boolean beam) {
		PresenceDirector director = PresenceDirector.INSTANCE;
		director.rememberLocation(player, effect, pos);
		if (!director.shouldReport(player)) {
			return;
		}

		String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
		MutableText location = Text.literal("[" + coords + "]").styled(style -> style
				.withColor(Formatting.AQUA)
				.withUnderline(true)
				.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @s " + coords))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Click to teleport"))));
		player.sendMessage(Text.literal("[Peripheral] ").formatted(Formatting.DARK_RED)
				.append(Text.literal(effect + ": ").formatted(Formatting.RED))
				.append(Text.literal(what + " ").formatted(Formatting.GRAY))
				.append(location)
				.append(Text.literal(" " + describe(player, pos)).formatted(Formatting.GRAY)), false);

		if (beam) {
			beam(player, pos);
		}
	}

	public static boolean isReporting(ServerPlayerEntity player) {
		return PresenceDirector.INSTANCE.shouldReport(player);
	}

	/** "18m NE, 3 below" relative to where the player is now. */
	public static String describe(ServerPlayerEntity player, BlockPos pos) {
		Vec3d target = Vec3d.ofCenter(pos);
		double dx = target.x - player.getX();
		double dz = target.z - player.getZ();
		int dy = pos.getY() - player.getBlockPos().getY();
		String height = dy > 1 ? ", " + dy + " above" : dy < -1 ? ", " + -dy + " below" : "";
		return Math.round(Math.hypot(dx, dz)) + "m " + compass(dx, dz) + height;
	}

	private static String compass(double dx, double dz) {
		// Minecraft's north is -Z and east is +X.
		double degrees = Math.toDegrees(Math.atan2(dx, -dz));
		String[] points = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
		int index = (int) Math.round(((degrees + 360.0) % 360.0) / 45.0) % 8;
		return points[index];
	}

	/** A column of particles rising from the spot, visible from far away, for half a minute. */
	private static void beam(ServerPlayerEntity player, BlockPos pos) {
		Vec3d base = Vec3d.ofCenter(pos);
		for (int t = 0; t < BEAM_TICKS; t += BEAM_INTERVAL) {
			PresenceDirector.INSTANCE.scheduler().schedule(t, () -> {
				if (player.isRemoved()) {
					return;
				}
				for (int y = 0; y < 8; y++) {
					player.getServerWorld().spawnParticles(player, ParticleTypes.END_ROD, true,
							base.x, base.y + y * 0.75, base.z, 1, 0.05, 0.05, 0.05, 0.0);
				}
			});
		}
	}
}
