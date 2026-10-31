package dev.tokensmp.data;

import java.util.UUID;

/** Mutable per-player record. Only touched on the main thread; copies are handed to the DB thread. */
public final class PlayerData {

    public final UUID uuid;
    public String name;
    public long balance;
    public long kills;
    public long deaths;
    public long bountiesClaimed;
    public long bountiesPlaced;
    public String ipHash;
    public boolean welcomed;
    public boolean sounds = true;
    public boolean notifications = true;
    public boolean scoreboard = true;
    public boolean broadcasts = true;
    public long firstSeen;
    public long lastSeen;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public PlayerData copy() {
        PlayerData c = new PlayerData(uuid, name);
        c.balance = balance;
        c.kills = kills;
        c.deaths = deaths;
        c.bountiesClaimed = bountiesClaimed;
        c.bountiesPlaced = bountiesPlaced;
        c.ipHash = ipHash;
        c.welcomed = welcomed;
        c.sounds = sounds;
        c.notifications = notifications;
        c.scoreboard = scoreboard;
        c.broadcasts = broadcasts;
        c.firstSeen = firstSeen;
        c.lastSeen = lastSeen;
        return c;
    }
}
