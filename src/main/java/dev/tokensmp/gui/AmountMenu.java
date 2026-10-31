package dev.tokensmp.gui;

import dev.tokensmp.TokenSMPPlugin;
import dev.tokensmp.data.PlayerData;
import dev.tokensmp.util.Fmt;
import dev.tokensmp.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Pick how many tokens to put on a target: preset buttons or a custom amount typed in chat. */
public final class AmountMenu extends Menu {

    private static final int[] PRESET_SLOTS = {10, 11, 12, 13, 14};

    private final UUID target;
    private final Supplier<Menu> back;

    public AmountMenu(TokenSMPPlugin plugin, Player player, UUID target, Supplier<Menu> back) {
        super(plugin, player);
        this.target = target;
        this.back = back;
    }

    @Override
    protected String title() {
        return "Montant de la prime";
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void build() {
        PlayerData t = plugin.economy().get(target);
        if (t == null) return;
        PlayerData me = plugin.economy().get(player.getUniqueId());
        long current = plugin.bounties().total(target);

        set(4, ItemBuilder.of(Material.PLAYER_HEAD).head(Bukkit.getOfflinePlayer(target)).name("<red>" + t.name)
                .lore("<gray>Prime actuelle : <gold>" + symbol() + " " + Fmt.num(current),
                        "<gray>Ton solde : <white>" + symbol() + " " + Fmt.num(me.balance),
                        "<gray>Minimum : <white>" + symbol() + " " + Fmt.num(plugin.settings().bountyMin)).build());

        List<Long> presets = plugin.settings().bountyPresets;
        for (int i = 0; i < presets.size() && i < PRESET_SLOTS.length; i++) {
            long amount = presets.get(i);
            boolean affordable = me.balance >= amount;
            set(PRESET_SLOTS[i], ItemBuilder.of(affordable ? Material.GOLD_NUGGET : Material.IRON_NUGGET)
                    .name((affordable ? "<gold>" : "<dark_gray>") + symbol() + " " + Fmt.num(amount))
                    .lore(affordable ? "<yellow>Clique pour continuer" : "<red>Pas assez de tokens").build(),
                    e -> plugin.bounties().startPlace(player, t, amount, back));
        }

        set(16, ItemBuilder.of(Material.NAME_TAG).name("<aqua>Montant personnalisé")
                .lore("<gray>Écris un montant dans le chat,", "<gray>par exemple <white>750</white> ou <white>2k</white>.", "", "<yellow>Clique pour écrire").build(),
                e -> plugin.prompts().ask(player, "prompt-amount", text -> {
                    long amount = Fmt.parseAmount(text);
                    if (amount <= 0) {
                        plugin.fail(player, "invalid-amount");
                        return;
                    }
                    plugin.bounties().startPlace(player, t, amount, back);
                }));

        set(22, ItemBuilder.of(Material.ARROW).name("<yellow>Retour").build(), e -> back.get().open());
    }
}
