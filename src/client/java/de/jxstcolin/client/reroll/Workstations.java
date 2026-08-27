package de.jxstcolin.client.reroll;

import java.util.List;

import net.minecraft.resources.Identifier;

public final class Workstations {
	public static final List<Identifier> SUGGESTED = List.of(
			vanilla("lectern"),
			vanilla("cartography_table"),
			vanilla("smithing_table"),
			vanilla("grindstone"),
			vanilla("fletching_table"),
			vanilla("loom"),
			vanilla("barrel"),
			vanilla("blast_furnace"),
			vanilla("smoker"),
			vanilla("stonecutter"),
			vanilla("brewing_stand"),
			vanilla("composter"),
			vanilla("cauldron"));

	private Workstations() {
	}

	public static boolean isJobSite(Identifier id) {
		return SUGGESTED.contains(id);
	}

	private static Identifier vanilla(String path) {
		return Identifier.fromNamespaceAndPath("minecraft", path);
	}
}
