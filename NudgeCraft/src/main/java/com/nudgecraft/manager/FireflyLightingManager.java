package com.nudgecraft.manager;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de Pirilampos (Fireflies), Iluminação Dinâmica e Mensagens Atmosféricas de Escuridão.
 * - SPOSITIVE: Partículas nativas de Firefly ao redor do jogador, sem iluminação nos blocos.
 * - POSITIVE:  Partículas nativas de Firefly + iluminação suave estável (Nível 6).
 * - VPOSITIVE: Partículas nativas de Firefly + iluminação forte estável (Nível 12).
 *
 * Envia mensagens na Action Bar ao afastar-se de fontes de luz:
 * - VPOSITIVE: "A natureza ilumina o teu caminho!"
 * - VNEGATIVE: "A tua visão falha.."
 */
public final class FireflyLightingManager {

    private static final Map<UUID, BlockPos> ACTIVE_LIGHT_BLOCKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> WAS_NEAR_LIGHT = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LIGHT_MSG_COOLDOWN = new ConcurrentHashMap<>();

    private static final int MSG_COOLDOWN_TICKS = 1200;

    private FireflyLightingManager() {
    }

    /**
     * Processa a emissão de pirilampos, iluminação dinâmica e mensagens de luz a cada tick.
     */
    public static void tick(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();
        KarmaState current = KarmaEffectManager.getCurrentKarma();

        int cooldown = LIGHT_MSG_COOLDOWN.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            LIGHT_MSG_COOLDOWN.put(uuid, cooldown - 1);
        }

        boolean isPositiveKarma = (current == KarmaState.SPOSITIVE || current == KarmaState.POSITIVE || current == KarmaState.VPOSITIVE);
        boolean isNight = level.isDarkOutside();

        boolean isNearTorch = isNearArtificialLight(level, player.blockPosition(), 5);
        boolean wasNearTorch = WAS_NEAR_LIGHT.getOrDefault(uuid, true);

        if (wasNearTorch && !isNearTorch) {
            handleMovingAwayFromLight(player, level, uuid, current, isNight);
        }
        WAS_NEAR_LIGHT.put(uuid, isNearTorch);

        boolean shouldBeActive = isPositiveKarma && isNight && !isNearTorch;

