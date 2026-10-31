package dev.tokensmp.pvp;

import dev.tokensmp.TokenSMPPlugin;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tracks who is in combat with whom, and which deaths were already settled (combat logouts). */
public final class CombatManager {

    private record Tag(UUID opponent, long expiresAt) {}

    private final TokenSMPPlugin plugin;
    private final Map<UUID, Tag> tags = new HashMap<>();
    private final Set<UUID> settled = new HashSet<>();

    public CombatManager(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    /** Tags both players. Returns true if {@code victim} was not already tagged. */
    public boolean tag(Player victim, Player attacker) {
        long until = System.currentTimeMillis() + plugin.settings().combatSeconds * 1000L;
        boolean fresh = opponent(victim.getUniqueId()) == null;
        tags.put(victim.getUniqueId(), new Tag(attacker.getUniqueId(), until));
        tags.put(attacker.getUniqueId(), new Tag(victim.getUniqueId(), until));
        return fresh;
    }

    /** The active opponent, or null when the tag has expired. */
    public UUID opponent(UUID id) {
        Tag t = tags.get(id);
        if (t == null) return null;
        if (t.expiresAt() < System.currentTimeMillis()) {
            tags.remove(id);
            return null;
        }
        return t.opponent();
    }

    public void clear(UUID id) {
        tags.remove(id);
    }

    /** Marks a death as already settled so the death listener does not pay it a second time. */
    public void markSettled(UUID id) {
        settled.add(id);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> settled.remove(id), 100L);
    }

    public boolean consumeSettled(UUID id) {
        return settled.remove(id);
    }
}
