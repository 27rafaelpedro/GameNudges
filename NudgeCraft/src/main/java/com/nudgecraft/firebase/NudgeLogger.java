package com.nudgecraft.firebase;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NudgeLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft_logger");
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor();

    public static void log(ServerPlayer player, boolean isPositive, String featureName) {
        if (player == null) return;
        String username = player.getGameProfile().name();
        
        CompletableFuture.runAsync(() -> {
            try {
                // Ex: "BobsTheBobs_positivelog" ou "BobsTheBobs_negativelog"
                String suffix = isPositive ? "_positivelog" : "_negativelog";
                String documentId = username + suffix;
                String collection = "logs";
                
                // Obter o documento atual
                JsonObject doc = FirebaseManager.getDocument(collection, documentId).join();
                long currentCount = 0;
                
                if (doc != null && doc.has("fields")) {
                    JsonObject fields = doc.getAsJsonObject("fields");
                    if (fields.has(featureName)) {
                        JsonObject fieldObj = fields.getAsJsonObject(featureName);
                        if (fieldObj.has("integerValue")) {
                            currentCount = Long.parseLong(fieldObj.get("integerValue").getAsString());
                        }
                    }
                }
                
                currentCount++;
                
                // Construir o payload de patch
                JsonObject patchFields = new JsonObject();
                patchFields.add(featureName, FirebaseManager.integerField(currentCount));
                
                // Atualizar ou criar o campo
                FirebaseManager.patchDocument(collection, documentId, patchFields, List.of(featureName)).join();
                
            } catch (Exception e) {
                LOGGER.error("[NudgeLogger] Erro ao registar log '{}' para {}: {}", featureName, username, e.getMessage());
            }
        }, LOG_EXECUTOR);
    }
}
