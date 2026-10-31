package dev.tokensmp.command;

import dev.tokensmp.TokenSMPPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Locale;

public final class HelpCommand implements TabExecutor {

    private final TokenSMPPlugin plugin;

    public HelpCommand(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String topic = args.length == 0 ? "main" : args[0].toLowerCase(Locale.ROOT);
        switch (topic) {
            case "tokens", "token" -> plugin.msg().sendList(sender, "help.tokens");
            case "bounty", "bounties" -> plugin.msg().sendList(sender, "help.bounty");
            case "commands", "command" -> plugin.msg().sendList(sender, "help.commands");
            default -> plugin.msg().sendList(sender, "help.main");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("tokens", "bounty", "commands").stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
