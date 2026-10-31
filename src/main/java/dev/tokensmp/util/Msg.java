package dev.tokensmp.util;

import dev.tokensmp.TokenSMPPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Loads messages.yml and renders MiniMessage text with placeholders. */
public final class Msg {

    public record Page(String title, List<String> lines) {}

    private final TokenSMPPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration yml = new YamlConfiguration();

    public Msg(TokenSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        yml = YamlConfiguration.loadConfiguration(f);
        // Keys missing from an older messages.yml fall back to the bundled defaults.
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                yml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // defaults are optional
        }
    }

    public MiniMessage mini() {
        return mm;
    }

    private TagResolver resolver(Object... kv) {
        Settings s = plugin.settings();
        List<TagResolver> list = new ArrayList<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            list.add(Placeholder.unparsed(String.valueOf(kv[i]), String.valueOf(kv[i + 1])));
        }
        list.add(Placeholder.parsed("prefix", yml.getString("prefix", "")));
        list.add(Placeholder.unparsed("symbol", s.symbol));
        list.add(Placeholder.unparsed("currency", s.currency));
        list.add(Placeholder.unparsed("reward", Fmt.num(s.killReward)));
        list.add(Placeholder.unparsed("steal", trim(s.stealPercent)));
        list.add(Placeholder.unparsed("bounty-min", Fmt.num(s.bountyMin)));
        return TagResolver.resolver(list);
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    public Component get(String key, Object... kv) {
        String raw = yml.getString(key);
        if (raw == null) return Component.text("Missing message: " + key);
        return mm.deserialize(raw, resolver(kv));
    }

    public Component parse(String mini, Object... kv) {
        return mm.deserialize(mini, resolver(kv));
    }

    public List<Component> list(String key, Object... kv) {
        List<Component> out = new ArrayList<>();
        for (String line : yml.getStringList(key)) out.add(mm.deserialize(line, resolver(kv)));
        return out;
    }

    public void send(CommandSender to, String key, Object... kv) {
        to.sendMessage(get(key, kv));
    }

    public void sendList(CommandSender to, String key, Object... kv) {
        for (Component c : list(key, kv)) to.sendMessage(c);
    }

    public List<Page> tutorialPages() {
        List<Page> pages = new ArrayList<>();
        ConfigurationSection sec = yml.getConfigurationSection("tutorial.pages");
        if (sec == null) return pages;
        for (String k : sec.getKeys(false)) {
            pages.add(new Page(sec.getString(k + ".title", k), sec.getStringList(k + ".lines")));
        }
        return pages;
    }
}
