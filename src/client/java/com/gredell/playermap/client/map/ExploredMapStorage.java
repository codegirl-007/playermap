package com.gredell.playermap.client.map;

import com.gredell.playermap.PlayersMap;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ExploredMapStorage {
	private static final Map<Identifier, Set<Long>> exploredByDimension = new HashMap<>();
	private static int ticksUntilSave = -1;

	private ExploredMapStorage() {
	}

	public static void markExplored(ResourceKey<Level> dimension, ChunkPos chunkPos) {
		Set<Long> explored = exploredByDimension.computeIfAbsent(dimension.identifier(), id -> new HashSet<>());
		if (explored.add(chunkPos.toLong())) {
			scheduleSave();
		}
	}

	public static boolean isExplored(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
		Set<Long> explored = exploredByDimension.get(dimension.identifier());
		return explored != null && explored.contains(ChunkPos.asLong(chunkX, chunkZ));
	}

	public static void load() {
		exploredByDimension.clear();
		Path path = storagePath();
		if (!Files.isRegularFile(path)) {
			return;
		}

		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			for (String dimensionId : root.keySet()) {
				ListTag list = root.getListOrEmpty(dimensionId);
				Set<Long> explored = new HashSet<>();
				for (int i = 0; i < list.size(); i++) {
					if (list.get(i) instanceof net.minecraft.nbt.LongTag longTag) {
						explored.add(longTag.longValue());
					}
				}
				exploredByDimension.put(Identifier.parse(dimensionId), explored);
			}
		} catch (IOException exception) {
			PlayersMap.LOGGER.warn("Failed to load explored map data", exception);
		}
	}

	public static void saveIfNeeded() {
		if (ticksUntilSave <= 0) {
			return;
		}
		ticksUntilSave--;
		if (ticksUntilSave == 0) {
			save();
		}
	}

	public static void saveNow() {
		save();
	}

	private static void scheduleSave() {
		ticksUntilSave = 20 * 30;
	}

	private static void save() {
		ticksUntilSave = -1;
		Path path = storagePath();
		try {
			Files.createDirectories(path.getParent());
			CompoundTag root = new CompoundTag();
			for (Map.Entry<Identifier, Set<Long>> entry : exploredByDimension.entrySet()) {
				ListTag list = new ListTag();
				for (Long chunkPos : entry.getValue()) {
					list.add(net.minecraft.nbt.LongTag.valueOf(chunkPos));
				}
				root.put(entry.getKey().toString(), list);
			}
			NbtIo.writeCompressed(root, path);
		} catch (IOException exception) {
			PlayersMap.LOGGER.warn("Failed to save explored map data", exception);
		}
	}

	private static Path storagePath() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("playersmap").resolve("explored.dat");
	}
}
