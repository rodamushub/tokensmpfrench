package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.sound.SoundType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuListener implements Listener {

    private final TokenSMPPlugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public MenuListener(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
        e.setCancelled(true); // menus never let items move, from either inventory
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        // Ignore rapid repeat clicks so a confirm button can never be triggered twice by accident.
        long now = System.currentTimeMillis();
        Long prev = lastClick.get(p.getUniqueId());
        if (prev != null && now - prev < plugin.settings().clickCooldownMs) return;
        lastClick.put(p.getUniqueId(), now);

        menu.handle(e);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder(false) instanceof Menu)) return;
        // Only when the player closes it themselves, not when we swap to another menu or close from code.
        if (e.getReason() == InventoryCloseEvent.Reason.PLAYER && e.getPlayer() instanceof Player p) {
            plugin.sounds().play(p, SoundType.MENU_CLOSE);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastClick.remove(e.getPlayer().getUniqueId());
    }
}
