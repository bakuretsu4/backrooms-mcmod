package com.rickr.backrooms.pilot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;

/**
 * Turns protocol requests into player input and telemetry. Always runs on the client thread.
 * Everything here is something a player could do with keyboard and mouse; there is deliberately
 * no command, teleport or creative shortcut.
 */
final class Pilot {
    private final PilotInput input = new PilotInput();
    private final Autopilot autopilot = new Autopilot(input);

    /** Per client tick, before the game reads its inputs. */
    void tick() {
        autopilot.tick();
        input.apply();
    }

    static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        return o;
    }

    JsonObject handle(JsonObject req) {
        String cmd = req.has("cmd") ? req.get("cmd").getAsString() : "";
        Minecraft mc = Minecraft.getInstance();
        if (cmd.equals("ping")) return ok();
        if (cmd.equals("status")) return status(mc);
        if (cmd.equals("screen")) return screen(mc);
        if (cmd.equals("closeScreen")) return closeScreen(mc);
        if (cmd.equals("click")) return clickWidget(mc, req);
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return error("not in a world");
        return switch (cmd) {
            case "look" -> {
                autopilot.stop("manual look");
                PilotInput.look(p, req.get("yaw").getAsFloat(), req.get("pitch").getAsFloat());
                yield ok();
            }
            case "lookAt" -> {
                autopilot.stop("manual look");
                double dx = req.get("x").getAsDouble() - p.getX();
                double dy = req.get("y").getAsDouble() - p.getEyeY();
                double dz = req.get("z").getAsDouble() - p.getZ();
                PilotInput.look(p, PilotInput.yawTowards(dx, dz), PilotInput.pitchTowards(dx, dy, dz));
                yield ok();
            }
            case "keys" -> keys(req);
            case "use" -> click(mc, PilotInput.USE);
            case "attack" -> click(mc, PilotInput.ATTACK);
            case "select" -> {
                int slot = req.get("slot").getAsInt();
                if (slot < 0 || slot >= Inventory.getSelectionSize()) yield error("slot must be 0-8");
                p.getInventory().selected = slot; // what the hotbar number keys do; synced to the server next tick
                yield ok();
            }
            case "respawn" -> respawn(mc);
            case "chat" -> chat(p, req.get("text").getAsString());
            case "goto" -> {
                autopilot.start(req.get("x").getAsDouble(), req.get("y").getAsDouble(), req.get("z").getAsDouble(),
                        req.has("radius") ? req.get("radius").getAsDouble() : 1.0, false, false, req);
                JsonObject o = ok();
                autopilot.describe(o, p);
                yield o;
            }
            case "explore" -> explore(p, req);
            case "stop" -> {
                autopilot.stop("stopped by request");
                input.releaseAll();
                yield ok();
            }
            case "path" -> {
                JsonObject o = ok();
                o.add("path", autopilot.pathJson(req.has("limit") ? req.get("limit").getAsInt() : 256));
                yield o;
            }
            case "scan" -> BlockScan.scan(mc.level, p.blockPosition(),
                    req.has("radius") ? req.get("radius").getAsInt() : 48, req.get("block").getAsString(),
                    req.has("limit") ? req.get("limit").getAsInt() : 64);
            case "inventory" -> inventory(p);
            default -> error("unknown cmd '" + cmd + "'");
        };
    }

    private JsonObject keys(JsonObject req) {
        // Manual keys take over from the autopilot so the two never fight over the same keys.
        autopilot.stop("manual keys");
        JsonObject keys = req.has("keys") ? req.getAsJsonObject("keys") : req;
        for (var e : keys.entrySet()) {
            if (e.getKey().equals("cmd") || e.getKey().equals("id")) continue;
            int i = PilotInput.indexOf(e.getKey());
            if (i < 0) return error("unknown key '" + e.getKey() + "' (known: " + String.join(",", PilotInput.NAMES) + ")");
            input.set(i, e.getValue().getAsBoolean());
        }
        JsonObject o = ok();
        input.describe(o);
        return o;
    }

    private static JsonObject click(Minecraft mc, int key) {
        if (mc.screen != null) return error("a screen is open (" + mc.screen.getClass().getName() + ")");
        if (!PilotInput.click(key)) return error("that key is unbound in options");
        JsonObject o = ok();
        o.add("target", target(mc));
        return o;
    }

    private JsonObject explore(LocalPlayer p, JsonObject req) {
        double distance = req.has("distance") ? req.get("distance").getAsDouble() : 512.0;
        double tx, tz;
        if (req.has("x") && req.has("z")) {
            tx = req.get("x").getAsDouble();
            tz = req.get("z").getAsDouble();
        } else {
            float yaw;
            if (req.has("yaw")) {
                yaw = req.get("yaw").getAsFloat();
            } else if (req.has("dir")) {
                yaw = switch (req.get("dir").getAsString().toLowerCase()) {
                    case "south", "+z" -> 0;
                    case "west", "-x" -> 90;
                    case "north", "-z" -> 180;
                    case "east", "+x" -> -90;
                    default -> Float.NaN;
                };
                if (Float.isNaN(yaw)) return error("dir must be north/south/east/west or +x/-x/+z/-z");
            } else {
                yaw = p.getYRot();
            }
            // Minecraft's facing vector for a yaw angle.
            tx = p.getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * distance;
            tz = p.getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * distance;
        }
        autopilot.start(tx, p.getY(), tz, req.has("radius") ? req.get("radius").getAsDouble() : 4.0, true, true, req);
        JsonObject o = ok();
        autopilot.describe(o, p);
        return o;
    }

    private static JsonObject chat(LocalPlayer p, String raw) {
        // Same clean-up the chat box does; commands are refused because the test must play fair.
        String text = StringUtil.trimChatMessage(StringUtils.normalizeSpace(raw.trim()));
        if (text.isEmpty()) return error("empty message");
        if (text.startsWith("/")) return error("commands are not allowed through the pilot");
        p.connection.sendChat(text);
        return ok();
    }

    private static JsonObject respawn(Minecraft mc) {
        if (!(mc.screen instanceof DeathScreen screen)) return error("death screen is not open");
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w && w.getMessage().getContents() instanceof TranslatableContents t
                    && (t.getKey().equals("deathScreen.respawn") || t.getKey().equals("deathScreen.spectate"))) {
                if (!w.active || !w.visible) return error("respawn button not enabled yet (the death screen waits ~1 s)");
                pressWidget(w);
                return ok();
            }
        }
        return error("respawn button not found");
    }

    private static boolean pressWidget(AbstractWidget w) {
        // A left click at the button's centre, through the widget's own click handling.
        return w.mouseClicked(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static JsonObject closeScreen(Minecraft mc) {
        Screen s = mc.screen;
        if (s == null) return error("no screen open");
        // Exactly what pressing Escape does; screens that refuse Escape (death screen) stay open.
        boolean handled = s.keyPressed(GLFW.GLFW_KEY_ESCAPE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), 0);
        JsonObject o = handled && mc.screen != s ? ok() : error("screen did not close on Escape");
        o.addProperty("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        return o;
    }

    private static JsonObject screen(Minecraft mc) {
        Screen s = mc.screen;
        JsonObject o = ok();
        if (s == null) {
            o.add("screen", null);
            return o;
        }
        o.addProperty("screen", s.getClass().getName());
        o.addProperty("title", s.getTitle().getString());
        JsonArray widgets = new JsonArray();
        int i = 0;
        for (GuiEventListener child : s.children()) {
            if (child instanceof AbstractWidget w) {
                JsonObject wj = new JsonObject();
                wj.addProperty("index", i);
                wj.addProperty("type", w.getClass().getSimpleName());
                wj.addProperty("label", w.getMessage().getString());
                wj.addProperty("active", w.active);
                wj.addProperty("visible", w.visible);
                wj.addProperty("x", w.getX());
                wj.addProperty("y", w.getY());
                wj.addProperty("w", w.getWidth());
                wj.addProperty("h", w.getHeight());
                widgets.add(wj);
            }
            i++;
        }
        o.add("widgets", widgets);
        return o;
    }

    private static JsonObject clickWidget(Minecraft mc, JsonObject req) {
        Screen s = mc.screen;
        if (s == null) return error("no screen open");
        var children = s.children();
        AbstractWidget target = null;
        if (req.has("index")) {
            int idx = req.get("index").getAsInt();
            if (idx >= 0 && idx < children.size() && children.get(idx) instanceof AbstractWidget w) target = w;
        } else if (req.has("label")) {
            String label = req.get("label").getAsString();
            for (GuiEventListener child : children) {
                if (child instanceof AbstractWidget w && w.visible && w.getMessage().getString().equalsIgnoreCase(label)) {
                    target = w;
                    break;
                }
            }
        }
        if (target == null) return error("no such widget (use the 'screen' command to list them)");
        if (!target.active || !target.visible) return error("widget is not active");
        boolean clicked = pressWidget(target);
        JsonObject o = clicked ? ok() : error("widget ignored the click");
        o.addProperty("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        return o;
    }

    private static JsonObject inventory(LocalPlayer p) {
        JsonObject o = ok();
        Inventory inv = p.getInventory();
        JsonArray items = new JsonArray();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            JsonObject it = new JsonObject();
            it.addProperty("slot", i);
            it.addProperty("id", BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
            it.addProperty("count", st.getCount());
            it.addProperty("name", st.getHoverName().getString());
            items.add(it);
        }
        o.addProperty("selected", inv.selected);
        o.add("items", items);
        return o;
    }

    private static JsonElement target(Minecraft mc) {
        HitResult hit = mc.hitResult;
        JsonObject t = new JsonObject();
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            t.addProperty("type", "miss");
        } else if (hit instanceof BlockHitResult bh) {
            BlockPos pos = bh.getBlockPos();
            t.addProperty("type", "block");
            t.addProperty("x", pos.getX());
            t.addProperty("y", pos.getY());
            t.addProperty("z", pos.getZ());
            t.addProperty("face", bh.getDirection().getName());
            t.addProperty("block", BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString());
        } else if (hit instanceof EntityHitResult eh) {
            t.addProperty("type", "entity");
            t.addProperty("entity", BuiltInRegistries.ENTITY_TYPE.getKey(eh.getEntity().getType()).toString());
            t.addProperty("entityId", eh.getEntity().getId());
        }
        return t;
    }

    private JsonObject status(Minecraft mc) {
        JsonObject o = ok();
        Runtime rt = Runtime.getRuntime();
        o.addProperty("fps", mc.getFps());
        o.addProperty("memUsedMB", (rt.totalMemory() - rt.freeMemory()) >> 20);
        o.addProperty("memMaxMB", rt.maxMemory() >> 20);
        o.addProperty("screen", mc.screen == null ? null : mc.screen.getClass().getName());
        LocalPlayer p = mc.player;
        o.addProperty("inWorld", p != null && mc.level != null);
        if (p != null && mc.level != null) {
            o.addProperty("dimension", mc.level.dimension().location().toString());
            o.addProperty("jumpAllowed", autopilot.jumpAllowed(mc.level));
            o.addProperty("x", p.getX());
            o.addProperty("y", p.getY());
            o.addProperty("z", p.getZ());
            o.addProperty("yaw", Mth.wrapDegrees(p.getYRot()));
            o.addProperty("pitch", p.getXRot());
            o.addProperty("onGround", p.onGround());
            o.addProperty("inWater", p.isInWater());
            o.addProperty("underWater", p.isUnderWater());
            o.addProperty("inLava", p.isInLava());
            o.addProperty("onClimbable", p.onClimbable());
            o.addProperty("horizontalCollision", p.horizontalCollision);
            o.addProperty("health", p.getHealth());
            o.addProperty("maxHealth", p.getMaxHealth());
            o.addProperty("food", p.getFoodData().getFoodLevel());
            o.addProperty("saturation", p.getFoodData().getSaturationLevel());
            o.addProperty("air", p.getAirSupply());
            o.addProperty("maxAir", p.getMaxAirSupply());
            o.addProperty("gamemode", mc.gameMode == null ? null : mc.gameMode.getPlayerMode().getName());
            o.addProperty("isDeadOrDying", p.isDeadOrDying());
            o.addProperty("selectedSlot", p.getInventory().selected);
            ItemStack held = p.getMainHandItem();
            o.addProperty("mainHand", held.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(held.getItem()) + " x" + held.getCount());
            o.addProperty("loadedChunks", mc.level.getChunkSource().getLoadedChunksCount());
            o.addProperty("entities", mc.level.getEntityCount());
            o.add("target", target(mc));
        }
        input.describe(o);
        autopilot.describe(o, p);
        return o;
    }
}
