package net.bored.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.PersistentState;

/**
 * Per-world data saved with the overworld: where the other base stands and how far it has been built. There is
 * only ever one, and it keeps growing a stage at a time until it's finished.
 */
public class PeripheralWorldState extends PersistentState {
	private static final String ID = "peripheral";

	/** The other base's door, or null if it hasn't been started. */
	private BlockPos otherBase;
	/** Centre of the cabin's floor and the way its door faces. Null for a base built before it could grow. */
	private BlockPos otherBaseCentre;
	private Direction otherBaseFront;
	private int otherBaseStage;
	/** World time at which the next stage is due. */
	private long otherBaseNextStage;

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

	public void setOtherBaseStage(int stage, long nextStage) {
		otherBaseStage = stage;
		otherBaseNextStage = nextStage;
		markDirty();
	}
}
