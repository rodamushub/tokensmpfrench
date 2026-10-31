package dev.tokensmp;

import dev.tokensmp.bounty.BountyManager;
import dev.tokensmp.command.AdminCommand;
import dev.tokensmp.command.BountyCommand;
import dev.tokensmp.command.HelpCommand;
import dev.tokensmp.command.TokensCommand;
import dev.tokensmp.command.TutorialCommand;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.data.Storage;
import dev.tokensmp.economy.Economy;
import dev.tokensmp.gui.MenuListener;
import dev.tokensmp.listener.CombatListener;
import dev.tokensmp.listener.DeathListener;
import dev.tokensmp.listener.JoinQuitListener;
import dev.tokensmp.pvp.CombatManager;
import dev.tokensmp.pvp.KillService;
import dev.tokensmp.sound.SoundService;
import dev.tokensmp.sound.SoundType;
import dev.tokensmp.ui.ChatPrompts;
import dev.tokensmp.ui.HologramService;
import dev.tokensmp.ui.LeaderboardService;
import dev.tokensmp.ui.ScoreboardService;
import dev.tokensmp.util.Msg;
import dev.tokensmp.util.Settings;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public final class TokenSMPPlugin extends JavaPlugin {

    private Settings settings;
    private Msg msg;
    private Storage storage;
    private Economy economy;
    private BountyManager bounties;
    private SoundService sounds;
    private CombatManager combat;
    private KillService kills;
    private LeaderboardService boards;
    private ChatPrompts prompts;
    private ScoreboardService scoreboards;
    private HologramService holograms;

    /** Startup checklist printed to the console so you can see exactly what worked. */
    private final List<String> report = new ArrayList<>();
    private boolean startupFailed = false;

    private void step(boolean success, String what) {
        report.add((success ? "[ OK ]   " : "[FAILED] ") + what);
        if (!success) startupFailed = true;
    }

    @Override
    public void onLoad() {
        // If you see this line, the server found and loaded the jar. If you never see it,
        // the file is not a valid compiled plugin (see the "Debug" section of README.md).
        getLogger().info("[DEBUG] TokenSMP jar detected and loaded (onLoad reached).");
    }

    @Override
    public void onEnable() {
        report.clear();
        startupFailed = false;
        try {
            enableInternal();
        } catch (Throwable t) {
            step(false, "Unexpected error while starting: " + t);
            getLogger().log(Level.SEVERE, "[DEBUG] Startup crashed", t);
        }
        printStartupReport();
        if (startupFailed) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // Runs on the first server tick, i.e. once the server has finished starting.
        // Proves the scheduler works and that commands are really wired up.
        getServer().getScheduler().runTask(this, () -> {
            StringBuilder missing = new StringBuilder();
            for (String name : new String[]{"tokens", "bounty", "help", "tutorial", "tokensmp"}) {
                PluginCommand c = getCommand(name);
                if (c == null || c.getExecutor() == this) missing.append(name).append(' ');
            }
            if (missing.length() == 0) {
                getLogger().info("[DEBUG] >>> TokenSMP IS WORKING: server finished starting, all commands answer. Try /tokens in game. <<<");
            } else {
                getLogger().warning("[DEBUG] >>> TokenSMP loaded but these commands have no handler: " + missing + "<<<");
            }
        });
    }

    private void printStartupReport() {
        var log = getLogger();
        log.info("================ TokenSMP startup check ================");
        for (String line : report) {
            if (line.startsWith("[FAILED]")) log.severe(line); else log.info(line);
        }
        if (startupFailed) {
            log.severe("RESULT: FAILED - TokenSMP did NOT start. Read the [FAILED] lines above.");
        } else {
            log.info("RESULT: SUCCESS - TokenSMP " + getPluginMeta().getVersion() + " enabled.");
        }
        log.info("========================================================");
    }

    private void enableInternal() {
        // --- Environment info -------------------------------------------------------------
        int java = Runtime.version().feature();
        step(java >= 25, "Java " + java + (java >= 25 ? "" : " (Paper 26.x needs Java 25: change it in the FalixNodes Startup tab)"));
        step(true, "Server: " + getServer().getVersion() + " | API: " + getServer().getBukkitVersion());

        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) { }
        step(!folia, folia ? "Folia detected: TokenSMP does not support Folia, use Paper" : "Paper (non-Folia) server");

        // ViaVersion / ViaBackwards only change what clients can connect; they do not affect this plugin.
        var pm = getServer().getPluginManager();
        report.add("[ INFO ] ViaVersion: " + (pm.getPlugin("ViaVersion") != null ? "present" : "not installed")
                + " | ViaBackwards: " + (pm.getPlugin("ViaBackwards") != null ? "present" : "not installed")
                + " (not required, no conflict)");
        if (startupFailed) return; // wrong Java or Folia: stop before touching anything

        // --- Config / messages ------------------------------------------------------------
        try {
            saveDefaultConfig();
            if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
            settings = new Settings(this);
            settings.load();
            msg = new Msg(this);
            msg.load();
            step(true, "config.yml and messages.yml loaded");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "[DEBUG] Config error", t);
            step(false, "config.yml / messages.yml could not be loaded: " + t);
            return;
        }

        // --- Database ---------------------------------------------------------------------
        storage = new Storage(this);
        try {
            storage.open();
            economy = new Economy(this, storage);
            economy.load();
            bounties = new BountyManager(this, economy, storage);
            bounties.load();
            step(true, "Database tokens.db opened, " + economy.all().size() + " players loaded");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "[DEBUG] Database error", t);
            step(false, "Database (SQLite) could not be opened: " + t);
            return;
        }

        // --- Services, listeners, commands ------------------------------------------------
        try {
            sounds = new SoundService(this);
            sounds.load();
            combat = new CombatManager(this);
            kills = new KillService(this);
            boards = new LeaderboardService(this);
            prompts = new ChatPrompts(this);
            scoreboards = new ScoreboardService(this);
            holograms = new HologramService(this);

            pm.registerEvents(new MenuListener(this), this);
            pm.registerEvents(prompts, this);
            pm.registerEvents(new JoinQuitListener(this), this);
            pm.registerEvents(new CombatListener(this), this);
            pm.registerEvents(new DeathListener(this), this);
            step(true, "Services created and 5 event listeners registered");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "[DEBUG] Service/listener error", t);
            step(false, "Services or listeners failed: " + t);
            return;
        }

        int registered = 0;
        registered += register("tokens", new TokensCommand(this)) ? 1 : 0;
        registered += register("bounty", new BountyCommand(this)) ? 1 : 0;
        registered += register("help", new HelpCommand(this)) ? 1 : 0;
        registered += register("tutorial", new TutorialCommand(this)) ? 1 : 0;
        registered += register("tokensmp", new AdminCommand(this)) ? 1 : 0;
        step(registered == 5, "Commands registered: " + registered + "/5");

        try {
            scoreboards.start();
            holograms.start();
            step(true, "Scoreboard and hologram tasks started");
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "[DEBUG] Scheduler error", t);
            step(false, "Scoreboard/hologram tasks failed: " + t);
        }
    }

    @Override
    public void onDisable() {
        if (holograms != null) holograms.shutdown();
        if (scoreboards != null) scoreboards.shutdown();
        if (storage != null) storage.close();
        getLogger().info("[DEBUG] TokenSMP disabled.");
    }

    private boolean register(String name, CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command missing from plugin.yml: " + name);
            return false;
        }
        cmd.setExecutor(executor);
        return true;
    }

    public void reload() {
        settings.load();
        msg.load();
        sounds.load();
        scoreboards.reload();
        holograms.reload();
    }

    /** Sends an error message and plays the "declined" sound. Always returns false for easy chaining. */
    public boolean fail(CommandSender to, String key, Object... kv) {
        msg.send(to, key, kv);
        if (to instanceof Player p) sounds.play(p, SoundType.DECLINED);
        return false;
    }

    /** Broadcasts to everyone who has broadcasts enabled, and to the console. */
    public void broadcast(Component message) {
        for (Player p : getServer().getOnlinePlayers()) {
            PlayerData d = economy.get(p.getUniqueId());
            if (d == null || d.broadcasts) p.sendMessage(message);
        }
        getServer().getConsoleSender().sendMessage(message);
    }

    /** Recent ledger lines for /tokensmp ledger (call off the main thread). */
    public java.util.List<String> storageLedger(int limit) throws java.sql.SQLException {
        return storage.recentLedger(limit);
    }

    public Settings settings() { return settings; }
    public Msg msg() { return msg; }
    public Economy economy() { return economy; }
    public BountyManager bounties() { return bounties; }
    public SoundService sounds() { return sounds; }
    public CombatManager combat() { return combat; }
    public KillService kills() { return kills; }
    public LeaderboardService boards() { return boards; }
    public ChatPrompts prompts() { return prompts; }
    public ScoreboardService scoreboards() { return scoreboards; }
    public HologramService holograms() { return holograms; }
}
