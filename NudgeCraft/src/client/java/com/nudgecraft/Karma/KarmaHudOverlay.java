package com.nudgecraft.Karma;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class KarmaHudOverlay {

    private static volatile KarmaState clientKarma = KarmaState.BASE;

    private KarmaHudOverlay() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(KarmaPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    clientKarma = KarmaState.valueOf(payload.karmaName());
                } catch (Exception e) {
                    clientKarma = KarmaState.BASE;
                }
            });
        });
    }

    public static void setClientKarma(KarmaState karma) {
        clientKarma = (karma != null) ? karma : KarmaState.BASE;
    }

    public static KarmaState getClientKarma() {
        return clientKarma;
    }

    public static void render(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();

        // Configurações da barra de Karma com 7 divisões
        int totalSegments = 7;
        int segmentWidth = 16;
        int segmentHeight = 5;
        int spacing = 2;
        int totalBarWidth = (segmentWidth * totalSegments) + (spacing * (totalSegments - 1));

        // Posição no topo central do ecrã
        int startX = (screenWidth - totalBarWidth) / 2;
        int startY = 12;

        int activeIndex = getIndexForKarma(clientKarma);

        // Fundo da barra
        graphicsExtractor.fill(startX - 3, startY - 3, startX + totalBarWidth + 3, startY + segmentHeight + 3, 0x88000000);
        graphicsExtractor.outline(startX - 3, startY - 3, totalBarWidth + 6, segmentHeight + 6, 0xFF333333);

        // Cores dos 7 segmentos
        int[] segmentColors = {
                0xFF8B1A1A, // VNEGATIVE (Vermelho Escuro)
                0xFFD32F2F, // NEGATIVE (Vermelho)
                0xFFE67E22, // SNEGATIVE (Laranja)
                0xFFF1C40F, // BASE (Amarelo / Dourado Neutro)
                0xFF8BC34A, // SPOSITIVE (Verde Claro)
                0xFF2ECC71, // POSITIVE (Verde)
                0xFF00E676  // VPOSITIVE (Esmeralda / Ciano)
        };

        // Renderiza cada divisão
        for (int i = 0; i < totalSegments; i++) {
            int segX = startX + (i * (segmentWidth + spacing));
            int color = segmentColors[i];

            if (i == activeIndex) {
                // Segmento ativo: preenchimento brilhante
                graphicsExtractor.fill(segX, startY, segX + segmentWidth, startY + segmentHeight, color);
                // Borda de destaque
                graphicsExtractor.outline(segX - 1, startY - 1, segmentWidth + 2, segmentHeight + 2, 0xFFFFFFFF);

                // Ícone indicador (marcador ▼ em cima do segmento ativo)
                int markerX = segX + (segmentWidth / 2);
                int markerY = startY - 7;
                graphicsExtractor.centeredText(client.font, "▼", markerX, markerY, 0xFFFFFFFF);
            } else {
                // Segmento inativo: cor mais escura/translúcida
                int dimColor = (color & 0x00FFFFFF) | 0x44000000;
                graphicsExtractor.fill(segX, startY, segX + segmentWidth, startY + segmentHeight, dimColor);
            }
        }
    }

    private static int getIndexForKarma(KarmaState state) {
        if (state == null) return 3;
        return switch (state) {
            case VNEGATIVE -> 0;
            case NEGATIVE -> 1;
            case SNEGATIVE -> 2;
            case BASE -> 3;
            case SPOSITIVE -> 4;
            case POSITIVE -> 5;
            case VPOSITIVE -> 6;
        };
    }
}
