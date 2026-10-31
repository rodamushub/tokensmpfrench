package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.ui.LeaderboardService.Board;
import dev.tokensmp.util.Fmt;
import dev.tokensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class MainMenu extends Menu {

    public MainMenu(TokenSMPPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String title() {
        return "Menu principal";
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void build() {
        PlayerData d = plugin.economy().get(player.getUniqueId());
        long bounty = plugin.bounties().total(player.getUniqueId());

        set(10, ItemBuilder.of(Material.GOLD_INGOT).name("<gold>Tes tokens")
                .lore("<white>" + symbol() + " " + Fmt.num(d.balance),
                        "<gray>Rang : <white>#" + plugin.economy().rankByBalance(player.getUniqueId()),
                        "",
                        "<gray>Gagne des tokens en remportant des combats PvP.",
                        "<gray>Envoie des tokens avec <white>/tokens pay</white>.").build());

        set(12, ItemBuilder.of(Material.TARGET).name("<red>Primes")
                .lore("<gray>Consulte les primes actives, cherche un",
                        "<gray>joueur ou mets des tokens sur sa tête.",
                        "",
                        bounty > 0 ? "<red>Il y a une prime sur toi : <white>" + symbol() + " " + Fmt.num(bounty) : "<gray>Aucune prime sur toi.",
                        "",
                        "<yellow>Clique pour ouvrir").build(),
                e -> new BountyMenu(plugin, player, BountyMenu.Mode.ALL).open());

        set(14, ItemBuilder.of(Material.GOLD_BLOCK).name("<yellow>Classements")
                .lore("<gray>Plus riches, plus de kills, plus grosses primes,",
                        "<gray>primes réclamées et placées.",
                        "",
                        "<yellow>Clique pour ouvrir").build(),
                e -> new LeaderboardMenu(plugin, player, Board.RICHEST).open());

        set(16, ItemBuilder.of(Material.PLAYER_HEAD).head(player).name("<aqua>Tes statistiques")
                .lore("<gray>Kills, morts, primes et plus encore.", "", "<yellow>Clique pour ouvrir").build(),
                e -> new StatsMenu(plugin, player).open());

        set(20, ItemBuilder.of(Material.KNOWLEDGE_BOOK).name("<green>Tutoriel")
                .lore("<gray>Un petit guide pour comprendre", "<gray>comment fonctionne Token SMP.", "", "<yellow>Clique pour ouvrir").build(),
                e -> new TutorialMenu(plugin, player, 0).open());

        set(22, ItemBuilder.of(Material.COMPARATOR).name("<white>Paramètres")
                .lore("<gray>Sons, notifications et", "<gray>préférences d'interface.", "", "<yellow>Clique pour ouvrir").build(),
                e -> new SettingsMenu(plugin, player).open());

        set(24, ItemBuilder.of(Material.BOOK).name("<white>Liste des commandes")
                .lore("<gray>Affiche toutes les commandes dans le chat.", "", "<yellow>Clique pour afficher").build(),
                e -> {
                    player.closeInventory();
                    plugin.msg().sendList(player, "help.commands");
                });

        set(31, ItemBuilder.of(Material.BARRIER).name("<red>Fermer").build(), e -> player.closeInventory());
    }
}
