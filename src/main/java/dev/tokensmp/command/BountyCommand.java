package dev.tokensmp.command;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.gui.BountyMenu;
import dev.tokensmp.util.Fmt;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BountyCommand implements TabExecutor {

    private static final int PER_PAGE = 10;

    private final TokenSMPPlugin plugin;

    public BountyCommand(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player p)) return plugin.fail(sender, "player-only");
            new BountyMenu(plugin, p, BountyMenu.Mode.ALL).open();
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Math.max(1, Integer.parseInt(args[1]));
                    } catch (NumberFormatException ignored) {
                        // keep page 1
                    }
                }
                sendList(sender, "Toutes les primes", page, Integer.MAX_VALUE);
            }
            case "top" -> sendList(sender, "Meilleures primes", 1, 10);
            case "check" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.msg().parse("<prefix><gray>Utilisation : <white>/bounty check <player>"));
                    return true;
                }
                PlayerData t = plugin.economy().find(args[1]);
                if (t == null) return plugin.fail(sender, "unknown-player", "player", args[1]);
                long total = plugin.bounties().total(t.uuid);
                if (total <= 0) {
                    plugin.msg().send(sender, "bounty-check-none", "target", t.name);
                } else {
                    plugin.msg().send(sender, "bounty-check", "target", t.name, "total", Fmt.num(total),
                            "count", plugin.bounties().contributors(t.uuid));
                }
            }
            default -> {
                if (!(sender instanceof Player p)) return plugin.fail(sender, "player-only");
                if (args.length < 2) {
                    p.sendMessage(plugin.msg().parse("<prefix><gray>Utilisation : <white>/bounty <player> <amount>"));
                    return true;
                }
                PlayerData target = plugin.economy().find(args[0]);
                if (target == null) return plugin.fail(p, "unknown-player", "player", args[0]);
                long amount = Fmt.parseAmount(args[1]);
                if (amount <= 0) return plugin.fail(p, "invalid-amount");
                plugin.bounties().startPlace(p, target, amount, null);
            }
        }
        return true;
    }

    private void sendList(CommandSender sender, String title, int page, int limit) {
        List<Map.Entry<UUID, Long>> all = plugin.bounties().ranked();
        if (all.isEmpty()) {
            plugin.msg().send(sender, "bounty-list-empty");
            return;
        }
        if (limit < Integer.MAX_VALUE) all = all.subList(0, Math.min(limit, all.size()));
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages);
        sender.sendMessage(plugin.msg().get("bounty-list-header", "title", title, "page", page, "pages", pages));
        int start = (page - 1) * PER_PAGE;
        for (int i = start; i < Math.min(all.size(), start + PER_PAGE); i++) {
            Map.Entry<UUID, Long> e = all.get(i);
            sender.sendMessage(plugin.msg().get("bounty-list-line", "rank", i + 1,
                    "target", plugin.economy().nameOf(e.getKey()), "total", Fmt.num(e.getValue())));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : List.of("list", "top", "check", "menu")) if (s.startsWith(prefix)) out.add(s);
            addPlayers(out, prefix);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("check")) {
            addPlayers(out, prefix);
        }
        return out;
    }

    private void addPlayers(List<String> out, String prefix) {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        }
    }
}
