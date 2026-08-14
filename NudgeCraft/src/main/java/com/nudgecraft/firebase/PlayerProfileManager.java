package com.nudgecraft.firebase;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.nudgecraft.Karma.KarmaState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
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
     */
    public static DocumentSnapshot getOrCreateProfile(Firestore db, ServerPlayer player) throws Exception {
        String username = getUsername(player);
        DocumentSnapshot doc = db.collection("players").document(username).get().get();

        if (!doc.exists()) {
            Map<String, Object> initialData = new HashMap<>();
            initialData.put("minecraft_username", username);
            initialData.put("uuid", player.getStringUUID());
            initialData.put("goal", DEFAULT_GOAL);
            initialData.put("karma", KarmaState.BASE.name());
            initialData.put("lastProcessedVisitDate", null);
            initialData.put("lastProcessedGoal", null);
            initialData.put("karmaBeforeLastProcessedVisit", KarmaState.BASE.name());

            db.collection("players").document(username).set(initialData).get();
            doc = db.collection("players").document(username).get().get();
        }

        return doc;
    }

    public static void setGoal(ServerPlayer player, long newGoal) {
        MinecraftServer server = player.level().getServer();
        Firestore db = FirebaseManager.getDb();

        if (db == null) {
            erro(player, server, "O Firebase não está operacional.");
            return;
        }

        if (newGoal <= 0) {
            erro(player, server, "A meta de passos deve ser maior que 0.");
            return;
        }

        String username = getUsername(player);

        CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> data = new HashMap<>();
                data.put("goal", newGoal);
                data.put("minecraft_username", username);

                db.collection("players").document(username).set(data, SetOptions.merge()).get();
                sucesso(player, server, "Meta de passos atualizada para " + newGoal + " passos diários!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                erro(player, server, "Erro ao atualizar a meta de passos.");
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao atualizar meta de {}", username, e);
                erro(player, server, "Erro ao comunicar com o Firebase.");
            }
        }, FirebaseManager.FIREBASE_EXECUTOR);
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
