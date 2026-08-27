package de.jxstcolin.client.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

public final class TradeFormat {
	private static final String ARROW = " \u2192 ";

	private TradeFormat() {
	}

	public static Component offer(int index, MerchantOffer offer) {
		MutableComponent line = Component.literal("  " + index + ") ").withStyle(ChatFormatting.DARK_GRAY);
		line.append(stack(offer.getCostA()));

		ItemStack costB = offer.getCostB();

		if (!costB.isEmpty()) {
			line.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));
			line.append(stack(costB));
		}

		line.append(Component.literal(ARROW).withStyle(ChatFormatting.DARK_GRAY));
		line.append(stack(offer.getResult()));

		if (offer.isOutOfStock()) {
			line.append(Component.literal(" (out of stock)").withStyle(ChatFormatting.DARK_RED));
		}

		return line;
	}

	private static MutableComponent stack(ItemStack stack) {
		MutableComponent text = Component.empty();
		text.append(Component.literal(stack.getCount() + "x ").withStyle(ChatFormatting.GRAY));
		text.append(stack.getHoverName());

		MutableComponent enchantments = enchantments(stack);

		if (enchantments != null) {
			text.append(Component.literal(" ")).append(enchantments);
		}

		return text;
	}

	private static MutableComponent enchantments(ItemStack stack) {
		ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);

		if (enchantments == null || enchantments.isEmpty()) {
			enchantments = stack.get(DataComponents.ENCHANTMENTS);
		}

		if (enchantments == null || enchantments.isEmpty()) {
			return null;
		}

		MutableComponent text = Component.literal("(").withStyle(ChatFormatting.DARK_GRAY);
		boolean first = true;

		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			if (!first) {
				text.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
			}

			text.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
			first = false;
		}

		return text.append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY));
	}
}
