package net.bored.client.effect;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** The skin and model the figure wears in the current world, as the server told us. */
public final class FigureSkin {
	private static UUID fallbackId = new UUID(0L, 0L);
	private static Identifier texture;
	private static boolean slim;
	private static PlayerEntityModel<AbstractClientPlayerEntity> wideModel;
	private static PlayerEntityModel<AbstractClientPlayerEntity> slimModel;

	private FigureSkin() {
	}

	public static void apply(MinecraftClient client, String textures, String signature, UUID fallback) {
		fallbackId = fallback;
		useDefault();
		if (textures.isEmpty()) {
			return;
		}
		GameProfile profile = new GameProfile(fallback, "figure");
		profile.getProperties().put("textures", new Property("textures", textures, signature.isEmpty() ? null : signature));
		client.getSkinProvider().loadSkin(profile, (type, id, profileTexture) -> {
			if (type == MinecraftProfileTexture.Type.SKIN) {
				texture = id;
				slim = "slim".equals(profileTexture.getMetadata("model"));
			}
		}, false);
	}

	public static void reset() {
		fallbackId = new UUID(0L, 0L);
		texture = null;
	}

	private static void useDefault() {
		texture = DefaultSkinHelper.getTexture(fallbackId);
		slim = "slim".equals(DefaultSkinHelper.getModel(fallbackId));
	}

	public static Identifier texture() {
		if (texture == null) {
			useDefault();
		}
		return texture;
	}

	public static PlayerEntityModel<AbstractClientPlayerEntity> model(MinecraftClient client) {
		if (slim) {
			if (slimModel == null) {
				slimModel = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER_SLIM), true);
			}
			return slimModel;
		}
		if (wideModel == null) {
			wideModel = new PlayerEntityModel<>(client.getEntityModelLoader().getModelPart(EntityModelLayers.PLAYER), false);
		}
		return wideModel;
	}
}
