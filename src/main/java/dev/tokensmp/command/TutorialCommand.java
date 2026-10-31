package dev.tokensmp.command;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.gui.TutorialMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TutorialCommand implements CommandExecutor {

    private final TokenSMPPlugin plugin;

    public TutorialCommand(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return plugin.fail(sender, "player-only");
        new TutorialMenu(plugin, p, 0).open();
        return true;
    }
}
