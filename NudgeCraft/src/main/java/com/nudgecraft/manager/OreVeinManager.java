package com.nudgecraft.manager;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de deteção de veias de minérios, bênção de 1-2 blocos brilhantes com efeito WAX_OFF,
 * double drops garantidos e controlo rigoroso de cooldown de 1 minuto e campo de visão (FOV / Line of Sight).
 */
public final class OreVeinManager {

    private static final int COOLDOWN_TICKS_1_MINUTE = 1200; // 60 segundos (1 min) * 20 ticks
    private static final Map<UUID, Integer> PLAYER_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Long> BLESSED_ORES = new ConcurrentHashMap<>();

    private OreVeinManager() {
    }

    /**
     * Processa a deteção de veias por linha de vista/FOV, emissão de partículas wax_off e cooldowns a cada tick.
     */
    public static void tick(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();

        // 1. Atualizar Cooldown do Jogador (1 minuto)
        int cd = PLAYER_COOLDOWNS.getOrDefault(uuid, 0);
        if (cd > 0) {
            PLAYER_COOLDOWNS.put(uuid, cd - 1);
        }

        // 2. Emitir partículas de WAX_OFF contínuas estritamente nos blocos escolhidos ativos
        if (level.getGameTime() % 8 == 0 && !BLESSED_ORES.isEmpty()) {
            Iterator<Map.Entry<BlockPos, Long>> it = BLESSED_ORES.entrySet().iterator();
            long now = System.currentTimeMillis();

            while (it.hasNext()) {
                Map.Entry<BlockPos, Long> entry = it.next();
                BlockPos pos = entry.getKey();
                long expiry = entry.getValue();

                // Expira após 2 minutos se não for minerado
                if (now > expiry) {
                    it.remove();
                    continue;
                }

                BlockState state = level.getBlockState(pos);
                if (isOreBlock(state.getBlock())) {
                    // Estritamente partículas de WAX_OFF nos blocos escolhidos
                    level.sendParticles(ParticleTypes.WAX_OFF,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            4, 0.35, 0.35, 0.35, 0.05);
                } else {
                    it.remove(); // Bloco já foi minerado ou alterado
                }
            }
        }

        // 3. Deteção de Veia quando o jogador tem o minério no seu Field of View (FOV) e linha de vista desobstruída
        KarmaState current = KarmaEffectManager.getCurrentKarma();
        if (current == KarmaState.VPOSITIVE || current == KarmaState.POSITIVE || current == KarmaState.SPOSITIVE) {
            if (PLAYER_COOLDOWNS.getOrDefault(uuid, 0) <= 0) {
                BlockPos targetOrePos = findVisibleOreInFOV(player, level);

                if (targetOrePos != null) {
                    BlockState targetState = level.getBlockState(targetOrePos);
                    List<BlockPos> vein = findOreVein(level, targetOrePos, targetState.getBlock());

                    if (!vein.isEmpty()) {
                        // Escolhe 1 ou 2 blocos da veia para dar drops a dobrar
                        Collections.shuffle(vein, new Random());
                        int countToBless = Math.min(vein.size(), (vein.size() > 1 && level.getRandom().nextBoolean()) ? 2 : 1);
                        long expireTime = System.currentTimeMillis() + 120000; // 2 minutos

                        for (int i = 0; i < countToBless; i++) {
                            BlockPos blessedPos = vein.get(i);
                            BLESSED_ORES.put(blessedPos, expireTime);

                            // Efeito inicial de WAX_OFF no bloco escolhido
                            level.sendParticles(ParticleTypes.WAX_OFF,
                                    blessedPos.getX() + 0.5, blessedPos.getY() + 0.5, blessedPos.getZ() + 0.5,
                                    12, 0.4, 0.4, 0.4, 0.08);
                        }

                        // Mensagem de Action Bar
                        player.sendSystemMessage(
                                Component.literal("Os minérios brilham com a tua energia!")
                                        .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC),
                                true
                        );

                        // Som suave de ressonância
                        level.playSound(null, targetOrePos.getX() + 0.5, targetOrePos.getY() + 0.5, targetOrePos.getZ() + 0.5,
                                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.3f);

                        // Inicia imediatamente o cooldown de 1 minuto (60 segundos)
                        PLAYER_COOLDOWNS.put(uuid, COOLDOWN_TICKS_1_MINUTE);
                    }
                }
            }
        }
    }

    /**
     * Encontra um minério visível diretamente no Field of View (FOV) do jogador com Linha de Vista (Line of Sight) limpa,
     * restrito a um raio de proximidade de 5 blocos (alcance de mineração).
     * Ignora minérios enterrados ou minérios distantes no horizonte de ravinas.
     */
    private static BlockPos findVisibleOreInFOV(ServerPlayer player, ServerLevel level) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);

        // 1. Visão Direta pelo Centro da Mira (Raycast no alcance de 5.0 blocos)
        HitResult hit = player.pick(5.0D, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            if (isOreBlock(level.getBlockState(pos).getBlock())) {
                return pos;
            }
        }

        // 2. Cone de Visão / Field of View (FOV) com Linha de Vista Direta e Raio Estrito de Proximidade (5 blocos)
        BlockPos playerPos = player.blockPosition();
        BlockPos bestPos = null;
        double bestDot = 0.70; // Cone de visão direto no ecrã (~45 graus)

        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos candidate = playerPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(candidate);

                    if (isOreBlock(state.getBlock())) {
                        Vec3 oreCenter = Vec3.atCenterOf(candidate);
                        Vec3 dirToOre = oreCenter.subtract(eyePos);
                        double distSq = dirToOre.lengthSqr();

                        // Restrito estritamente a 5.0 blocos de distância (alcance de mineração)
                        if (distSq <= 25.0) {
                            double dot = lookVec.dot(dirToOre.normalize());

                            if (dot > bestDot) {
                                // Verifica se há Linha de Vista direta (o minério não está tapado por pedra/terra)
                                ClipContext clipCtx = new ClipContext(
                                        eyePos,
                                        oreCenter,
                                        ClipContext.Block.VISUAL,
                                        ClipContext.Fluid.NONE,
                                        player
                                );
                                BlockHitResult trace = level.clip(clipCtx);

                                if (trace.getType() == HitResult.Type.BLOCK && trace.getBlockPos().equals(candidate)) {
                                    bestDot = dot;
                                    bestPos = candidate;
                                }
                            }
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    /**
     * Verifica se uma dada posição de bloco é um minério abençoado com double drop garantido.
     */
    public static boolean isBlessedOre(BlockPos pos) {
        return BLESSED_ORES.containsKey(pos);
    }

    /**
     * Remove o bloco abençoado do registo após ser minerado.
     */
    public static void consumeBlessedOre(BlockPos pos) {
        BLESSED_ORES.remove(pos);
    }

    /**
     * Limpa o estado quando o jogador se desconecta.
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        PLAYER_COOLDOWNS.remove(player.getUUID());
    }

    /**
     * Algoritmo BFS para encontrar até 12 blocos de minério conectados na mesma veia.
     */
    private static List<BlockPos> findOreVein(ServerLevel level, BlockPos startPos, Block targetOre) {
        List<BlockPos> vein = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty() && vein.size() < 12) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);

            if (isSameOreFamily(state.getBlock(), targetOre)) {
                vein.add(current);

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos neighbor = current.offset(dx, dy, dz);
                            if (!visited.contains(neighbor) && neighbor.closerThan(startPos, 5.0)) {
                                visited.add(neighbor);
                                queue.add(neighbor);
                            }
                        }
                    }
                }
            }
        }
        return vein;
    }

    public static boolean isOreBlock(Block block) {
        return block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.NETHER_GOLD_ORE || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.ANCIENT_DEBRIS;
    }

    private static boolean isSameOreFamily(Block a, Block b) {
        if (a == b) return true;
        if (isCoal(a) && isCoal(b)) return true;
        if (isIron(a) && isIron(b)) return true;
        if (isCopper(a) && isCopper(b)) return true;
        if (isGold(a) && isGold(b)) return true;
        if (isRedstone(a) && isRedstone(b)) return true;
        if (isLapis(a) && isLapis(b)) return true;
        if (isDiamond(a) && isDiamond(b)) return true;
        if (isEmerald(a) && isEmerald(b)) return true;
        return false;
    }

    private static boolean isCoal(Block b) { return b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE; }
    private static boolean isIron(Block b) { return b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE; }
    private static boolean isCopper(Block b) { return b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE; }
    private static boolean isGold(Block b) { return b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE; }
    private static boolean isRedstone(Block b) { return b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE; }
    private static boolean isLapis(Block b) { return b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE; }
    private static boolean isDiamond(Block b) { return b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE; }
    private static boolean isEmerald(Block b) { return b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE; }
}
