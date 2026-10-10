package net.bored.client.effect;

import net.bored.presence.Observer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * The figure: drawn only on this player's screen, wearing the world's borrowed skin. It never exists on the server,
 * so nobody else can see it, and it is gone the moment the player looks straight at it or comes close.
 */
public final class Figure {
	public static final Figure INSTANCE = new Figure();

	private static final float EYE_HEIGHT = 1.62f;

	private final Random random = Random.create();

	private Vec3d pos;
	private Mode mode;
	private int age;
	private int focusedTicks;
	private int unseenTicks;
	private boolean debug;

	private Figure() {
	}

	private enum Mode {
		/** Out at the edge of view, 20 to 40 blocks away. */
		WATCHER("the_watcher", 30 * 20, 15.0, 0, 16.0),
		/** Right behind the player, only caught by a quick turn. */
		BEHIND("behind_you", 3 * 20, 40.0, 2, 1.0);

		final String name;
		final int maxAge;
		/** Looking within this many degrees of it makes it vanish. */
		final double vanishAngle;
		/** How many ticks it survives being looked at, so a fast turn still catches a glimpse. */
		final int graceTicks;
		/** Coming closer than this makes it vanish. */
		final double vanishDistance;

		Mode(String name, int maxAge, double vanishAngle, int graceTicks, double vanishDistance) {
			this.name = name;
			this.maxAge = maxAge;
			this.vanishAngle = vanishAngle;
			this.graceTicks = graceTicks;
			this.vanishDistance = vanishDistance;
		}
	}

