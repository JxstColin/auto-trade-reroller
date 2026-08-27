package de.jxstcolin.client;

import net.minecraft.client.Minecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import de.jxstcolin.client.command.AtrCommand;
import de.jxstcolin.client.reroll.RerollSession;

public class AutoTradeRerollerClient implements ClientModInitializer {
	private static RerollSession session;

	@Override
	public void onInitializeClient() {
		AtrKeys.register();
		ClientCommandRegistrationCallback.EVENT.register(new AtrCommand());
		ClientTickEvents.END_CLIENT_TICK.register(AutoTradeRerollerClient::tick);
	}

	public static boolean isRunning() {
		return session != null;
	}

	public static void startSession(RerollSession newSession) {
		session = newSession;
	}

	public static void stopSession() {
		RerollSession running = session;
		session = null;

		if (running != null) {
			running.cancel();
		}
	}

	private static void tick(Minecraft client) {
		boolean continueRequested = AtrKeys.consumeContinue();
		RerollSession running = session;

		if (running == null) {
			return;
		}

		if (continueRequested) {
			running.requestContinue();
		}

		running.tick(client);

		if (running.isFinished() && session == running) {
			session = null;
		}
	}
}
