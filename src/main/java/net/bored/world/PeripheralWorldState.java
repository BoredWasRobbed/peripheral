package net.bored.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

/**
 * Per-world data saved with the overworld: where the other base was built. There is only ever one, and once it
 * stands it is left alone like any other build.
 */
public class PeripheralWorldState extends PersistentState {
	private static final String ID = "peripheral";

	private BlockPos otherBase;

	public static PeripheralWorldState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(PeripheralWorldState::fromNbt, PeripheralWorldState::new, ID);
	}

	private static PeripheralWorldState fromNbt(NbtCompound nbt) {
		PeripheralWorldState state = new PeripheralWorldState();
		if (nbt.contains("otherBase")) {
			state.otherBase = NbtHelper.toBlockPos(nbt.getCompound("otherBase"));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		if (otherBase != null) {
			nbt.put("otherBase", NbtHelper.fromBlockPos(otherBase));
		}
		return nbt;
	}

	/** The other base's door, or null if it hasn't been built in this world. */
	public BlockPos otherBase() {
		return otherBase;
	}

	public void setOtherBase(BlockPos pos) {
		otherBase = pos.toImmutable();
		markDirty();
	}
}
