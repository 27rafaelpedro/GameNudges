package com.nudgecraft.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Random;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NudgeMessages {
    private static final Random RANDOM = new Random();
    
    private static final Set<UUID> SEEN_MINE_HANDS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SEEN_RUNNING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SEEN_FLOWERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SEEN_FIREFLIES = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SEEN_HUNGER = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SEEN_NIGHT_FAST = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SEEN_BUSH = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SEEN_ANIMALS_AFRAID = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SEEN_BLINDNESS = ConcurrentHashMap.newKeySet();

    public static void clearSession(UUID uuid) {
        SEEN_MINE_HANDS.remove(uuid);
        SEEN_RUNNING.remove(uuid);
        SEEN_FLOWERS.remove(uuid);
        SEEN_FIREFLIES.remove(uuid);
        SEEN_HUNGER.remove(uuid);
        SEEN_NIGHT_FAST.remove(uuid);
        SEEN_BUSH.remove(uuid);
        SEEN_ANIMALS_AFRAID.remove(uuid);
        SEEN_BLINDNESS.remove(uuid);
    }
    
    private static Component formatPositive(String text) {
        return Component.literal(text).withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.ITALIC);
    }
    
    private static Component formatNegative(String text) {
        return Component.literal(text).withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC);
    }

    public static Component getMiningHandsMessage(ServerPlayer player) {
        if (SEEN_MINE_HANDS.add(player.getUUID())) {
            String[] msgs = {
                "Sentes uma energia revigorante nos punhos",
                "Os teus punhos movem-se com uma velocidade impressionante",
                "Uma for\u00E7a implac\u00E1vel corre-te nas veias"
            };
            return formatPositive(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getRunningMessage(ServerPlayer player) {
        if (SEEN_RUNNING.add(player.getUUID())) {
            String[] msgs = {
                "Sentes o teu corpo mais leve sob o c\u00E9u aberto",
                "Corres veloz e livre como o pr\u00F3prio vento",
                "Impulsionado pelo sol, corres a uma velocidade vertiginosa"
            };
            return formatPositive(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getFeatherFallMessage() {
        String[] msgs = {
            "O teu corpo absorve a queda de forma instintiva",
            "Ultrapassas a queda sem um \u00FAnico arranh\u00E3o",
            "Aterras no ch\u00E3o com grande agilidade"
        };
        return formatPositive(msgs[RANDOM.nextInt(msgs.length)]);
    }
    
    public static Component getFlowersSpawnMessage(ServerPlayer player) {
        if (SEEN_FLOWERS.add(player.getUUID())) {
            String[] msgs = {
                "A natureza decora o teu caminho",
                "Sentes a vida a brotar \u00E0 tua volta",
                "A terra floresce por onde passas"
            };
            return formatPositive(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getSeedsGrowMessage() {
        String[] msgs = {
            "As planta\u00E7\u00F5es ganham uma nova energia quando te aproximas",
            "A tua presen\u00E7a acelera o crescimento das planta\u00E7\u00F5es"
        };
        return formatPositive(msgs[RANDOM.nextInt(msgs.length)]);
    }
    
    public static Component getFirefliesMessage(ServerPlayer player) {
        if (SEEN_FIREFLIES.add(player.getUUID())) {
            String[] msgs = {
                "A natureza ilumina o teu caminho",
                "Uma dan\u00E7a de clar\u00F5es acontece \u00E0 tua volta",
                "Um enxame de luz afasta a escurid\u00E3o"
            };
            return formatPositive(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getHungerMessage(ServerPlayer player) {
        if (SEEN_HUNGER.add(player.getUUID())) {
            String[] msgs = {
                "Sentes um vazio invulgar no est\u00F4mago",
                "O cansa\u00E7o consome-te rapidamente",
                "Sentes uma exaust\u00E3o devoradora"
            };
            return formatNegative(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getFasterNightMessage(ServerPlayer player) {
        if (SEEN_NIGHT_FAST.add(player.getUUID())) {
            String[] msgs = {
                "O dia parece ter passado mais r\u00E1pido",
                "A sombra chega mais rapidamente",
                "O proprio mundo parece exausto"
            };
            return formatNegative(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getMiningCurseMessage() {
        String[] msgs = {
            "O bloco desfez-se e n\u00E3o deixa nada",
            "N\u00E3o consegues extrair o min\u00E9rio"
        };
        return formatNegative(msgs[RANDOM.nextInt(msgs.length)]);
    }
    
    public static Component getBushMessage(ServerPlayer player) {
        if (SEEN_BUSH.add(player.getUUID())) {
            String[] msgs = {
                "Alguma vegeta\u00E7\u00E3o seca \u00E0 tua volta",
                "O solo parece secar por onde passas"
            };
            return formatNegative(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getSeedsShrinkMessage() {
        String[] msgs = {
            "As sementes perdem vida com a tua presenca",
            "As planta\u00E7\u00F5es regridem quando te aproximas"
        };
        return formatNegative(msgs[RANDOM.nextInt(msgs.length)]);
    }
    
    public static Component getAnimalsAfraidMessage(ServerPlayer player) {
        if (SEEN_ANIMALS_AFRAID.add(player.getUUID())) {
            String[] msgs = {
                "A vida selvagem recua",
                "Os animais sentem uma presen\u00E7a obscura",
                "A natureza teme os teus passos"
            };
            return formatNegative(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getBlindnessMessage(ServerPlayer player) {
        if (SEEN_BLINDNESS.add(player.getUUID())) {
            String[] msgs = {
                "A tua vis\u00E3o est\u00E1 cansada",
                "As sombras parecem mais espessas",
                "A escurid\u00E3o pesa na tua vis\u00E3o"
            };
            return formatNegative(msgs[RANDOM.nextInt(msgs.length)]);
        }
        return null;
    }
    
    public static Component getAnimalsAttractedMessage() {
        String[] msgs = {
            "A tua energia atrai a vida selvagem",
            "A natureza confia em ti",
            "Os animais aproximam-se calmamente"
        };
        return formatPositive(msgs[RANDOM.nextInt(msgs.length)]);
    }
    
    public static Component getOreBlessedMessage() {
        String[] msgs = {
            "Os min\u00E9rios brilham com a tua energia",
            "O min\u00E9rio irradia poder",
            "A terra recompensa o teu esfor\u00E7o"
        };
        return formatPositive(msgs[RANDOM.nextInt(msgs.length)]);
    }
    
    // Client-side
    public static String getBlindnessString() {
        String[] msgs = {
            "A tua vis\u00E3o est\u00E1 cansada",
            "As sombras parecem mais espessas",
            "A escurid\u00E3o pesa na tua vis\u00E3o"
        };
        return "\u00A7c\u00A7o" + msgs[RANDOM.nextInt(msgs.length)];
    }
}
