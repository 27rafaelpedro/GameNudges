package com.nudgecraft.manager;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.strategy.KarmaStrategy;
import com.nudgecraft.Karma.strategy.NegativeKarmaStrategy;
import com.nudgecraft.Karma.strategy.PositiveKarmaStrategy;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor global do ciclo de estratégias de Karma no servidor.
 * Controla qual a estratégia ativa de efeitos passivos aplicada a cada jogador no mundo.
 */
public final class KarmaEffectManager {

    private static final Identifier POSITIVE_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("nudgecraft", "positive_speed_boost");
    private static final Map<UUID, Integer> PLAY_TIME_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, CropMessageState> CROP_MSG_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> AIRBORNE_VALID_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ANIMAL_MSG_COOLDOWNS = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> HAS_SEEN_FLOWER = ConcurrentHashMap.newKeySet();

    private static class CropMessageState {
        int displayTicks = 0;
        int cooldownTicks = 0;
    }

    private static volatile KarmaState currentKarma = KarmaState.BASE;
    private static volatile KarmaStrategy activeStrategy = (player, level) -> {};
    private static volatile long serverLoginTime = 0;

    private KarmaEffectManager() {
    }

    public static void setServerLoginTime(long time) {
        serverLoginTime = time;
    }

    public static long getServerLoginTime() {
        return serverLoginTime;
    }

    public static void updateStrategy(KarmaState karma) {
        currentKarma = (karma != null) ? karma : KarmaState.BASE;
        com.nudgecraft.Karma.KarmaStateHolder.set(currentKarma);

        switch (currentKarma) {
            case SPOSITIVE, POSITIVE, VPOSITIVE -> activeStrategy = new PositiveKarmaStrategy();
            case SNEGATIVE, NEGATIVE, VNEGATIVE -> activeStrategy = new NegativeKarmaStrategy();
            case BASE -> activeStrategy = (player, level) -> {};
        }
    }

    public static KarmaStrategy getActiveStrategy() {
        return activeStrategy;
    }

    public static KarmaState getCurrentKarma() {
        return currentKarma;
    }

    public static void tick(ServerPlayer player, ServerLevel level) {
        managePositiveSpeed(player, level);
        TimeSpeedManager.tick(level);
        manageAnimalProximity(player, level);
        updateCropMessageState(player);
        updateFlowerMessageState(player);
        checkHungerLevel(player);
        updateHungerMessageState(player);
        trackPlayTime(player);
        OreVeinManager.tick(player, level);
        FireflyLightingManager.tick(player, level);
        FatigueManager.tick(player, level);
        activeStrategy.applyPassiveEffects(player, level);
    }

    private static void manageAnimalProximity(ServerPlayer player, ServerLevel level) {
        KarmaState current = getCurrentKarma();
        if (current == KarmaState.BASE || current == KarmaState.SPOSITIVE || current == KarmaState.SNEGATIVE) {
            return;
        }

        UUID uuid = player.getUUID();
        int cd = ANIMAL_MSG_COOLDOWNS.getOrDefault(uuid, 0);
        if (cd > 0) {
            ANIMAL_MSG_COOLDOWNS.put(uuid, cd - 1);
            return;
        }

        if (player.tickCount % 20 != 0) {
            return;
        }

        AABB box = player.getBoundingBox().inflate(7.0);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, box);

