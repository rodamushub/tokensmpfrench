package dev.tokensmp.util;

import dev.tokensmp.TokenSMPPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Typed snapshot of config.yml. Reloaded with /tokensmp reload. */
public final class Settings {

    private final TokenSMPPlugin plugin;

    public long killReward;
    public double stealPercent;
    public long maxSteal;
    public long startingBalance;
    public String symbol;
    public String currency;

    public Set<String> disabledWorlds = new HashSet<>();

    public long repeatCooldownMs;
    public int repeatMaxPerHour;
    public boolean blockSameNetworkKills;
    public boolean blockSameNetworkTransfers;

    public boolean combatEnabled;
    public int combatSeconds;
    public boolean killOnLogout;
    public boolean countEnvironmentDeaths;

    public long bountyMin;
    public long bountyMaxTotal;
    public List<Long> bountyPresets = new ArrayList<>();
    public long broadcastPlacedMin;
    public long broadcastClaimedMin;
    public boolean bountyTabList;
    public boolean confirmBounty;

    public long payMin;
    public boolean confirmPay;

    public boolean soundsEnabled;
    public long soundMinIntervalMs;

    public boolean scoreboardEnabled;
    public int scoreboardTicks;
    public String scoreboardTitle;
    public List<String> scoreboardLines = new ArrayList<>();

    public int boardCacheSeconds;
    public int boardGuiEntries;

    public boolean hologramsEnabled;
    public int hologramRefreshSeconds;
    public int hologramEntries;

    public boolean logTransactions;
    public long clickCooldownMs;

    public Settings(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        killReward = Math.max(0, c.getLong("tokens.kill-reward", 100));
        stealPercent = Math.min(100, Math.max(0, c.getDouble("tokens.steal-percent", 50)));
        maxSteal = Math.max(0, c.getLong("tokens.max-steal-per-kill", 0));
        startingBalance = Math.max(0, c.getLong("tokens.starting-balance", 0));
        symbol = c.getString("tokens.symbol", "◆");
        currency = c.getString("tokens.currency-name", "Tokens");

        disabledWorlds = new HashSet<>(c.getStringList("pvp.disabled-worlds"));

        repeatCooldownMs = Math.max(0, c.getLong("anti-exploit.repeat-kill.cooldown-seconds", 600)) * 1000L;
        repeatMaxPerHour = Math.max(1, c.getInt("anti-exploit.repeat-kill.max-per-hour", 3));
        blockSameNetworkKills = c.getBoolean("anti-exploit.block-same-network-kills", true);
        blockSameNetworkTransfers = c.getBoolean("anti-exploit.block-same-network-transfers", false);

        combatEnabled = c.getBoolean("combat.enabled", true);
        combatSeconds = Math.max(1, c.getInt("combat.tag-seconds", 15));
        killOnLogout = c.getBoolean("combat.kill-on-logout", true);
        countEnvironmentDeaths = c.getBoolean("combat.count-environment-deaths-while-tagged", true);

        bountyMin = Math.max(1, c.getLong("bounty.min-amount", 50));
        bountyMaxTotal = Math.max(bountyMin, c.getLong("bounty.max-total-per-player", 1_000_000));
        bountyPresets = new ArrayList<>();
        for (long l : c.getLongList("bounty.presets")) {
            if (l > 0 && bountyPresets.size() < 5) bountyPresets.add(l);
        }
        broadcastPlacedMin = Math.max(0, c.getLong("bounty.broadcast-placed-min", 1000));
        broadcastClaimedMin = Math.max(0, c.getLong("bounty.broadcast-claimed-min", 500));
        bountyTabList = c.getBoolean("bounty.show-in-tab-list", true);
        confirmBounty = c.getBoolean("bounty.confirm", true);

        payMin = Math.max(1, c.getLong("transfers.min-amount", 1));
        confirmPay = c.getBoolean("transfers.confirm", true);

        soundsEnabled = c.getBoolean("sounds.enabled", true);
        soundMinIntervalMs = Math.max(0, c.getLong("sounds.min-interval-ms", 150));

        scoreboardEnabled = c.getBoolean("scoreboard.enabled", true);
        scoreboardTicks = Math.max(5, c.getInt("scoreboard.update-ticks", 20));
        scoreboardTitle = c.getString("scoreboard.title", "<gold><bold>TOKEN SMP");
        scoreboardLines = c.getStringList("scoreboard.lines");
        if (scoreboardLines.size() > 15) scoreboardLines = scoreboardLines.subList(0, 15);

        boardCacheSeconds = Math.max(0, c.getInt("leaderboards.cache-seconds", 5));
        boardGuiEntries = c.getInt("leaderboards.gui-entries", 14) <= 7 ? 7 : 14;

        hologramsEnabled = c.getBoolean("holograms.enabled", true);
        hologramRefreshSeconds = Math.max(5, c.getInt("holograms.refresh-seconds", 30));
        hologramEntries = Math.min(15, Math.max(3, c.getInt("holograms.entries", 10)));

        logTransactions = c.getBoolean("logging.log-transactions-to-console", true);
        clickCooldownMs = Math.max(0, c.getLong("gui.click-cooldown-ms", 150));
    }
}
