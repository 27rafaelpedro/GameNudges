package com.nudgecraft.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaStateHolder;
import com.nudgecraft.Karma.KarmaPayload;
import com.nudgecraft.manager.KarmaEffectManager;
import com.nudgecraft.firebase.FirebaseManager;
import com.nudgecraft.firebase.PlayerProfileManager;
import com.nudgecraft.firebase.StepsManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Gestor e registador de comandos de consola (/steps, /setgoal, /nudgesteps, /setkarma) do mod NudgeCraft.
 */
public final class ModCommands {

    private ModCommands() {
    }

    /**
     * Regista todos os comandos associados ao ciclo de eventos de comandos do Fabric.
     */
    public static void registar() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // Comando /steps: consulta passos de hoje e de ontem
            dispatcher.register(Commands.literal("steps")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        StepsManager.buscarSteps(player);
                        return 1;
                    }));

            // Comando /setgoal: define meta de passos
            dispatcher.register(Commands.literal("setgoal")
                    .then(Commands.argument("passos", LongArgumentType.longArg(1))
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                long goal = LongArgumentType.getLong(ctx, "passos");
                                PlayerProfileManager.setGoal(player, goal);
                                return 1;
                            })));

            // Comando /nudgesteps: consulta passos de outros jogadores (apenas GameMasters)
            dispatcher.register(Commands.literal("nudgesteps")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("username", StringArgumentType.word())
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                String username = StringArgumentType.getString(ctx, "username").trim();
                                FirebaseManager.consultarPassosPorUsername(username, source);
                                return 1;
                            })));

            // Comando /setkarma: força dinamicamente o Karma para testes rápidos (apenas GameMasters)
            dispatcher.register(Commands.literal("setkarma")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("estado", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("vpositive");
                                builder.suggest("positive");
                                builder.suggest("spositive");
                                builder.suggest("base");
                                builder.suggest("snegative");
                                builder.suggest("negative");
                                builder.suggest("vnegative");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String estadoStr = StringArgumentType.getString(ctx, "estado").toUpperCase();
                                try {
                                    KarmaState karma = KarmaState.valueOf(estadoStr);

                                    // Sincroniza HUD do cliente
                                    ServerPlayNetworking.send(player, new KarmaPayload(karma.name()));

                                    // Atualiza estratégia no servidor
                                    KarmaEffectManager.updateStrategy(karma);
                                    KarmaStateHolder.set(karma);

                                    // Redefine login time para teste instantâneo de partículas
                                    KarmaEffectManager.setServerLoginTime(System.currentTimeMillis());

                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "§a[Nudgecraft] Karma definido com sucesso para: §e" + karma.name()
                                    ), true);
                                } catch (IllegalArgumentException e) {
                                    ctx.getSource().sendFailure(Component.literal(
                                            "§c[Nudgecraft] Estado de Karma inválido. Escolha um dos seguintes: " +
                                            "vpositive, positive, spositive, base, snegative, negative, vnegative"
                                    ));
                                }
                                return 1;
                            })));
        });
    }
}