        if (shouldBeActive) {
            spawnFireflies(player, level, current);

            int lightLevel = 0;
            if (current == KarmaState.POSITIVE) {
                lightLevel = 6;
            } else if (current == KarmaState.VPOSITIVE) {
                lightLevel = 12;
            }

            updatePlayerLight(player, level, uuid, lightLevel);
        } else {
            clearPlayerLight(level, uuid);
        }
    }

    /**
     * Aciona a mensagem na Action Bar ao afastar-se de fontes de luz caso o cooldown tenha expirado.
     */
    private static void handleMovingAwayFromLight(ServerPlayer player, ServerLevel level, UUID uuid, KarmaState karma, boolean isNight) {
        int cooldown = LIGHT_MSG_COOLDOWN.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            return;
        }

        if (karma == KarmaState.VPOSITIVE || karma == KarmaState.POSITIVE) {
            if (isNight) {
                player.sendSystemMessage(
                        Component.literal("A natureza ilumina o teu caminho!")
                                .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.ITALIC),
                        true
                );
                LIGHT_MSG_COOLDOWN.put(uuid, MSG_COOLDOWN_TICKS);
            }
        } else if (karma == KarmaState.VNEGATIVE || karma == KarmaState.NEGATIVE) {
            if (isNight) {
                player.sendSystemMessage(
                        Component.literal("A tua visão falha..")
                                .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC),
                        true
                );
                level.sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.2, player.getZ(),
                        5, 0.3, 0.3, 0.3, 0.01);
                LIGHT_MSG_COOLDOWN.put(uuid, MSG_COOLDOWN_TICKS);
            }
        }
    }

    /**
     * Emite as partículas autênticas de Firefly orbitando o jogador
     * e herdando a sua velocidade de movimento.
     */
    private static void spawnFireflies(ServerPlayer player, ServerLevel level, KarmaState karma) {
        int count = switch (karma) {
            case VPOSITIVE -> 3;
            case POSITIVE -> 2;
            case SPOSITIVE -> 1;
            default -> 0;
        };

        net.minecraft.world.phys.Vec3 motion = player.getDeltaMovement();

        for (int i = 0; i < count; i++) {
            if (level.getRandom().nextFloat() < 0.60f) {
                double angle = level.getRandom().nextDouble() * 2 * Math.PI;
                double dist = 0.5 + level.getRandom().nextDouble() * 1.1;

                double px = player.getX() + Math.cos(angle) * dist;
                double py = player.getY() + 0.3 + level.getRandom().nextDouble() * 1.5;
                double pz = player.getZ() + Math.sin(angle) * dist;

                double vx = motion.x * 0.85 + (level.getRandom().nextDouble() - 0.5) * 0.025;
                double vy = motion.y * 0.85 + (level.getRandom().nextDouble() - 0.2) * 0.025;
                double vz = motion.z * 0.85 + (level.getRandom().nextDouble() - 0.5) * 0.025;

                level.sendParticles(ParticleTypes.FIREFLY, px, py, pz, 1, vx, vy, vz, 0.0);
            }
        }
    }

    /**
     * Atualiza o bloco invisível de luz dinâmica que acompanha os movimentos do jogador sem piscar.
     */
    private static void updatePlayerLight(ServerPlayer player, ServerLevel level, UUID uuid, int lightLevel) {
        if (lightLevel <= 0) {
            clearPlayerLight(level, uuid);
            return;
        }

        BlockPos targetPos = findBestLightPosition(player, level);
        if (targetPos == null) {
            return;
        }

        BlockPos oldPos = ACTIVE_LIGHT_BLOCKS.get(uuid);

        if (oldPos != null && !oldPos.equals(targetPos)) {
            BlockState oldState = level.getBlockState(oldPos);
            if (oldState.is(Blocks.LIGHT)) {
                level.setBlock(oldPos, Blocks.AIR.defaultBlockState(), 3);
            }
            ACTIVE_LIGHT_BLOCKS.remove(uuid);
        }

        BlockState targetState = level.getBlockState(targetPos);
        BlockState desiredLightState = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lightLevel);

        if (targetState.isAir() || targetState.is(Blocks.LIGHT)) {
            if (!targetState.equals(desiredLightState)) {
                level.setBlock(targetPos, desiredLightState, 3);
            }
            ACTIVE_LIGHT_BLOCKS.put(uuid, targetPos);
        }
    }

    /**
     * Encontra a melhor posição para o bloco de luz ao redor do jogador.
     */
    private static BlockPos findBestLightPosition(ServerPlayer player, ServerLevel level) {
        BlockPos headPos = player.blockPosition().above();
        BlockState headState = level.getBlockState(headPos);

        if (headState.isAir() || headState.is(Blocks.LIGHT)) {
            return headPos;
        }

        BlockPos feetPos = player.blockPosition();
        BlockState feetState = level.getBlockState(feetPos);
        if (feetState.isAir() || feetState.is(Blocks.LIGHT)) {
            return feetPos;
        }

        BlockPos topPos = headPos.above();
        BlockState topState = level.getBlockState(topPos);
        if (topState.isAir() || topState.is(Blocks.LIGHT)) {
            return topPos;
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sidePos = headPos.relative(dir);
            BlockState sideState = level.getBlockState(sidePos);
            if (sideState.isAir() || sideState.is(Blocks.LIGHT)) {
                return sidePos;
            }
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sideFeetPos = feetPos.relative(dir);
            BlockState sideFeetState = level.getBlockState(sideFeetPos);
            if (sideFeetState.isAir() || sideFeetState.is(Blocks.LIGHT)) {
                return sideFeetPos;
            }
        }

        return null;
    }

    /**
     * Verifica se existem fontes de luz artificiais nas proximidades do jogador.
     */
    private static boolean isNearArtificialLight(ServerLevel level, BlockPos center, int radius) {
        int rSq = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= rSq) {
                        BlockPos checkPos = center.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(checkPos);
                        if (!state.isAir() && !state.is(Blocks.LIGHT)) {
                            if (state.getLightEmission() >= 8) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Limpa o bloco de luz de um jogador.
     */
    private static void clearPlayerLight(ServerLevel level, UUID uuid) {
        BlockPos oldPos = ACTIVE_LIGHT_BLOCKS.remove(uuid);
        if (oldPos != null && level != null) {
            BlockState state = level.getBlockState(oldPos);
            if (state.is(Blocks.LIGHT)) {
                level.setBlock(oldPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /**
     * Limpa recursos quando o jogador sai do servidor.
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        WAS_NEAR_LIGHT.remove(uuid);
        LIGHT_MSG_COOLDOWN.remove(uuid);
        if (player.level() instanceof ServerLevel serverLevel) {
            clearPlayerLight(serverLevel, uuid);
        }
    }
}
