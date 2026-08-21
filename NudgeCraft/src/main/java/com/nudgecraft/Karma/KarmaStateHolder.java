package com.nudgecraft.Karma;

/**
 * Recipiente partilhado (Common) do estado de Karma ativo.
 * Permite que mixins e sistemas comuns (comuns ao servidor e cliente)
 * consultem o nível de Karma atual de forma sincronizada e sem acoplamento de classes exclusivas de cliente.
 */
public final class KarmaStateHolder {

    private static volatile KarmaState currentKarma = KarmaState.BASE;

    private KarmaStateHolder() {
    }

    public static KarmaState get() {
        return currentKarma;
    }

    public static void set(KarmaState state) {
        currentKarma = (state != null) ? state : KarmaState.BASE;
    }
}
