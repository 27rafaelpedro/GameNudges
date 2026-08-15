package com.nudgecraft;

import com.nudgecraft.Karma.KarmaCalculator;
import com.nudgecraft.command.ModCommands;
import com.nudgecraft.firebase.FirebaseManager;
import com.nudgecraft.Karma.KarmaPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.nudgecraft.Karma.strategy.KarmaEffectManager;
import net.minecraft.server.level.ServerPlayer;

public class Nudgecraft implements ModInitializer {

    public static final String MOD_ID = "nudgecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(KarmaPayload.TYPE, KarmaPayload.CODEC);
        ModCommands.registar();
        com.nudgecraft.event.BlockBreakEventHandler.init();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> FirebaseManager.init());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FirebaseManager.shutdown());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                KarmaCalculator.processPlayerLogin(handler.player));

        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            for (ServerPlayer player : level.players()) {
                KarmaEffectManager.tick(player, level);
            }
        });

        LOGGER.info("[Nudgecraft] Mod inicializado.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
