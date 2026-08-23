package com.nudgecraft.firebase;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nudgecraft.config.NudgeConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ponto de acesso direto à REST API oficial do Cloud Firestore.
 */
public final class FirebaseManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");

    public static final ExecutorService API_EXECUTOR = Executors.newFixedThreadPool(2, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "nudgecraft-firestore-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(API_EXECUTOR)
            .build();

    private static volatile boolean ready = false;

    private FirebaseManager() {
    }

    public static synchronized void init() {
        NudgeConfig.getInstance(); // Inicializa / carrega config.json
        ready = true;
        LOGGER.info("[Nudgecraft] Firestore REST Client inicializado com sucesso.");
    }

    public static synchronized void shutdown() {
        ready = false;
    }

    public static boolean isReady() {
        return ready;
    }

    /** Corre {@code action} na thread principal do servidor (obrigatório para interagir com jogadores). */
    public static void onServerThread(MinecraftServer server, Runnable action) {
        if (server != null) {
            server.execute(action);
        }
    }

    // --- Helpers de Formato Firestore REST ---

    public static JsonObject stringField(String val) {
        JsonObject obj = new JsonObject();
        obj.addProperty("stringValue", val != null ? val : "");
        return obj;
    }

    public static JsonObject integerField(long val) {
        JsonObject obj = new JsonObject();
        obj.addProperty("integerValue", String.valueOf(val));
        return obj;
    }

    public static String getString(JsonObject fields, String fieldName, String defaultValue) {
        if (fields != null && fields.has(fieldName)) {
            JsonObject f = fields.getAsJsonObject(fieldName);
            if (f != null && f.has("stringValue") && !f.get("stringValue").isJsonNull()) {
                return f.get("stringValue").getAsString();
            }
        }
        return defaultValue;
    }

    public static Long getLong(JsonObject fields, String fieldName, Long defaultValue) {
        if (fields != null && fields.has(fieldName)) {
            JsonObject f = fields.getAsJsonObject(fieldName);
            if (f != null && f.has("integerValue") && !f.get("integerValue").isJsonNull()) {
                try {
                    return Long.parseLong(f.get("integerValue").getAsString());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return defaultValue;
    }

    // --- Chamadas REST ao Firestore ---

    /**
     * Obtém um documento pelo caminho da coleção e ID.
     * Retorna o JsonObject do documento (com "fields") ou null se não existir (404).
     */
    public static CompletableFuture<JsonObject> getDocument(String collection, String documentId) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        try {
            NudgeConfig config = NudgeConfig.getInstance();
            String encodedId = URLEncoder.encode(documentId, StandardCharsets.UTF_8);
            String url = config.getFirestoreBaseUrl() + "/" + collection + "/" + encodedId + "?key=" + config.getApiKey();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            return JsonParser.parseString(response.body()).getAsJsonObject();
                        } else if (response.statusCode() == 404) {
                            return null;
                        } else {
                            throw new RuntimeException("Firestore retornou HTTP " + response.statusCode() + ": " + response.body());
                        }
                    })
                    .whenComplete((json, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(json);
                        }
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Cria ou atualiza um documento no Firestore via PATCH com suporte a mask de campos.
     */
    public static CompletableFuture<JsonObject> patchDocument(String collection, String documentId, JsonObject fields, List<String> fieldMask) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        try {
            NudgeConfig config = NudgeConfig.getInstance();
            String encodedId = URLEncoder.encode(documentId, StandardCharsets.UTF_8);
            StringBuilder urlBuilder = new StringBuilder(config.getFirestoreBaseUrl())
                    .append("/").append(collection).append("/").append(encodedId)
                    .append("?key=").append(config.getApiKey());

            if (fieldMask != null) {
                for (String field : fieldMask) {
                    urlBuilder.append("&updateMask.fieldPaths=").append(URLEncoder.encode(field, StandardCharsets.UTF_8));
                }
            }

            JsonObject body = new JsonObject();
            body.add("fields", fields);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlBuilder.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return JsonParser.parseString(response.body()).getAsJsonObject();
                        } else {
                            throw new RuntimeException("Firestore PATCH retornou HTTP " + response.statusCode() + ": " + response.body());
                        }
                    })
                    .whenComplete((json, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(json);
                        }
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Executa uma consulta estruturada (runQuery) no Firestore.
     */
    public static CompletableFuture<List<JsonObject>> queryUserVisits(String username) {
        CompletableFuture<List<JsonObject>> future = new CompletableFuture<>();
        try {
            NudgeConfig config = NudgeConfig.getInstance();
            String url = config.getFirestoreBaseUrl() + ":runQuery?key=" + config.getApiKey();

            JsonObject structuredQuery = new JsonObject();
            JsonArray from = new JsonArray();
            JsonObject collection = new JsonObject();
            collection.addProperty("collectionId", "user_visits");
            from.add(collection);
            structuredQuery.add("from", from);

            JsonObject where = new JsonObject();
            JsonObject fieldFilter = new JsonObject();
            JsonObject field = new JsonObject();
            field.addProperty("fieldPath", "minecraft_username");
            fieldFilter.add("field", field);
            fieldFilter.addProperty("op", "EQUAL");
            JsonObject value = new JsonObject();
            value.addProperty("stringValue", username);
            fieldFilter.add("value", value);
            where.add("fieldFilter", fieldFilter);
            structuredQuery.add("where", where);

            JsonObject body = new JsonObject();
            body.add("structuredQuery", structuredQuery);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            List<JsonObject> docs = new ArrayList<>();
                            JsonArray array = JsonParser.parseString(response.body()).getAsJsonArray();
                            for (JsonElement elem : array) {
                                JsonObject obj = elem.getAsJsonObject();
                                if (obj.has("document")) {
                                    docs.add(obj.getAsJsonObject("document"));
                                }
                            }
                            return docs;
                        } else {
                            throw new RuntimeException("Firestore runQuery retornou HTTP " + response.statusCode() + ": " + response.body());
                        }
                    })
                    .whenComplete((docs, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                        } else {
                            future.complete(docs);
                        }
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public static void consultarPassosPorUsername(String username, CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        queryUserVisits(username)
                .thenAccept(docs -> {
                    if (docs.isEmpty()) {
                        onServerThread(server, () -> source.sendFailure(
                                Component.literal("[Nudgecraft] Nenhum registo encontrado para o jogador: " + username).withStyle(ChatFormatting.RED)));
                        return;
                    }

                    long totalSteps = 0;
                    for (JsonObject doc : docs) {
                        if (doc.has("fields")) {
                            Long steps = getLong(doc.getAsJsonObject("fields"), "steps", null);
                            if (steps != null) {
                                totalSteps += steps;
                            }
                        }
                    }

                    final long passosFinais = totalSteps;
                    final int totalDias = docs.size();

                    onServerThread(server, () -> source.sendSuccess(() -> Component.literal(
                            "=== Estatísticas do Utilizador ==="
                                    + "\nJogador: " + username
                                    + "\nPassos Totais: " + passosFinais
                                    + "\nRegistos no Sistema: " + totalDias
                    ).withStyle(ChatFormatting.GREEN), false));
                })
                .exceptionally(ex -> {
                    LOGGER.error("[Nudgecraft] Erro ao consultar passos de {}", username, ex);
                    onServerThread(server, () -> source.sendFailure(
                            Component.literal("[Nudgecraft] Erro ao comunicar com o Firestore.").withStyle(ChatFormatting.RED)));
                    return null;
                });
    }

    static void awaitTermination() {
        API_EXECUTOR.shutdown();
        try {
            API_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
