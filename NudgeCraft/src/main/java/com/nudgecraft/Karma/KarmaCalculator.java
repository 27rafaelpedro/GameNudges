package com.nudgecraft.Karma;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.nudgecraft.firebase.FirebaseManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public final class KarmaCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    private KarmaCalculator() {
    }

    public static CompletableFuture<KarmaState> calculate(ServerPlayer player) {
        return calculate(player, true);
    }

    public static CompletableFuture<KarmaState> calculateInBackground(ServerPlayer player) {
        return calculate(player, false);
    }

    private static CompletableFuture<KarmaState> calculate(ServerPlayer player, boolean notifyPlayer) {
        CompletableFuture<KarmaState> result = new CompletableFuture<>();
        MinecraftServer server = player.level().getServer();
        Firestore db = FirebaseManager.getDb();

        if (db == null) {
            erro(player, server, "O Firebase nao esta operacional. Avisa um administrador.", notifyPlayer);
            result.complete(KarmaState.BASE);
            return result;
        }

        CompletableFuture.runAsync(() -> {
            try {
                DocumentSnapshot linkedAccount = db.collection("linked_accounts")
                        .document(player.getStringUUID())
                        .get()
                        .get();

                if (!linkedAccount.exists()) {
                    erro(player, server, "Ainda nao ligaste um email. Usa /linkemail <email>.", notifyPlayer);
                    result.complete(KarmaState.BASE);
                    return;
                }

                String email = linkedAccount.getString("email");

                if (email == null) {
                    erro(player, server, "A tua conta ligada nao tem email associado.", notifyPlayer);
                    result.complete(KarmaState.BASE);
                    return;
                }

                QuerySnapshot snapshot = db.collection("user_visits")
                        .whereEqualTo("email", email)
                        .orderBy("date", Query.Direction.DESCENDING)
                        .limit(2)
                        .get()
                        .get();

                var docs = snapshot.getDocuments();

                if (docs.isEmpty()) {
                    erro(player, server, "Nenhum registo de passos encontrado. Tenta novamente mais tarde.", notifyPlayer);
                    result.complete(KarmaState.BASE);
                    return;
                }

                String visitDate = docs.get(0).getString("date");

                if (visitDate == null) {
                    erro(player, server, "O ultimo registo nao tem data.", notifyPlayer);
                    result.complete(readStoredKarma(linkedAccount));
                    return;
                }

                String lastProcessedVisitDate = linkedAccount.getString("lastProcessedVisitDate");
                KarmaState storedKarma = readStoredKarma(linkedAccount);

                if (docs.size() == 1) {
                    updatePlayerKarma(db, player, KarmaState.BASE, storedKarma, visitDate, null);
                    sucesso(player, server, "Karma atual: " + KarmaState.BASE, notifyPlayer);
                    result.complete(KarmaState.BASE);
                    return;
                }

                Long stepsOntem = docs.get(0).getLong("steps");

                if (stepsOntem == null) {
                    erro(player, server, "O ultimo registo nao tem contagem de passos.", notifyPlayer);
                    result.complete(storedKarma);
                    return;
                }

                Long goal = linkedAccount.getLong("goal");

                if (goal == null || goal <= 0) {
                    erro(player, server, "Ainda nao tens um objetivo de passos definido.", notifyPlayer);
                    result.complete(storedKarma);
                    return;
                }

                Long lastProcessedGoal = linkedAccount.getLong("lastProcessedGoal");

                if (visitDate.equals(lastProcessedVisitDate) && goal.equals(lastProcessedGoal)) {
                    sucesso(player, server, "Karma atual: " + storedKarma, notifyPlayer);
                    result.complete(storedKarma);
                    return;
                }

                KarmaState baseKarma = storedKarma;

                if (visitDate.equals(lastProcessedVisitDate)) {
                    baseKarma = readKarmaBeforeLastProcessedVisit(linkedAccount);
                }

                KarmaState karmaState = calculateFromGoal(baseKarma, stepsOntem >= goal);
                updatePlayerKarma(db, player, karmaState, baseKarma, visitDate, goal);
                sucesso(player, server, "Karma atual: " + karmaState, notifyPlayer);
                result.complete(karmaState);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(e);
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao calcular karma de {}", player.getStringUUID(), e);
                erro(player, server, "Erro ao comunicar com o Firebase.", notifyPlayer);
                result.completeExceptionally(e);
            }
        }, FirebaseManager.FIREBASE_EXECUTOR);

        return result;
    }

    private static void updatePlayerKarma(
            Firestore db,
            ServerPlayer player,
            KarmaState karmaState,
            KarmaState karmaBeforeProcessedVisit,
            String processedVisitDate,
            Long processedGoal
    ) throws Exception {
        db.collection("linked_accounts")
                .document(player.getStringUUID())
                .update(
                        "karma", karmaState.name(),
                        "lastProcessedVisitDate", processedVisitDate,
                        "lastProcessedGoal", processedGoal,
                        "karmaBeforeLastProcessedVisit", karmaBeforeProcessedVisit.name()
                )
                .get();
    }

    private static KarmaState readStoredKarma(DocumentSnapshot linkedAccount) {
        String storedKarma = linkedAccount.getString("karma");

        if (storedKarma == null) {
            return KarmaState.BASE;
        }

        try {
            return KarmaState.valueOf(storedKarma);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Nudgecraft] Karma invalido guardado para {}: {}",
                    linkedAccount.getId(), storedKarma);
            return KarmaState.BASE;
        }
    }

    private static KarmaState readKarmaBeforeLastProcessedVisit(DocumentSnapshot linkedAccount) {
        String storedKarma = linkedAccount.getString("karmaBeforeLastProcessedVisit");

        if (storedKarma == null) {
            return KarmaState.BASE;
        }

        try {
            return KarmaState.valueOf(storedKarma);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Nudgecraft] Karma anterior invalido guardado para {}: {}",
                    linkedAccount.getId(), storedKarma);
            return KarmaState.BASE;
        }
    }

    private static KarmaState calculateFromGoal(KarmaState currentKarma, boolean goalAchieved) {
        if (currentKarma == KarmaState.BASE) {
            return goalAchieved ? KarmaState.POSITIVE : KarmaState.NEGATIVE;
        }

        int currentLevel = levelOf(currentKarma);
        int nextLevel = goalAchieved ? currentLevel + 1 : currentLevel - 1;

        return karmaFromLevel(nextLevel);
    }

    private static int levelOf(KarmaState karmaState) {
        return switch (karmaState) {
            case VNEGATIVE -> -3;
            case NEGATIVE -> -2;
            case SNEGATIVE -> -1;
            case BASE -> 0;
            case SPOSITIVE -> 1;
            case POSITIVE -> 2;
            case VPOSITIVE -> 3;
        };
    }

    private static KarmaState karmaFromLevel(int level) {
        if (level <= -3) {
            return KarmaState.VNEGATIVE;
        }

        if (level == -2) {
            return KarmaState.NEGATIVE;
        }

        if (level <= 0) {
            return KarmaState.SNEGATIVE;
        }

        if (level == 1) {
            return KarmaState.SPOSITIVE;
        }

        if (level == 2) {
            return KarmaState.POSITIVE;
        }

        return KarmaState.VPOSITIVE;
    }

    private static void erro(ServerPlayer player, MinecraftServer server, String mensagem, boolean notifyPlayer) {
        if (!notifyPlayer) {
            return;
        }

        FirebaseManager.onServerThread(server, () ->
                player.sendSystemMessage(Component.literal(mensagem).withStyle(ChatFormatting.RED)));
    }

    private static void sucesso(ServerPlayer player, MinecraftServer server, String mensagem, boolean notifyPlayer) {
        if (!notifyPlayer) {
            return;
        }

        FirebaseManager.onServerThread(server, () ->
                player.sendSystemMessage(Component.literal(mensagem).withStyle(ChatFormatting.GREEN)));
    }
}
