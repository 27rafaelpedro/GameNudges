package com.nudgecraft.client;

import net.fabricmc.api.ClientModInitializer;

public class NudgecraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		com.nudgecraft.Karma.KarmaHudOverlay.init();
	}
}