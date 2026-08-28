package de.jxstcolin.client.command;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import de.jxstcolin.client.AutoTradeRerollerClient;
import de.jxstcolin.client.reroll.RerollSession;
import de.jxstcolin.client.reroll.Wish;
import de.jxstcolin.client.reroll.Workstations;
import de.jxstcolin.client.util.Chat;

public final class AtrCommand implements ClientCommandRegistrationCallback {
	private static final double PICKUP_RANGE = 1.4;

	@Override
	public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(ClientCommands.literal("atr")
				.requires(FabricClientCommandSource::attended)
				.then(ClientCommands.literal("start")
						.then(ClientCommands.argument("block", IdentifierArgument.id())
								.suggests((context, builder) ->
										SharedSuggestionProvider.suggestResource(Workstations.SUGGESTED, builder))
								.executes(context -> start(context, null, 0, 0))
								.then(ClientCommands.literal("want")
										.then(ClientCommands.argument("wish", IdentifierArgument.id())
												.suggests(AtrCommand::suggestWishes)
												.executes(context -> start(context, wish(context), 0, 0))
												.then(maxPrice(context ->
														start(context, wish(context), 0, emeralds(context))))
												.then(ClientCommands.argument("level", IntegerArgumentType.integer(1, 255))
														.executes(context ->
																start(context, wish(context), level(context), 0))
														.then(maxPrice(context -> start(context, wish(context),
																level(context), emeralds(context)))))))))
				.then(ClientCommands.literal("stop")
						.executes(context -> stop())));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> maxPrice(Command<FabricClientCommandSource> action) {
		return ClientCommands.literal("max")
				.then(ClientCommands.argument("emeralds", IntegerArgumentType.integer(1, 64))
						.executes(action));
	}

	private static Identifier wish(CommandContext<FabricClientCommandSource> context) {
		return context.getArgument("wish", Identifier.class);
	}

	private static int level(CommandContext<FabricClientCommandSource> context) {
		return IntegerArgumentType.getInteger(context, "level");
	}

	private static int emeralds(CommandContext<FabricClientCommandSource> context) {
		return IntegerArgumentType.getInteger(context, "emeralds");
	}

	private static CompletableFuture<Suggestions> suggestWishes(
			CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
		if (context.getSource().getLevel() == null) {
			return SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet().stream(), builder);
		}

		Stream<Identifier> enchantments = context.getSource().getLevel().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.listElementIds()
				.map(ResourceKey::identifier);
		return SharedSuggestionProvider.suggestResource(
				Stream.concat(enchantments, BuiltInRegistries.ITEM.keySet().stream()), builder);
	}

	private static int start(CommandContext<FabricClientCommandSource> context,
			Identifier wishId, int level, int maxEmeralds) {
		FabricClientCommandSource source = context.getSource();
		Minecraft client = source.getClient();

		if (AutoTradeRerollerClient.isRunning()) {
			Chat.error(Component.literal("Already running - use /atr stop first."));
			return 0;
		}

		Identifier blockId = context.getArgument("block", Identifier.class);
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

		Wish wish = null;

		if (wishId != null) {
			wish = resolveWish(source, wishId, level, maxEmeralds);

			if (wish == null) {
				return 0;
			}
		}

		AutoTradeRerollerClient.startSession(new RerollSession(source.getPlayer(), workstation, pos, villager, wish));

		Chat.info(Component.empty()
				.append(Component.literal("Rerolling "))
				.append(workstation.getName())
				.append(Component.literal(" at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".")));

		if (wish == null) {
			Chat.info(Component.literal("Pausing after every roll.").withStyle(ChatFormatting.GRAY));
		} else {
			Chat.info(Component.empty()
					.append(Component.literal("Rolling until: ").withStyle(ChatFormatting.GRAY))
					.append(wish.describe()));
		}

		warnAboutPickup(source, pos);

		if (!Workstations.isJobSite(blockId)) {
			Chat.info(Component.empty()
					.append(workstation.getName())
					.append(Component.literal(" is not a villager job site block, so this will probably do nothing."))
					.withStyle(ChatFormatting.YELLOW));
		}

		return 1;
	}

	private static Wish resolveWish(FabricClientCommandSource source, Identifier wishId, int level, int maxEmeralds) {
		Optional<Holder.Reference<Enchantment>> enchantment = source.getLevel().registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.get(ResourceKey.create(Registries.ENCHANTMENT, wishId));

		if (enchantment.isPresent()) {
			return new Wish(enchantment.get(), level, null, maxEmeralds);
		}

		Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(wishId);

		if (item.isEmpty()) {
			Chat.error(Component.literal(wishId + " is neither an enchantment nor an item."));
			return null;
		}

		if (level > 0) {
			Chat.info(Component.literal("Ignoring the level - " + wishId + " is an item, not an enchantment.")
					.withStyle(ChatFormatting.YELLOW));
		}

		return new Wish(null, 0, item.get().value(), maxEmeralds);
	}

	private static void warnAboutPickup(FabricClientCommandSource source, BlockPos pos) {
		Vec3 player = source.getPlayer().position();
		Vec3 block = Vec3.atCenterOf(pos);
		double dx = player.x - block.x;
		double dz = player.z - block.z;

		if (Math.sqrt(dx * dx + dz * dz) <= PICKUP_RANGE) {
			return;
		}

		Chat.info(Component.literal("You are far enough away that the drop can land out of pickup range. "
						+ "Step closer, or the loop will wait for you to grab it.")
				.withStyle(ChatFormatting.YELLOW));
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
