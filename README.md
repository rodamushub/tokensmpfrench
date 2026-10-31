# Token SMP

A Paper plugin for a PvP survival server with tokens, bounties, leaderboards and clean GUIs.
Targets Minecraft Java 26.2+ on Paper. Storage is SQLite. There are no other dependencies.

> **Build status:** source only, not yet compiled. Follow "Installing on FalixNodes" below to get a jar.

## Building

Requirements: JDK matching your server (25 by default, see `gradle.properties`) and Gradle 9.1 or newer (Gradle 8 cannot use Java 25).

```
gradle build
```

The jar is written to `build/libs/TokenSMP-1.0.0.jar`.

Before the first build, open `gradle.properties` and check `paperApiVersion` against the
coordinate documented at https://docs.papermc.io for 26.2. If the Paper API version string or
the required Java version differs, change those two lines. No wrapper is included; run
`gradle wrapper` once if you want one.

## Installing

1. Stop the server.
2. Put `TokenSMP-1.0.0.jar` in `plugins/`.
3. Start the server. `plugins/TokenSMP/` now contains `config.yml`, `messages.yml` and `tokens.db`.
4. Edit the config, then run `/tokensmp reload`. No restart is needed for config or message changes.

## Installing on FalixNodes (or any hosted server)

**Important:** the zip you downloaded contains *source code*. A server can only load a compiled
`.jar`, so extracting the zip into `plugins/` does nothing. You must build the jar first.

### Easiest way: build online (nothing to install)

1. Create a free account on github.com and a new empty repository.
2. Upload the *contents* of this folder (`build.gradle.kts`, `src`, `.github`, ...) to it
   (Add file > Upload files; make sure the `.github` folder is included).
3. Open the **Actions** tab, wait for "Build TokenSMP" to turn green (about 2 minutes), open the run,
   and download the **TokenSMP-jar** artifact at the bottom. Unzip it to get `TokenSMP-1.0.0.jar`.
4. In the FalixNodes file manager, upload that single `.jar` into the `plugins` folder
   (do not extract it, do not upload the zip).
5. Restart the server (full stop and start, not `/reload`).

### Build on your own PC instead

Install JDK 25 and Gradle 9.1 or newer, then run `gradle build` in this folder.

## Debug: how to tell if it worked

At startup the console prints a checklist like this:

```
[TokenSMP] [DEBUG] TokenSMP jar detected and loaded (onLoad reached).
[TokenSMP] ================ TokenSMP startup check ================
[TokenSMP] [ OK ]   Java 25
[TokenSMP] [ OK ]   Server: ... | API: ...
[TokenSMP] [ OK ]   Paper (non-Folia) server
[TokenSMP] [ INFO ] ViaVersion: present | ViaBackwards: present (not required, no conflict)
[TokenSMP] [ OK ]   config.yml and messages.yml loaded
[TokenSMP] [ OK ]   Database tokens.db opened, 0 players loaded
[TokenSMP] [ OK ]   Services created and 5 event listeners registered
[TokenSMP] [ OK ]   Commands registered: 5/5
[TokenSMP] RESULT: SUCCESS - TokenSMP 1.0.0 enabled.
[TokenSMP] [DEBUG] >>> TokenSMP IS WORKING: ... <<<
```

Any `[FAILED]` line tells you which step broke. If you see **nothing** from TokenSMP at all, the
file in `plugins/` is not a valid compiled jar (for example the zip or the source folder), or it
is not directly inside `plugins/`. Also check that `/plugins` in game lists TokenSMP in green.



- **Tokens:** every valid kill pays 100 tokens (configurable) and moves 50% of the victim's balance to the killer.
- **Bounties:** deducted immediately, stackable, paid to whoever kills the target, shown in `/bounty list`, the GUI, the scoreboard, the tab list and chat broadcasts.
- **Leaderboards:** richest, most kills, highest bounty, most claimed, most placed. Available in chat, in a GUI and as holograms.
- **Menus:** main menu, bounties, player picker, amount picker, leaderboards, statistics, settings and a paged tutorial. Every token-spending action has a confirmation screen.
- **Sounds:** vanilla sound events only, per-player on/off, rate limited. Declined actions play the villager "no".
- **Help:** first-join welcome with title and sound, `/help`, `/help tokens|bounty|commands`, `/tutorial`.

