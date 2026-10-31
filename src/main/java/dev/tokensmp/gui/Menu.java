package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.sound.SoundType;
import dev.tokensmp.util.ItemBuilder;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class for every menu. All clicks are cancelled by {@link MenuListener}; only slots registered
 * with {@link #set(int, ItemStack, Consumer)} do anything. Titles follow one pattern for consistency.
 */
public abstract class Menu implements InventoryHolder {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    protected final TokenSMPPlugin plugin;
    protected final Player player;
    private Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected Menu(TokenSMPPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Short name after the shared "Token SMP »" prefix. */
    protected abstract String title();

    protected abstract int rows();

    protected abstract void build();

    public final void open() {
        inventory = Bukkit.createInventory(this, rows() * 9, MM.deserialize("<dark_gray>Token SMP <gray>» <white>" + title()));
        render();
        player.openInventory(inventory);
        plugin.sounds().play(player, SoundType.MENU_OPEN);
    }

    /** Rebuilds the contents in place (used by toggles and paging). */
    protected final void render() {
        actions.clear();
        inventory.clear();
        ItemStack filler = ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
        build();
    }

    protected final void set(int slot, ItemStack item) {
        inventory.setItem(slot, item);
    }

    protected final void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
    }

    protected final String symbol() {
        return plugin.settings().symbol;
    }

    final void handle(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> action = actions.get(e.getRawSlot());
        if (action != null) action.accept(e);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
