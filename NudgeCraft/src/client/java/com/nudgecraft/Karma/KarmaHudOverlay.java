package com.nudgecraft.Karma;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

public final class KarmaHudOverlay {

    private static final Identifier TEX_VNEGATIVE = Identifier.fromNamespaceAndPath("nudgecraft", "textures/gui/vnegative.png");
    private static final Identifier TEX_NEGATIVE = Identifier.fromNamespaceAndPath("nudgecraft", "textures/gui/negative.png");
    private static final Identifier TEX_SNEGATIVE = Identifier.fromNamespaceAndPath("nudgecraft", "textures/gui/snegative.png");
    private static final Identifier TEX_BASE = Identifier.fromNamespaceAndPath("nudgecraft", "textures/gui/base.png");
    private static final Identifier TEX_SPOSITIVE = Identifier.fromNamespaceAndPath("nudgecraft", "textures/gui/spositive.png");
    private static final Identifier TEX_POSITIVE = Identifier.fromNamespaceAndPath("nudgecraft", "textures/gui/positive.png");
    private static final Identifier TEX_VPOSITIVE = Identifier.fromNamespaceAndPath("nudgecraft", "textures/gui/vpositive.png");

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
            });
        });
    }

    public static void setClientKarma(KarmaState karma) {
        clientKarma = (karma != null) ? karma : KarmaState.BASE;
        lastUpdateTime = System.currentTimeMillis();
    }

    public static KarmaState getClientKarma() {
        return clientKarma;
    }

    public static void render(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker) {
        // Exibe apenas nos primeiros 45 segundos (45.000 ms) após atualização do Karma (login ou comando)
        if (System.currentTimeMillis() - lastUpdateTime >= 45000) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();

        // Configurações do Ícone no canto superior direito
        int width = 16;
        int height = 16;
        int startX = screenWidth - width - 10; // 10 pixels de margem da direita
        int startY = 10; // 10 pixels de margem do topo

        Identifier texture = switch (clientKarma) {
            case VNEGATIVE -> TEX_VNEGATIVE;
            case NEGATIVE -> TEX_NEGATIVE;
            case SNEGATIVE -> TEX_SNEGATIVE;
            case BASE -> TEX_BASE;
            case SPOSITIVE -> TEX_SPOSITIVE;
            case POSITIVE -> TEX_POSITIVE;
            case VPOSITIVE -> TEX_VPOSITIVE;
        };

        // Desenha a textura do ícone de pixel art de 16x16 pixels especificando a pipeline e o tamanho correto
        graphicsExtractor.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                startX,
                startY,
                0.0f,
                0.0f,
                16,
                16,
                16,
                16
        );
    }
}
