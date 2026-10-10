package net.bored.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-world data saved with the overworld: where the other base stands and how far it has been built. There is
 * only ever one, and it keeps growing a stage at a time until it's finished.
 */
public class PeripheralWorldState extends PersistentState {
	private static final String ID = "peripheral";
	private static final int MAX_PLACED = 1024;

	/** The other base's door, or null if it hasn't been started. */
	private BlockPos otherBase;
	/** Centre of the cabin's floor and the way its door faces. Null for a base built before it could grow. */
	private BlockPos otherBaseCentre;
	private Direction otherBaseFront;
	private int otherBaseStage;
	/** World time at which the next stage is due. */
	private long otherBaseNextStage;
	/** What the base is built from: wood name, stone block id, and the animal kept out front (empty for a farm). */
	private String otherBaseWood = "";
	private String otherBaseStone = "";
	private String otherBaseYard = "";
	/**
	 * How the surface around the base looked, as the owner knows it: block id by packed position, air left out.
	 * A block put back where the same block used to be isn't noticed. Empty until the first snapshot is taken.
	 */
	private final Map<Long, String> baseSnapshot = new HashMap<>();
	/** Blocks players placed around the other base, with the block that was placed, waiting to be tidied away. */
	private final Map<BlockPos, String> placedNearBase = new LinkedHashMap<>();

