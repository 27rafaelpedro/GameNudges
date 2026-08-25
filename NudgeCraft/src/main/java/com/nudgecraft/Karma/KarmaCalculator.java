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

/**
 * Calculador de Karma responsável por avaliar a atividade física do jogador
 * e sincronizar o estado ambiental correspondente no ecossistema NudgeCraft.
 */
public final class KarmaCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    private KarmaCalculator() {
    }

    /**
     * Processa a entrada do jogador no servidor, iniciando o cálculo de Karma de login.
     *
     * @param player O jogador que entrou no servidor.
     */
    public static void processPlayerLogin(ServerPlayer player) {
        calculateKarma(player, true);
    }

    /**
     * Calcula a evolução cronológica do Karma do jogador para todos os dias concluídos
     * desde o último processamento até ontem, garantindo que dias em que o jogador não jogou
     * continuam a fazer evoluir o Karma com base nos passos reais dados.
     *
     * @param player  O jogador a avaliar.
     * @param isLogin Indica se a chamada teve origem no evento de login.
     * @return CompletableFuture com o estado de Karma resultante.
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

                                    LocalDate registrationDate;
                                    try {
                                        registrationDate = (registrationDateStr != null) ? LocalDate.parse(registrationDateStr) : today;
                                    } catch (DateTimeParseException e) {
                                        registrationDate = today;
                                    }

                                    LocalDate lastProcessedDate;
                                    try {
                                        if (lastProcessedVisitDateStr != null) {
                                            lastProcessedDate = LocalDate.parse(lastProcessedVisitDateStr);
                                        } else {
                                            lastProcessedDate = registrationDate.minusDays(1);
                                        }
                                    } catch (DateTimeParseException e) {
                                        lastProcessedDate = registrationDate.minusDays(1);
                                    }

                                    Map<String, Long> stepsByDate = new HashMap<>();
                                    for (JsonObject d : docsList) {
                                        JsonObject f = d.has("fields") ? d.getAsJsonObject("fields") : null;
                                        String dDate = FirebaseManager.getString(f, "date", null);
                                        Long dSteps = FirebaseManager.getLong(f, "steps", null);
                                        if (dDate != null && dSteps != null) {
                                            stepsByDate.put(dDate, dSteps);
                                        }
                                    }

                                    LocalDate startDate = lastProcessedDate.plusDays(1);
                                    if (startDate.isBefore(registrationDate)) {
                                        startDate = registrationDate;
                                    }

                                    KarmaState currentKarma = storedKarma;
                                    KarmaState karmaBeforeLast = readKarmaBeforeLastProcessedVisit(profileFields);
                                    int daysProcessedCount = 0;
                                    String lastProcessedString = lastProcessedDate.toString();
                                    long lastDaySteps = stepsByDate.getOrDefault(lastProcessedString, stepsByDate.getOrDefault(yesterday.toString(), 0L));

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

                                    if (daysProcessedCount > 0) {
                                        updatePlayerKarma(username, currentKarma, karmaBeforeLast, lastProcessedString, goal);
                                    }

                                    sendWelcomeOrStatus(player, server, username, currentKarma, isLogin, daysProcessedCount, lastDaySteps);
                                    result.complete(currentKarma);
                                }))
                .exceptionally(ex -> {
                    LOGGER.error("[Nudgecraft] Erro ao calcular karma de {}", username, ex);
                    if (!isLogin) {
                        PlayerProfileManager.erro(player, server, "Erro ao comunicar com o Firestore.");
                    }
                    sendWelcomeOrStatus(player, server, username, KarmaState.BASE, isLogin, 0, 0);
                    result.complete(KarmaState.BASE);
                    return null;
                });

        return result;
    }

    /**
     * Envia as mensagens de receção e atualiza o estado de rede e estratégia do jogador no servidor.
     */
    private static void sendWelcomeOrStatus(
            ServerPlayer player,
            MinecraftServer server,
            String username,
            KarmaState karma,
            boolean isLogin,
            int daysProcessedCount,
            long lastDaySteps
    ) {
        FirebaseManager.onServerThread(server, () -> {
            ServerPlayNetworking.send(player, new KarmaPayload(karma.name()));
            com.nudgecraft.manager.KarmaEffectManager.updateStrategy(karma);
            com.nudgecraft.Karma.KarmaStateHolder.set(karma);

            if (isLogin) {
                com.nudgecraft.manager.KarmaEffectManager.setServerLoginTime(System.currentTimeMillis());

                if (daysProcessedCount > 1) {
                    player.sendSystemMessage(Component.literal("Estiveste fora " + daysProcessedCount + " dias! O teu Karma foi atualizado com base nos teus passos diários.")
                            .withStyle(ChatFormatting.GOLD));
                }

                switch (karma) {
                    case BASE -> {
                        player.sendSystemMessage(Component.literal("Bem-vindo/a ")
                                .withStyle(ChatFormatting.AQUA)
                                .append(Component.literal(username).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                .append(Component.literal("! Este é o começo da tua aventura fitness no NudgeCraft!").withStyle(ChatFormatting.AQUA)));
                    }
                    case VNEGATIVE, NEGATIVE, SNEGATIVE -> {
                        player.sendSystemMessage(Component.literal("O mundo à tua volta está a perder a sua cor... Fizeste " + lastDaySteps + " passos no último dia avaliado.")
                                .withStyle(ChatFormatting.RED));
                    }
                    case SPOSITIVE, POSITIVE, VPOSITIVE -> {
                        player.sendSystemMessage(Component.literal("O mundo à tua volta parece mais radiante e cheio de vida! Fizeste " + lastDaySteps + " passos no último dia avaliado.")
                                .withStyle(ChatFormatting.GREEN));
                    }
                }
            } else {
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

    /**
     * Atualiza o estado de Karma e as datas de controlo do jogador no Cloud Firestore.
     */
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

    /**
     * Lê o estado de Karma guardado no perfil do jogador.
     */
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

    /**
     * Lê o estado de Karma anterior à última visita processada.
     */
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

    /**
     * Calcula a transição de estado de Karma:
     * - No Dia 1 (BASE): ao cumprir a meta vai direto para POSITIVE; ao falhar vai para NEGATIVE.
     * - A partir daí: evolui para VPOSITIVE / VNEGATIVE ou ajusta para SPOSITIVE / SNEGATIVE.
     *
     * @param current      O estado de Karma atual.
     * @param goalAchieved Verdadeiro se a meta de passos foi atingida, falso caso contrário.
     * @return O próximo estado de Karma resultante.
     */
    private static KarmaState calculateFromGoal(KarmaState current, boolean goalAchieved) {
        if (goalAchieved) {
            return switch (current) {
                case VNEGATIVE -> KarmaState.NEGATIVE;
                case NEGATIVE -> KarmaState.SNEGATIVE;
                case SNEGATIVE -> KarmaState.SPOSITIVE;
                case BASE -> KarmaState.POSITIVE;
                case SPOSITIVE -> KarmaState.POSITIVE;
                case POSITIVE, VPOSITIVE -> KarmaState.VPOSITIVE;
            };
        } else {
            return switch (current) {
                case VPOSITIVE -> KarmaState.POSITIVE;
                case POSITIVE -> KarmaState.SPOSITIVE;
                case SPOSITIVE -> KarmaState.SNEGATIVE;
                case BASE -> KarmaState.NEGATIVE;
                case SNEGATIVE -> KarmaState.NEGATIVE;
                case NEGATIVE, VNEGATIVE -> KarmaState.VNEGATIVE;
            };
        }
    }
}
