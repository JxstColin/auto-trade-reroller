package de.jxstcolin.client.reroll;

import java.util.List;
import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import de.jxstcolin.client.AtrKeys;
import de.jxstcolin.client.util.Chat;
import de.jxstcolin.client.util.TradeFormat;

public final class RerollSession {
	public static final double REACH = 6.0;

	private static final double PICK_RANGE = 4.5;

	private static final int PRINTED_TRADES = 2;
	private static final int PLACE_RETRY_TICKS = 10;
	private static final int PLACE_CONFIRM_TICKS = 5;
	private static final int INTERACT_RETRY_TICKS = 20;
	private static final double DROP_SEARCH_RANGE = 4.0;

	private static final Direction[] PLACEMENT_ORDER = {
			Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
	};

	private final Block workstation;
	private final BlockPos pos;
	private final int villagerId;
	private final Direction breakFace;
	private final int originalSlot;
	private final Wish wish;

	private Phase phase = Phase.BREAK;
	private int phaseTicks;
	private int cycles;
	private boolean finished;
	private boolean continueRequested;
	private boolean dropHintShown;

	public RerollSession(LocalPlayer player, Block workstation, BlockPos pos, Villager villager, Wish wish) {
		this.workstation = workstation;
		this.pos = pos.immutable();
		this.villagerId = villager.getId();
		this.breakFace = Direction.getApproximateNearest(player.getEyePosition().subtract(Vec3.atCenterOf(pos)));
		this.originalSlot = player.getInventory().getSelectedSlot();
		this.wish = wish;
	}

	public BlockPos workstationPos() {
		return pos;
	}

	public Block workstation() {
		return workstation;
	}

	public boolean isFinished() {
		return finished;
	}

	public void requestContinue() {
		continueRequested = true;
	}

	public void cancel() {
		Chat.info(Component.empty()
				.append(Component.literal("Keeping the current trades. ").withStyle(ChatFormatting.GREEN))
				.append(summary()));
		finish();
	}

	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		MultiPlayerGameMode gameMode = client.gameMode;

		if (player == null || level == null || gameMode == null) {
			finish();
			return;
		}

		Villager villager = villager(level);

		if (phase != Phase.AWAIT_DECISION) {
			if (villager == null || !villager.isAlive()) {
				abort(Component.literal("Lost the villager."));
				return;
			}

			if (player.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
				abort(Component.literal("Moved too far away from the job site block."));
				return;
			}
		}

		phaseTicks++;

		if (phase.timeoutTicks > 0 && phaseTicks > phase.timeoutTicks) {
			abort(Component.literal(phase.timeoutMessage));
			return;
		}