	public static PeripheralWorldState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(PeripheralWorldState::fromNbt, PeripheralWorldState::new, ID);
	}

	private static PeripheralWorldState fromNbt(NbtCompound nbt) {
		PeripheralWorldState state = new PeripheralWorldState();
		if (nbt.contains("otherBase")) {
			state.otherBase = NbtHelper.toBlockPos(nbt.getCompound("otherBase"));
		}
		if (nbt.contains("otherBaseCentre")) {
			state.otherBaseCentre = NbtHelper.toBlockPos(nbt.getCompound("otherBaseCentre"));
			state.otherBaseFront = Direction.byName(nbt.getString("otherBaseFront"));
		}
		state.otherBaseStage = nbt.getInt("otherBaseStage");
		state.otherBaseNextStage = nbt.getLong("otherBaseNextStage");
		state.otherBaseWood = nbt.getString("otherBaseWood");
		state.otherBaseStone = nbt.getString("otherBaseStone");
		state.otherBaseYard = nbt.getString("otherBaseYard");
		NbtList palette = nbt.getList("snapshotPalette", NbtElement.STRING_TYPE);
		long[] positions = nbt.getLongArray("snapshotPositions");
		int[] blocks = nbt.getIntArray("snapshotBlocks");
		for (int i = 0; i < Math.min(positions.length, blocks.length); i++) {
			if (blocks[i] >= 0 && blocks[i] < palette.size()) {
				state.baseSnapshot.put(positions[i], palette.getString(blocks[i]));
			}
		}
		NbtList placed = nbt.getList("placedNearBase", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < placed.size(); i++) {
			NbtCompound entry = placed.getCompound(i);
			state.placedNearBase.put(NbtHelper.toBlockPos(entry.getCompound("pos")), entry.getString("block"));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		if (otherBase != null) {
			nbt.put("otherBase", NbtHelper.fromBlockPos(otherBase));
		}
		if (otherBaseCentre != null && otherBaseFront != null) {
			nbt.put("otherBaseCentre", NbtHelper.fromBlockPos(otherBaseCentre));
			nbt.putString("otherBaseFront", otherBaseFront.getName());
		}
		nbt.putInt("otherBaseStage", otherBaseStage);
		nbt.putLong("otherBaseNextStage", otherBaseNextStage);
		nbt.putString("otherBaseWood", otherBaseWood);
		nbt.putString("otherBaseStone", otherBaseStone);
		nbt.putString("otherBaseYard", otherBaseYard);
		List<String> palette = new ArrayList<>();
		Map<String, Integer> paletteIndex = new HashMap<>();
		long[] positions = new long[baseSnapshot.size()];
		int[] blocks = new int[baseSnapshot.size()];
		int i = 0;
		for (Map.Entry<Long, String> entry : baseSnapshot.entrySet()) {
			positions[i] = entry.getKey();
			blocks[i] = paletteIndex.computeIfAbsent(entry.getValue(), block -> {
				palette.add(block);
				return palette.size() - 1;
			});
			i++;
		}
		NbtList paletteNbt = new NbtList();
		for (String block : palette) {
			paletteNbt.add(NbtString.of(block));
		}
		nbt.put("snapshotPalette", paletteNbt);
		nbt.putLongArray("snapshotPositions", positions);
		nbt.putIntArray("snapshotBlocks", blocks);
		NbtList placed = new NbtList();
		placedNearBase.forEach((pos, block) -> {
			NbtCompound entry = new NbtCompound();
			entry.put("pos", NbtHelper.fromBlockPos(pos));
			entry.putString("block", block);
			placed.add(entry);
		});
		nbt.put("placedNearBase", placed);
		return nbt;
	}

	public BlockPos otherBase() {
		return otherBase;
	}

	public BlockPos otherBaseCentre() {
		return otherBaseCentre;
	}

	public Direction otherBaseFront() {
		return otherBaseFront;
	}

	public int otherBaseStage() {
		return otherBaseStage;
	}

	public long otherBaseNextStage() {
		return otherBaseNextStage;
	}

	public void setOtherBase(BlockPos door, BlockPos centre, Direction front, int stage, long nextStage) {
		otherBase = door.toImmutable();
		otherBaseCentre = centre.toImmutable();
		otherBaseFront = front;
		otherBaseStage = stage;
		otherBaseNextStage = nextStage;
		markDirty();
	}

	/** Forgets the other base so a new one can be started. Its blocks stay where they are. */
	public void clearOtherBase() {
		otherBase = null;
		otherBaseCentre = null;
		otherBaseFront = null;
		otherBaseStage = 0;
		otherBaseNextStage = 0L;
		placedNearBase.clear();
		baseSnapshot.clear();
		otherBaseWood = "";
		otherBaseStone = "";
		otherBaseYard = "";
		markDirty();
	}

	public String otherBaseWood() {
		return otherBaseWood;
	}

	public String otherBaseStone() {
		return otherBaseStone;
	}

	public String otherBaseYard() {
		return otherBaseYard;
	}

	public void setOtherBaseStyle(String wood, String stone, String yard) {
		otherBaseWood = wood;
		otherBaseStone = stone;
		otherBaseYard = yard;
		markDirty();
	}

	/** Blocks placed by players near the other base that haven't been tidied yet. */
	public Map<BlockPos, String> placedNearBase() {
		return Collections.unmodifiableMap(placedNearBase);
	}

	public void addPlacedNearBase(BlockPos pos, String block) {
		if (placedNearBase.size() < MAX_PLACED || placedNearBase.containsKey(pos)) {
			placedNearBase.put(pos.toImmutable(), block);
			markDirty();
		}
	}

	public boolean hasBaseSnapshot() {
		return !baseSnapshot.isEmpty();
	}

	/** The block the snapshot remembers at this position, or null for air (or nothing remembered). */
	public String snapshotBlock(BlockPos pos) {
		return baseSnapshot.get(pos.asLong());
	}

	public void setBaseSnapshot(Map<Long, String> snapshot) {
		baseSnapshot.clear();
		baseSnapshot.putAll(snapshot);
		markDirty();
	}

	public void removePlacedNearBase(BlockPos pos) {
		if (placedNearBase.remove(pos) != null) {
			markDirty();
		}
	}

	public void setOtherBaseStage(int stage, long nextStage) {
		otherBaseStage = stage;
		otherBaseNextStage = nextStage;
		markDirty();
	}
}
