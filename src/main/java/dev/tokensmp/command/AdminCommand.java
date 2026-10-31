package dev.tokensmp.command;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.gui.MainMenu;
import dev.tokensmp.ui.HologramService;
import dev.tokensmp.ui.LeaderboardService.Board;
import dev.tokensmp.util.Fmt;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** /tokensmp: opens the main menu for players; administration subcommands need tokensmp.admin. */
public final class AdminCommand implements TabExecutor {

    private static final List<String> SUBS = List.of("menu", "reload", "balance", "give", "take", "set",
            "clearbounty", "ledger", "hologram");

    private final TokenSMPPlugin plugin;

    public AdminCommand(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("menu")) {
            if (!(sender instanceof Player p)) return plugin.fail(sender, "player-only");
            new MainMenu(plugin, p).open();
            return true;
        }
        if (!sender.hasPermission("tokensmp.admin")) return plugin.fail(sender, "no-permission");

        switch (sub) {
            case "reload" -> {
                plugin.reload();
                plugin.msg().send(sender, "admin-reloaded");
            }
            case "balance", "give", "take", "set" -> balanceCommand(sender, sub, args);
            case "clearbounty" -> {
                if (args.length < 2) return usage(sender);
                PlayerData t = plugin.economy().find(args[1]);
                if (t == null) return plugin.fail(sender, "unknown-player", "player", args[1]);
                plugin.bounties().clear(t.uuid, sender.getName());
                plugin.msg().send(sender, "admin-bounty-cleared", "player", t.name);
            }
            case "ledger" -> {
                int n = 10;
                if (args.length > 1) {
                    try {
                        n = Math.max(1, Math.min(50, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }
                int limit = n;
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        for (String line : plugin.storageLedger(limit)) sender.sendMessage(line);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "Could not read the ledger", e);
                    }
                });
            }
            case "hologram" -> hologramCommand(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void balanceCommand(CommandSender sender, String sub, String[] args) {
        if (args.length < 2) {
            usage(sender);
            return;
        }
        PlayerData t = plugin.economy().find(args[1]);
        if (t == null) {
            plugin.fail(sender, "unknown-player", "player", args[1]);
            return;
        }
        if (sub.equals("balance")) {
            plugin.msg().send(sender, "admin-balance", "player", t.name, "balance", Fmt.num(t.balance));
            return;
        }
        long amount = args.length > 2 ? Fmt.parseAmount(args[2]) : -1;
        if (amount <= 0 && !(sub.equals("set") && args.length > 2 && args[2].equals("0"))) {
            plugin.fail(sender, "invalid-amount");
            return;
        }
        long next = switch (sub) {
            case "give" -> t.balance + amount;
            case "take" -> t.balance - amount;
            default -> Math.max(0, amount);
        };
        plugin.economy().adminSet(t, next, sender.getName());
        plugin.msg().send(sender, "admin-adjusted", "player", t.name, "balance", Fmt.num(t.balance));
    }

    private void hologramCommand(CommandSender sender, String[] args) {
        HologramService holos = plugin.holograms();
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "create" -> {
                if (!(sender instanceof Player p)) {
                    plugin.fail(sender, "player-only");
                    return;
                }
                Board board = args.length > 2 ? HologramService.parseBoard(args[2]) : null;
                if (board == null) {
                    plugin.msg().send(sender, "admin-hologram-types");
                    return;
                }
                Location loc = p.getLocation().add(0, 2.5, 0);
                int id = holos.create(board, loc);
                plugin.msg().send(sender, "admin-hologram-created", "id", id, "type", board.name().toLowerCase(Locale.ROOT));
            }
            case "remove" -> {
                int id = -1;
                try {
                    id = args.length > 2 ? Integer.parseInt(args[2]) : -1;
                } catch (NumberFormatException ignored) {
                    // handled below
                }
                if (holos.remove(id)) plugin.msg().send(sender, "admin-hologram-removed", "id", id);
                else plugin.msg().send(sender, "admin-hologram-missing");
            }
            case "list" -> {
                for (HologramService.Holo h : holos.list()) {
                    plugin.msg().send(sender, "admin-hologram-list", "id", h.id(),
                            "type", h.board().name().toLowerCase(Locale.ROOT), "world", h.world(),
                            "x", (int) h.x(), "y", (int) h.y(), "z", (int) h.z());
                }
            }
            default -> usage(sender);
        }
    }

    private boolean usage(CommandSender sender) {
        plugin.msg().send(sender, "admin-usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.equals("menu") || sender.hasPermission("tokensmp.admin")) {
                    if (s.startsWith(prefix)) out.add(s);
                }
            }
        } else if (sender.hasPermission("tokensmp.admin")) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2 && List.of("balance", "give", "take", "set", "clearbounty").contains(sub)) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
                }
            } else if (sub.equals("hologram")) {
                if (args.length == 2) {
                    for (String s : List.of("create", "remove", "list")) if (s.startsWith(prefix)) out.add(s);
                } else if (args.length == 3 && args[1].equalsIgnoreCase("create")) {
                    for (String s : List.of("richest", "kills", "bounty", "claimed", "placed")) if (s.startsWith(prefix)) out.add(s);
                }
            }
        }
        return out;
    }
}
