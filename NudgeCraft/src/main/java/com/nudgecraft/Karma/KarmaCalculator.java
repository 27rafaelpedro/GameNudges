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
     * Ponto de entrada chamado no login do jogador para calcular e sincronizar o Karma.
     */
    public static void processPlayerLogin(ServerPlayer player) {
        calculateKarma(player, true);
    }

    /**
     * Calcula o Karma do jogador com base nos passos de ontem registados a partir da data de registo/instalação.
     * Ignora quaisquer registos históricos anteriores ao dia de entrada no estudo.
     */
    public static CompletableFuture<KarmaState> calculateKarma(ServerPlayer player, boolean isLogin) {
        String username = PlayerProfileManager.getUsername(player);
        String uuid = player.getStringUUID();
        MinecraftServer server = player.level().getServer();

        CompletableFuture<KarmaState> result = new CompletableFuture<>();

        PlayerProfileManager.getOrCreateProfile(username, uuid)
                .thenCompose(profileFields ->
                        FirebaseManager.queryUserVisits(username)
                                .thenAccept(docsList -> {
                                    Long goal = FirebaseManager.getLong(profileFields, "goal", PlayerProfileManager.DEFAULT_GOAL);
                                    if (goal == null || goal <= 0) {
                                        goal = PlayerProfileManager.DEFAULT_GOAL;
                                    }

                                    KarmaState storedKarma = readStoredKarma(profileFields);
                                    String registrationDate = FirebaseManager.getString(profileFields, "registrationDate", null);
                                    String lastProcessedVisitDate = FirebaseManager.getString(profileFields, "lastProcessedVisitDate", null);
                                    Long lastProcessedGoal = FirebaseManager.getLong(profileFields, "lastProcessedGoal", null);

                                    // Se for o primeiro acesso absoluto, inicializa a data de registo para hoje
                                    if (registrationDate == null) {
                                        registrationDate = LocalDate.now().toString();
                                    }

                                    final String finalRegDate = registrationDate;

                                    // Filtra registos anteriores à data de instalação/registo do jogador
                                    List<JsonObject> docs = new ArrayList<>();
                                    for (JsonObject d : docsList) {
                                        JsonObject f = d.has("fields") ? d.getAsJsonObject("fields") : null;
                                        String dDate = FirebaseManager.getString(f, "date", "");
                                        if (dDate.compareTo(finalRegDate) >= 0) {
                                            docs.add(d);
                                        }
                                    }

                                    if (docs.isEmpty()) {
                                        sendWelcomeOrStatus(player, server, username, KarmaState.BASE, isLogin, false, 0);
                                        result.complete(KarmaState.BASE);
                                        return;
                                    }

                                    // Ordena por data decrescente (mais recente primeiro)
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

                                    // Se não houver dia anterior válido registado DEPOIS da data de registo, mantém BASE
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
                    // Mensagem de boas-vindas para novos jogadores / primeiro dia
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
                    player.sendSystemMessage(Component.literal("Karma atual: " + karma + " (Primeiro dia de registo no estudo / ponto de partida BASE).")
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

    private static KarmaState calculateFromGoal(KarmaState current, boolean goalAchieved) {
        if (goalAchieved) {
            return switch (current) {
                case VNEGATIVE -> KarmaState.NEGATIVE;
                case NEGATIVE -> KarmaState.SNEGATIVE;
                case SNEGATIVE -> KarmaState.BASE;
                case BASE -> KarmaState.SPOSITIVE;
                case SPOSITIVE -> KarmaState.POSITIVE;
                case POSITIVE, VPOSITIVE -> KarmaState.VPOSITIVE;
            };
        } else {
            return switch (current) {
                case VPOSITIVE -> KarmaState.POSITIVE;
                case POSITIVE -> KarmaState.SPOSITIVE;
                case SPOSITIVE -> KarmaState.BASE;
                case BASE -> KarmaState.SNEGATIVE;
                case SNEGATIVE -> KarmaState.NEGATIVE;
                case NEGATIVE, VNEGATIVE -> KarmaState.VNEGATIVE;
            };
        }
    }
}
