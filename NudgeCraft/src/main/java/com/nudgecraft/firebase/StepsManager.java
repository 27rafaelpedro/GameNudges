package com.nudgecraft.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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

        // Consulta os registos do jogador sem exigir índice composto no Firestore
        Query query = db.collection("user_visits")
                .whereEqualTo("minecraft_username", username);

        ApiFuture<QuerySnapshot> future = query.get();

        future.addListener(() -> {
            try {
                QuerySnapshot snapshot = future.get();
                List<QueryDocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());

                if (docs.isEmpty()) {
                    PlayerProfileManager.erro(player, server, "Nenhum registo de passos encontrado para " + username + ".");
                    return;
                }

                // Ordena em memória por data decrescente
                docs.sort((d1, d2) -> {
                    String date1 = d1.getString("date");
                    String date2 = d2.getString("date");
                    if (date1 == null && date2 == null) return 0;
                    if (date1 == null) return 1;
                    if (date2 == null) return -1;
                    return date2.compareTo(date1);
                });

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
