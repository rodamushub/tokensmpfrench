package dev.tokensmp.listener;

import dev.tokensmp.TokenSMPPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class CombatListener implements Listener {

    private final TokenSMPPlugin plugin;

    public CombatListener(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!plugin.settings().combatEnabled) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (e.getFinalDamage() <= 0) return;
        if (plugin.settings().disabledWorlds.contains(victim.getWorld().getName())) return;

        boolean victimWasFree = plugin.combat().tag(victim, attacker);
        if (victimWasFree) victim.sendActionBar(plugin.msg().get("combat-tagged"));
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
