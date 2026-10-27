package com.gredell.playermap.client.keybindings.map;

import com.gredell.playermap.client.map.MapScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class OpenMapKey {
	private static KeyMapping key;
	private static int suppressOpenTicks;

	private OpenMapKey() {
	}

	public static void register() {
		key = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.playersmap.open_map",
				GLFW.GLFW_KEY_M,
				KeyMapping.Category.MISC
			)
		);

		ClientTickEvents.END_CLIENT_TICK.register((final Minecraft client) -> {
			if (suppressOpenTicks > 0) {
				suppressOpenTicks--;
			}

			while (key.consumeClick()) {
				if (suppressOpenTicks > 0 || client.player == null || client.level == null) {
					continue;
				}
				if (client.screen == null) {
					client.setScreen(MapScreen.createForCurrentWorld());
				}
			}
		});
	}

	public static KeyMapping getKey() {
		return key;
	}

	public static void suppressOpen() {
		suppressOpenTicks = 2;
	}
}
