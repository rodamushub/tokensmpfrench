package dev.tokensmp.command;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.gui.MainMenu;
import dev.tokensmp.ui.LeaderboardService.Board;
import dev.tokensmp.ui.LeaderboardService.Entry;
import dev.tokensmp.util.Fmt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TokensCommand implements TabExecutor {

    private final TokenSMPPlugin plugin;

    public TokensCommand(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> {
                if (!(sender instanceof Player p)) return plugin.fail(sender, "player-only");
                PlayerData d = plugin.economy().get(p.getUniqueId());
                plugin.msg().send(p, "balance", "balance", Fmt.num(d.balance),
                        "rank", plugin.economy().rankByBalance(p.getUniqueId()));
            }
            case "menu" -> {
                if (!(sender instanceof Player p)) return plugin.fail(sender, "player-only");
                new MainMenu(plugin, p).open();
            }
            case "top" -> sendTop(sender);
            case "pay" -> {
                if (!(sender instanceof Player p)) return plugin.fail(sender, "player-only");
                if (args.length < 3) {
                    p.sendMessage(plugin.msg().parse("<prefix><gray>Utilisation : <white>/tokens pay <player> <amount>"));
                    return true;
                }
                PlayerData target = plugin.economy().find(args[1]);
                if (target == null) return plugin.fail(p, "unknown-player", "player", args[1]);
                long amount = Fmt.parseAmount(args[2]);
                if (amount <= 0) return plugin.fail(p, "invalid-amount");
                plugin.economy().startPay(p, target, amount);
            }
            default -> sender.sendMessage(plugin.msg().parse(
                    "<prefix><gray>Utilisation : <white>/tokens</white>, <white>/tokens pay <player> <amount></white>, <white>/tokens top"));
        }
        return true;
    }

    private void sendTop(CommandSender sender) {
        sender.sendMessage(plugin.msg().get("top-header", "title", Board.RICHEST.title));
        List<Entry> top = plugin.boards().top(Board.RICHEST, 10);
        if (top.isEmpty()) {
            plugin.msg().send(sender, "top-empty");
            return;
        }
        int rank = 1;
        for (Entry e : top) {
            sender.sendMessage(plugin.msg().get("top-line", "rank", rank++, "player", e.name(),
                    "value", plugin.settings().symbol + " " + Fmt.num(e.value())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("pay", "top", "menu")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pay")) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
        }
        return out;
    }
}
