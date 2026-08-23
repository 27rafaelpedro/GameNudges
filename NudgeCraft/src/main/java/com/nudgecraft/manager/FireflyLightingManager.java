package com.nudgecraft.manager;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de Pirilampos (Fireflies) e Iluminação Dinâmica Noturna Permanente e Sem Piscar.
 * - SPOSITIVE: Partículas nativas de Firefly ao redor do jogador, sem iluminação nos blocos.
 * - POSITIVE:  Partículas nativas de Firefly + iluminação suave estável (Nível 6).
 * - VPOSITIVE: Partículas nativas de Firefly + iluminação forte estável (Nível 12).
 * Funciona de forma estável e contínua, inclusive ao subir vinhas (vines), árvores ou bambu.
 */
public final class FireflyLightingManager {

    private static final Map<UUID, BlockPos> ACTIVE_LIGHT_BLOCKS = new ConcurrentHashMap<>();

    private FireflyLightingManager() {
    }

    /**
     * Processa a emissão de pirilampos e a iluminação dinâmica estável a cada tick.
     */
    public static void tick(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();
        KarmaState current = KarmaEffectManager.getCurrentKarma();

        boolean isPositiveKarma = (current == KarmaState.SPOSITIVE || current == KarmaState.POSITIVE || current == KarmaState.VPOSITIVE);
        boolean isNight = level.isDarkOutside();

        // Verifica se o jogador está numa zona escura e afastado de fontes de luz artificiais (tochas, glowstone, etc.)
        boolean isNearTorch = isNearArtificialLight(level, player.blockPosition(), 5);
        boolean shouldBeActive = isPositiveKarma && isNight && !isNearTorch;

        if (shouldBeActive) {
            // 1. Emitir enxame de pirilampos (ParticleTypes.FIREFLY) ao redor do jogador
            spawnFireflies(player, level, current);

            // 2. Determinar o nível de iluminação dos blocos
            int lightLevel = 0;
            if (current == KarmaState.POSITIVE) {
                lightLevel = 6;  // Pouca iluminação suave
            } else if (current == KarmaState.VPOSITIVE) {
                lightLevel = 12; // Iluminação mais forte e clara
            }

            // 3. Atualizar a posição do bloco de luz dinâmica de forma estável (mesmo em vinhas)
            updatePlayerLight(player, level, uuid, lightLevel);
        } else {
            // Se for de dia, houver tochas por perto ou não for karma positivo, desliga a luz
            clearPlayerLight(level, uuid);
        }
    }

    /**
     * Emite as partículas autênticas de Firefly (Firefly Bush) orbitando de perto o jogador
     * e herdando a sua velocidade de movimento para que nunca fiquem para trás.
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
                // Raio compacto ao redor do corpo do jogador (0.6 a 1.6 blocos)
                double angle = level.getRandom().nextDouble() * 2 * Math.PI;
                double dist = 0.5 + level.getRandom().nextDouble() * 1.1;

                double px = player.getX() + Math.cos(angle) * dist;
                double py = player.getY() + 0.3 + level.getRandom().nextDouble() * 1.5;
                double pz = player.getZ() + Math.sin(angle) * dist;

                // Herda o vetor de movimento do jogador + suave flutuação orgânica
                double vx = motion.x * 0.85 + (level.getRandom().nextDouble() - 0.5) * 0.025;
                double vy = motion.y * 0.85 + (level.getRandom().nextDouble() - 0.2) * 0.025;
                double vz = motion.z * 0.85 + (level.getRandom().nextDouble() - 0.5) * 0.025;

                // Partícula nativa e autêntica de pirilampos com inércia que acompanha o jogador
                level.sendParticles(ParticleTypes.FIREFLY, px, py, pz, 1, vx, vy, vz, 0.0);
            }
        }
    }

    /**
     * Atualiza o bloco invisível de luz dinâmica que acompanha os movimentos do jogador sem piscar.
     * Encontra uma posição válida mesmo se o jogador estiver em vinhas, bambu ou folhagem.
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

        // Se o jogador se moveu para outra posição de luz válida
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
     * Suporta vinhas (vines), bambu, folhagem e espaços abertos.
     */
    private static BlockPos findBestLightPosition(ServerPlayer player, ServerLevel level) {
        BlockPos headPos = player.blockPosition().above();
        BlockState headState = level.getBlockState(headPos);

        // 1. Posição ideal na cabeça se for ar ou já for o nosso bloco de luz
        if (headState.isAir() || headState.is(Blocks.LIGHT)) {
            return headPos;
        }

        // 2. Posição nos pés do jogador
        BlockPos feetPos = player.blockPosition();
        BlockState feetState = level.getBlockState(feetPos);
        if (feetState.isAir() || feetState.is(Blocks.LIGHT)) {
            return feetPos;
        }

        // 3. Posição 1 bloco acima da cabeça (ao subir vinhas numa parede)
        BlockPos topPos = headPos.above();
        BlockState topState = level.getBlockState(topPos);
        if (topState.isAir() || topState.is(Blocks.LIGHT)) {
            return topPos;
        }

        // 4. Posições horizontais adjacentes à cabeça (ao escalar vinhas encostado a blocos sólidos)
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sidePos = headPos.relative(dir);
            BlockState sideState = level.getBlockState(sidePos);
            if (sideState.isAir() || sideState.is(Blocks.LIGHT)) {
                return sidePos;
            }
        }

        // 5. Posições horizontais adjacentes aos pés
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
     * Verifica se existem fontes de luz artificiais (tochas, lanternas, glowstone, lava, etc.)
     * nas proximidades do jogador, excluindo o bloco de luz gerado pelo próprio mod.
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
                            // Se o bloco emite luz significativa (tocha, lanterna, glowstone, etc.)
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
        if (player.level() instanceof ServerLevel serverLevel) {
            clearPlayerLight(serverLevel, player.getUUID());
        }
    }
}
