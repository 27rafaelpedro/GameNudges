package com.nudgecraft.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Leitura do registo de passos mais recente do jogador via minecraft_username. */
public final class StepsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    private StepsManager() {
    }

    public static void buscarSteps(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Firestore db = FirebaseManager.getDb();

        if (db == null) {
            PlayerProfileManager.erro(player, server, "O Firebase não está operacional. Avisa um administrador.");
            return;
        }

        String username = PlayerProfileManager.getUsername(player);

        // limit(2) obtém o registo mais recente (índice 0) e o anterior (índice 1)
        Query query = db.collection("user_visits")
                .whereEqualTo("minecraft_username", username)
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(2);

        ApiFuture<QuerySnapshot> future = query.get();

        future.addListener(() -> {
            try {
                QuerySnapshot snapshot = future.get();
                var docs = snapshot.getDocuments();

                if (docs.isEmpty()) {
                    PlayerProfileManager.erro(player, server, "Nenhum registo de passos encontrado para " + username + ".");
                    return;
                }

                // Dia mais recente (índice 0)
                var docHoje = docs.get(0);
                Long stepsHoje = docHoje.getLong("steps");
                String dateHoje = docHoje.getString("date");

                if (stepsHoje == null) {
                    PlayerProfileManager.erro(player, server, "O último registo não tem contagem de passos.");
                    return;
                }

                String dataHojeTexto = (dateHoje != null) ? dateHoje : "Hoje";

                // Se existir o 2º documento (dia anterior)
                if (docs.size() >= 2) {
                    var docOntem = docs.get(1);
                    Long stepsOntem = docOntem.getLong("steps");
                    String dateOntem = docOntem.getString("date");

                    if (stepsOntem != null) {
                        String dataOntemTexto = (dateOntem != null) ? dateOntem : "Anterior";

                        PlayerProfileManager.sucesso(player, server,
                                "Passos (" + dataHojeTexto + "): " + stepsHoje +
                                        " | Passos (" + dataOntemTexto + "): " + stepsOntem);
                        return;
                    }
                }

                // Caso seja o primeiro dia e ainda não exista o dia anterior
                PlayerProfileManager.sucesso(player, server, "Passos (" + dataHojeTexto + "): " + stepsHoje);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao ler passos de {}", username, e);
                PlayerProfileManager.erro(player, server, "Erro ao comunicar com o Firebase.");
            }
        }, FirebaseManager.FIREBASE_EXECUTOR);
    }
}
