package dev.tokensmp.sound;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Plays configurable sounds, honouring each player's setting and a per-type rate limit. */
public final class SoundService {

    private record Def(Key key, float volume, float pitch) {}

    private final TokenSMPPlugin plugin;
    private final Map<SoundType, Def> defs = new EnumMap<>(SoundType.class);
    private final Map<UUID, Map<SoundType, Long>> last = new HashMap<>();

    public SoundService(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        defs.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sounds.events");
        if (sec == null) return;
        for (SoundType type : SoundType.values()) {
            ConfigurationSection s = sec.getConfigurationSection(type.configKey);
            if (s == null) continue;
            try {
                Key key = Key.key(s.getString("sound", ""));
                defs.put(type, new Def(key, (float) s.getDouble("volume", 1.0), (float) s.getDouble("pitch", 1.0)));
            } catch (InvalidKeyException e) {
                plugin.getLogger().warning("Invalid sound key for " + type.configKey + " in config.yml");
            }
        }
    }

    public void play(Player player, SoundType type) {
        if (!plugin.settings().soundsEnabled) return;
        Def def = defs.get(type);
        if (def == null) return;
        PlayerData data = plugin.economy().get(player.getUniqueId());
        if (data != null && !data.sounds) return;

        long now = System.currentTimeMillis();
        Map<SoundType, Long> perType = last.computeIfAbsent(player.getUniqueId(), k -> new EnumMap<>(SoundType.class));
        Long prev = perType.get(type);
        if (prev != null && now - prev < plugin.settings().soundMinIntervalMs) return;
        perType.put(type, now);

        player.playSound(Sound.sound(def.key(), Sound.Source.MASTER, def.volume(), def.pitch()), Sound.Emitter.self());
    }

    public void forget(UUID id) {
        last.remove(id);
    }
}
