package com.gredell.playermap.client.map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

public final class MapDimensions {
	public static final List<ResourceKey<Level>> LAYERS = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

	private MapDimensions() {
	}

	public static Component label(final ResourceKey<Level> dimension) {
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

	public static String shortLabel(final ResourceKey<Level> dimension) {
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

	public static Component tabLabel(final int hotkey, final ResourceKey<Level> dimension) {
		return Component.literal(hotkey + " · " + shortLabel(dimension));
	}

	/**
	 * A dimension can be shown when it is the overworld, the player is standing in it, or cached terrain exists.
	 */
	public static boolean isMapAvailable(final ResourceKey<Level> dimension, final Level playerLevel) {
		if (dimension.equals(Level.OVERWORLD)) {
			return true;
		}
		if (playerLevel != null && playerLevel.dimension().equals(dimension)) {
			return true;
		}
		return ChunkSurfaceColors.hasDataForDimension(dimension);
	}

	public static List<ResourceKey<Level>> availableLayers(final Minecraft client) {
		final Level playerLevel = client.level;
		return LAYERS.stream().filter((final ResourceKey<Level> layer) -> isMapAvailable(layer, playerLevel)).toList();
	}

	public static int hotkeyForDimension(final ResourceKey<Level> dimension) {
		for (int i = 0; i < LAYERS.size(); i++) {
			if (LAYERS.get(i).equals(dimension)) {
				return i + 1;
			}
		}
		return -1;
	}
}
