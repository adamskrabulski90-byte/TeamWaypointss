package pl.teamwaypoints;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Net {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final AtomicInteger GEN = new AtomicInteger();
    private static volatile String topic = null;

    public static void connect(String key) {
        int g = GEN.incrementAndGet();
        String t = "twp-" + key;
        topic = t;
        Thread th = new Thread(() -> loop(t, g), "TeamWaypoints-Net");
        th.setDaemon(true);
        th.start();
    }

    public static void disconnect() {
        GEN.incrementAndGet();
        topic = null;
    }

    public static boolean connected() {
        return topic != null;
    }

    private static void loop(String t, int g) {
        while (GEN.get() == g) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create("https://ntfy.sh/" + t + "/json")).GET().build();
                HttpResponse<Stream<String>> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofLines());
                resp.body().forEach(line -> {
                    if (GEN.get() != g) throw new RuntimeException("stop");
                    handle(line);
                });
            } catch (Exception ignored) {}
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private static void handle(String line) {
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            if (!o.has("event") || !"message".equals(o.get("event").getAsString())) return;
            JsonObject m = JsonParser.parseString(o.get("message").getAsString()).getAsJsonObject();
            MinecraftClient.getInstance().execute(() -> TeamWaypointsClient.apply(m));
        } catch (Exception ignored) {}
    }

    public static void send(JsonObject json) {
        String t = topic;
        if (t == null) return;
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://ntfy.sh/" + t))
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding());
    }
}
