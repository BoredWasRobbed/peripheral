package net.bored.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.UUID;

/**
 * Per-world data saved with the overworld: the skin the figure wears in this world.
 */
public class PeripheralWorldState extends PersistentState {
	private static final String ID = "peripheral";

	/** Base64 "textures" property of a real player's profile, or empty if none has been fetched. */
	private String skinTextures = "";
	private String skinSignature = "";
	/** Picks one of Minecraft's default skins when no fetched skin is available. */
	private UUID fallbackId = UUID.randomUUID();
	/** Once the figure has been seen, its skin never changes, even if a fetch later succeeds. */
	private boolean figureShown;

	public static PeripheralWorldState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(PeripheralWorldState::fromNbt, PeripheralWorldState::new, ID);
	}

	private static PeripheralWorldState fromNbt(NbtCompound nbt) {
		PeripheralWorldState state = new PeripheralWorldState();
		state.skinTextures = nbt.getString("skinTextures");
		state.skinSignature = nbt.getString("skinSignature");
		if (nbt.containsUuid("fallbackId")) {
			state.fallbackId = nbt.getUuid("fallbackId");
		}
		state.figureShown = nbt.getBoolean("figureShown");
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putString("skinTextures", skinTextures);
		nbt.putString("skinSignature", skinSignature);
		nbt.putUuid("fallbackId", fallbackId);
		nbt.putBoolean("figureShown", figureShown);
		return nbt;
	}

	public String skinTextures() {
		return skinTextures;
	}

	public String skinSignature() {
		return skinSignature;
	}

	public UUID fallbackId() {
		return fallbackId;
	}

	public boolean needsSkin() {
		return skinTextures.isEmpty() && !figureShown;
	}

	public void setSkin(String textures, String signature) {
		skinTextures = textures;
		skinSignature = signature == null ? "" : signature;
		markDirty();
	}

	public void markFigureShown() {
		if (!figureShown) {
			figureShown = true;
			markDirty();
		}
	}
}
