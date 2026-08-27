package de.jxstcolin.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Chat {
	private Chat() {
	}

	public static void info(Component message) {
		send(Component.empty().append(prefix()).append(message));
	}

	public static void error(Component message) {
		send(Component.empty().append(prefix()).append(Component.empty().append(message).withStyle(ChatFormatting.RED)));
	}

	public static void raw(Component message) {
		send(message);
	}

	private static Component prefix() {
		return Component.literal("[ATR] ").withStyle(ChatFormatting.AQUA);
	}

	private static void send(Component message) {
		Minecraft client = Minecraft.getInstance();

		if (client.gui == null) {
			return;
		}

		client.gui.hud.getChat().addClientSystemMessage(message);
	}
}
