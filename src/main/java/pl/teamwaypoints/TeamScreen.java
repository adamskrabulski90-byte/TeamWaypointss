package pl.teamwaypoints;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import java.security.SecureRandom;

public class TeamScreen extends Screen {
    private static final int PX = 12, PY = 12, PW = 250, PH = 345;
    private static final int BX = PX + 5, BW = 240, BH = 22;
    // pozycje przyciskow (Create, Join, Copy, Leave, RemoveWp, RemoveTeam)
    private static final int[] BY = {PY + 100, PY + 132, PY + 164, PY + 196, PY + 258, PY + 290};
    private static final String[] LABELS = {"Create key", "Join team", "Copy key", "Leave team",
            "Remove waypoint", "Remove waypoint team"};
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private String field = Config.key;
    private boolean focused = false;
    private boolean listening = false;
    private String status = "";

    public TeamScreen() {
        super(Text.literal("Team Waypoints"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void rr(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 2, y, x + w - 2, y + h, col);
        c.fill(x, y + 2, x + w, y + h - 2, col);
        c.fill(x + 1, y + 1, x + w - 1, y + h - 1, col);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        TextRenderer tr = this.textRenderer;
        rr(c, PX, PY, PW, PH, 0xD01C1822);

        // naglowek
        c.drawText(tr, "Team Waypoints", PX + 10, PY + 20, 0xFFFFFFFF, false);
        c.drawText(tr, "v", PX + 190, PY + 20, 0xFFB48CE6, false);
        rr(c, PX + 206, PY + 19, 32, 12, Config.enabled ? 0xFFB48CE6 : 0xFF4A4458);
        int kx = Config.enabled ? PX + 224 : PX + 208;
        rr(c, kx, PY + 21, 12, 8, 0xFFFFFFFF);

        // Team key
        c.drawText(tr, "Team key", PX + 10, PY + 70, 0xFFFFFFFF, false);
        if (focused) c.fill(PX + 108, PY + 66, PX + PW - 8, PY + 80, 0x40FFFFFF);
        c.enableScissor(PX + 110, PY + 60, PX + PW - 8, PY + 90);
        String shown = field + (focused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        c.drawText(tr, shown, PX + 112, PY + 70, 0xFFFFFFFF, false);
        c.disableScissor();

        // przyciski
        for (int i = 0; i < 6; i++) {
            boolean hover = in(mx, my, BX, BY[i], BW, BH);
            rr(c, BX, BY[i], BW, BH, hover ? 0xFF6F5CA0 : 0xFF5A4A82);
            int tw = tr.getWidth(LABELS[i]);
            c.drawText(tr, LABELS[i], BX + (BW - tw) / 2, BY[i] + (BH - 8) / 2, 0xFFFFFFFF, false);
        }

        // Create waypoint + klawisz
        c.drawText(tr, "Create waypoint", PX + 10, PY + 233, 0xFFFFFFFF, false);
        String k = listening ? "..." : keyName(TeamWaypointsClient.CREATE);
        rr(c, PX + 200, PY + 229, 38, 16, 0xFFC9A0F0);
        int kw = tr.getWidth(k);
        c.drawText(tr, k, PX + 200 + (38 - kw) / 2, PY + 233, 0xFF2A1F3D, false);

        // status
        String st = status.isEmpty() ? (Net.connected() ? "Polaczono z teamem" : "Brak teamu") : status;
        c.drawText(tr, st, PX + 10, PY + 325, 0xFFAAAAAA, false);
    }

    private String keyName(KeyBinding kb) {
        if (kb.isUnbound()) return "-";
        String s = kb.getBoundKeyLocalizedText().getString();
        return s.length() > 5 ? s.substring(0, 5) : s.toUpperCase();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // ustawianie klawisza przyciskiem myszy
        if (listening) {
            InputUtil.Key mk = InputUtil.Type.MOUSE.createFromCode(button);
            TeamWaypointsClient.CREATE.setBoundKey(mk);
            KeyBinding.updateKeysByCode();
            MinecraftClient.getInstance().options.write();
            listening = false;
            return true;
        }

        focused = in(mx, my, PX + 108, PY + 66, PW - 116, 14);

        // toggle
        if (in(mx, my, PX + 206, PY + 19, 32, 12)) {
            Config.enabled = !Config.enabled;
            Config.save();
            return true;
        }
        // klawisz create waypoint
        if (in(mx, my, PX + 200, PY + 229, 38, 16)) {
            listening = true;
            return true;
        }
        for (int i = 0; i < 6; i++) {
            if (in(mx, my, BX, BY[i], BW, BH)) {
                action(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void action(int i) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (i) {
            case 0 -> { // create key
                SecureRandom r = new SecureRandom();
                StringBuilder sb = new StringBuilder();
                for (int n = 0; n < 18; n++) sb.append(CHARS.charAt(r.nextInt(CHARS.length())));
                field = sb.toString();
                join();
                status = "Klucz utworzony";
            }
            case 1 -> join();
            case 2 -> {
                mc.keyboard.setClipboard(field);
                status = "Klucz skopiowany";
            }
            case 3 -> {
                Net.disconnect();
                Config.key = "";
                Config.save();
                field = "";
                TeamWaypointsClient.WAYPOINTS.clear();
                status = "Opuszczono team";
            }
            case 4 -> TeamWaypointsClient.removeOwn();
            case 5 -> TeamWaypointsClient.removeTeam();
        }
    }

    private void join() {
        if (field.length() < 6) {
            status = "Wklej poprawny klucz";
            return;
        }
        TeamWaypointsClient.WAYPOINTS.clear();
        Config.key = field;
        Config.save();
        Net.connect(field);
        status = "Dolaczono do teamu";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (listening) {
            if (keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                InputUtil.Key key = InputUtil.fromKeyCode(keyCode, scanCode);
                TeamWaypointsClient.CREATE.setBoundKey(key);
                KeyBinding.updateKeysByCode();
                mc.options.write();
            }
            listening = false;
            return true;
        }
        if (focused) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (!field.isEmpty()) field = field.substring(0, field.length() - 1);
                return true;
            }
            if (Screen.hasControlDown() && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
                String clip = mc.keyboard.getClipboard().trim().toUpperCase();
                StringBuilder sb = new StringBuilder();
                for (char ch : clip.toCharArray()) if (CHARS.indexOf(ch) >= 0) sb.append(ch);
                field = sb.length() > 18 ? sb.substring(0, 18) : sb.toString();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                join();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focused) {
            char u = Character.toUpperCase(chr);
            if (CHARS.indexOf(u) >= 0 && field.length() < 18) field += u;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }
}
