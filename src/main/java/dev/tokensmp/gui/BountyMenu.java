package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.sound.SoundType;
import dev.tokensmp.util.Fmt;
import dev.tokensmp.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BountyMenu extends PagedMenu {

    public enum Mode { ALL, MINE }

    private Mode mode;

    public BountyMenu(TokenSMPPlugin plugin, Player player, Mode mode) {
        super(plugin, player);
        this.mode = mode;
    }

    @Override
    protected String title() {
        return mode == Mode.ALL ? "Primes actives" : "Mes primes";
    }

    @Override
    protected void header() {
        set(1, ItemBuilder.of(Material.EMERALD).name("<green>Placer une prime")
                .lore("<gray>Choisis un joueur en ligne et", "<gray>mets des tokens sur sa tête.", "", "<yellow>Clique pour choisir").build(),
                e -> new PlayerPickerMenu(plugin, player).open());

        set(3, ItemBuilder.of(Material.COMPASS).name("<yellow>Rechercher un joueur")
                .lore("<gray>Écris le pseudo d'un joueur dans le chat,", "<gray>connecté ou non.", "", "<yellow>Clique pour rechercher").build(),
                e -> plugin.prompts().ask(player, "prompt-search", text -> {
                    PlayerData target = plugin.economy().find(text);
                    if (target == null) {
                        plugin.fail(player, "unknown-player", "player", text);
                        return;
                    }
                    new AmountMenu(plugin, player, target.uuid, () -> new BountyMenu(plugin, player, Mode.ALL)).open();
                }));

        boolean all = mode == Mode.ALL;
        set(5, ItemBuilder.of(all ? Material.WRITABLE_BOOK : Material.BOOK)
                .name(all ? "<aqua>Voir mes primes" : "<aqua>Voir toutes les primes")
                .lore("<gray>Alterne entre toutes les primes", "<gray>et celles que tu as financées.", "", "<yellow>Clique pour changer").build(),
                e -> {
                    mode = all ? Mode.MINE : Mode.ALL;
                    page = 0;
                    render();
                });

        set(7, ItemBuilder.of(Material.ARROW).name("<yellow>Retour").build(), e -> new MainMenu(plugin, player).open());
    }

    @Override
    protected List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        if (mode == Mode.ALL) {
            for (Map.Entry<UUID, Long> b : plugin.bounties().ranked()) {
                UUID target = b.getKey();
                out.add(entry(target, b.getValue(),
                        "<gray>Contributeurs : <white>" + plugin.bounties().contributors(target)));
            }
        } else {
            for (Map.Entry<UUID, Long> mine : plugin.bounties().contributedBy(player.getUniqueId()).entrySet()) {
                UUID target = mine.getKey();
                out.add(entry(target, plugin.bounties().total(target),
                        "<gray>Ta contribution : <white>" + symbol() + " " + Fmt.num(mine.getValue())));
            }
        }
        return out;
    }

    private Entry entry(UUID target, long total, String extraLine) {
        String name = plugin.economy().nameOf(target);
        boolean self = target.equals(player.getUniqueId());
        boolean online = Bukkit.getPlayer(target) != null;
        var item = ItemBuilder.of(Material.PLAYER_HEAD).head(Bukkit.getOfflinePlayer(target))
                .name("<red>" + name)
                .lore("<gray>Prime : <gold>" + symbol() + " " + Fmt.num(total),
                        extraLine,
                        "<gray>Statut : " + (online ? "<green>en ligne" : "<dark_gray>hors ligne"),
                        "",
                        self ? "<dark_gray>C'est toi." : "<yellow>Clique pour ajouter à cette prime")
                .build();
        return new Entry(item, e -> {
            if (self) {
                plugin.sounds().play(player, SoundType.DECLINED);
                return;
            }
            new AmountMenu(plugin, player, target, () -> new BountyMenu(plugin, player, mode)).open();
        });
    }
}
