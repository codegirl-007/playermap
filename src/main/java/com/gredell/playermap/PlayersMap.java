package com.gredell.playermap;

import com.gredell.playermap.server.PlayerMapServerSync;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayersMap implements ModInitializer {
	public static final String MOD_ID = "playersmap";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PlayerMapServerSync.register();
		LOGGER.info("PlayersMap initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
