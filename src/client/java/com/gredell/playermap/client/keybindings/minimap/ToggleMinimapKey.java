package com.gredell.playermap.client.keybindings.minimap;

import com.gredell.playermap.client.map.MinimapOverlay;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ToggleMinimapKey {
	private static KeyMapping key;

	private ToggleMinimapKey() {
	}

	public static void register() {
		key = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.playersmap.toggle_minimap",
				GLFW.GLFW_KEY_COMMA,
				KeyMapping.Category.MISC
			)
		);

		ClientTickEvents.END_CLIENT_TICK.register((final Minecraft client) -> {
			while (key.consumeClick()) {
				MinimapOverlay.toggleEnabled();
			}
		});
	}
}
