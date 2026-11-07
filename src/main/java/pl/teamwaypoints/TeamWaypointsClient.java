package pl.teamwaypoints;

import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TeamWaypointsClient implements ClientModInitializer {
    public static KeyBinding OPEN, CREATE, REMOVE, REMOVE_TEAM;

    public record Waypoint(UUID id, String name, double x, double y, double z, String dim) {}

    public static final Map<UUID, Waypoint> WAYPOINTS = new HashMap<>();
    private static final Map<UUID, SkinTextures> SKINS = new HashMap<>();
    private static final Set<UUID> REQUESTED = new HashSet<>();

    @Override
    public void onInitializeClient() {
        Config.load();

        OPEN = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teamwaypoints.open",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.teamwaypoints"));
        CREATE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teamwaypoints.create",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.teamwaypoints"));
        REMOVE = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teamwaypoints.remove",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.teamwaypoints"));
        REMOVE_TEAM = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.teamwaypoints.remove_team",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "category.teamwaypoints"));

        if (!Config.key.isEmpty()) Net.connect(Config.key);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (OPEN.wasPressed()) mc.setScreen(new TeamScreen());
            while (CREATE.wasPressed()) createWaypoint();
            while (REMOVE.wasPressed()) removeOwn();
            while (REMOVE_TEAM.wasPressed()) removeTeam();
        });

        HudRenderCallback.EVENT.register(TeamWaypointsClient::render);
    }

    // ---------- akcje ----------

    private static UUID ownId(MinecraftClient mc) {
        return mc.getSession().getUuidOrNull();
    }

    private static void msg(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    public static void createWaypoint() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!Net.connected()) {
            msg("Team Waypoints: najpierw dolacz do teamu");
            return;
        }
        UUID id = ownId(mc);
        if (id == null) return;
        String dim = mc.world.getRegistryKey().getValue().toString();
        Vec3d p = mc.player.getPos();
        Waypoint w = new Waypoint(id, mc.getSession().getUsername(), p.x, p.y, p.z, dim);
        WAYPOINTS.put(id, w);

        JsonObject o = new JsonObject();
        o.addProperty("type", "set");
        o.addProperty("uuid", id.toString());
        o.addProperty("name", w.name());
        o.addProperty("x", w.x());
        o.addProperty("y", w.y());
        o.addProperty("z", w.z());
        o.addProperty("dim", dim);
        Net.send(o);
        msg("Waypoint ustawiony");
    }

    public static void removeOwn() {
        MinecraftClient mc = MinecraftClient.getInstance();
        UUID id = ownId(mc);
        if (id == null) return;
        WAYPOINTS.remove(id);
        if (Net.connected()) {
            JsonObject o = new JsonObject();
            o.addProperty("type", "remove");
            o.addProperty("uuid", id.toString());
            Net.send(o);
        }
        msg("Waypoint usuniety");
    }

    /** Usuwa tylko u Ciebie waypointy kolegow. */
    public static void removeTeam() {
        UUID own = ownId(MinecraftClient.getInstance());
        WAYPOINTS.keySet().removeIf(u -> !u.equals(own));
        msg("Usunieto waypointy teamu");
    }

    public static void apply(JsonObject o) {
        try {
            UUID id = UUID.fromString(o.get("uuid").getAsString());
            UUID own = ownId(MinecraftClient.getInstance());
            if (id.equals(own)) return;
            String type = o.get("type").getAsString();
            if (type.equals("set")) {
                WAYPOINTS.put(id, new Waypoint(id, o.get("name").getAsString(),
                        o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(),
                        o.get("dim").getAsString()));
            } else if (type.equals("remove")) {
                WAYPOINTS.remove(id);
            }
        } catch (Exception ignored) {}
    }

    // ---------- skiny ----------

    private static SkinTextures skin(MinecraftClient mc, UUID id) {
        SkinTextures s = SKINS.get(id);
        if (s != null) return s;

        if (mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(id);
            if (e != null) {
                s = e.getSkinTextures();
                SKINS.put(id, s);
                return s;
            }
        }
        if (REQUESTED.add(id)) {
            CompletableFuture.supplyAsync(() -> {
                var r = mc.getSessionService().fetchProfile(id, false);
                return r == null ? null : r.profile();
            }).thenCompose(p -> p == null
                    ? CompletableFuture.completedFuture(Optional.<SkinTextures>empty())
                    : mc.getSkinProvider().fetchSkinTextures(p)
            ).thenAccept(o -> o.ifPresent(t -> mc.execute(() -> SKINS.put(id, t))));
        }
        return DefaultSkinHelper.getSkinTextures(id);
    }

    // ---------- rysowanie ----------

    private static void render(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || !Config.enabled || WAYPOINTS.isEmpty()) return;
        if (mc.options.hudHidden) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getPos();
        double yaw = Math.toRadians(cam.getYaw());
        double pitch = Math.toRadians(cam.getPitch());

        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        double tan = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) w / h;
                String dim = mc.world.getRegistryKey().getValue().toString();

        for (Waypoint wp : WAYPOINTS.values()) {
            if (!wp.dim().equals(dim)) continue;
            double dx = wp.x() - cp.x;
            double dy = wp.y() + 2.2 - cp.y;
            double dz = wp.z() - cp.z;
            double z = dx * fx + dy * fy + dz * fz;
            if (z <= 0.1) continue;
            double x = dx * rx + dy * ry + dz * rz;
            double y = dx * ux + dy * uy + dz * uz;

            int sx = (int) (w / 2.0 * (1 + x / (z * tan * aspect)));
            int sy = (int) (h / 2.0 * (1 - y / (z * tan)));
            sx = Math.max(20, Math.min(w - 20, sx));
            sy = Math.max(20, Math.min(h - 30, sy));

            double dist = Math.sqrt((wp.x() - cp.x) * (wp.x() - cp.x)
                    + (wp.y() - cp.y) * (wp.y() - cp.y)
                    + (wp.z() - cp.z) * (wp.z() - cp.z));
            String txt = (int) Math.round(dist) + "m";

            // tlo pod glowa
            ctx.fill(sx - 9, sy - 9, sx + 9, sy + 9, 0xAA000000);
            PlayerSkinDrawer.draw(ctx, skin(mc, wp.id()), sx - 8, sy - 8, 16);
            int tw = mc.textRenderer.getWidth(txt);
            ctx.drawText(mc.textRenderer, txt, sx - tw / 2, sy + 11, 0xFFFFFFFF, true);
        }
    }
}
