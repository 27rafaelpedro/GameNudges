package com.nudgecraft.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * Ponto de entrada do cliente (ClientModInitializer) do mod NudgeCraft.
 * Regista recetores de rede do lado do cliente e gere estados de renderização locais.
 */
public class NudgecraftClient implements ClientModInitializer {

    /**
     * Inicializa os subsistemas e canais de rede exclusivos do cliente.
     */
    @Override
    public void onInitializeClient() {
        com.nudgecraft.Karma.KarmaHudOverlay.init();
    }
}