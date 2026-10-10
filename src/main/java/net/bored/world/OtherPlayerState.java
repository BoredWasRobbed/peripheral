package net.bored.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-world data for the other you: whether it's in the game right now, and everything its routine has done so far.
 * Saved with the overworld next to {@link PeripheralWorldState}.
 */
public class OtherPlayerState extends PersistentState {
	private static final String ID = "peripheral_other";
	private static final int MAX_JOURNAL = 100;

	// Sessions
	public boolean online;
	/** Whether this session's "joined the game" was shown, so its "left the game" is too. */
	public boolean announced;
	public long sessionEnds;
	public long nextJoin;
	public boolean swappedThisSession;

	// Routine and traces
	public String lastActivity = "";
	public BlockPos treeSite;
	public boolean treeSiteChosen;
	public int pathLength;
	public boolean pathDone;
	public long lastChopDay = -1;
	public int mineDepth;
	/** Height of the mine's entrance above the cabin floor, so its steps line up from one day to the next. */
	public int mineTopOffset;
	public boolean mineDone;
	public int pillars;
	public long lastPillarDay = -1;

	// Progress
	public int toolTier;
	public int armorTier;

	// Mirroring the player
	public int plantingsSeen;
	public boolean wheatFieldDone;

	// Journal
	public long lastJournalDay = -1;
	public final List<String> journal = new ArrayList<>();
	/** Each player's stats when the last journal entry was written, to tell what they did since. */
	public final Map<UUID, NbtCompound> statBaselines = new HashMap<>();

	public static OtherPlayerState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(OtherPlayerState::fromNbt, OtherPlayerState::new, ID);
	}

	private static OtherPlayerState fromNbt(NbtCompound nbt) {
		OtherPlayerState state = new OtherPlayerState();
		state.online = nbt.getBoolean("online");
		state.announced = nbt.getBoolean("announced");
		state.sessionEnds = nbt.getLong("sessionEnds");
		state.nextJoin = nbt.getLong("nextJoin");
		state.swappedThisSession = nbt.getBoolean("swappedThisSession");
		state.lastActivity = nbt.getString("lastActivity");
		if (nbt.contains("treeSite")) {
			state.treeSite = NbtHelper.toBlockPos(nbt.getCompound("treeSite"));
		}
		state.treeSiteChosen = nbt.getBoolean("treeSiteChosen");
		state.pathLength = nbt.getInt("pathLength");
		state.pathDone = nbt.getBoolean("pathDone");
		state.lastChopDay = nbt.contains("lastChopDay") ? nbt.getLong("lastChopDay") : -1;
		state.mineDepth = nbt.getInt("mineDepth");
		state.mineDone = nbt.getBoolean("mineDone");
		state.mineTopOffset = nbt.getInt("mineTopOffset");
		state.pillars = nbt.getInt("pillars");
		state.lastPillarDay = nbt.contains("lastPillarDay") ? nbt.getLong("lastPillarDay") : -1;
		state.toolTier = nbt.getInt("toolTier");
		state.armorTier = nbt.getInt("armorTier");
		state.plantingsSeen = nbt.getInt("plantingsSeen");
		state.wheatFieldDone = nbt.getBoolean("wheatFieldDone");
		state.lastJournalDay = nbt.contains("lastJournalDay") ? nbt.getLong("lastJournalDay") : -1;
		NbtList journal = nbt.getList("journal", NbtElement.STRING_TYPE);
		for (int i = 0; i < journal.size(); i++) {
			state.journal.add(journal.getString(i));
		}
		NbtCompound baselines = nbt.getCompound("statBaselines");
		for (String key : baselines.getKeys()) {
			try {
				state.statBaselines.put(UUID.fromString(key), baselines.getCompound(key));
			} catch (IllegalArgumentException ignored) {
				// Not a player id; skip it.
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putBoolean("online", online);
		nbt.putBoolean("announced", announced);
		nbt.putLong("sessionEnds", sessionEnds);
		nbt.putLong("nextJoin", nextJoin);
		nbt.putBoolean("swappedThisSession", swappedThisSession);
		nbt.putString("lastActivity", lastActivity);
		if (treeSite != null) {
			nbt.put("treeSite", NbtHelper.fromBlockPos(treeSite));
		}
		nbt.putBoolean("treeSiteChosen", treeSiteChosen);
		nbt.putInt("pathLength", pathLength);
		nbt.putBoolean("pathDone", pathDone);
		nbt.putLong("lastChopDay", lastChopDay);
		nbt.putInt("mineDepth", mineDepth);
		nbt.putBoolean("mineDone", mineDone);
		nbt.putInt("mineTopOffset", mineTopOffset);
		nbt.putInt("pillars", pillars);
		nbt.putLong("lastPillarDay", lastPillarDay);
		nbt.putInt("toolTier", toolTier);
		nbt.putInt("armorTier", armorTier);
		nbt.putInt("plantingsSeen", plantingsSeen);
		nbt.putBoolean("wheatFieldDone", wheatFieldDone);
		nbt.putLong("lastJournalDay", lastJournalDay);
		NbtList journalNbt = new NbtList();
		for (String line : journal) {
			journalNbt.add(NbtString.of(line));
		}
		nbt.put("journal", journalNbt);
		NbtCompound baselines = new NbtCompound();
		statBaselines.forEach((id, stats) -> baselines.put(id.toString(), stats));
		nbt.put("statBaselines", baselines);
		return nbt;
	}

	public void addJournal(String line) {
		journal.add(line);
		while (journal.size() > MAX_JOURNAL) {
			journal.remove(0);
		}
		markDirty();
	}

	/** Forgets everything; used when the other base is reset. */
	public void reset() {
		online = false;
		announced = false;
		sessionEnds = 0;
		nextJoin = 0;
		swappedThisSession = false;
		lastActivity = "";
		treeSite = null;
		treeSiteChosen = false;
		pathLength = 0;
		pathDone = false;
		lastChopDay = -1;
		mineDepth = 0;
		mineDone = false;
		mineTopOffset = 0;
		pillars = 0;
		lastPillarDay = -1;
		toolTier = 0;
		armorTier = 0;
		plantingsSeen = 0;
		wheatFieldDone = false;
		lastJournalDay = -1;
		journal.clear();
		statBaselines.clear();
		markDirty();
	}
}
