package net.bored.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

import java.util.Collections;
import java.util.LinkedHashMap;
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
