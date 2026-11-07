package pl.teamwaypoints;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static String key = "";
    public static boolean enabled = true;

    private static final Gson GSON = new Gson();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("teamwaypoints.json");

    private static class Data {
        String key = "";
        boolean enabled = true;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Data d = GSON.fromJson(Files.readString(FILE), Data.class);
                if (d != null) {
                    key = d.key == null ? "" : d.key;
                    enabled = d.enabled;
                }
            }
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            Data d = new Data();
            d.key = key;
            d.enabled = enabled;
            Files.writeString(FILE, GSON.toJson(d));
        } catch (Exception ignored) {}
    }
}
