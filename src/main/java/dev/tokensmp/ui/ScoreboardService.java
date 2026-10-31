package dev.tokensmp.ui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.util.Fmt;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Per-player sidebar: tokens, kills, active bounty. Lines are only rewritten when their text changes. */
public final class ScoreboardService {

    private static final int MAX_LINES = 15;

    private static final class Board {
        final Scoreboard scoreboard;
        final Objective objective;
        final Component[] shown = new Component[MAX_LINES];
        Component title;

        Board(Scoreboard scoreboard, Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }
    }

    private final TokenSMPPlugin plugin;
    private final Map<UUID, Board> boards = new HashMap<>();
    private BukkitTask task;

    public ScoreboardService(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stopTask();
        if (!plugin.settings().scoreboardEnabled) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) apply(p);
        }, 20L, plugin.settings().scoreboardTicks);
    }

    public void reload() {
        for (Player p : Bukkit.getOnlinePlayers()) remove(p.getUniqueId(), p);
        start();
        for (Player p : Bukkit.getOnlinePlayers()) apply(p);
    }

    public void shutdown() {
        stopTask();
        for (Player p : Bukkit.getOnlinePlayers()) remove(p.getUniqueId(), p);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void refresh(Player p) {
        apply(p);
    }

    public void remove(UUID id) {
        boards.remove(id);
    }

    private void remove(UUID id, Player p) {
        boards.remove(id);
        if (p != null && Bukkit.getScoreboardManager() != null) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public void apply(Player p) {
        PlayerData d = plugin.economy().get(p.getUniqueId());
        if (d == null) return;
        if (!plugin.settings().scoreboardEnabled || !d.scoreboard) {
            if (boards.containsKey(p.getUniqueId())) remove(p.getUniqueId(), p);
            return;
        }
        Board b = boards.get(p.getUniqueId());
        if (b == null) {
            b = create();
            if (b == null) return;
            boards.put(p.getUniqueId(), b);
            p.setScoreboard(b.scoreboard);
        }

        Component title = plugin.msg().parse(plugin.settings().scoreboardTitle);
        if (!title.equals(b.title)) {
            b.objective.displayName(title);
            b.title = title;
        }

        long bounty = plugin.bounties().total(p.getUniqueId());
        TagResolver values = TagResolver.resolver(
                Placeholder.unparsed("tokens", Fmt.num(d.balance)),
                Placeholder.unparsed("kills", Fmt.num(d.kills)),
                Placeholder.unparsed("deaths", Fmt.num(d.deaths)),
                Placeholder.unparsed("bounty", bounty > 0 ? Fmt.num(bounty) : "aucune"),
                Placeholder.unparsed("rank", "#" + plugin.economy().rankByBalance(p.getUniqueId())),
                Placeholder.unparsed("symbol", plugin.settings().symbol));

        List<String> lines = plugin.settings().scoreboardLines;
        for (int i = 0; i < MAX_LINES; i++) {
            Team team = b.scoreboard.getTeam("l" + i);
            String entry = entry(i);
            if (i >= lines.size()) {
                if (team != null) {
                    b.scoreboard.resetScores(entry);
                    team.unregister();
                    b.shown[i] = null;
                }
                continue;
            }
            Component text = plugin.msg().mini().deserialize(lines.get(i), values);
            if (team == null) {
                team = b.scoreboard.registerNewTeam("l" + i);
                team.addEntry(entry);
            }
            if (!text.equals(b.shown[i])) {
                team.prefix(text);
                b.shown[i] = text;
            }
            b.objective.getScore(entry).setScore(lines.size() - i);
        }
    }

    private Board create() {
        if (Bukkit.getScoreboardManager() == null) return null;
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("tsmp", Criteria.DUMMY, Component.text("Token SMP"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());
        return new Board(sb, obj);
    }

    /** Invisible, unique scoreboard entry per line (a colour code renders as nothing). */
    private static String entry(int i) {
        return "§" + Integer.toHexString(i);
    }
}
