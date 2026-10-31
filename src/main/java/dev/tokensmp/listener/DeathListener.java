package dev.tokensmp.listener;

import dev.tokensmp.TokenSMPPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

public final class DeathListener implements Listener {

    private final TokenSMPPlugin plugin;

    public DeathListener(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        UUID victimId = victim.getUniqueId();

        // Combat logouts were already paid when the player quit.
        if (plugin.combat().consumeSettled(victimId)) {
            plugin.combat().clear(victimId);
            return;
        }

        UUID killerId = null;
        Player killer = victim.getKiller();
        if (killer != null) {
            killerId = killer.getUniqueId();
        } else if (plugin.settings().combatEnabled && plugin.settings().countEnvironmentDeaths) {
            // Fell, burned or drowned while still tagged by another player: that player gets the kill.
            killerId = plugin.combat().opponent(victimId);
        }

        // Suicide and plain environmental deaths reward nobody.
        if (killerId != null && !killerId.equals(victimId)) {
            plugin.kills().processKill(killerId, victimId);
        }

        plugin.combat().clear(victimId);
        if (killerId != null) plugin.combat().clear(killerId);
    }
}
