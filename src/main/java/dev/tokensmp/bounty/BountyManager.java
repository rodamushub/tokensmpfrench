package dev.tokensmp.bounty;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.data.Storage;
import dev.tokensmp.economy.Economy;
import dev.tokensmp.gui.ConfirmMenu;
import dev.tokensmp.gui.Menu;
import dev.tokensmp.sound.SoundType;
import dev.tokensmp.util.Fmt;
import dev.tokensmp.util.ItemBuilder;
import dev.tokensmp.util.Settings;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Active bounties: target -> (contributor -> amount). Main-thread only. */
public final class BountyManager {

    public record ClaimResult(long paid) {}

    private final TokenSMPPlugin plugin;
    private final Economy economy;
    private final Storage storage;
    private final Map<UUID, LinkedHashMap<UUID, Long>> bounties = new HashMap<>();

    public BountyManager(TokenSMPPlugin plugin, Economy economy, Storage storage) {
        this.plugin = plugin;
        this.economy = economy;
        this.storage = storage;
    }

    public void load() throws SQLException {
        for (Storage.BountyRow r : storage.loadBounties()) {
            bounties.computeIfAbsent(r.target(), k -> new LinkedHashMap<>()).merge(r.placer(), r.amount(), Long::sum);
        }
    }

    // ── queries ──────────────────────────────────────────────────────────

    public long total(UUID target) {
        Map<UUID, Long> m = bounties.get(target);
        if (m == null) return 0;
        long sum = 0;
        for (long v : m.values()) sum += v;
        return sum;
    }

    public int contributors(UUID target) {
        Map<UUID, Long> m = bounties.get(target);
        return m == null ? 0 : m.size();
    }

    public Map<UUID, Long> contributions(UUID target) {
        Map<UUID, Long> m = bounties.get(target);
        return m == null ? Map.of() : Collections.unmodifiableMap(m);
    }

    /** All active bounties, highest first. */
    public List<Map.Entry<UUID, Long>> ranked() {
        List<Map.Entry<UUID, Long>> out = new ArrayList<>();
        for (UUID t : bounties.keySet()) {
            long total = total(t);
            if (total > 0) out.add(Map.entry(t, total));
        }
        out.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return out;
    }

