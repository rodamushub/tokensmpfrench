package dev.tokensmp.ui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Computes the five leaderboards from memory and caches them briefly. */
public final class LeaderboardService {

    public enum Board {
        RICHEST("Joueurs les plus riches", Material.GOLD_BLOCK, true),
        KILLS("Plus de kills", Material.SKELETON_SKULL, false),
        BOUNTY("Plus grosses primes", Material.TARGET, true),
        CLAIMED("Plus de primes réclamées", Material.EMERALD, false),
        PLACED("Plus de primes placées", Material.WRITABLE_BOOK, false);

        public final String title;
        public final Material icon;
        /** True when the value is a token amount (shown with the currency symbol). */
        public final boolean tokens;

        Board(String title, Material icon, boolean tokens) {
            this.title = title;
            this.icon = icon;
            this.tokens = tokens;
        }
    }

    public record Entry(UUID uuid, String name, long value) {}

    private record Cached(long at, List<Entry> list) {}

    private final TokenSMPPlugin plugin;
    private final Map<Board, Cached> cache = new EnumMap<>(Board.class);

    public LeaderboardService(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Entry> top(Board board, int n) {
        List<Entry> all = all(board);
        return all.subList(0, Math.min(n, all.size()));
    }

    /** 1-based rank, or 0 when the player does not appear on the board. */
    public int rank(Board board, UUID id) {
        List<Entry> all = all(board);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).uuid().equals(id)) return i + 1;
        }
        return 0;
    }

    private List<Entry> all(Board board) {
        long now = System.currentTimeMillis();
        Cached c = cache.get(board);
        if (c != null && now - c.at() < plugin.settings().boardCacheSeconds * 1000L) return c.list();

        List<Entry> out = new ArrayList<>();
        if (board == Board.BOUNTY) {
            for (Map.Entry<UUID, Long> e : plugin.bounties().ranked()) {
                out.add(new Entry(e.getKey(), plugin.economy().nameOf(e.getKey()), e.getValue()));
            }
        } else {
            for (PlayerData d : plugin.economy().all()) {
                long v = switch (board) {
                    case RICHEST -> d.balance;
                    case KILLS -> d.kills;
                    case CLAIMED -> d.bountiesClaimed;
                    case PLACED -> d.bountiesPlaced;
                    case BOUNTY -> 0;
                };
                if (v > 0) out.add(new Entry(d.uuid, d.name, v));
            }
        }
        out.sort((a, b) -> {
            int cmp = Long.compare(b.value(), a.value());
            return cmp != 0 ? cmp : a.name().compareToIgnoreCase(b.name());
        });
        cache.put(board, new Cached(now, out));
        return out;
    }
}
