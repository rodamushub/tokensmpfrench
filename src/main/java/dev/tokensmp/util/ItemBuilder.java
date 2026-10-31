package dev.tokensmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/** Small helper for GUI icons. Text is MiniMessage; italics are switched off so names look clean. */
public final class ItemBuilder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ItemStack item;
    private final ItemMeta meta;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    private static Component text(String mini) {
        return MM.deserialize(mini).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public ItemBuilder name(String mini) {
        meta.displayName(text(mini));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(List.of(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        List<Component> out = new ArrayList<>();
        for (String l : lines) out.add(text(l));
        meta.lore(out);
        return this;
    }

    public ItemBuilder glow(boolean on) {
        if (on) meta.setEnchantmentGlintOverride(true);
        return this;
    }

    public ItemBuilder head(OfflinePlayer player) {
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
