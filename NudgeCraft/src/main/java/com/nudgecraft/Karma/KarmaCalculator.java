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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Calcula a evolução cronológica do Karma do jogador para todos os dias concluídos
     * desde o último processamento até ontem, garantindo que dias em que o jogador não jogou
     * continuam a fazer evoluir o Karma com base nos passos reais dados.
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
                                    String registrationDateStr = FirebaseManager.getString(profileFields, "registrationDate", null);
                                    String lastProcessedVisitDateStr = FirebaseManager.getString(profileFields, "lastProcessedVisitDate", null);

                                    LocalDate today = LocalDate.now();
                                    LocalDate yesterday = today.minusDays(1);

                                    // 1. Determinar a data de registo inicial do jogador
                                    LocalDate registrationDate;
                                    try {
                                        registrationDate = (registrationDateStr != null) ? LocalDate.parse(registrationDateStr) : today;
                                    } catch (DateTimeParseException e) {
                                        registrationDate = today;
                                    }

                                    // 2. Determinar o último dia concluído já processado
                                    LocalDate lastProcessedDate;
                                    try {
                                        if (lastProcessedVisitDateStr != null) {
                                            lastProcessedDate = LocalDate.parse(lastProcessedVisitDateStr);
                                        } else {
                                            // Se nunca foi processado, o ponto de partida é o dia anterior ao registo
                                            lastProcessedDate = registrationDate.minusDays(1);
                                        }
                                    } catch (DateTimeParseException e) {
                                        lastProcessedDate = registrationDate.minusDays(1);
                                    }

                                    // 3. Mapear os registos de passos do utilizador por data (YYYY-MM-DD -> passos)
                                    Map<String, Long> stepsByDate = new HashMap<>();
                                    for (JsonObject d : docsList) {
                                        JsonObject f = d.has("fields") ? d.getAsJsonObject("fields") : null;
                                        String dDate = FirebaseManager.getString(f, "date", null);
                                        Long dSteps = FirebaseManager.getLong(f, "steps", null);
                                        if (dDate != null && dSteps != null) {
                                            stepsByDate.put(dDate, dSteps);
                                        }
                                    }

                                    // 4. Se o jogador já está no primeiro dia e não há dias anteriores por processar
                                    LocalDate startDate = lastProcessedDate.plusDays(1);
                                    if (startDate.isBefore(registrationDate)) {
                                        startDate = registrationDate;
                                    }

                                    KarmaState currentKarma = storedKarma;
                                    KarmaState karmaBeforeLast = readKarmaBeforeLastProcessedVisit(profileFields);
                                    int daysProcessedCount = 0;
                                    long lastDaySteps = 0;
                                    String lastProcessedString = lastProcessedDate.toString();

                                    // 5. Processar sequencialmente cada dia pendente desde startDate até ontem
                                    LocalDate cursor = startDate;
                                    while (!cursor.isAfter(yesterday)) {
                                        String cursorDateStr = cursor.toString();
                                        Long daySteps = stepsByDate.getOrDefault(cursorDateStr, 0L);

                                        karmaBeforeLast = currentKarma;
                                        boolean goalAchieved = daySteps >= goal;
                                        currentKarma = calculateFromGoal(currentKarma, goalAchieved);

                                        lastDaySteps = daySteps;
                                        lastProcessedString = cursorDateStr;
                                        daysProcessedCount++;

                                        cursor = cursor.plusDays(1);
                                    }

                                    // 6. Atualizar Firestore se foram processados novos dias
                                    if (daysProcessedCount > 0) {
                                        updatePlayerKarma(username, currentKarma, karmaBeforeLast, lastProcessedString, goal);
                                    }

                                    // 7. Enviar feedback e mensagens de boas-vindas
                                    sendWelcomeOrStatus(player, server, username, currentKarma, isLogin, daysProcessedCount, lastDaySteps, yesterday.toString().equals(lastProcessedString));
                                    result.complete(currentKarma);
                                }))
                .exceptionally(ex -> {
                    LOGGER.error("[Nudgecraft] Erro ao calcular karma de {}", username, ex);
                    if (!isLogin) {
                        PlayerProfileManager.erro(player, server, "Erro ao comunicar com o Firestore.");
                    }
                    sendWelcomeOrStatus(player, server, username, KarmaState.BASE, isLogin, 0, 0, false);
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
            int daysProcessedCount,
            long lastDaySteps,
            boolean hasYesterdayData
    ) {
        FirebaseManager.onServerThread(server, () -> {
            // Sincroniza o Karma atual com o cliente via rede para atualização imediata do HUD
            ServerPlayNetworking.send(player, new KarmaPayload(karma.name()));

            // Atualiza a estratégia do jogador no servidor
            com.nudgecraft.manager.KarmaEffectManager.updateStrategy(karma);

            if (isLogin) {
                com.nudgecraft.manager.KarmaEffectManager.setServerLoginTime(System.currentTimeMillis());

                if (daysProcessedCount == 0 && !hasYesterdayData) {
                    // Primeiro dia de registo no estudo
                    player.sendSystemMessage(Component.literal("Bem-vindo/a ")
                            .withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(username).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                            .append(Component.literal("! Este é o começo da tua aventura fitness no NudgeCraft!").withStyle(ChatFormatting.AQUA)));
                    return;
                }

                if (daysProcessedCount > 1) {
                    // Notificação de ausência de múltiplos dias
                    player.sendSystemMessage(Component.literal("Estiveste fora " + daysProcessedCount + " dias! O teu Karma foi atualizado com base nos teus passos diários.")
                            .withStyle(ChatFormatting.GOLD));
                }

                // Mensagem baseada no estado de Karma resultante
                switch (karma) {
                    case VNEGATIVE, NEGATIVE, SNEGATIVE -> {
                        player.sendSystemMessage(Component.literal("O mundo à tua volta está a perder a sua cor... Fizeste " + lastDaySteps + " passos no último dia avaliado.")
                                .withStyle(ChatFormatting.RED));
                    }
                    case SPOSITIVE, POSITIVE, VPOSITIVE -> {
                        player.sendSystemMessage(Component.literal("O mundo à tua volta parece mais radiante e cheio de vida! Fizeste " + lastDaySteps + " passos no último dia avaliado.")
                                .withStyle(ChatFormatting.GREEN));
                    }
                    case BASE -> {
                        player.sendSystemMessage(Component.literal("Mantém o ritmo! Fizeste " + lastDaySteps + " passos no último dia avaliado.")
                                .withStyle(ChatFormatting.YELLOW));
                    }
                }
            } else {
                // Resposta ao comando /karma
                ChatFormatting cor = switch (karma) {
                    case VNEGATIVE, NEGATIVE, SNEGATIVE -> ChatFormatting.RED;
                    case SPOSITIVE, POSITIVE, VPOSITIVE -> ChatFormatting.GREEN;
                    case BASE -> ChatFormatting.YELLOW;
                };
                player.sendSystemMessage(Component.literal("Karma atual: " + karma + (lastDaySteps > 0 ? " | Passos recentes: " + lastDaySteps : ""))
                        .withStyle(cor));
            }
        });
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
