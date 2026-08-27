package de.jxstcolin.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

public final class AtrKeys {
	public static final KeyMapping CONTINUE = new KeyMapping(
			"key.auto-trade-reroller.continue",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_F4,
			KeyMapping.Category.GAMEPLAY);

	private AtrKeys() {
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(CONTINUE);
	}

	public static boolean consumeContinue() {
		boolean pressed = false;

		while (CONTINUE.consumeClick()) {
			pressed = true;
		}

		return pressed;
	}
}
