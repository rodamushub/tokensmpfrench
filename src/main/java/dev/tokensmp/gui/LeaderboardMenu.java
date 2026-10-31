package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.ui.LeaderboardService.Board;
import dev.tokensmp.ui.LeaderboardService.Entry;
import dev.tokensmp.util.Fmt;
import dev.tokensmp.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

public final class LeaderboardMenu extends Menu {

    private static final int[] TAB_SLOTS = {2, 3, 4, 5, 6};
    private static final int[] ENTRY_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private Board board;

    public LeaderboardMenu(TokenSMPPlugin plugin, Player player, Board board) {
        super(plugin, player);
        this.board = board;
    }

    @Override
    protected String title() {
        return "Classements";
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void build() {
        Board[] boards = Board.values();
        for (int i = 0; i < boards.length; i++) {
            Board b = boards[i];
            boolean selected = b == board;
            set(TAB_SLOTS[i], ItemBuilder.of(b.icon).name((selected ? "<yellow><bold>" : "<white>") + b.title)
                    .lore(selected ? "<gray>Affiché actuellement" : "<yellow>Clique pour voir").glow(selected).build(),
                    e -> {
                        board = b;
                        render();
                    });
        }

        int limit = plugin.settings().boardGuiEntries;
        List<Entry> top = plugin.boards().top(board, limit);
        for (int i = 0; i < limit && i < ENTRY_SLOTS.length; i++) {
            if (i >= top.size()) break;
            Entry en = top.get(i);
            int rank = i + 1;
            String color = rank == 1 ? "<gold>" : rank == 2 ? "<gray>" : rank == 3 ? "<#cd7f32>" : "<white>";
            String value = board.tokens ? symbol() + " " + Fmt.num(en.value()) : Fmt.num(en.value());
            set(ENTRY_SLOTS[i], ItemBuilder.of(Material.PLAYER_HEAD).head(Bukkit.getOfflinePlayer(en.uuid()))
                    .name(color + "#" + rank + " " + en.name())
                    .lore("<gray>" + board.title + ": <white>" + value).build());
        }
        if (top.isEmpty()) {
            set(22, ItemBuilder.of(Material.BARRIER).name("<gray>Rien à afficher pour l'instant").build());
        }

        int mine = plugin.boards().rank(board, player.getUniqueId());
        set(36, ItemBuilder.of(Material.PAPER).name("<white>Ta position")
                .lore(mine > 0 ? "<gray>Tu es <gold>#" + mine + "</gold> dans ce classement." : "<gray>Tu n'es pas encore classé ici.").build());
        set(40, ItemBuilder.of(Material.ARROW).name("<yellow>Retour").build(), e -> new MainMenu(plugin, player).open());
    }
}
