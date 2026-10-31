package dev.tokensmp.pvp;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.bounty.BountyManager;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.economy.Economy;
import dev.tokensmp.sound.SoundType;
import dev.tokensmp.util.Fmt;
import dev.tokensmp.util.Settings;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Settles a PvP kill: steals tokens, pays the reward and any bounty, and applies the anti-farm rules. */
public final class KillService {

    private static final long HOUR_MS = 3_600_000L;

    private final TokenSMPPlugin plugin;
    /** "killer:victim" -> timestamps of rewarded kills in the last hour. */
    private final Map<String, ArrayDeque<Long>> history = new HashMap<>();

    public KillService(TokenSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanup, 20L * 600, 20L * 600);
    }

    /** Returns true when rewards were paid. */
    public boolean processKill(UUID killerId, UUID victimId) {
        Settings s = plugin.settings();
        Economy eco = plugin.economy();
        BountyManager bounties = plugin.bounties();
        if (killerId.equals(victimId)) return false;

        PlayerData killer = eco.get(killerId);
        PlayerData victim = eco.get(victimId);
        if (killer == null || victim == null) return false;

        Player kp = plugin.getServer().getPlayer(killerId);
        Player vp = plugin.getServer().getPlayer(victimId);
        if (vp != null && s.disabledWorlds.contains(vp.getWorld().getName())) return false;

        // Anti-exploit: alt accounts on one network.
        if (s.blockSameNetworkKills && eco.sameNetwork(killer, victim)) {
            if (kp != null) plugin.msg().send(kp, "kill-blocked-network", "victim", victim.name);
            return false;
        }
        // Anti-exploit: farming the same player.
        if (!allowedByHistory(killerId, victimId)) {
            if (kp != null) plugin.msg().send(kp, "kill-blocked-repeat", "victim", victim.name);
            return false;
        }

        long stolen = (long) Math.floor(victim.balance * (s.stealPercent / 100.0));
        if (s.maxSteal > 0) stolen = Math.min(stolen, s.maxSteal);
        stolen = Math.min(stolen, victim.balance);
        long reward = s.killReward;

        Economy.Tx tx = eco.tx();
        victim.balance -= stolen;
        victim.deaths++;
        long bounty = bounties.claim(killer, victimId, tx).paid();
        killer.balance += stolen + reward + bounty;
        killer.kills++;
        if (bounty > 0) killer.bountiesClaimed++;

        tx.save(killer, victim)
                .ledger("KILL_STEAL", victimId, killerId, stolen, null)
                .ledger("KILL_REWARD", null, killerId, reward, null)
                .ledger("BOUNTY_CLAIM", victimId, killerId, bounty, null)
                .commit();

        long gain = stolen + reward;
        if (kp != null) {
            if (killer.notifications) {
                plugin.msg().send(kp, "kill-reward", "victim", victim.name, "total", Fmt.num(gain),
                        "reward", Fmt.num(reward), "stolen", Fmt.num(stolen));
                if (bounty > 0) {
                    plugin.msg().send(kp, "bounty-claimed", "amount", Fmt.num(bounty), "target", victim.name);
                }
            }
            plugin.sounds().play(kp, bounty > 0 ? SoundType.BOUNTY_CLAIMED : SoundType.TOKEN_RECEIVED);
            plugin.scoreboards().refresh(kp);
        }
        if (vp != null) {
            if (victim.notifications) {
                plugin.msg().send(vp, "death-loss", "killer", killer.name, "lost", Fmt.num(stolen),
                        "remaining", Fmt.num(victim.balance));
            }
            plugin.sounds().play(vp, SoundType.TOKEN_LOST);
            plugin.scoreboards().refresh(vp);
        }
        if (bounty > 0 && bounty >= s.broadcastClaimedMin) {
            plugin.broadcast(plugin.msg().get("bounty-broadcast-claimed", "killer", killer.name,
                    "amount", Fmt.num(bounty), "target", victim.name));
        }
        return true;
    }

    private boolean allowedByHistory(UUID killer, UUID victim) {
        Settings s = plugin.settings();
        long now = System.currentTimeMillis();
        String key = killer + ":" + victim;
        ArrayDeque<Long> q = history.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > HOUR_MS) q.pollFirst();
        if (!q.isEmpty() && now - q.peekLast() < s.repeatCooldownMs) return false;
        if (q.size() >= s.repeatMaxPerHour) return false;
        q.addLast(now);
        return true;
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ArrayDeque<Long>>> it = history.entrySet().iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> q = it.next().getValue();
            while (!q.isEmpty() && now - q.peekFirst() > HOUR_MS) q.pollFirst();
            if (q.isEmpty()) it.remove();
        }
    }
}
