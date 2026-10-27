package com.gredell.playermap.client.keybindings.waypoint;

import com.gredell.playermap.client.map.PlayerWaypoint;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ClearWaypointKey {
	private static KeyMapping key;

	private ClearWaypointKey() {
	}

	public static void register() {
		key = KeyBindingHelper.registerKeyBinding(
			new KeyMapping(
				"key.playersmap.clear_waypoint",
				GLFW.GLFW_KEY_U,
				KeyMapping.Category.MISC
			)
		);

		ClientTickEvents.END_CLIENT_TICK.register((final Minecraft client) -> {
			while (key.consumeClick()) {
				PlayerWaypoint.clear();
			}
		});
	}
}
