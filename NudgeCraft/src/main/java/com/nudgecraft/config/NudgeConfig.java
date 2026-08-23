package com.nudgecraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NudgeConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger("nudgecraft");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_PROJECT_ID = "gamenudges";
    public static final String DEFAULT_API_KEY = "AIzaSyBTnMNgQ_3YcXpmPFsC4Km3w4GPpDMp_bQ";

    private String projectId = DEFAULT_PROJECT_ID;
    private String apiKey = DEFAULT_API_KEY;

    private static NudgeConfig instance;

    public static synchronized NudgeConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public String getProjectId() {
        return (projectId != null && !projectId.trim().isEmpty()) ? projectId.trim() : DEFAULT_PROJECT_ID;
    }

    public String getApiKey() {
        return (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey.trim() : DEFAULT_API_KEY;
    }

    public String getFirestoreBaseUrl() {
        return "https://firestore.googleapis.com/v1/projects/" + getProjectId() + "/databases/(default)/documents";
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
        save();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        save();
    }

    private static NudgeConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("nudgecraft");
        Path configFile = configDir.resolve("config.json");

        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                NudgeConfig config = GSON.fromJson(reader, NudgeConfig.class);
                if (config != null) {
                    LOGGER.info("[Nudgecraft] Configuração carregada com sucesso.");
                    return config;
                }
            } catch (Exception e) {
                LOGGER.error("[Nudgecraft] Erro ao ler config.json, a usar valores padrão.", e);
            }
        }

        NudgeConfig config = new NudgeConfig();
        config.save();
        return config;
    }

    public void save() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("nudgecraft");
        Path configFile = configDir.resolve("config.json");

        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[Nudgecraft] Erro ao guardar config.json.", e);
        }
    }
}
