package com.nudgecraft.Karma;

import com.nudgecraft.Nudgecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class KarmaHudOverlay {

    private static final Identifier TEX_FRAME = Nudgecraft.id("textures/gui/frame.png");

    private static volatile KarmaState clientKarma = KarmaState.BASE;
    private static volatile long lastUpdateTime = 0;

    private KarmaHudOverlay() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(KarmaPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                try {
                    clientKarma = KarmaState.valueOf(payload.karmaName());
                    lastUpdateTime = System.currentTimeMillis();
                } catch (Exception e) {
                    clientKarma = KarmaState.BASE;
                }
                KarmaStateHolder.set(clientKarma);
            });
        });
    }

    public static void setClientKarma(KarmaState karma) {
        clientKarma = (karma != null) ? karma : KarmaState.BASE;
        lastUpdateTime = System.currentTimeMillis();
        KarmaStateHolder.set(clientKarma);
    }

    public static KarmaState getClientKarma() {
        return clientKarma;
    }

    public static void render(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker) {
        // Exibe apenas nos primeiros 45 segundos (45.000 ms) após atualização do Karma
        if (System.currentTimeMillis() - lastUpdateTime >= 45000) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();

        Identifier texture = switch (clientKarma) {
            case VNEGATIVE -> Nudgecraft.id("textures/gui/vnegative.png");
            case NEGATIVE -> Nudgecraft.id("textures/gui/negative.png");
            case SNEGATIVE -> Nudgecraft.id("textures/gui/snegative.png");
            case BASE -> Nudgecraft.id("textures/gui/base.png");
            case SPOSITIVE -> Nudgecraft.id("textures/gui/spositive.png");
            case POSITIVE -> Nudgecraft.id("textures/gui/positive.png");
            case VPOSITIVE -> Nudgecraft.id("textures/gui/vpositive.png");
        };

        int frameSize = 22;
        int marginX = 10;
        int marginY = 10;
        int frameX = marginX; // Canto superior esquerdo (evita sobreposição com poções no topo direito)
        int frameY = marginY;

        // 1. Desenha a moldura 22x22 (com relevo e fundo semi-transparente)
        graphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, TEX_FRAME, frameX, frameY, 0.0F, 0.0F, frameSize, frameSize, frameSize, frameSize);

        // 2. Desenha o ícone 16x16 centrado dentro da moldura (+3px de margem interna)
        int iconSize = 16;
        int iconX = frameX + 3;
        int iconY = frameY + 3;
        graphicsExtractor.blit(RenderPipelines.GUI_TEXTURED, texture, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
    }
}
