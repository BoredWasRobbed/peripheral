package net.bored.client.effect;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

/** The figure is another version of the player, so it wears their own skin on their own model. */
public final class FigureSkin {
	private static PlayerEntityModel<AbstractClientPlayerEntity> wideModel;
	private static PlayerEntityModel<AbstractClientPlayerEntity> slimModel;

	private FigureSkin() {
	}

	public static Identifier texture(MinecraftClient client) {
		return client.player != null ? client.player.getSkinTexture() : DefaultSkinHelper.getTexture();
	}

	public static PlayerEntityModel<AbstractClientPlayerEntity> model(MinecraftClient client) {
		if (client.player != null && "slim".equals(client.player.getModel())) {
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
