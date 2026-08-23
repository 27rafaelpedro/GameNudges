package com.nudgecraft.Karma;

import com.google.gson.JsonObject;
import com.nudgecraft.firebase.FirebaseManager;
import com.nudgecraft.firebase.PlayerProfileManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
        String username = PlayerProfileManager.getUsername(player);
        String uuid = player.getStringUUID();

        PlayerProfileManager.getOrCreateProfile(username, uuid)
                .thenCompose(profileFields ->
                        FirebaseManager.queryUserVisits(username)
                                .thenAccept(docsList -> {
                                    Long goal = FirebaseManager.getLong(profileFields, "goal", PlayerProfileManager.DEFAULT_GOAL);
                                    if (goal == null || goal <= 0) {
                                        goal = PlayerProfileManager.DEFAULT_GOAL;
                                    }

                                    KarmaState storedKarma = readStoredKarma(profileFields);
                                    String lastProcessedVisitDate = FirebaseManager.getString(profileFields, "lastProcessedVisitDate", null);
                                    Long lastProcessedGoal = FirebaseManager.getLong(profileFields, "lastProcessedGoal", null);

                                    if (docsList.isEmpty()) {
                                        sendWelcomeOrStatus(player, server, username, KarmaState.BASE, isLogin, false, 0);
                                        result.complete(KarmaState.BASE);
                                        return;
                                    }

                                    List<JsonObject> docs = new ArrayList<>(docsList);
                                    docs.sort((d1, d2) -> {
                                        JsonObject f1 = d1.has("fields") ? d1.getAsJsonObject("fields") : null;
                                        JsonObject f2 = d2.has("fields") ? d2.getAsJsonObject("fields") : null;
                                        String date1 = FirebaseManager.getString(f1, "date", "");
                                        String date2 = FirebaseManager.getString(f2, "date", "");
                                        return date2.compareTo(date1);
                                    });

                                    JsonObject latestDoc = docs.get(0);
                                    JsonObject latestFields = latestDoc.has("fields") ? latestDoc.getAsJsonObject("fields") : null;
                                    String latestVisitDate = FirebaseManager.getString(latestFields, "date", null);

                                    JsonObject processedDoc = null;
                                    if (isToday(latestVisitDate)) {
                                        if (docs.size() >= 2) {
                                            processedDoc = docs.get(1);
                                        }
                                    } else {
                                        processedDoc = latestDoc;
                                    }

                                    if (processedDoc == null) {
                                        sendWelcomeOrStatus(player, server, username, storedKarma, isLogin, false, 0);
                                        result.complete(storedKarma);
                                        return;
                                    }

                                    JsonObject processedFields = processedDoc.has("fields") ? processedDoc.getAsJsonObject("fields") : null;
                                    String visitDate = FirebaseManager.getString(processedFields, "date", null);
                                    Long stepsOntem = FirebaseManager.getLong(processedFields, "steps", null);

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
                                            baseKarma = readKarmaBeforeLastProcessedVisit(profileFields);
                                        }

                                        boolean goalAchieved = stepsOntem >= goal;
                                        currentKarma = calculateFromGoal(baseKarma, goalAchieved);
                                        updatePlayerKarma(username, currentKarma, baseKarma, visitDate, goal);
                                    }

                                    sendWelcomeOrStatus(player, server, username, currentKarma, isLogin, true, stepsOntem);
                                    result.complete(currentKarma);
                                }))
                .exceptionally(ex -> {
                    LOGGER.error("[Nudgecraft] Erro ao calcular karma de {}", username, ex);
                    if (!isLogin) {
                        PlayerProfileManager.erro(player, server, "Erro ao comunicar com o Firestore.");
                    }
                    sendWelcomeOrStatus(player, server, username, KarmaState.BASE, isLogin, false, 0);
                    result.complete(KarmaState.BASE);
                    return null;
                });

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
            ServerPlayNetworking.send(player, new KarmaPayload(karma.name()));

            // Atualiza a estratégia do jogador no servidor
            com.nudgecraft.manager.KarmaEffectManager.updateStrategy(karma);

            if (isLogin) {
                com.nudgecraft.manager.KarmaEffectManager.setServerLoginTime(System.currentTimeMillis());
                if (!hasYesterdayData) {
                    // Mensagem de boas-vindas para novos jogadores / sem registo de ontem
                    player.sendSystemMessage(Component.literal("Bem-vindo/a ")
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
            String username,
            KarmaState karmaState,
            KarmaState karmaBeforeProcessedVisit,
            String processedVisitDate,
            Long processedGoal
    ) {
        JsonObject fields = new JsonObject();
        fields.add("karma", FirebaseManager.stringField(karmaState.name()));
        fields.add("lastProcessedVisitDate", FirebaseManager.stringField(processedVisitDate));
        fields.add("lastProcessedGoal", FirebaseManager.integerField(processedGoal));
        fields.add("karmaBeforeLastProcessedVisit", FirebaseManager.stringField(karmaBeforeProcessedVisit.name()));

        List<String> mask = List.of("karma", "lastProcessedVisitDate", "lastProcessedGoal", "karmaBeforeLastProcessedVisit");
        FirebaseManager.patchDocument("players", username, fields, mask);
    }

    private static KarmaState readStoredKarma(JsonObject profileFields) {
        String storedKarma = FirebaseManager.getString(profileFields, "karma", null);

        if (storedKarma == null) {
            return KarmaState.BASE;
        }

        try {
            return KarmaState.valueOf(storedKarma);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Nudgecraft] Karma inválido guardado: {}", storedKarma);
            return KarmaState.BASE;
        }
    }

    private static KarmaState readKarmaBeforeLastProcessedVisit(JsonObject profileFields) {
        String storedKarma = FirebaseManager.getString(profileFields, "karmaBeforeLastProcessedVisit", null);

        if (storedKarma == null) {
            return KarmaState.BASE;
        }

        try {
            return KarmaState.valueOf(storedKarma);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Nudgecraft] Karma anterior inválido guardado: {}", storedKarma);
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
