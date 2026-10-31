package dev.tokensmp.listener;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.sound.SoundType;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.net.InetSocketAddress;
import java.util.UUID;

public final class JoinQuitListener implements Listener {

    private final TokenSMPPlugin plugin;

    public JoinQuitListener(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.economy().getOrCreate(p);
        d.lastSeen = System.currentTimeMillis();
        InetSocketAddress addr = p.getAddress();
        if (addr != null && addr.getAddress() != null) {
            d.ipHash = plugin.economy().hashIp(addr.getAddress().getHostAddress());
        }
        boolean first = !d.welcomed;
        d.welcomed = true;
        plugin.economy().tx().save(d).commit();

        plugin.scoreboards().apply(p);
        plugin.bounties().refreshTab(p.getUniqueId());

        if (first) {
            // Small delay so the welcome is not buried under the join messages.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                plugin.msg().sendList(p, "welcome", "player", p.getName());
                p.showTitle(Title.title(plugin.msg().get("welcome-title"), plugin.msg().get("welcome-subtitle")));
                plugin.sounds().play(p, SoundType.FIRST_JOIN);
            }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        var s = plugin.settings();

        UUID opponent = plugin.combat().opponent(id);
        if (s.combatEnabled && opponent != null && !p.isDead()) {
            boolean paid = plugin.kills().processKill(opponent, id);
            if (paid) {
                plugin.broadcast(plugin.msg().get("combat-log", "player", p.getName()));
            }
            if (s.killOnLogout) {
                plugin.combat().markSettled(id);
                p.setHealth(0.0);
            }
        }
        plugin.combat().clear(id);
        if (opponent != null) plugin.combat().clear(opponent);

        PlayerData d = plugin.economy().get(id);
        if (d != null) {
            d.lastSeen = System.currentTimeMillis();
            plugin.economy().tx().save(d).commit();
        }
        plugin.scoreboards().remove(id);
        plugin.prompts().cancel(id);
        plugin.sounds().forget(id);
    }
}