		switch (phase) {
			case BREAK -> tickBreak(level, gameMode, player);
			case BREAKING -> tickBreaking(level, gameMode, player);
			case AWAIT_ITEM -> tickAwaitItem(level, gameMode, player);
			case PLACE -> tickPlace(level, gameMode, player);
			case AWAIT_PLACED -> tickAwaitPlaced(level, gameMode, player);
			case INTERACT -> tickInteract(gameMode, player, villager);
			case AWAIT_TRADES -> tickAwaitTrades(gameMode, player, villager);
			case AWAIT_DECISION -> tickAwaitDecision();
		}
	}

	private void tickBreak(ClientLevel level, MultiPlayerGameMode gameMode, LocalPlayer player) {
		if (!standsOnPos(level)) {
			enter(Phase.AWAIT_ITEM);
			return;
		}

		if (!equipBestTool(level, gameMode, player)) {
			return;
		}

		gameMode.startDestroyBlock(pos, breakFace);
		player.swing(InteractionHand.MAIN_HAND);
		enter(Phase.BREAKING);
	}

	private void tickBreaking(ClientLevel level, MultiPlayerGameMode gameMode, LocalPlayer player) {
		if (!standsOnPos(level)) {
			gameMode.stopDestroyBlock();
			enter(Phase.AWAIT_ITEM);
			return;
		}

		gameMode.continueDestroyBlock(pos, breakFace);
		player.swing(InteractionHand.MAIN_HAND);
	}

	private void tickAwaitItem(ClientLevel level, MultiPlayerGameMode gameMode, LocalPlayer player) {
		int slot = inventorySlot(player);

		if (slot < 0) {
			waitOutDrop(level);
			return;
		}

		if (!Inventory.isHotbarSlot(slot)) {
			swapIntoHotbar(gameMode, player, slot);
			return;
		}

		if (player.getInventory().getSelectedSlot() != slot) {
			player.getInventory().setSelectedSlot(slot);
		}

		enter(Phase.PLACE);
	}

	private void waitOutDrop(ClientLevel level) {
		ItemEntity drop = nearbyDrop(level);

		if (drop == null) {
			return;
		}

		phaseTicks = 0;

		if (!dropHintShown) {
			dropHintShown = true;
			Chat.info(Component.empty()
					.append(workstation.getName())
					.append(Component.literal(" landed out of reach at ")
							.withStyle(ChatFormatting.GRAY))
					.append(Component.literal(String.format("%.1f %.1f %.1f",
							drop.getX(), drop.getY(), drop.getZ())).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(". Step over and pick it up, the loop continues by itself.")
							.withStyle(ChatFormatting.GRAY)));
		}
	}

	private ItemEntity nearbyDrop(ClientLevel level) {
		Item item = workstation.asItem();
		AABB area = new AABB(pos).inflate(DROP_SEARCH_RANGE);

		for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
			if (entity.isAlive() && entity.getItem().getItem() == item) {
				return entity;
			}
		}

		return null;
	}

	private void tickPlace(ClientLevel level, MultiPlayerGameMode gameMode, LocalPlayer player) {
		if (!holdingWorkstation(player)) {
			enter(Phase.AWAIT_ITEM);
			return;
		}

		if (!place(level, gameMode, player)) {
			abort(Component.empty()
					.append(Component.literal("Found no solid block to place the "))
					.append(workstation.getName())
					.append(Component.literal(" against.")));
			return;
		}

		enter(Phase.AWAIT_PLACED);
	}

	private void tickAwaitPlaced(ClientLevel level, MultiPlayerGameMode gameMode, LocalPlayer player) {
		if (!standsOnPos(level)) {
			if (!holdingWorkstation(player)) {
				enter(Phase.AWAIT_ITEM);
				return;
			}

			if (phaseTicks % PLACE_RETRY_TICKS == 0) {
				place(level, gameMode, player);
			}

			return;
		}

		if (phaseTicks >= PLACE_CONFIRM_TICKS) {
			enter(Phase.INTERACT);
		}
	}

	private void tickInteract(MultiPlayerGameMode gameMode, LocalPlayer player, Villager villager) {
		interact(gameMode, player, villager);
		enter(Phase.AWAIT_TRADES);
	}

	private void tickAwaitTrades(MultiPlayerGameMode gameMode, LocalPlayer player, Villager villager) {
		if (player.containerMenu instanceof MerchantMenu menu) {
			MerchantOffers offers = menu.getOffers();

			if (offers.isEmpty()) {
				return;
			}

			cycles++;

			if (wish == null) {
				printOffers(villager, menu, offers);
				player.closeContainer();
				enter(Phase.AWAIT_DECISION);
				printDecisionHint();
				return;
			}

			MerchantOffer hit = wish.findIn(offers);
			player.closeContainer();

			if (hit != null) {
				printMatch(villager, menu, offers);
				finish();
				return;
			}

			printMiss();
			enter(Phase.BREAK);
			return;
		}

		if (phaseTicks % INTERACT_RETRY_TICKS == 0) {
			interact(gameMode, player, villager);
		}
	}

	private void tickAwaitDecision() {
		if (continueRequested) {
			enter(Phase.BREAK);
		}
	}

	private boolean place(ClientLevel level, MultiPlayerGameMode gameMode, LocalPlayer player) {
		BlockHitResult hit = placementHit(level);

		if (hit == null) {
			return false;
		}

		InteractionResult result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);

		if (result.consumesAction()) {
			player.swing(InteractionHand.MAIN_HAND);
		}

		return true;
	}

	private void interact(MultiPlayerGameMode gameMode, LocalPlayer player, Villager villager) {
		Vec3 aim = villager.position().add(0.0, villager.getBbHeight() * 0.5, 0.0);
		gameMode.interact(player, villager, new EntityHitResult(villager, aim), InteractionHand.MAIN_HAND);
		player.swing(InteractionHand.MAIN_HAND);
	}

	private BlockHitResult placementHit(ClientLevel level) {
		for (Direction offset : PLACEMENT_ORDER) {
			BlockPos support = pos.relative(offset);
			BlockState state = level.getBlockState(support);

			if (state.isAir() || state.canBeReplaced()) {
				continue;
			}

			Direction face = offset.getOpposite();
			Vec3 center = Vec3.atCenterOf(support);
			Vec3 hit = new Vec3(
					center.x + face.getStepX() * 0.5,
					center.y + face.getStepY() * 0.5,
					center.z + face.getStepZ() * 0.5);
			return new BlockHitResult(hit, face, support, false);
		}

		return null;
	}

	private void printOffers(Villager villager, MerchantMenu menu, MerchantOffers offers) {
		Chat.info(Component.empty()
				.append(Component.literal("Reroll #" + cycles).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
				.append(profession(villager))
				.append(Component.literal(" (level " + menu.getTraderLevel() + ")").withStyle(ChatFormatting.GRAY)));

		int shown = Math.min(PRINTED_TRADES, offers.size());

		for (int index = 0; index < shown; index++) {
			Chat.raw(TradeFormat.offer(index + 1, offers.get(index)));
		}
	}

	private void printMatch(Villager villager, MerchantMenu menu, MerchantOffers offers) {
		Chat.info(Component.empty()
				.append(Component.literal("Match after " + cycles + (cycles == 1 ? " reroll" : " rerolls") + "! ")
						.withStyle(ChatFormatting.GREEN))
				.append(profession(villager))
				.append(Component.literal(" (level " + menu.getTraderLevel() + "), stopped here.")
						.withStyle(ChatFormatting.GRAY)));

		for (int index = 0; index < offers.size(); index++) {
			Chat.raw(TradeFormat.offer(index + 1, offers.get(index)));
		}
	}

	private void printMiss() {
		Chat.info(Component.literal("Reroll #" + cycles + " - no match.").withStyle(ChatFormatting.GRAY));
	}

	private static void printDecisionHint() {
		Chat.info(Component.empty()
				.append(Component.literal("Press ").withStyle(ChatFormatting.GRAY))
				.append(AtrKeys.CONTINUE.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" to roll again, or /atr stop to keep these.")
						.withStyle(ChatFormatting.GRAY)));
	}

	private boolean standsOnPos(ClientLevel level) {
		return level.getBlockState(pos).getBlock() == workstation;
	}

	private Villager villager(ClientLevel level) {
		return level.getEntity(villagerId) instanceof Villager villager ? villager : null;
	}

	private boolean equipBestTool(ClientLevel level, MultiPlayerGameMode gameMode, LocalPlayer player) {
		BlockState state = level.getBlockState(pos);
		Holder<Enchantment> efficiency = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.EFFICIENCY);

		Inventory inventory = player.getInventory();
		int best = -1;
		float bestScore = toolScore(inventory.getItem(inventory.getSelectedSlot()), state, efficiency);

		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			float score = toolScore(inventory.getItem(slot), state, efficiency);

			if (score > bestScore) {
				bestScore = score;
				best = slot;
			}
		}

		if (best < 0) {
			return true;
		}

		if (Inventory.isHotbarSlot(best)) {
			inventory.setSelectedSlot(best);
			return false;
		}

		return swapIntoHotbar(gameMode, player, best);
	}

	private static float toolScore(ItemStack stack, BlockState state, Holder<Enchantment> efficiency) {
		if (state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state)) {
			return 0.0F;
		}

		float speed = stack.getDestroySpeed(state);

		if (speed > 1.0F) {
			int level = EnchantmentHelper.getItemEnchantmentLevel(efficiency, stack);

			if (level > 0) {
				speed += level * level + 1;
			}
		}

		return speed;
	}

	private boolean swapIntoHotbar(MultiPlayerGameMode gameMode, LocalPlayer player, int inventorySlot) {
		if (player.containerMenu != player.inventoryMenu) {
			return true;
		}

		int menuSlot = menuSlotOf(player, inventorySlot);

		if (menuSlot < 0) {
			return true;
		}

		int target = spareHotbarSlot(player);
		gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, target, ContainerInput.SWAP, player);
		player.getInventory().setSelectedSlot(target);
		return false;
	}

	private static int menuSlotOf(LocalPlayer player, int inventorySlot) {
		for (Slot slot : player.inventoryMenu.slots) {
			if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) {
				return slot.index;
			}
		}

		return -1;
	}

	private int spareHotbarSlot(LocalPlayer player) {
		Inventory inventory = player.getInventory();

		for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
			if (inventory.getItem(slot).isEmpty()) {
				return slot;
			}
		}

		for (int slot = 0; slot < Inventory.SELECTION_SIZE; slot++) {
			if (inventory.getItem(slot).getItem() != workstation.asItem()) {
				return slot;
			}
		}

		return originalSlot;
	}

	private boolean holdingWorkstation(LocalPlayer player) {
		Inventory inventory = player.getInventory();
		return inventory.getItem(inventory.getSelectedSlot()).getItem() == workstation.asItem();
	}

	private int inventorySlot(LocalPlayer player) {
		Item item = workstation.asItem();
		Inventory inventory = player.getInventory();

		if (inventory.getItem(inventory.getSelectedSlot()).getItem() == item) {
			return inventory.getSelectedSlot();
		}

		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			if (inventory.getItem(slot).getItem() == item) {
				return slot;
			}
		}

		return -1;
	}

	private void enter(Phase next) {
		phase = next;
		phaseTicks = 0;

		continueRequested = false;
		dropHintShown = false;
	}

	private void abort(Component reason) {
		Chat.error(reason);
		Chat.info(summary());
		finish();
	}

	private Component summary() {
		return Component.literal("Stopped after " + cycles + (cycles == 1 ? " reroll." : " rerolls."));
	}

	private void finish() {
		finished = true;

		Minecraft client = Minecraft.getInstance();

		if (client.gameMode != null) {
			client.gameMode.stopDestroyBlock();
		}

		if (client.player != null) {
			client.player.getInventory().setSelectedSlot(originalSlot);
		}
	}

	private static Component profession(Villager villager) {
		String name = villager.getVillagerData().profession().getRegisteredName();
		return Component.translatable("entity.minecraft.villager." + name.substring(name.indexOf(':') + 1))
				.withStyle(ChatFormatting.YELLOW);
	}

	public static BlockPos findWorkstation(Minecraft client, Block workstation) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			return null;
		}

		if (client.hitResult instanceof BlockHitResult hit
				&& hit.getType() == HitResult.Type.BLOCK
				&& level.getBlockState(hit.getBlockPos()).getBlock() == workstation) {
			return hit.getBlockPos().immutable();
		}

		BlockPos front = player.blockPosition().relative(player.getDirection());

		for (BlockPos candidate : List.of(front, front.above(), front.below())) {
			if (level.getBlockState(candidate).getBlock() == workstation) {
				return candidate.immutable();
			}
		}

		return null;
	}

	public static Villager findVillager(Minecraft client) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;

		if (player == null || level == null) {
			return null;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F).normalize();
		Vec3 end = eye.add(look.scale(PICK_RANGE));
		AABB search = player.getBoundingBox().expandTowards(look.scale(PICK_RANGE)).inflate(1.0);
		List<Villager> villagers = level.getEntitiesOfClass(Villager.class, search, Entity::isAlive);

		Villager aimedAt = null;
		double aimedDistance = Double.MAX_VALUE;

		for (Villager villager : villagers) {
			Optional<Vec3> clip = villager.getBoundingBox().inflate(0.3).clip(eye, end);

			if (clip.isEmpty()) {
				continue;
			}

			double distance = eye.distanceToSqr(clip.get());

			if (distance < aimedDistance) {
				aimedDistance = distance;
				aimedAt = villager;
			}
		}

		if (aimedAt != null) {
			return aimedAt;
		}

		Villager nearest = null;
		double bestAlignment = 0.55;

		for (Villager villager : villagers) {
			Vec3 toward = villager.position().add(0.0, villager.getBbHeight() * 0.5, 0.0).subtract(eye);

			if (toward.lengthSqr() > PICK_RANGE * PICK_RANGE) {
				continue;
			}

			double alignment = toward.normalize().dot(look);

			if (alignment > bestAlignment) {
				bestAlignment = alignment;
				nearest = villager;
			}
		}

		return nearest;
	}

	private enum Phase {
		BREAK(40, "Could not start breaking the job site block."),
		BREAKING(600, "Breaking the job site block took too long - out of reach or protected?"),
		AWAIT_ITEM(60, "The job site block never made it back into the hotbar."),
		PLACE(40, "Could not place the job site block."),
		AWAIT_PLACED(60, "The server refused the placement."),
		INTERACT(40, "Could not right-click the villager."),
		AWAIT_TRADES(200, "The villager offered no trades - did it claim the job site block?"),
		AWAIT_DECISION(0, "");

		private final int timeoutTicks;
		private final String timeoutMessage;

		Phase(int timeoutTicks, String timeoutMessage) {
			this.timeoutTicks = timeoutTicks;
			this.timeoutMessage = timeoutMessage;
		}
	}
}
