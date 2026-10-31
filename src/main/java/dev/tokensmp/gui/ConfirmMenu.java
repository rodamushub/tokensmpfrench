package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.sound.SoundType;
import dev.tokensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Generic "are you sure?" screen. The action runs at most once, and callers re-validate when it fires. */
public final class ConfirmMenu extends Menu {

    private final String heading;
    private final ItemStack summary;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private boolean used;

    /** @param onCancel what to do on cancel; null just closes the menu */
    public ConfirmMenu(TokenSMPPlugin plugin, Player player, String heading, ItemStack summary,
                       Runnable onConfirm, Runnable onCancel) {
        super(plugin, player);
        this.heading = heading;
        this.summary = summary;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected String title() {
        return heading;
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        set(13, summary);
        set(11, ItemBuilder.of(Material.LIME_CONCRETE).name("<green><bold>Confirmer")
                .lore("<gray>Clique pour confirmer.").build(), e -> {
            if (used) return;
            used = true;
            player.closeInventory();
            onConfirm.run();
        });
        set(15, ItemBuilder.of(Material.RED_CONCRETE).name("<red><bold>Annuler")
                .lore("<gray>Rien ne sera dépensé.").build(), e -> {
            if (used) return;
            used = true;
            plugin.sounds().play(player, SoundType.DECLINED);
            if (onCancel != null) onCancel.run();
            else player.closeInventory();
        });
    }
}