        if (!animals.isEmpty()) {
            if (current == KarmaState.VPOSITIVE || current == KarmaState.POSITIVE) {
                player.sendSystemMessage(
                        Component.literal("A tua energia atrai a vida selvagem!")
                                .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.ITALIC),
                        true
                );
                ANIMAL_MSG_COOLDOWNS.put(uuid, 900); // 45 segundos de cooldown
            } else if (current == KarmaState.VNEGATIVE || current == KarmaState.NEGATIVE) {
                player.sendSystemMessage(
                        Component.literal("A vida selvagem recua..")
                                .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC),
                        true
                );
                ANIMAL_MSG_COOLDOWNS.put(uuid, 900); // 45 segundos de cooldown
            }
        }
    }

    private static void trackPlayTime(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int ticks = PLAY_TIME_TICKS.getOrDefault(uuid, 0) + 1;

        if (ticks >= 36000) {
            player.sendSystemMessage(
                    Component.literal("§6Já estás a jogar há 30 minutos! Lembra-te de fazer uma pausa e manter-te ativo.")
            );
            PLAY_TIME_TICKS.put(uuid, 0);
        } else {
            PLAY_TIME_TICKS.put(uuid, ticks);
        }
    }

    private static final Map<UUID, Integer> FLOWER_MSG_TICKS = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> HAS_SEEN_HUNGER_MSG = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Integer> HUNGER_MSG_TICKS = new ConcurrentHashMap<>();

    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PLAY_TIME_TICKS.remove(uuid);
        CROP_MSG_STATES.remove(uuid);
        AIRBORNE_VALID_TICKS.remove(uuid);
        ANIMAL_MSG_COOLDOWNS.remove(uuid);
        HAS_SEEN_FLOWER.remove(uuid);
        FLOWER_MSG_TICKS.remove(uuid);
        HAS_SEEN_HUNGER_MSG.remove(uuid);
        HUNGER_MSG_TICKS.remove(uuid);
        OreVeinManager.onPlayerDisconnect(player);
        FireflyLightingManager.onPlayerDisconnect(player);
        FatigueManager.onPlayerDisconnect(player);
        TimeSpeedManager.onPlayerDisconnect(player);
    }

    public static void checkHungerLevel(ServerPlayer player) {
        KarmaState current = getCurrentKarma();
        if (current == KarmaState.SNEGATIVE || current == KarmaState.NEGATIVE || current == KarmaState.VNEGATIVE) {
            if (player.getFoodData().getFoodLevel() <= 10) {
                UUID uuid = player.getUUID();
                if (HAS_SEEN_HUNGER_MSG.add(uuid)) {
                    HUNGER_MSG_TICKS.put(uuid, 100); // 5 segundos
                }
            }
        }
    }

    private static void updateHungerMessageState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Integer ticks = HUNGER_MSG_TICKS.get(uuid);
        if (ticks != null && ticks > 0) {
            HUNGER_MSG_TICKS.put(uuid, ticks - 1);
            if (ticks % 20 == 0 || ticks == 100) {
                player.sendSystemMessage(
                        Component.literal("O teu apetite aumenta..")
                                .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC),
                        true
                );
            }
        }
    }



    public static void triggerFlowerMessage(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (HAS_SEEN_FLOWER.add(uuid)) {
            FLOWER_MSG_TICKS.put(uuid, 100); // 5 segundos
        }
    }

    private static void updateFlowerMessageState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Integer ticks = FLOWER_MSG_TICKS.get(uuid);
        if (ticks != null && ticks > 0) {
            FLOWER_MSG_TICKS.put(uuid, ticks - 1);
            if (ticks % 20 == 0 || ticks == 100) { // Envia a cada segundo para manter no ecrã
                player.sendSystemMessage(
                        Component.literal("A natureza decora o teu caminho!")
                                .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.ITALIC),
                        true
                );
            }
        }
    }

    public static void triggerCropMessage(ServerPlayer player, boolean isGrowth) {
        UUID uuid = player.getUUID();
        CropMessageState state = CROP_MSG_STATES.computeIfAbsent(uuid, k -> new CropMessageState());

        if (state.cooldownTicks == 0 && state.displayTicks == 0) {
            state.displayTicks = 100;
            sendCropActionBarMessage(player);
        }
    }

    private static void updateCropMessageState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        CropMessageState state = CROP_MSG_STATES.get(uuid);
        if (state == null) {
            return;
        }

        if (state.cooldownTicks > 0) {
            state.cooldownTicks--;
        } else if (state.displayTicks > 0) {
            state.displayTicks--;
            if (state.displayTicks % 20 == 0 && state.displayTicks > 0) {
                sendCropActionBarMessage(player);
            }
            if (state.displayTicks == 0) {
                state.cooldownTicks = 200;
            }
        }
    }

    private static void sendCropActionBarMessage(ServerPlayer player) {
        KarmaState karma = getCurrentKarma();
        Component msg = null;

        if (karma == KarmaState.VPOSITIVE || karma == KarmaState.POSITIVE) {
            msg = Component.literal("As tuas sementes crescem com a tua energia positiva!")
                    .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.ITALIC);
        } else if (karma == KarmaState.VNEGATIVE || karma == KarmaState.NEGATIVE) {
            msg = Component.literal("As sementes sofrem com a tua energia..")
                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC);
        }

        if (msg != null) {
            player.sendSystemMessage(msg, true);
        }
    }

    private static void managePositiveSpeed(ServerPlayer player, ServerLevel level) {
        AttributeInstance attributeInstance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attributeInstance == null) {
            return;
        }

        double boostValue = 0.0;
        KarmaState current = getCurrentKarma();
        if (current == KarmaState.SPOSITIVE) {
            boostValue = 0.10;
        } else if (current == KarmaState.POSITIVE) {
            boostValue = 0.20;
        } else if (current == KarmaState.VPOSITIVE) {
            boostValue = 0.50;
        }

        boolean isDay = !level.isDarkOutside();
        UUID uuid = player.getUUID();
        boolean isOnGround = player.onGround();

        BlockState stateOn = player.getBlockStateOn();
        boolean onValidSurface = isNaturalBuildingSurface(stateOn);

        if (!onValidSurface) {
            BlockState stateBelow = level.getBlockState(player.blockPosition().below());
            onValidSurface = isNaturalBuildingSurface(stateBelow);
        }

        if (!onValidSurface) {
            BlockState stateMovement = level.getBlockState(player.getOnPos());
            onValidSurface = isNaturalBuildingSurface(stateMovement);
        }

        boolean effectiveValidSurface = false;
        if (onValidSurface) {
            AIRBORNE_VALID_TICKS.put(uuid, 0);
            effectiveValidSurface = true;
        } else if (!isOnGround) {
            int airTicks = AIRBORNE_VALID_TICKS.getOrDefault(uuid, 999) + 1;
            if (airTicks <= 25) {
                AIRBORNE_VALID_TICKS.put(uuid, airTicks);
                effectiveValidSurface = true;
            } else {
                AIRBORNE_VALID_TICKS.put(uuid, airTicks);
            }
        } else {
            AIRBORNE_VALID_TICKS.put(uuid, 999);
        }

        if (boostValue > 0.0 && isDay && effectiveValidSurface) {
            AttributeModifier existing = attributeInstance.getModifier(POSITIVE_SPEED_MODIFIER_ID);
            if (existing == null || existing.amount() != boostValue) {
                if (existing != null) {
                    attributeInstance.removeModifier(POSITIVE_SPEED_MODIFIER_ID);
                }
                attributeInstance.addTransientModifier(new AttributeModifier(
                        POSITIVE_SPEED_MODIFIER_ID,
                        boostValue,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        } else {
            if (attributeInstance.hasModifier(POSITIVE_SPEED_MODIFIER_ID)) {
                attributeInstance.removeModifier(POSITIVE_SPEED_MODIFIER_ID);
            }
        }
    }



    private static boolean isNaturalBuildingSurface(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.MUD) || state.is(Blocks.PODZOL)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MYCELIUM)) {
            return true;
        }
        if (state.is(BlockTags.SAND) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.SMOOTH_RED_SANDSTONE)
                || state.is(Blocks.CUT_SANDSTONE) || state.is(Blocks.CUT_RED_SANDSTONE)) {
            return true;
        }
        if (state.is(BlockTags.SNOW) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return true;
        }
        if (state.is(BlockTags.TERRACOTTA) || state.is(Blocks.TERRACOTTA)) {
            return true;
        }
        return false;
    }
}
