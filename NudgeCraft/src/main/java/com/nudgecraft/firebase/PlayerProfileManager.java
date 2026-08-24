package com.nudgecraft.firebase;

import com.google.gson.JsonObject;
import com.nudgecraft.Karma.KarmaState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PlayerProfileManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");
    public static final long DEFAULT_GOAL = 5000L;

    private PlayerProfileManager() {
    }

    public static String getUsername(ServerPlayer player) {
        return player.getGameProfile().name();
    }

    /**
     * Obtém ou inicializa o perfil do jogador no Firestore (coleção 'players').
     * Garante que novos jogadores começam sempre no Karma BASE, ignorando passos anteriores ao registo.
     */
    public static CompletableFuture<JsonObject> getOrCreateProfile(String username, String uuid) {
        return FirebaseManager.getDocument("players", username)
                .thenCompose(doc -> {
                    if (doc != null && doc.has("fields")) {
                        return CompletableFuture.completedFuture(doc.getAsJsonObject("fields"));
                    }

                    String today = LocalDate.now().toString();
                    String yesterday = LocalDate.now().minusDays(1).toString();

                    // Criar perfil inicial de novo participante sempre em Karma BASE
                    JsonObject initialFields = new JsonObject();
                    initialFields.add("minecraft_username", FirebaseManager.stringField(username));
                    initialFields.add("uuid", FirebaseManager.stringField(uuid));
                    initialFields.add("goal", FirebaseManager.integerField(DEFAULT_GOAL));
                    initialFields.add("karma", FirebaseManager.stringField(KarmaState.BASE.name()));
                    initialFields.add("karmaBeforeLastProcessedVisit", FirebaseManager.stringField(KarmaState.BASE.name()));
                    initialFields.add("registrationDate", FirebaseManager.stringField(today));
                    initialFields.add("lastProcessedVisitDate", FirebaseManager.stringField(yesterday));
                    initialFields.add("lastProcessedGoal", FirebaseManager.integerField(DEFAULT_GOAL));

                    return FirebaseManager.patchDocument("players", username, initialFields, null)
                            .thenApply(createdDoc -> createdDoc != null && createdDoc.has("fields")
                                    ? createdDoc.getAsJsonObject("fields")
                                    : initialFields);
                });
    }

    public static void setGoal(ServerPlayer player, long newGoal) {
        MinecraftServer server = player.level().getServer();

        if (newGoal <= 0) {
            erro(player, server, "A meta de passos deve ser maior que 0.");
            return;
        }

        String username = getUsername(player);
        JsonObject fields = new JsonObject();
        fields.add("goal", FirebaseManager.integerField(newGoal));

        FirebaseManager.patchDocument("players", username, fields, List.of("goal"))
                .thenAccept(response ->
                        sucesso(player, server, "Meta de passos atualizada para " + newGoal + " passos diários!"))
                .exceptionally(ex -> {
                    LOGGER.error("[Nudgecraft] Erro ao atualizar meta de {}", username, ex);
                    erro(player, server, "Erro ao comunicar com o Firestore.");
                    return null;
                });
    }

    public static void erro(ServerPlayer player, MinecraftServer server, String mensagem) {
        FirebaseManager.onServerThread(server, () ->
                player.sendSystemMessage(Component.literal(mensagem).withStyle(ChatFormatting.RED)));
    }

    public static void sucesso(ServerPlayer player, MinecraftServer server, String mensagem) {
        FirebaseManager.onServerThread(server, () ->
                player.sendSystemMessage(Component.literal(mensagem).withStyle(ChatFormatting.GREEN)));
    }
}
