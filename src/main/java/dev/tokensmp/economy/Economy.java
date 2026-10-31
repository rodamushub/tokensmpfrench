package dev.tokensmp.economy;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.data.Storage;
import dev.tokensmp.gui.ConfirmMenu;
import dev.tokensmp.sound.SoundType;
import dev.tokensmp.util.Fmt;
import dev.tokensmp.util.ItemBuilder;
import dev.tokensmp.util.Settings;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Token balances. Everything here runs on the main thread, so a balance check and the matching
 * deduction can never interleave. That is the protection against duplicated transactions.
 */
public final class Economy {

    private final TokenSMPPlugin plugin;
    private final Storage storage;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();

    public Economy(TokenSMPPlugin plugin, Storage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void load() throws SQLException {
        for (PlayerData d : storage.loadPlayers()) index(d);
    }

    private void index(PlayerData d) {
        players.put(d.uuid, d);
        names.put(d.name.toLowerCase(Locale.ROOT), d.uuid);
    }

    public PlayerData get(UUID id) {
        return players.get(id);
    }

    public PlayerData find(String name) {
        UUID id = names.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : players.get(id);
    }

    public Collection<PlayerData> all() {
        return Collections.unmodifiableCollection(players.values());
    }

    public PlayerData getOrCreate(Player p) {
        PlayerData d = players.get(p.getUniqueId());
        if (d == null) {
            d = new PlayerData(p.getUniqueId(), p.getName());
            d.balance = plugin.settings().startingBalance;
            d.firstSeen = System.currentTimeMillis();
            index(d);
        } else if (!d.name.equals(p.getName())) {
            names.remove(d.name.toLowerCase(Locale.ROOT), d.uuid);
            d.name = p.getName();
            index(d);
        }
        return d;
    }

    public String nameOf(UUID id) {
        PlayerData d = players.get(id);
        return d == null ? id.toString().substring(0, 8) : d.name;
    }

    public String hashIp(String ip) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest((storage.ipSalt() + ip).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean sameNetwork(PlayerData a, PlayerData b) {
        return a.ipHash != null && a.ipHash.equals(b.ipHash);
    }

    public int rankByBalance(UUID id) {
        PlayerData me = players.get(id);
        if (me == null) return 0;
        int rank = 1;
        for (PlayerData d : players.values()) {
            if (d.balance > me.balance) rank++;
        }
        return rank;
    }

    // ── persistence ──────────────────────────────────────────────────────

    /** Collects player rows, bounty rows and ledger lines, then writes them as one DB transaction. */
    public Tx tx() {
        return new Tx();
    }

    public final class Tx {
        private final List<Storage.SqlTask> ops = new ArrayList<>();

        public Tx save(PlayerData... data) {
            List<PlayerData> snaps = new ArrayList<>();
            for (PlayerData d : data) snaps.add(d.copy());
            ops.add(c -> {
                for (PlayerData s : snaps) Storage.upsert(c, s);
            });
            return this;
        }

        public Tx bounty(UUID target, Map<UUID, Long> rows) {
            Map<UUID, Long> copy = new HashMap<>(rows);
            ops.add(c -> Storage.replaceBounty(c, target, copy));
            return this;
        }

        public Tx ledger(String type, UUID from, UUID to, long amount, String note) {
            if (amount == 0) return this;
            long ts = System.currentTimeMillis();
            if (plugin.settings().logTransactions) {
                plugin.getLogger().info("[tx] " + type + " " + amount + " from=" + (from == null ? "-" : nameOf(from))
                        + " to=" + (to == null ? "-" : nameOf(to)) + (note == null ? "" : " (" + note + ")"));
            }
            ops.add(c -> Storage.insertLedger(c, ts, type, from, to, amount, note));
            return this;
        }

        public void commit() {
            if (ops.isEmpty()) return;
            storage.submit(c -> {
                for (Storage.SqlTask op : ops) op.run(c);
            });
        }
    }

    // ── player-to-player transfers ───────────────────────────────────────

    /** Validates a transfer and tells the player why it failed. */
    public boolean checkPay(Player p, PlayerData to, long amount) {
        Settings s = plugin.settings();
        PlayerData me = players.get(p.getUniqueId());
        if (me == null) return false;
        if (to.uuid.equals(p.getUniqueId())) return plugin.fail(p, "self-target");
        if (amount <= 0) return plugin.fail(p, "invalid-amount");
        if (amount < s.payMin) return plugin.fail(p, "pay-below-min", "min", Fmt.num(s.payMin));
        if (me.balance < amount) {
            return plugin.fail(p, "not-enough", "amount", Fmt.num(amount), "balance", Fmt.num(me.balance));
        }
        if (s.blockSameNetworkTransfers && sameNetwork(me, to)) return plugin.fail(p, "pay-same-network");
        return true;
    }

    /** Entry point used by commands and menus: validates, then asks for confirmation if configured. */
    public void startPay(Player p, PlayerData to, long amount) {
        if (!checkPay(p, to, amount)) return;
        if (!plugin.settings().confirmPay) {
            pay(p, to, amount);
            return;
        }
        String sym = plugin.settings().symbol;
        var summary = ItemBuilder.of(Material.GOLD_INGOT)
                .name("<gold>Envoyer des tokens")
                .lore("<gray>À : <white>" + to.name,
                        "<gray>Montant : <gold>" + sym + " " + Fmt.num(amount),
                        "<gray>Ton solde après : <white>" + Fmt.num(players.get(p.getUniqueId()).balance - amount),
                        "",
                        "<red>Les transferts sont irréversibles.")
                .build();
        new ConfirmMenu(plugin, p, "Confirmer le paiement", summary, () -> pay(p, to, amount), null).open();
    }

    public void pay(Player p, PlayerData to, long amount) {
        if (!checkPay(p, to, amount)) return;
        PlayerData me = players.get(p.getUniqueId());
        me.balance -= amount;
        to.balance += amount;
        tx().save(me, to).ledger("PAY", me.uuid, to.uuid, amount, null).commit();

        plugin.msg().send(p, "pay-sent", "amount", Fmt.num(amount), "player", to.name);
        plugin.sounds().play(p, SoundType.SUCCESS);
        notifyGain(to, amount, "pay-received", "amount", Fmt.num(amount), "player", me.name);
        plugin.scoreboards().refresh(p);
    }

    /** Tells an online player they gained tokens, if they have notifications on. */
    public void notifyGain(PlayerData d, long amount, String key, Object... kv) {
        Player p = plugin.getServer().getPlayer(d.uuid);
        if (p == null) return;
        if (d.notifications) plugin.msg().send(p, key, kv);
        plugin.sounds().play(p, SoundType.TOKEN_RECEIVED);
        plugin.scoreboards().refresh(p);
    }

    // ── admin ────────────────────────────────────────────────────────────

    public void adminSet(PlayerData target, long newBalance, String by) {
        long old = target.balance;
        target.balance = Math.max(0, newBalance);
        tx().save(target).ledger("ADMIN_SET", null, target.uuid, target.balance - old, "by " + by).commit();
        Player p = plugin.getServer().getPlayer(target.uuid);
        if (p != null) plugin.scoreboards().refresh(p);
    }
}
