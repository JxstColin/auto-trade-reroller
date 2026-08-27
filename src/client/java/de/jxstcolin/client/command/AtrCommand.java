package de.jxstcolin.client.command;

import java.util.Optional;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import de.jxstcolin.client.AutoTradeRerollerClient;
import de.jxstcolin.client.reroll.RerollSession;
import de.jxstcolin.client.reroll.Workstations;
import de.jxstcolin.client.util.Chat;

public final class AtrCommand implements ClientCommandRegistrationCallback {
	@Override
	public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(ClientCommands.literal("atr")

				.requires(FabricClientCommandSource::attended)
				.then(ClientCommands.literal("start")
						.then(ClientCommands.argument("block", IdentifierArgument.id())
								.suggests((context, builder) ->
										SharedSuggestionProvider.suggestResource(Workstations.SUGGESTED, builder))
								.executes(context ->
										start(context.getSource(), context.getArgument("block", Identifier.class)))))
				.then(ClientCommands.literal("stop")
						.executes(context -> stop())));
	}

	private static int start(FabricClientCommandSource source, Identifier blockId) {
		Minecraft client = source.getClient();

		if (AutoTradeRerollerClient.isRunning()) {
			Chat.error(Component.literal("Already running - use /atr stop first."));
			return 0;
		}

		Optional<Holder.Reference<Block>> entry = BuiltInRegistries.BLOCK.get(blockId);

		if (entry.isEmpty()) {
			Chat.error(Component.literal("There is no block called " + blockId + "."));
			return 0;
		}

		Block workstation = entry.get().value();

		BlockPos pos = RerollSession.findWorkstation(client, workstation);

		if (pos == null) {
			Chat.error(Component.empty()
					.append(Component.literal("No "))
					.append(workstation.getName())
					.append(Component.literal(" in front of you. Put one down one block ahead, or look right at it.")));
			return 0;
		}

		Villager villager = RerollSession.findVillager(client);

		if (villager == null) {
			Chat.error(Component.literal("No villager in front of you. Look at the villager you want to reroll."));
			return 0;
		}

		AutoTradeRerollerClient.startSession(new RerollSession(source.getPlayer(), workstation, pos, villager));

		Chat.info(Component.empty()
				.append(Component.literal("Rerolling "))
				.append(workstation.getName())
				.append(Component.literal(" at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "."))
				.append(Component.literal(" Pausing after every roll.").withStyle(ChatFormatting.GRAY)));

		if (!Workstations.isJobSite(blockId)) {
			Chat.info(Component.empty()
					.append(workstation.getName())
					.append(Component.literal(" is not a villager job site block, so this will probably do nothing."))
					.withStyle(ChatFormatting.YELLOW));
		}

		return 1;
	}

	private static int stop() {
		if (!AutoTradeRerollerClient.isRunning()) {
			Chat.error(Component.literal("Nothing is running."));
			return 0;
		}

		AutoTradeRerollerClient.stopSession();
		return 1;
	}
}
