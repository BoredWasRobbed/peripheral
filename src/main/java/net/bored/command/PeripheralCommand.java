package net.bored.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.bored.config.PeripheralConfig;
import net.bored.effect.OtherBaseBuilder;
import net.bored.effect.PresenceEffect;
import net.bored.effect.PresenceEffects;
import net.bored.presence.PresenceDirector;
import net.bored.presence.UneaseHolder;
import net.bored.world.PeripheralWorldState;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Operator tools for playtesting:
 * <pre>
 * /peripheral unease [player]              show unease
 * /peripheral unease set &lt;value&gt; [player]  set unease (0 to 100)
 * /peripheral trigger &lt;effect&gt; [player]    fire an effect now
 * /peripheral otherbase                    where the other base is and how far along
 * /peripheral otherbase grow               build its next stage now
 * /peripheral otherbase reset              forget it (blocks stay) so a new one can start
 * /peripheral debug                        toggle the on-screen readout
 * /peripheral reload                       reload config/peripheral.json
 * </pre>
 */
public final class PeripheralCommand {
	private PeripheralCommand() {
	}

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("peripheral")
				.requires(source -> source.hasPermissionLevel(2))
				.then(literal("unease")
						.executes(ctx -> showUnease(ctx, ctx.getSource().getPlayerOrThrow()))
						.then(argument("player", EntityArgumentType.player())
								.executes(ctx -> showUnease(ctx, EntityArgumentType.getPlayer(ctx, "player"))))
						.then(literal("set")
								.then(argument("value", FloatArgumentType.floatArg(0, 100))
										.executes(ctx -> setUnease(ctx, ctx.getSource().getPlayerOrThrow()))
										.then(argument("player", EntityArgumentType.player())
												.executes(ctx -> setUnease(ctx, EntityArgumentType.getPlayer(ctx, "player")))))))
				.then(literal("trigger")
						.then(argument("effect", StringArgumentType.word())
								.suggests((ctx, builder) -> CommandSource.suggestMatching(
										PresenceEffects.all().stream().map(PresenceEffect::name), builder))
								.executes(ctx -> trigger(ctx, ctx.getSource().getPlayerOrThrow()))
								.then(argument("player", EntityArgumentType.player())
										.executes(ctx -> trigger(ctx, EntityArgumentType.getPlayer(ctx, "player"))))))
				.then(literal("otherbase")
						.executes(PeripheralCommand::showOtherBase)
						.then(literal("grow")
								.executes(PeripheralCommand::growOtherBase))
						.then(literal("reset")
								.executes(ctx -> {
									PeripheralWorldState.get(ctx.getSource().getServer()).clearOtherBase();
									ctx.getSource().sendFeedback(() -> Text.literal(
											"Forgot the other base; its blocks are still there. A new one can now be started"), true);
									return 1;
								})))
				.then(literal("debug")
						.executes(PeripheralCommand::toggleDebug))
				.then(literal("reload")
						.executes(ctx -> {
							PeripheralConfig.load();
							ctx.getSource().sendFeedback(() -> Text.literal("Reloaded Peripheral config"), true);
							return 1;
						})));
	}

	private static int showUnease(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
		float unease = ((UneaseHolder) player).peripheral$getUnease();
		ctx.getSource().sendFeedback(() -> Text.literal(String.format("%s: unease %.1f (tier %d)",
				player.getName().getString(), unease, PresenceDirector.tierFor(unease))), false);
		return Math.round(unease);
	}

	private static int setUnease(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
		float value = FloatArgumentType.getFloat(ctx, "value");
		((UneaseHolder) player).peripheral$setUnease(value);
		ctx.getSource().sendFeedback(() -> Text.literal(String.format("Set %s's unease to %.1f", player.getName().getString(), value)), true);
		return 1;
	}

	private static int trigger(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity player) {
		String name = StringArgumentType.getString(ctx, "effect");
		PresenceEffect effect = PresenceEffects.get(name);
		if (effect == null) {
			ctx.getSource().sendError(Text.literal("Unknown effect: " + name));
			return 0;
		}
		if (!PresenceDirector.INSTANCE.forceEffect(player, effect)) {
			ctx.getSource().sendError(Text.literal(name + " found nothing to work with here (no container nearby, no rock, ...)"));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.literal("Triggered " + name + " for " + player.getName().getString()), true);
		return 1;
	}

	private static int showOtherBase(CommandContext<ServerCommandSource> ctx) {
		ServerCommandSource source = ctx.getSource();
		PeripheralWorldState state = PeripheralWorldState.get(source.getServer());
		BlockPos door = state.otherBase();
		if (door == null) {
			source.sendFeedback(() -> Text.literal("The other base hasn't been started in this world"), false);
			return 0;
		}
		int stage = state.otherBaseStage();
		String coords = door.getX() + " " + door.getY() + " " + door.getZ();
		String next = stage >= OtherBaseBuilder.MAX_STAGE ? "finished"
				: String.format("next stage in %ds (when nobody is within 40 blocks or looking)",
						Math.max(0, (state.otherBaseNextStage() - source.getServer().getOverworld().getTime()) / 20));
		source.sendFeedback(() -> Text.literal(String.format("The other base: stage %d/%d (%s), door at ",
						stage, OtherBaseBuilder.MAX_STAGE, OtherBaseBuilder.stageName(stage)))
				.append(Text.literal("[" + coords + "]").styled(style -> style.withColor(Formatting.AQUA).withUnderline(true)
						.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp @s " + coords))))
				.append(Text.literal(", " + next)), false);
		return stage;
	}

	private static int growOtherBase(CommandContext<ServerCommandSource> ctx) {
		int stage = OtherBaseBuilder.forceGrow(ctx.getSource().getServer());
		if (stage < 0) {
			ctx.getSource().sendError(Text.literal("Can't grow the other base: not started, already finished, or not loaded"));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.literal("The other base grew to stage " + stage + ": " + OtherBaseBuilder.stageName(stage)), true);
		return stage;
	}

	private static int toggleDebug(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
		boolean on = PresenceDirector.INSTANCE.toggleDebug(ctx.getSource().getPlayerOrThrow());
		ctx.getSource().sendFeedback(() -> Text.literal("Peripheral debug readout " + (on ? "on" : "off")), false);
		return 1;
	}
}
