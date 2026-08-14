package com.nudgecraft.Karma;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.nudgecraft.firebase.FirebaseManager;
import com.nudgecraft.firebase.PlayerProfileManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KarmaCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    private KarmaCalculator() {
    }

    /**
     * Executado quando o jogador entra no jogo/servidor.
     * Calcula o Karma e envia a mensagem narrativa correspondente.
     */
    public static CompletableFuture<KarmaState> processPlayerLogin(ServerPlayer player) {
        return calculateInternal(player, true);
    }

    /**
     * Executado quando o jogador usa o comando /karma.
     */
    public static CompletableFuture<KarmaState> calculate(ServerPlayer player) {
        return calculateInternal(player, false);
    }

    private static CompletableFuture<KarmaState> calculateInternal(ServerPlayer player, boolean isLogin) {
        CompletableFuture<KarmaState> result = new CompletableFuture<>();
        MinecraftServer server = player.level().getServer();
        Firestore db = FirebaseManager.getDb();

        if (db == null) {
            if (!isLogin) {
                PlayerProfileManager.erro(player, server, "O Firebase não está operacional. Avisa um administrador.");
            }
            result.complete(KarmaState.BASE);
            return result;
        }

        String username = PlayerProfileManager.getUsername(player);

        CompletableFuture.runAsync(() -> {
            try {
                DocumentSnapshot profileDoc = PlayerProfileManager.getOrCreateProfile(db, player);

                Long goal = profileDoc.getLong("goal");
                if (goal == null || goal <= 0) {
                    goal = PlayerProfileManager.DEFAULT_GOAL;
                }

                KarmaState storedKarma = readStoredKarma(profileDoc);
                String lastProcessedVisitDate = profileDoc.getString("lastProcessedVisitDate");
                Long lastProcessedGoal = profileDoc.getLong("lastProcessedGoal");

                // Consulta todos os registos do jogador (até 7 dias) sem exigir índice composto no Firestore
                QuerySnapshot snapshot = db.collection("user_visits")
                        .whereEqualTo("minecraft_username", username)
                        .get()
                        .get();

                List<QueryDocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                // Ordena por data decrescente em memória
                docs.sort((d1, d2) -> {
                    String date1 = d1.getString("date");
                    String date2 = d2.getString("date");
                    if (date1 == null && date2 == null) return 0;
                    if (date1 == null) return 1;
                    if (date2 == null) return -1;
                    return date2.compareTo(date1);
                });

                // Cenário 1: Nenhum registo de passos ou apenas o primeiro dia sem dados de ontem
                if (docs.isEmpty()) {
                    sendWelcomeOrStatus(player, server, username, KarmaState.BASE, isLogin, false, 0);
                    result.complete(KarmaState.BASE);
                    return;
                }

                DocumentSnapshot latestDoc = docs.get(0);
                String latestVisitDate = latestDoc.getString("date");

                DocumentSnapshot processedVisit = null;

                if (isToday(latestVisitDate)) {
                    // Se o primeiro documento for de hoje, precisamos do segundo documento para saber os passos de ontem
                    if (docs.size() >= 2) {
                        processedVisit = docs.get(1);
                    }
                } else {
                    // Se o documento mais recente não for de hoje, ele representa o dia anterior com registo
                    processedVisit = latestDoc;
                }

                if (processedVisit == null) {
                    // Estamos no primeiro dia (apenas existe registo de hoje)
                    sendWelcomeOrStatus(player, server, username, storedKarma, isLogin, false, 0);
                    result.complete(storedKarma);
                    return;
                }

                String visitDate = processedVisit.getString("date");
                Long stepsOntem = processedVisit.getLong("steps");

                if (visitDate == null || stepsOntem == null) {
                    sendWelcomeOrStatus(player, server, username, storedKarma, isLogin, false, 0);
                    result.complete(storedKarma);
                    return;
                }

                KarmaState currentKarma = storedKarma;

                // Processa a evolução de karma apenas se for um novo dia ou a meta tiver mudado
                if (!visitDate.equals(lastProcessedVisitDate) || !goal.equals(lastProcessedGoal)) {
                    KarmaState baseKarma = storedKarma;
                    if (visitDate.equals(lastProcessedVisitDate)) {
                        baseKarma = readKarmaBeforeLastProcessedVisit(profileDoc);
                    }

                    boolean goalAchieved = stepsOntem >= goal;
                    currentKarma = calculateFromGoal(baseKarma, goalAchieved);
                    updatePlayerKarma(db, username, currentKarma, baseKarma, visitDate, goal);
                }

                // Envia a mensagem correspondente
                sendWelcomeOrStatus(player, server, username, currentKarma, isLogin, true, stepsOntem);
                result.complete(currentKarma);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result.completeExceptionally(e);
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao calcular karma de {}", username, e);
                if (!isLogin) {
                    PlayerProfileManager.erro(player, server, "Erro ao comunicar com o Firebase.");
                }
                result.completeExceptionally(e);
            }
        }, FirebaseManager.FIREBASE_EXECUTOR);

        return result;
    }

    private static void sendWelcomeOrStatus(
            ServerPlayer player,
            MinecraftServer server,
            String username,
            KarmaState karma,
            boolean isLogin,
            boolean hasYesterdayData,
            long stepsOntem
    ) {
        FirebaseManager.onServerThread(server, () -> {
            // Sincroniza o Karma atual com o cliente via rede para atualização imediata do HUD
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new KarmaPayload(karma.name()));

            if (isLogin) {
                if (!hasYesterdayData) {
                    // Mensagem de boas-vindas para novos jogadores / sem registo de ontem
                    player.sendSystemMessage(Component.literal("Bem-vindo ")
                            .withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(username).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal("! Este é o começo da tua aventura fitness no NudgeCraft!").withStyle(ChatFormatting.AQUA)));
                    return;
                }

                // Mensagem baseada no Karma
                switch (karma) {
                    case VNEGATIVE, NEGATIVE, SNEGATIVE -> {
                        player.sendSystemMessage(Component.literal("O mundo à tua volta está a perder a sua cor... Fizeste " + stepsOntem + " passos ontem.")
                                .withStyle(ChatFormatting.RED));
                    }
                    case SPOSITIVE, POSITIVE, VPOSITIVE -> {
                        player.sendSystemMessage(Component.literal("O mundo à tua volta parece mais radiante e cheio de vida! Fizeste " + stepsOntem + " passos ontem.")
                                .withStyle(ChatFormatting.GREEN));
                    }
                    case BASE -> {
                        player.sendSystemMessage(Component.literal("Mantém o ritmo! Fizeste " + stepsOntem + " passos ontem.")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                }
            } else {
                // Resposta ao comando /karma
                if (!hasYesterdayData) {
                    player.sendSystemMessage(Component.literal("Karma atual: " + karma + " (Ainda sem registos anteriores de passos para avaliar).")
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    ChatFormatting cor = switch (karma) {
                        case VNEGATIVE, NEGATIVE, SNEGATIVE -> ChatFormatting.RED;
                        case SPOSITIVE, POSITIVE, VPOSITIVE -> ChatFormatting.GREEN;
                        case BASE -> ChatFormatting.YELLOW;
                    };
                    player.sendSystemMessage(Component.literal("Karma atual: " + karma + " | Passos de ontem: " + stepsOntem)
                            .withStyle(cor));
                }
            }
        });
    }

    private static boolean isToday(String visitDate) {
        if (visitDate == null) {
            return false;
        }
        try {
            return LocalDate.parse(visitDate).equals(LocalDate.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static void updatePlayerKarma(
            Firestore db,
            String username,
            KarmaState karmaState,
            KarmaState karmaBeforeProcessedVisit,
            String processedVisitDate,
            Long processedGoal
    ) throws Exception {
        db.collection("players")
                .document(username)
                .update(
                        "karma", karmaState.name(),
                        "lastProcessedVisitDate", processedVisitDate,
                        "lastProcessedGoal", processedGoal,
                        "karmaBeforeLastProcessedVisit", karmaBeforeProcessedVisit.name()
                )
                .get();
    }

    private static KarmaState readStoredKarma(DocumentSnapshot profileDoc) {
        String storedKarma = profileDoc.getString("karma");

        if (storedKarma == null) {
            return KarmaState.BASE;
        }

        try {
            return KarmaState.valueOf(storedKarma);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Nudgecraft] Karma inválido guardado para {}: {}",
                    profileDoc.getId(), storedKarma);
            return KarmaState.BASE;
        }
    }

    private static KarmaState readKarmaBeforeLastProcessedVisit(DocumentSnapshot profileDoc) {
        String storedKarma = profileDoc.getString("karmaBeforeLastProcessedVisit");

        if (storedKarma == null) {
            return KarmaState.BASE;
        }

        try {
            return KarmaState.valueOf(storedKarma);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Nudgecraft] Karma anterior inválido guardado para {}: {}",
                    profileDoc.getId(), storedKarma);
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
}
