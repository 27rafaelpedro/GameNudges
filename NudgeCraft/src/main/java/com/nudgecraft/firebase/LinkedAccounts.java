package com.nudgecraft.firebase;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Ligação entre o UUID de um jogador e o email usado nos registos de passos. */
public final class LinkedAccounts {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    private static final Pattern EMAIL_REGEX =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private LinkedAccounts() {
    }

    public static void ligarEmail(ServerPlayer player, String rawEmail) {
        MinecraftServer server = player.level().getServer();
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        if (!EMAIL_REGEX.matcher(email).matches()) {
            erro(player, server, "Email inválido. Tenta novamente.");
            return;
        }

        Firestore db = FirebaseManager.getDb();

        if (db == null) {
            erro(player, server, "O Firebase não está operacional. Avisa um administrador.");
            return;
        }

        String uuid = player.getStringUUID();

        CompletableFuture.runAsync(() -> {
            try {
                // 0º verificar se este jogador já tem um email ligado
                DocumentSnapshot jaLigado =
                        db.collection("linked_accounts").document(uuid).get().get();

                if (jaLigado.exists()) {
                    String emailAtual = jaLigado.getString("email");
                    erro(player, server, "Já tens uma conta ligada (" + emailAtual
                            + "). Usa /unlinkemail primeiro se quiseres mudar.");
                    return;
                }

                // 1º verificar se o email existe nos registos de passos
                QuerySnapshot existeSnapshot = db.collection("user_visits")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get().get();

                if (existeSnapshot.isEmpty()) {
                    erro(player, server, "Não encontrámos esse email nos registos. "
                            + "Verifica se escreveste corretamente e tenta novamente.");
                    return;
                }

                // 2º verificar se já está ligado a OUTRO jogador
                QuerySnapshot dupSnapshot = db.collection("linked_accounts")
                        .whereEqualTo("email", email)
                        .limit(1)
                        .get().get();

                if (!dupSnapshot.isEmpty()) {
                    String outroUuid = dupSnapshot.getDocuments().get(0).getId();

                    if (!outroUuid.equals(uuid)) {
                        erro(player, server, "Esse email já está ligado a outro jogador.");
                        return;
                    }
                }

                // tudo ok -> gravar ligação
                Map<String, Object> data = new HashMap<>();
                data.put("email", email);
                data.put("linkedAt", System.currentTimeMillis());
                data.put("playerName", player.getGameProfile().name());

                db.collection("linked_accounts").document(uuid).set(data).get();

                sucesso(player, server, "Conta ligada com sucesso a " + email + "!");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                erro(player, server, "Erro ao ligar conta. Tenta novamente mais tarde.");
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao ligar {} a {}", uuid, email, e);
                erro(player, server, "Erro ao ligar conta. Tenta novamente mais tarde.");
            }
        }, FirebaseManager.FIREBASE_EXECUTOR);
    }

    public static void desligarEmail(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Firestore db = FirebaseManager.getDb();

        if (db == null) {
            erro(player, server, "O Firebase não está operacional. Avisa um administrador.");
            return;
        }

        String uuid = player.getStringUUID();

        CompletableFuture.runAsync(() -> {
            try {
                DocumentSnapshot doc =
                        db.collection("linked_accounts").document(uuid).get().get();

                if (!doc.exists()) {
                    erro(player, server, "Não tens nenhuma conta ligada.");
                    return;
                }

                db.collection("linked_accounts").document(uuid).delete().get();

                sucesso(player, server, "Conta desligada. Podes ligar um novo email com /linkemail.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                erro(player, server, "Erro ao desligar conta. Tenta novamente mais tarde.");
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao desligar {}", uuid, e);
                erro(player, server, "Erro ao desligar conta. Tenta novamente mais tarde.");
            }
        }, FirebaseManager.FIREBASE_EXECUTOR);
    }

    /**
     * Obtém o email ligado ao jogador. O {@code callback} corre numa thread do Firebase,
     * <b>não</b> na thread do servidor.
     */
    public static void obterEmailLigado(ServerPlayer player, Consumer<String> callback) {
        Firestore db = FirebaseManager.getDb();

        if (db == null) {
            callback.accept(null);
            return;
        }

        ApiFuture<DocumentSnapshot> future =
                db.collection("linked_accounts").document(player.getStringUUID()).get();

        future.addListener(() -> {
            try {
                DocumentSnapshot doc = future.get();
                callback.accept(doc.exists() ? doc.getString("email") : null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                callback.accept(null);
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao obter o email ligado a {}", player.getStringUUID(), e);
                callback.accept(null);
            }
        }, FirebaseManager.FIREBASE_EXECUTOR);
    }

    static void erro(ServerPlayer player, MinecraftServer server, String mensagem) {
        FirebaseManager.onServerThread(server, () ->
                player.sendSystemMessage(Component.literal(mensagem).withStyle(ChatFormatting.RED)));
    }

    static void sucesso(ServerPlayer player, MinecraftServer server, String mensagem) {
        FirebaseManager.onServerThread(server, () ->
                player.sendSystemMessage(Component.literal(mensagem).withStyle(ChatFormatting.GREEN)));
    }
}
