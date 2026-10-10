package net.bored.effect;

import net.bored.world.OtherPlayerState;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A book and quill in the other base's chest, one page per day, about what <em>you</em> did: what you mined, what you
 * killed, how far you went, whether you died or slept. It's written from the players' own statistics, and only on
 * days the other you is in the game to write it. Take the book and a new copy is there the next day.
 */
final class OtherJournal {
	private static final Block[] STONE = {Blocks.STONE, Blocks.DEEPSLATE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF};
	private static final Block[] IRON = {Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE};
	private static final Block[] COAL = {Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE};
	private static final Block[] DIAMOND = {Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE};

	private OtherJournal() {
	}

	static void tick(MinecraftServer server, ServerWorld world, OtherPlayerState other, OtherBaseBuilder.Layout layout) {
		long day = world.getTimeOfDay() / 24000L;
		if (other.lastJournalDay < 0) {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				other.statBaselines.put(player.getUuid(), stats(player));
			}
			other.lastJournalDay = day;
			other.markDirty();
			return;
		}
		if (day <= other.lastJournalDay) {
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			NbtCompound now = stats(player);
			NbtCompound before = other.statBaselines.get(player.getUuid());
			other.statBaselines.put(player.getUuid(), now);
			if (before != null) {
				other.addJournal(entry(day, before, now));
			}
		}
		other.lastJournalDay = day;
		other.markDirty();
		OtherBaseBuilder.loadArea(world, layout);
		writeBook(world, layout, other.journal);
	}

	private static NbtCompound stats(ServerPlayerEntity player) {
		ServerStatHandler handler = player.getStatHandler();
		NbtCompound stats = new NbtCompound();
		stats.putInt("stone", mined(handler, STONE));
		stats.putInt("iron", mined(handler, IRON));
		stats.putInt("coal", mined(handler, COAL));
		stats.putInt("diamond", mined(handler, DIAMOND));
		stats.putInt("kills", custom(handler, Stats.MOB_KILLS));
		stats.putInt("deaths", custom(handler, Stats.DEATHS));
		stats.putInt("sleeps", custom(handler, Stats.SLEEP_IN_BED));
		stats.putInt("walk", custom(handler, Stats.WALK_ONE_CM) + custom(handler, Stats.SPRINT_ONE_CM));
		return stats;
	}

	private static int mined(ServerStatHandler handler, Block[] blocks) {
		int total = 0;
		for (Block block : blocks) {
			total += handler.getStat(Stats.MINED.getOrCreateStat(block));
		}
		return total;
	}

	private static int custom(ServerStatHandler handler, Identifier stat) {
		return handler.getStat(Stats.CUSTOM.getOrCreateStat(stat));
	}

	/** "Day 14. You mined 52 stone and 3 iron ore. You killed 4 mobs. You walked 1.2 km. You slept." */
	private static String entry(long day, NbtCompound before, NbtCompound now) {
		int stone = diff(before, now, "stone");
		int iron = diff(before, now, "iron");
		int coal = diff(before, now, "coal");
		int diamond = diff(before, now, "diamond");
		int kills = diff(before, now, "kills");
		int deaths = diff(before, now, "deaths");
		int sleeps = diff(before, now, "sleeps");
		int walkCm = diff(before, now, "walk");

		StringBuilder text = new StringBuilder("Day ").append(day + 1).append(".\n\n");
		List<String> mined = new ArrayList<>();
		if (stone > 0) mined.add(stone + " stone");
		if (coal > 0) mined.add(coal + " coal");
		if (iron > 0) mined.add(iron + " iron ore");
		if (diamond > 0) mined.add(diamond + (diamond == 1 ? " diamond" : " diamonds"));
		int lines = 0;
		if (!mined.isEmpty()) {
			text.append("You mined ").append(join(mined)).append(".\n");
			lines++;
		}
		if (kills > 0) {
			text.append("You killed ").append(kills).append(kills == 1 ? " thing.\n" : " things.\n");
			lines++;
		}
		if (walkCm >= 10000) {
			text.append(String.format("You walked %.1f km.\n", walkCm / 100000.0));
			lines++;
		}
		if (deaths > 0) {
			text.append(deaths == 1 ? "You died.\n" : "You died " + deaths + " times.\n");
			lines++;
		}
		if (sleeps > 0) {
			text.append("You slept.\n");
			lines++;
		}
		if (lines == 0) {
			text.append("You stayed where you were.\n");
		}
		return text.toString();
	}

	private static int diff(NbtCompound before, NbtCompound now, String key) {
		return Math.max(0, now.getInt(key) - before.getInt(key));
	}

	private static String join(List<String> parts) {
		if (parts.size() == 1) {
			return parts.get(0);
		}
		return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
	}

	/** Rewrites the book in the base's chests with every entry, or leaves a new one if it's gone. */
	private static void writeBook(ServerWorld world, OtherBaseBuilder.Layout layout, List<String> entries) {
		NbtList pages = new NbtList();
		for (String entry : entries) {
			pages.add(NbtString.of(entry));
		}
		for (OtherBaseTidy.Storage storage : OtherBaseTidy.storages(world, layout)) {
			for (BlockPos pos : storage.positions()) {
				if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
					for (int i = 0; i < chest.size(); i++) {
						ItemStack stack = chest.getStack(i);
						if (stack.isOf(Items.WRITABLE_BOOK)) {
							stack.getOrCreateNbt().put("pages", pages);
							chest.markDirty();
							return;
						}
					}
				}
			}
		}
		ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
		book.getOrCreateNbt().put("pages", pages);
		OtherBaseTidy.store(world, layout, List.of(book));
	}
}
