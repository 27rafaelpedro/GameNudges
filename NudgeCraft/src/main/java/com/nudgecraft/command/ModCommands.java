package com.nudgecraft.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.nudgecraft.Karma.KarmaCalculator;
import com.nudgecraft.firebase.FirebaseManager;
import com.nudgecraft.firebase.PlayerProfileManager;
import com.nudgecraft.firebase.StepsManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class ModCommands {

    private ModCommands() {
    }

    public static void registar() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // /steps — Mostra os passos de hoje e de ontem do jogador
            dispatcher.register(Commands.literal("steps")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        StepsManager.buscarSteps(player);
                        return 1;
                    }));


            // /setgoal <passos> — Define a meta de passos diários do jogador
            dispatcher.register(Commands.literal("setgoal")
                    .then(Commands.argument("passos", LongArgumentType.longArg(1))
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                long goal = LongArgumentType.getLong(ctx, "passos");
                                PlayerProfileManager.setGoal(player, goal);
                                return 1;
                            })));

            // /nudgesteps <username> — Consulta administrativa por nome de jogador (restrito a gamemasters)
            dispatcher.register(Commands.literal("nudgesteps")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("username", StringArgumentType.word())
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                String username = StringArgumentType.getString(ctx, "username").trim();
                                FirebaseManager.consultarPassosPorUsername(username, source);
                                return 1;
                            })));
        });
    }
}