    /** Bounties this player has contributed to: target -> amount. */
    public Map<UUID, Long> contributedBy(UUID placer) {
        Map<UUID, Long> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, LinkedHashMap<UUID, Long>> e : bounties.entrySet()) {
            Long v = e.getValue().get(placer);
            if (v != null) out.put(e.getKey(), v);
        }
        return out;
    }

    // ── placing ──────────────────────────────────────────────────────────

    public boolean checkPlace(Player p, PlayerData target, long amount) {
        Settings s = plugin.settings();
        PlayerData me = economy.get(p.getUniqueId());
        if (me == null) return false;
        if (target.uuid.equals(p.getUniqueId())) return plugin.fail(p, "self-target");
        if (amount <= 0) return plugin.fail(p, "invalid-amount");
        if (amount < s.bountyMin) return plugin.fail(p, "bounty-too-low", "min", Fmt.num(s.bountyMin));
        long current = total(target.uuid);
        if (current + amount > s.bountyMaxTotal) {
            return plugin.fail(p, "bounty-cap", "max", Fmt.num(s.bountyMaxTotal), "current", Fmt.num(current));
        }
        if (me.balance < amount) {
            return plugin.fail(p, "not-enough", "amount", Fmt.num(amount), "balance", Fmt.num(me.balance));
        }
        return true;
    }

    /** Validates, then shows the confirmation screen (or places directly if confirmations are off). */
    public void startPlace(Player p, PlayerData target, long amount, Supplier<Menu> back) {
        if (!checkPlace(p, target, amount)) return;
        if (!plugin.settings().confirmBounty) {
            place(p, target, amount);
            return;
        }
        String sym = plugin.settings().symbol;
        var summary = ItemBuilder.of(Material.TARGET)
                .name("<gold>Placer une prime")
                .lore("<gray>Cible : <red>" + target.name,
                        "<gray>Montant : <gold>" + sym + " " + Fmt.num(amount),
                        "<gray>Nouveau total sur la cible : <gold>" + sym + " " + Fmt.num(total(target.uuid) + amount),
                        "",
                        "<red>Les tokens sont déduits immédiatement",
                        "<red>et ne sont pas remboursables.")
                .build();
        new ConfirmMenu(plugin, p, "Confirmer la prime", summary, () -> place(p, target, amount),
                back == null ? null : () -> back.get().open()).open();
    }

    public void place(Player p, PlayerData target, long amount) {
        if (!checkPlace(p, target, amount)) return;
        PlayerData me = economy.get(p.getUniqueId());
        Settings s = plugin.settings();

        me.balance -= amount;
        me.bountiesPlaced++;
        LinkedHashMap<UUID, Long> map = bounties.computeIfAbsent(target.uuid, k -> new LinkedHashMap<>());
        map.merge(me.uuid, amount, Long::sum);
        long total = total(target.uuid);

        economy.tx().save(me).bounty(target.uuid, map)
                .ledger("BOUNTY_PLACE", me.uuid, target.uuid, amount, "total " + total).commit();

        plugin.msg().send(p, "bounty-placed", "amount", Fmt.num(amount), "target", target.name, "total", Fmt.num(total));
        plugin.sounds().play(p, SoundType.BOUNTY_PLACED);
        plugin.scoreboards().refresh(p);

        Player tp = plugin.getServer().getPlayer(target.uuid);
        if (tp != null) {
            plugin.msg().send(tp, "bounty-on-you", "placer", me.name, "amount", Fmt.num(amount), "total", Fmt.num(total));
            plugin.sounds().play(tp, SoundType.BOUNTY_PLACED);
            plugin.scoreboards().refresh(tp);
        }
        refreshTab(target.uuid);

        if (amount >= s.broadcastPlacedMin) {
            plugin.broadcast(plugin.msg().get("bounty-broadcast-placed", "placer", me.name, "amount", Fmt.num(amount),
                    "target", target.name, "total", Fmt.num(total)));
        }
    }

    // ── claiming ─────────────────────────────────────────────────────────

    /**
     * Removes the bounty on {@code victim}. Contributions from the killer or from accounts on the
     * killer's network are refunded to their owners instead of being paid out, so a bounty can never
     * be used to move tokens to yourself. Call {@code tx.save(killer)} after this returns.
     */
    public ClaimResult claim(PlayerData killer, UUID victim, Economy.Tx tx) {
        LinkedHashMap<UUID, Long> map = bounties.remove(victim);
        if (map == null || map.isEmpty()) return new ClaimResult(0);

        long paid = 0;
        for (Map.Entry<UUID, Long> e : map.entrySet()) {
            PlayerData placer = economy.get(e.getKey());
            boolean refund = e.getKey().equals(killer.uuid) || (placer != null && economy.sameNetwork(placer, killer));
            if (!refund) {
                paid += e.getValue();
                continue;
            }
            if (placer != null) {
                placer.balance += e.getValue();
                if (!placer.uuid.equals(killer.uuid)) tx.save(placer);
                tx.ledger("BOUNTY_REFUND", victim, placer.uuid, e.getValue(), "claimed by " + killer.name);
                Player online = plugin.getServer().getPlayer(placer.uuid);
                if (online != null && placer.notifications) {
                    plugin.msg().send(online, "bounty-refunded", "amount", Fmt.num(e.getValue()), "target", economy.nameOf(victim));
                }
            }
        }
        tx.bounty(victim, Map.of());
        refreshTab(victim);
        return new ClaimResult(paid);
    }

    /** Admin: remove a bounty and refund every contributor. */
    public void clear(UUID target, String by) {
        LinkedHashMap<UUID, Long> map = bounties.remove(target);
        if (map == null) return;
        Economy.Tx tx = economy.tx();
        for (Map.Entry<UUID, Long> e : map.entrySet()) {
            PlayerData placer = economy.get(e.getKey());
            if (placer == null) continue;
            placer.balance += e.getValue();
            tx.save(placer).ledger("BOUNTY_REFUND", target, placer.uuid, e.getValue(), "cleared by " + by);
            Player online = plugin.getServer().getPlayer(placer.uuid);
            if (online != null) plugin.scoreboards().refresh(online);
        }
        tx.bounty(target, Map.of()).commit();
        refreshTab(target);
    }

    // ── public display ───────────────────────────────────────────────────

    public void refreshTab(UUID target) {
        Player p = plugin.getServer().getPlayer(target);
        if (p == null) return;
        long total = total(target);
        if (total > 0 && plugin.settings().bountyTabList) {
            p.playerListName(Component.text(p.getName()).append(
                    plugin.msg().parse(" <gold>[<symbol> " + Fmt.num(total) + "]")));
        } else {
            p.playerListName(null);
        }
    }
}
