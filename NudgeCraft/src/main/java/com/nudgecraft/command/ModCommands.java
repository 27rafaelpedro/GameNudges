package com.nudgecraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;

import com.nudgecraft.firebase.FirebaseManager;
import com.nudgecraft.firebase.LinkedAccounts;
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

            // /linkemail <email> — greedyString porque word() não aceita '@'
            dispatcher.register(Commands.literal("linkemail")
                    .then(Commands.argument("email", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                LinkedAccounts.ligarEmail(player, StringArgumentType.getString(ctx, "email"));
                                return 1;
                            })));

            dispatcher.register(Commands.literal("unlinkemail")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        LinkedAccounts.desligarEmail(player);
                        return 1;
                    }));

            dispatcher.register(Commands.literal("steps")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        StepsManager.buscarSteps(player);
                        return 1;
                    }));

            // Consulta administrativa por email (dados de terceiros -> restrito a gamemasters)
            dispatcher.register(Commands.literal("nudgesteps")
                    .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("email", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                CommandSourceStack source = ctx.getSource();
                                FirebaseManager.consultarPassosPorEmail(
                                        StringArgumentType.getString(ctx, "email").trim(), source);
                                return 1;
                            })));
        });
    }
}
