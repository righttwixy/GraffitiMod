package by.righttwixys.graffiti.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class GraffitiConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = FabricLoader.getInstance().getConfigDir().resolve("graffiti.json").toFile();

    // Сами настройки
    public boolean enabled = true;
    public boolean useCulling = true;
    public int renderDistance = 32;

    private static GraffitiConfig INSTANCE = new GraffitiConfig();

    public static GraffitiConfig get() {
        return INSTANCE;
    }

    public static void load() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                INSTANCE = GSON.fromJson(reader, GraffitiConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}