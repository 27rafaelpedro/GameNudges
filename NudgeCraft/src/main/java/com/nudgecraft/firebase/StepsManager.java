package com.nudgecraft.firebase;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Leitura do registo de passos mais recente do jogador via Firestore REST API. */
public final class StepsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    private StepsManager() {
    }

    public static void buscarSteps(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        String username = PlayerProfileManager.getUsername(player);

        FirebaseManager.queryUserVisits(username)
                .thenAccept(docsList -> {
                    if (docsList.isEmpty()) {
                        PlayerProfileManager.erro(player, server, "Nenhum registo de passos encontrado para " + username + ".");
                        return;
                    }

                    List<JsonObject> docs = new ArrayList<>(docsList);

                    // Ordena por data decrescente
                    docs.sort((d1, d2) -> {
                        JsonObject f1 = d1.has("fields") ? d1.getAsJsonObject("fields") : null;
                        JsonObject f2 = d2.has("fields") ? d2.getAsJsonObject("fields") : null;
                        String date1 = FirebaseManager.getString(f1, "date", "");
                        String date2 = FirebaseManager.getString(f2, "date", "");
                        return date2.compareTo(date1);
                    });

                    JsonObject fieldsHoje = docs.get(0).has("fields") ? docs.get(0).getAsJsonObject("fields") : null;
                    Long stepsHoje = FirebaseManager.getLong(fieldsHoje, "steps", null);
                    String dateHoje = FirebaseManager.getString(fieldsHoje, "date", "Hoje");

                    if (stepsHoje == null) {
                        PlayerProfileManager.erro(player, server, "O último registo não tem contagem de passos.");
                        return;
                    }

                    if (docs.size() >= 2) {
                        JsonObject fieldsOntem = docs.get(1).has("fields") ? docs.get(1).getAsJsonObject("fields") : null;
                        Long stepsOntem = FirebaseManager.getLong(fieldsOntem, "steps", null);
                        String dateOntem = FirebaseManager.getString(fieldsOntem, "date", "Anterior");

                        if (stepsOntem != null) {
                            PlayerProfileManager.sucesso(player, server,
                                    "Passos (" + dateHoje + "): " + stepsHoje +
                                            " | Passos (" + dateOntem + "): " + stepsOntem);
                            return;
                        }
                    }

                    PlayerProfileManager.sucesso(player, server, "Passos (" + dateHoje + "): " + stepsHoje);
                })
                .exceptionally(ex -> {
                    LOGGER.error("[Nudgecraft] Erro ao ler passos de {}", username, ex);
                    PlayerProfileManager.erro(player, server, "Erro ao comunicar com o Firestore.");
                    return null;
                });
    }
}
