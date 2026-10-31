package dev.tokensmp.ui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.ui.LeaderboardService.Board;
import dev.tokensmp.ui.LeaderboardService.Entry;
import dev.tokensmp.util.Fmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Leaderboard holograms built from vanilla text display entities (no extra plugin needed).
 * Entities are non-persistent: they are respawned from holograms.yml, so they never pile up.
 */
public final class HologramService {

    public record Holo(int id, Board board, String world, double x, double y, double z) {}

    private final TokenSMPPlugin plugin;
    private final File file;
    private final Map<Integer, Holo> holos = new LinkedHashMap<>();
    private final Map<Integer, UUID> spawned = new LinkedHashMap<>();
    private BukkitTask task;

    public HologramService(TokenSMPPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holograms.yml");
    }

    public void start() {
        stopTask();
        load();
        if (!plugin.settings().hologramsEnabled) return;
        long period = 20L * plugin.settings().hologramRefreshSeconds;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, period);
    }

    public void reload() {
        despawnAll();
        start();
    }

    public void shutdown() {
        stopTask();
        despawnAll();
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ── admin operations ─────────────────────────────────────────────────

    public static Board parseBoard(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "richest", "tokens" -> Board.RICHEST;
            case "kills" -> Board.KILLS;
            case "bounty", "bounties" -> Board.BOUNTY;
            case "claimed" -> Board.CLAIMED;
            case "placed" -> Board.PLACED;
            default -> null;
        };
    }

    public int create(Board board, Location loc) {
        int id = 1;
        while (holos.containsKey(id)) id++;
        holos.put(id, new Holo(id, board, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ()));
        save();
        refreshAll();
        return id;
    }

    public boolean remove(int id) {
        if (holos.remove(id) == null) return false;
        despawn(id);
        save();
        return true;
    }

    public List<Holo> list() {
        return new ArrayList<>(holos.values());
    }

    // ── rendering ────────────────────────────────────────────────────────

    private void refreshAll() {
        for (Holo h : holos.values()) render(h);
    }

    private void render(Holo h) {
        World world = plugin.getServer().getWorld(h.world());
        if (world == null) return;
        Location loc = new Location(world, h.x(), h.y(), h.z());
        if (!loc.isChunkLoaded()) return;

        Component text = buildText(h.board());
        Entity existing = spawned.containsKey(h.id()) ? plugin.getServer().getEntity(spawned.get(h.id())) : null;
        if (existing instanceof TextDisplay td && td.isValid()) {
            td.text(text);
            return;
        }
        TextDisplay td = world.spawn(loc, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.text(text);
        });
        spawned.put(h.id(), td.getUniqueId());
    }

    private Component buildText(Board board) {
        String sym = plugin.settings().symbol;
        List<Component> lines = new ArrayList<>();
        lines.add(plugin.msg().parse("<gold><bold>" + board.title.toUpperCase(Locale.ROOT)));
        List<Entry> top = plugin.boards().top(board, plugin.settings().hologramEntries);
        if (top.isEmpty()) {
            lines.add(plugin.msg().parse("<gray>Rien à afficher pour l'instant"));
        }
        int rank = 1;
        for (Entry e : top) {
            String value = board.tokens ? sym + " " + Fmt.num(e.value()) : Fmt.num(e.value());
            String color = rank == 1 ? "<gold>" : rank == 2 ? "<gray>" : rank == 3 ? "<#cd7f32>" : "<white>";
            lines.add(plugin.msg().mini().deserialize(color + rank + ". " + e.name() + " <dark_gray>» <yellow>" + value));
            rank++;
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private void despawn(int id) {
        UUID uuid = spawned.remove(id);
        if (uuid == null) return;
        Entity e = plugin.getServer().getEntity(uuid);
        if (e != null) e.remove();
    }

    private void despawnAll() {
        for (Integer id : new ArrayList<>(spawned.keySet())) despawn(id);
    }

    // ── storage ──────────────────────────────────────────────────────────

    private void load() {
        holos.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = yml.getConfigurationSection("holograms");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            Board board = parseBoard(s.getString("type", ""));
            if (board == null) continue;
            int id = Integer.parseInt(key);
            holos.put(id, new Holo(id, board, s.getString("world", "world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z")));
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Holo h : holos.values()) {
            String p = "holograms." + h.id() + ".";
            yml.set(p + "type", h.board().name().toLowerCase(Locale.ROOT));
            yml.set(p + "world", h.world());
            yml.set(p + "x", h.x());
            yml.set(p + "y", h.y());
            yml.set(p + "z", h.z());
        }
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save holograms.yml", e);
        }
    }
}
