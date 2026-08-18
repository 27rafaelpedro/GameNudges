package com.nudgecraft.Karma.strategy;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Gestor global do ciclo de estratégias de Karma no servidor.
 * Controla qual a estratégia ativa de efeitos passivos aplicada a cada jogador no mundo.
 */
public final class KarmaEffectManager {

    private static volatile KarmaState currentKarma = KarmaState.BASE;
    private static volatile KarmaStrategy activeStrategy = (player, level) -> {};
    private static volatile long serverLoginTime = 0;

    private KarmaEffectManager() {
    }

    /**
     * Define o registo do instante de login do jogador no servidor em milissegundos.
     * Utilizado para controlar a duração de partículas de entrada.
     *
     * @param time Instante temporal em milissegundos.
     */
    public static void setServerLoginTime(long time) {
        serverLoginTime = time;
    }

    /**
     * Obtém o instante de login registado em milissegundos.
     *
     * @return Instante temporal do login em milissegundos.
     */
    public static long getServerLoginTime() {
        return serverLoginTime;
    }

    /**
     * Atualiza o estado de Karma atual e reconstrói a estratégia de efeitos ativa.
     * Associa estados de Karma a classes especializadas de estratégia ou expressões Lambda.
     *
     * @param state O novo estado de Karma do jogador.
     */
    public static void updateStrategy(KarmaState state) {
        currentKarma = (state != null) ? state : KarmaState.BASE;
        activeStrategy = switch (currentKarma) {
            case VPOSITIVE, POSITIVE -> new PositiveKarmaStrategy();
            case VNEGATIVE, NEGATIVE -> new NegativeKarmaStrategy();
            default -> (player, level) -> {};
        };
    }

    /**
     * Obtém a estratégia ativa atualmente instanciada.
     *
     * @return A instância de {@link KarmaStrategy} ativa.
     */
    public static KarmaStrategy getActiveStrategy() {
        return activeStrategy;
    }

    /**
     * Obtém o estado de Karma atual ativo na sessão de jogo.
     *
     * @return O {@link KarmaState} atual do jogador.
     */
    public static KarmaState getCurrentKarma() {
        return currentKarma;
    }

    /**
     * Acionado a cada tick de processamento do jogador no servidor para
     * aplicar os efeitos passivos correspondentes da estratégia em vigor.
     *
     * @param player O jogador alvo do processamento.
     * @param level  O nível de servidor em processamento.
     */
    public static void tick(ServerPlayer player, ServerLevel level) {
        activeStrategy.applyPassiveEffects(player, level);
    }
}
