package com.nudgecraft;

import com.nudgecraft.command.ModCommands;
import com.nudgecraft.firebase.FirebaseManager;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nudgecraft implements ModInitializer {
	// Tem de coincidir com o "id" do fabric.mod.json e ser um namespace válido ([a-z0-9_.-]).
	public static final String MOD_ID = "nudgecraft";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModCommands.registar();

		// O Firebase só é preciso do lado do servidor (inclui o servidor integrado do singleplayer).
		ServerLifecycleEvents.SERVER_STARTING.register(server -> FirebaseManager.init());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FirebaseManager.shutdown());

		LOGGER.info("[Nudgecraft] Mod inicializado.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
