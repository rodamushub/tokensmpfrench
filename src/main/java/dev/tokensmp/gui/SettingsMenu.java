package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.sound.SoundType;
import dev.tokensmp.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Personal preferences, stored per player in the database. */
public final class SettingsMenu extends Menu {

    public SettingsMenu(TokenSMPPlugin plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected String title() {
        return "Paramètres";
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        PlayerData d = plugin.economy().get(player.getUniqueId());

        toggle(10, "Sons personnalisés", "Sons des pièces, des primes et des menus.", d.sounds, () -> d.sounds, v -> d.sounds = v);
        toggle(12, "Notifications de tokens", "Messages dans le chat quand tu gagnes ou perds des tokens.", d.notifications,
                () -> d.notifications, v -> d.notifications = v);
        toggle(14, "Tableau des scores", "Barre latérale avec tokens, kills et prime.", d.scoreboard,
                () -> d.scoreboard, v -> d.scoreboard = v);
        toggle(16, "Annonces de primes", "Messages pour tout le serveur sur les grosses primes.", d.broadcasts,
                () -> d.broadcasts, v -> d.broadcasts = v);

        set(22, ItemBuilder.of(Material.ARROW).name("<yellow>Retour").build(), e -> new MainMenu(plugin, player).open());
    }

    private void toggle(int slot, String name, String description, boolean on, Supplier<Boolean> get, Consumer<Boolean> setter) {
        PlayerData d = plugin.economy().get(player.getUniqueId());
        set(slot, ItemBuilder.of(on ? Material.LIME_DYE : Material.GRAY_DYE)
                .name((on ? "<green>" : "<red>") + name + ": " + (on ? "ACTIVÉ" : "DÉSACTIVÉ"))
                .lore("<gray>" + description, "", "<yellow>Clique pour " + (on ? "désactiver" : "activer")).glow(on).build(),
                e -> {
                    setter.accept(!get.get());
                    plugin.economy().tx().save(d).commit();
                    plugin.scoreboards().refresh(player);
                    render();
                    // Confirmation tone; skipped automatically when the player just switched sounds off.
                    plugin.sounds().play(player, SoundType.SUCCESS);
                });
    }
}
