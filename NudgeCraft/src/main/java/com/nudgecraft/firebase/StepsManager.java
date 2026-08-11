package com.nudgecraft.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Leitura do registo de passos mais recente do email ligado ao jogador. */
public final class StepsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    private StepsManager() {
    }

    public static void buscarSteps(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Firestore db = FirebaseManager.getDb();

        if (db == null) {
            LinkedAccounts.erro(player, server, "O Firebase não está operacional. Avisa um administrador.");
            return;
        }

        LinkedAccounts.obterEmailLigado(player, email -> {
            if (email == null) {
                LinkedAccounts.erro(player, server, "Ainda não ligaste um email. Usa /linkemail <email>.");
                return;
            }

            Query query = db.collection("user_visits")
                    .whereEqualTo("email", email)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1);

            ApiFuture<QuerySnapshot> future = query.get();

            future.addListener(() -> {
                try {
                    QuerySnapshot snapshot = future.get();

                    if (snapshot.isEmpty()) {
                        LinkedAccounts.erro(player, server, "Nenhum registo de passos encontrado. Tenta novamente mais tarde.");
                        return;
                    }

                    Long steps = snapshot.getDocuments().get(0).getLong("steps");

                    if (steps == null) {
                        LinkedAccounts.erro(player, server, "O último registo não tem contagem de passos.");
                        return;
                    }

                    LinkedAccounts.sucesso(player, server, "Passos hoje: " + steps);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    LOGGER.error("[Nudgecraft] Erro ao ler passos de {}", email, e);
                    LinkedAccounts.erro(player, server, "Erro ao comunicar com o Firebase.");
                }
            }, FirebaseManager.FIREBASE_EXECUTOR);
        });
    }
}
