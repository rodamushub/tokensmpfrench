package dev.tokensmp.data;

import dev.tokensmp.TokenSMPPlugin;

import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite persistence. The server keeps all state in memory and hands every change to a single
 * writer thread as one transaction, so the main thread never waits on disk.
 */
public final class Storage {

    @FunctionalInterface
    public interface SqlTask {
        void run(Connection c) throws SQLException;
    }

    public record BountyRow(UUID target, UUID placer, long amount) {}

    private final TokenSMPPlugin plugin;
    private final String url;
    private Connection conn;
    private ExecutorService exec;
    private String salt;

    public Storage(TokenSMPPlugin plugin) {
        this.plugin = plugin;
        this.url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "tokens.db").getAbsolutePath();
    }

    public void open() throws Exception {
        plugin.getDataFolder().mkdirs();
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("CREATE TABLE IF NOT EXISTS players(" +
                    "uuid TEXT PRIMARY KEY, name TEXT NOT NULL, balance INTEGER NOT NULL DEFAULT 0," +
                    "kills INTEGER NOT NULL DEFAULT 0, deaths INTEGER NOT NULL DEFAULT 0," +
                    "bounties_claimed INTEGER NOT NULL DEFAULT 0, bounties_placed INTEGER NOT NULL DEFAULT 0," +
                    "ip_hash TEXT, welcomed INTEGER NOT NULL DEFAULT 0, sounds INTEGER NOT NULL DEFAULT 1," +
                    "notifications INTEGER NOT NULL DEFAULT 1, scoreboard INTEGER NOT NULL DEFAULT 1," +
                    "broadcasts INTEGER NOT NULL DEFAULT 1, first_seen INTEGER, last_seen INTEGER)");
            st.execute("CREATE TABLE IF NOT EXISTS bounties(" +
                    "target TEXT NOT NULL, placer TEXT NOT NULL, amount INTEGER NOT NULL, PRIMARY KEY(target, placer))");
            st.execute("CREATE TABLE IF NOT EXISTS ledger(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, type TEXT NOT NULL," +
                    "from_uuid TEXT, to_uuid TEXT, amount INTEGER NOT NULL, note TEXT)");
            st.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)");
        }
        salt = meta("ip_salt");
        if (salt == null) {
            byte[] bytes = new byte[16];
            new SecureRandom().nextBytes(bytes);
            salt = HexFormat.of().formatHex(bytes);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO meta(k, v) VALUES('ip_salt', ?)")) {
                ps.setString(1, salt);
                ps.executeUpdate();
            }
        }
        exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TokenSMP-DB");
            t.setDaemon(false);
            return t;
        });
    }

    public String ipSalt() {
        return salt;
    }

    private String meta(String key) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT v FROM meta WHERE k = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Startup only, before the writer thread is used. */
    public List<PlayerData> loadPlayers() throws SQLException {
        List<PlayerData> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM players")) {
            while (rs.next()) {
                PlayerData d = new PlayerData(UUID.fromString(rs.getString("uuid")), rs.getString("name"));
                d.balance = rs.getLong("balance");
                d.kills = rs.getLong("kills");
                d.deaths = rs.getLong("deaths");
                d.bountiesClaimed = rs.getLong("bounties_claimed");
                d.bountiesPlaced = rs.getLong("bounties_placed");
                d.ipHash = rs.getString("ip_hash");
                d.welcomed = rs.getInt("welcomed") == 1;
                d.sounds = rs.getInt("sounds") == 1;
                d.notifications = rs.getInt("notifications") == 1;
                d.scoreboard = rs.getInt("scoreboard") == 1;
                d.broadcasts = rs.getInt("broadcasts") == 1;
                d.firstSeen = rs.getLong("first_seen");
                d.lastSeen = rs.getLong("last_seen");
                out.add(d);
            }
        }
        return out;
    }

    /** Startup only. */
    public List<BountyRow> loadBounties() throws SQLException {
        List<BountyRow> out = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT target, placer, amount FROM bounties")) {
            while (rs.next()) {
                out.add(new BountyRow(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getLong(3)));
            }
        }
        return out;
    }

    /** Most recent ledger lines, read over a separate connection so the writer is never blocked. */
    public List<String> recentLedger(int limit) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement ps = c.prepareStatement("SELECT id, ts, type, from_uuid, to_uuid, amount, note FROM ledger ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add("#" + rs.getLong(1) + " " + new java.util.Date(rs.getLong(2)) + " " + rs.getString(3)
                            + " " + rs.getLong(6) + " from=" + rs.getString(4) + " to=" + rs.getString(5)
                            + (rs.getString(7) == null ? "" : " (" + rs.getString(7) + ")"));
                }
            }
        }
        return out;
    }

    /** Queues one atomic transaction on the writer thread. */
    public void submit(SqlTask task) {
        try {
            exec.execute(() -> {
                try {
                    conn.setAutoCommit(false);
                    task.run(conn);
                    conn.commit();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Database write failed and was rolled back", e);
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                        // nothing more we can do
                    }
                } finally {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // connection is closing
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            plugin.getLogger().warning("Database is shutting down; a write was dropped.");
        }
    }

    public void close() {
        if (exec != null) {
            exec.shutdown();
            try {
                if (!exec.awaitTermination(20, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("Database writer did not finish in time.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        try {
            if (conn != null) conn.close();
        } catch (SQLException ignored) {
            // already closed
        }
    }

    // ── statement helpers, always called on the writer thread ────────────

    public static void upsert(Connection c, PlayerData d) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO players(uuid,name,balance,kills,deaths,bounties_claimed,bounties_placed,ip_hash,welcomed," +
                        "sounds,notifications,scoreboard,broadcasts,first_seen,last_seen) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, balance=excluded.balance, kills=excluded.kills," +
                        "deaths=excluded.deaths, bounties_claimed=excluded.bounties_claimed, bounties_placed=excluded.bounties_placed," +
                        "ip_hash=excluded.ip_hash, welcomed=excluded.welcomed, sounds=excluded.sounds," +
                        "notifications=excluded.notifications, scoreboard=excluded.scoreboard, broadcasts=excluded.broadcasts," +
                        "first_seen=excluded.first_seen, last_seen=excluded.last_seen")) {
            ps.setString(1, d.uuid.toString());
            ps.setString(2, d.name);
            ps.setLong(3, d.balance);
            ps.setLong(4, d.kills);
            ps.setLong(5, d.deaths);
            ps.setLong(6, d.bountiesClaimed);
            ps.setLong(7, d.bountiesPlaced);
            ps.setString(8, d.ipHash);
            ps.setInt(9, d.welcomed ? 1 : 0);
            ps.setInt(10, d.sounds ? 1 : 0);
            ps.setInt(11, d.notifications ? 1 : 0);
            ps.setInt(12, d.scoreboard ? 1 : 0);
            ps.setInt(13, d.broadcasts ? 1 : 0);
            ps.setLong(14, d.firstSeen);
            ps.setLong(15, d.lastSeen);
            ps.executeUpdate();
        }
    }

    public static void replaceBounty(Connection c, UUID target, Map<UUID, Long> rows) throws SQLException {
        try (PreparedStatement del = c.prepareStatement("DELETE FROM bounties WHERE target = ?")) {
            del.setString(1, target.toString());
            del.executeUpdate();
        }
        if (rows.isEmpty()) return;
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO bounties(target, placer, amount) VALUES(?,?,?)")) {
            for (Map.Entry<UUID, Long> e : rows.entrySet()) {
                ins.setString(1, target.toString());
                ins.setString(2, e.getKey().toString());
                ins.setLong(3, e.getValue());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    public static void insertLedger(Connection c, long ts, String type, UUID from, UUID to, long amount, String note) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ledger(ts, type, from_uuid, to_uuid, amount, note) VALUES(?,?,?,?,?,?)")) {
            ps.setLong(1, ts);
            ps.setString(2, type);
            ps.setString(3, from == null ? null : from.toString());
            ps.setString(4, to == null ? null : to.toString());
            ps.setLong(5, amount);
            ps.setString(6, note);
            ps.executeUpdate();
        }
    }
}
