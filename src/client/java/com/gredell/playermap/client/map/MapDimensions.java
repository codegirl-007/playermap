package com.gredell.playermap.client.map;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

public final class MapDimensions {
	public static final List<ResourceKey<Level>> LAYERS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

	private MapDimensions() {
	}

	public static Component label(ResourceKey<Level> dimension) {
		if (dimension.equals(Level.OVERWORLD)) {
			return Component.literal("Overworld");
		}
		if (dimension.equals(Level.NETHER)) {
			return Component.literal("Nether");
		}
		if (dimension.equals(Level.END)) {
			return Component.literal("End");
		}
		return Component.literal(dimension.identifier().toString());
	}

	public static String shortLabel(ResourceKey<Level> dimension) {
		if (dimension.equals(Level.OVERWORLD)) {
			return "O";
		}
		if (dimension.equals(Level.NETHER)) {
			return "N";
		}
		if (dimension.equals(Level.END)) {
			return "E";
		}
		return "?";
	}

	public static Component tabLabel(int hotkey, ResourceKey<Level> dimension) {
		return Component.literal(hotkey + " · " + shortLabel(dimension));
	}
}
