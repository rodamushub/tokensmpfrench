package dev.tokensmp.ui;

import dev.tokensmp.TokenSMPPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** One-shot "type your answer in chat" prompts used by the GUIs (search, custom amount). */
public final class ChatPrompts implements Listener {

    private final TokenSMPPlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatPrompts(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void ask(Player p, String promptKey, Consumer<String> callback) {
        UUID id = p.getUniqueId();
        pending.put(id, callback);
        p.closeInventory();
        plugin.msg().send(p, promptKey);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(id, callback)) {
                Player online = plugin.getServer().getPlayer(id);
                if (online != null) plugin.msg().send(online, "prompt-timeout");
            }
        }, 20L * 30);
    }

    public void cancel(UUID id) {
        pending.remove(id);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Consumer<String> callback = pending.remove(p.getUniqueId());
        if (callback == null) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("annuler")) {
                plugin.msg().send(p, "cancelled");
                return;
            }
            callback.accept(text);
        });
    }
}