	public void showWatcher(MinecraftClient client, boolean debug) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		// Off to one side of where they're looking, inside a normal field of view but away from the centre.
		float lookYaw = player.getYaw();
		for (int attempt = 0; attempt < 40; attempt++) {
			float side = random.nextBoolean() ? 1.0f : -1.0f;
			float yaw = lookYaw + side * (35.0f + random.nextFloat() * 15.0f);
			double distance = 20.0 + random.nextDouble() * 20.0;
			double x = player.getX() - Math.sin(Math.toRadians(yaw)) * distance;
			double z = player.getZ() + Math.cos(Math.toRadians(yaw)) * distance;
			Vec3d feet = ground(client.world, x, player.getY(), z, 10);
			if (feet == null) {
				continue;
			}
			Vec3d head = feet.add(0, EYE_HEIGHT, 0);
			double angle = Observer.angleTo(player, head);
			if (angle > 25.0 && angle < 55.0 && Observer.hasLineOfSight(player, head)) {
				show(feet, Mode.WATCHER, debug, player);
				return;
			}
		}
		debugMessage(player, debug, "the_watcher found nowhere to stand in view");
	}

	public void showBehind(MinecraftClient client, boolean debug) {
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			return;
		}
		Vec3d look = player.getRotationVec(1.0f);
		Vec3d back = new Vec3d(-look.x, 0, -look.z);
		if (back.lengthSquared() < 1.0E-4) {
			back = Vec3d.of(player.getHorizontalFacing().getOpposite().getVector());
		}
		Vec3d target = player.getPos().add(back.normalize().multiply(2.5));
		Vec3d feet = ground(client.world, target.x, player.getY(), target.z, 2);
		if (feet == null) {
			debugMessage(player, debug, "behind_you found no room behind you");
			return;
		}
		show(feet, Mode.BEHIND, debug, player);
	}

	private void show(Vec3d feet, Mode mode, boolean debug, ClientPlayerEntity player) {
		this.pos = feet;
		this.mode = mode;
		this.debug = debug;
		this.age = 0;
		this.focusedTicks = 0;
		this.unseenTicks = 0;
		if (debug) {
			BlockPos block = BlockPos.ofFloored(feet);
			String coords = block.getX() + " " + block.getY() + " " + block.getZ();
			player.sendMessage(Text.literal("[Peripheral] ").formatted(Formatting.DARK_RED)
					.append(Text.literal(mode.name + ": figure at ").formatted(Formatting.GRAY))
					.append(Text.literal("[" + coords + "]").styled(style -> style.withColor(Formatting.AQUA).withUnderline(true)
							.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + coords))))
					.append(Text.literal(String.format(" %dm away", Math.round(feet.distanceTo(player.getPos())))).formatted(Formatting.GRAY)), false);
		}
	}

	public void hide() {
		pos = null;
	}

	public void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (pos == null) {
			return;
		}
		if (player == null || client.world == null) {
			hide();
			return;
		}

		age++;
		Vec3d head = pos.add(0, EYE_HEIGHT, 0);
		double angle = Observer.angleTo(player, head);
		boolean visible = angle < Observer.VIEW_DEGREES && Observer.hasLineOfSight(player, head);

		if (angle < mode.vanishAngle && visible) {
			if (++focusedTicks > mode.graceTicks) {
				vanish(player, "you looked at it");
				return;
			}
		}
		if (player.getPos().distanceTo(pos) < mode.vanishDistance) {
			vanish(player, "you came too close");
			return;
		}
		// Once it has slipped out of view for a couple of seconds, it isn't there when they look back.
		unseenTicks = visible ? 0 : unseenTicks + 1;
		if (mode == Mode.WATCHER && unseenTicks > 40) {
			vanish(player, "it was out of view");
			return;
		}
		if (age > mode.maxAge) {
			vanish(player, "it timed out");
			return;
		}

		if (debug && age % 10 == 0) {
			client.world.addParticle(ParticleTypes.END_ROD, pos.x, pos.y + 2.4, pos.z, 0.0, 0.05, 0.0);
		}
	}

	private void vanish(ClientPlayerEntity player, String why) {
		debugMessage(player, debug, mode.name + " vanished: " + why);
		hide();
	}

	public void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		VertexConsumerProvider consumers = context.consumers();
		if (pos == null || client.player == null || client.world == null || consumers == null) {
			return;
		}

		// Face the player: the body turned toward them, the head tilted to meet their eyes.
		Vec3d eyes = client.player.getEyePos();
		Vec3d delta = eyes.subtract(pos.add(0, EYE_HEIGHT, 0));
		float bodyYaw = (float) Math.toDegrees(MathHelper.atan2(delta.z, delta.x)) - 90.0f;
		float headPitch = (float) -Math.toDegrees(MathHelper.atan2(delta.y, delta.horizontalLength()));

		PlayerEntityModel<AbstractClientPlayerEntity> model = FigureSkin.model(client);
		model.child = false;
		model.sneaking = false;
		model.riding = false;
		model.head.yaw = 0.0f;
		model.head.pitch = (float) Math.toRadians(headPitch);
		model.hat.copyTransform(model.head);

		MatrixStack matrices = context.matrixStack();
		Vec3d camera = context.camera().getPos();
		matrices.push();
		matrices.translate(pos.x - camera.x, pos.y - camera.y, pos.z - camera.z);
		// The same transforms the vanilla player renderer applies.
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
		matrices.scale(-1.0f, -1.0f, 1.0f);
		matrices.scale(0.9375f, 0.9375f, 0.9375f);
		matrices.translate(0.0f, -1.501f, 0.0f);

		int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(pos.x, pos.y + 1.0, pos.z));
		model.render(matrices, consumers.getBuffer(RenderLayer.getEntityTranslucent(FigureSkin.texture())),
				light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, 1.0f);
		matrices.pop();
	}

	/** Feet position on solid ground near {@code y}, with two blocks of room above, or null. */
	private static Vec3d ground(ClientWorld world, double x, double y, double z, int range) {
		BlockPos.Mutable pos = BlockPos.ofFloored(x, y + range, z).mutableCopy();
		for (int i = 0; i <= range * 2; i++) {
			BlockPos below = pos.down();
			if (world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)
					&& world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
					&& world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
					&& world.getFluidState(pos).isEmpty()) {
				return new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			}
			pos.move(Direction.DOWN);
		}
		return null;
	}

	private static void debugMessage(ClientPlayerEntity player, boolean debug, String message) {
		if (debug) {
			player.sendMessage(Text.literal("[Peripheral] " + message).formatted(Formatting.GRAY), false);
		}
	}
}