## Commands

| Command | Description |
|---|---|
| `/tokens` | Your balance and rank |
| `/tokens pay <player> <amount>` | Send tokens (confirmation screen) |
| `/tokens top` | Richest players |
| `/bounty` | Bounty menu |
| `/bounty <player> <amount>` | Place or add to a bounty (confirmation screen) |
| `/bounty list [page]` · `top` · `check <player>` | View bounties |
| `/help [tokens\|bounty\|commands]` | Help in chat |
| `/tutorial` | Interactive tutorial |
| `/tokensmp` (`/tsmp`, `/smp`) | Main menu |

Amounts accept `500`, `1 500`, `2k` and `1,5m` (comma = decimal point).

Admin commands (permission `tokensmp.admin`, default op):

| Command | Description |
|---|---|
| `/tokensmp reload` | Reload config and messages |
| `/tokensmp balance|give|take|set <player> [amount]` | Adjust balances (recorded in the ledger) |
| `/tokensmp clearbounty <player>` | Remove a bounty and refund contributors |
| `/tokensmp ledger [n]` | Show the last n recorded transactions |
| `/tokensmp hologram create <richest\|kills\|bounty\|claimed\|placed>` | Place a leaderboard hologram where you stand |
| `/tokensmp hologram remove <id>` · `list` | Manage holograms |

Permissions: `tokensmp.use` (default true) for players, `tokensmp.admin` (default op).

`/help` is registered by this plugin and takes over the plain `/help` command. If you use another
plugin that also defines `/help`, remove the `help` entry from `plugin.yml`.

## Death, kill and bounty rules

1. Player kills player: victim loses `steal-percent` of their balance, killer gains that plus `kill-reward`, plus any bounty on the victim.
2. Suicide and environmental deaths pay nothing. If the victim was tagged by another player in the last `tag-seconds`, that player gets the kill (switchable).
3. Logging out while tagged settles as a death and, by default, kills the player.

Anti-exploit protections:

| Exploit | Protection |
|---|---|
| Killing the same player repeatedly | No rewards inside `cooldown-seconds` for the same killer and victim, and at most `max-per-hour` rewarded kills per pair per hour |
| Alt accounts | Accounts sharing an IP get no kill rewards. IPs are stored only as salted hashes |
| Claiming your own bounty | Contributions from the killer, or from accounts on the killer's network, are refunded to their owner instead of paid out |
| Duplicated transactions | All balance changes run on the main thread, a check and its deduction cannot interleave, and menus ignore clicks faster than `click-cooldown-ms` and can only fire once |
| Combat logging | Counts as a death |
| Accidental spending | Confirmation screens for transfers and bounties |

Every transaction is stored in the `ledger` table of `tokens.db` and printed to the console
(`logging.log-transactions-to-console`). Database writes happen on a separate thread, one
transaction per game action, so the main thread never waits on disk.

## Configuration

`config.yml` holds all numbers: rewards, percentages, cooldowns, bounty limits and thresholds,
sound keys, scoreboard lines, leaderboard and hologram sizes. `messages.yml` holds all chat text in
MiniMessage format. GUI item text is in the `gui` package. To use custom resource-pack sounds
later, change the `sound` keys in `config.yml`.

## Layout

```
dev.tokensmp
  data/      PlayerData, Storage (SQLite, single writer thread)
  economy/   Economy (balances, transfers, transaction batching)
  bounty/    BountyManager
  pvp/       KillService, CombatManager
  listener/  join/quit, death, combat tagging
  gui/       Menu base, paged menu, confirm screen and all menus
  ui/        scoreboard, holograms, leaderboards, chat prompts
  sound/     SoundService
  command/   commands
  util/      Settings, Msg, ItemBuilder, Fmt
```

To add a feature, give it a manager class, create it in `TokenSMPPlugin.onEnable`, and use
`Economy.tx()` for anything that changes balances so it is saved and logged as one transaction.

## Known limits

- Single server only. SQLite is not meant to be shared between servers.
- Repeat-kill history is kept in memory and resets on restart.
- Not tested on Folia; it uses the standard Bukkit scheduler.
- No PlaceholderAPI expansion yet.
