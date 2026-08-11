package com.nudgecraft.firebase;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ponto único de acesso ao Firestore.
 *
 * <p>Todas as chamadas ao Firebase são feitas fora da thread principal do servidor
 * ({@link #FIREBASE_EXECUTOR}); qualquer coisa que toque no mundo/jogadores tem de
 * voltar à thread do servidor via {@link #onServerThread}.
 */
public final class FirebaseManager {

    /** Nome da FirebaseApp deste mod — evita colidir com a app "[DEFAULT]" de outro mod. */
    private static final String APP_NAME = "nudgecraft";

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    /** Threads dedicadas ao I/O do Firebase. Daemon para não impedir o encerramento do jogo. */
    public static final ExecutorService FIREBASE_EXECUTOR = Executors.newFixedThreadPool(2, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "nudgecraft-firebase-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private static volatile FirebaseApp app;
    private static volatile Firestore db;

    private FirebaseManager() {
    }

    public static synchronized void init() {
        if (db != null) {
            return; // já inicializado (ex.: segundo mundo aberto na mesma sessão)
        }

        Path key = FabricLoader.getInstance().getConfigDir().resolve("nudgecraft").resolve("serviceAccountKey.json");

        if (!Files.isRegularFile(key)) {
            LOGGER.error("[Nudgecraft] serviceAccountKey.json não encontrado em {}. Os comandos do Firebase ficam desativados.", key);
            return;
        }

        // O SDK do Firebase usa ServiceLoader (gRPC, transporte HTTP). Sob o Knot do Fabric o
        // context classloader da thread pode não ver as libs embutidas, por isso forçamo-lo.
        ClassLoader previous = Thread.currentThread().getContextClassLoader();

        try (InputStream credentials = Files.newInputStream(key)) {
            Thread.currentThread().setContextClassLoader(FirebaseManager.class.getClassLoader());

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();

            try {
                app = FirebaseApp.getInstance(APP_NAME);
            } catch (IllegalStateException notYetCreated) {
                app = FirebaseApp.initializeApp(options, APP_NAME);
            }

            db = FirestoreClient.getFirestore(app);
            LOGGER.info("[Nudgecraft] Firebase inicializado com sucesso.");
        } catch (Exception e) {
            app = null;
            db = null;
            LOGGER.error("[Nudgecraft] Erro ao inicializar o Firebase. Verifica se o ficheiro serviceAccountKey.json está em config/nudgecraft/", e);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    public static synchronized void shutdown() {
        Firestore current = db;
        db = null;

        if (current != null) {
            try {
                current.close();
            } catch (Exception e) {
                LOGGER.warn("[Nudgecraft] Erro ao fechar a ligação ao Firestore.", e);
            }
        }

        if (app != null) {
            app.delete();
            app = null;
        }
    }

    public static boolean isReady() {
        return db != null;
    }

    /**
     * @return o cliente Firestore, ou {@code null} se o Firebase não arrancou.
     */
    public static Firestore getDb() {
        return db;
    }

    /** Corre {@code action} na thread principal do servidor (obrigatório para mexer em jogadores). */
    public static void onServerThread(MinecraftServer server, Runnable action) {
        if (server != null) {
            server.execute(action);
        }
    }

    public static void consultarPassosPorEmail(String email, CommandSourceStack source) {
        Firestore firestore = db;

        if (firestore == null) {
            source.sendFailure(Component.literal("[Nudgecraft] O Firebase não está operacional.").withStyle(ChatFormatting.RED));
            return;
        }

        MinecraftServer server = source.getServer();

        ApiFuture<QuerySnapshot> future = firestore.collection("user_visits")
                .whereEqualTo("email", email)
                .get();

        future.addListener(() -> {
            try {
                QuerySnapshot querySnapshot = future.get();

                if (querySnapshot.isEmpty()) {
                    onServerThread(server, () -> source.sendFailure(
                            Component.literal("[Nudgecraft] Nenhum registo encontrado para: " + email).withStyle(ChatFormatting.RED)));
                    return;
                }

                long totalSteps = 0;
                int totalDias = querySnapshot.size();

                for (QueryDocumentSnapshot document : querySnapshot.getDocuments()) {
                    Long steps = document.getLong("steps");

                    if (steps != null) {
                        totalSteps += steps;
                    }
                }

                final long passosFinais = totalSteps;

                onServerThread(server, () -> source.sendSuccess(() -> Component.literal(
                        "=== Estatísticas do Utilizador ==="
                                + "\nE-mail: " + email
                                + "\nPassos Totais: " + passosFinais
                                + "\nRegistos no Sistema: " + totalDias
                ).withStyle(ChatFormatting.GREEN), false));
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao consultar passos para {}", email, e);
                onServerThread(server, () -> source.sendFailure(
                        Component.literal("[Nudgecraft] Erro ao comunicar com o Firebase.").withStyle(ChatFormatting.RED)));
            }
        }, FIREBASE_EXECUTOR);
    }

    static void awaitTermination() {
        FIREBASE_EXECUTOR.shutdown();

        try {
            FIREBASE_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
