package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/** Six-row list menu: header buttons in row 1, 36 entries in rows 2-5, paging in row 6. */
public abstract class PagedMenu extends Menu {

    public record Entry(ItemStack item, Consumer<InventoryClickEvent> action) {}

    private static final int PER_PAGE = 36;

    protected int page;

    protected PagedMenu(TokenSMPPlugin plugin, Player player) {
        super(plugin, player);
    }

    protected abstract List<Entry> entries();

    /** Fill slots 0-8 with buttons. */
    protected abstract void header();

    @Override
    protected final int rows() {
        return 6;
    }

    @Override
    protected final void build() {
        header();
        List<Entry> all = entries();
        int maxPage = Math.max(0, (all.size() - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage));

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.size()) break;
            set(9 + i, all.get(idx).item(), all.get(idx).action());
        }

        set(49, ItemBuilder.of(Material.PAPER).name("<white>Page " + (page + 1) + " / " + (maxPage + 1))
                .lore("<gray>" + all.size() + " entrées").build());
        if (page > 0) {
            set(45, ItemBuilder.of(Material.ARROW).name("<yellow>Page précédente").build(), e -> {
                page--;
                render();
            });
        }
        if (page < maxPage) {
            set(53, ItemBuilder.of(Material.ARROW).name("<yellow>Page suivante").build(), e -> {
                page++;
                render();
            });
        }
    }
}
