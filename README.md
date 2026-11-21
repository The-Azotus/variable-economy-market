# Variable Economy: Market

A server-wide market for Minecraft, and an add-on for [Variable Economy](https://github.com/The-Azotus/variable-economy-core). Players sell items to the server for money and buy them back from one shared stock, and prices rise and fall with supply and demand.

- **One market for the whole server.** Everything players sell goes into a shared stock that everyone buys from, at the same prices for every player.
- **Bank payments.** All money moves through each player's bank account in Variable Economy's core, so there are no coins to carry. Money is counted in bits.
- **Prices that move.** Selling an item lowers its price and buying raises it. Prices drift back toward normal over time.
- **Item classes.** Groups of items share pricing behaviour: common resources stay stable, while rare items swing hard and recover slowly, so dumping a pile of something valuable doesn't pay.
- **The Market Terminal.** A craftable block that opens the market's screen. Players can also open it with `/market` if the server allows it.
- **Prices in tooltips.** Any item the market buys shows what it pays, wherever the item appears.
- **Easy to price a whole modpack.** Built-in prices for vanilla and many popular mods. Export every item to a spreadsheet, fill in prices, and import it back, or let the market suggest prices for crafted items from their recipes. Admins can also edit prices in game, one item at a time or for a whole tag such as "all logs".

| You are | Read |
| --- | --- |
| A player | [Player guide](docs/player-guide.md) |
| A server admin | [Admin guide](docs/admin-guide.md) |
| A mod or modpack author building on the market | [Add-on guide](docs/api.md) |

## Requirements

The market is an add-on: it needs [Variable Economy core](https://github.com/The-Azotus/variable-economy-core), which has the coins, the bank and the accounts. It works alone on top of core, and with any of the other add-ons, [Contracts](https://github.com/The-Azotus/variable-economy-contracts) and [Exchange](https://github.com/The-Azotus/variable-economy-exchange). Core's README describes the whole family.

- Variable Economy core 0.6.1 or a later 0.6.x (from 0.6.1 up to, but not including, 0.7)
- Minecraft 1.21.1 with NeoForge 21.1.228 or newer, on Java 21
- Optional: [Create](https://modrinth.com/mod/create) 6.0.10 or a later 6.0.x: the market's price suggestions read its machine recipes more closely.

Core's own optional mods (FTB Teams, ProgressiveStages and KubeJS) work with the market too; with ProgressiveStages, items a player hasn't unlocked are hidden from them in the market.

Install core and the market on the **server and every client**. Clients need them for the screen and the tooltips.

What changed in each version is in the [changelog](CHANGELOG.md).

**Coming from an older version?** Worlds from Dynamic-Economy, the mod's name before 0.6.0, can't be carried over: the mod ids changed, so their market, bank accounts, coins and terminals don't load.

## Quick start

**Players:** right-click a **Market Terminal**, or type `/market` if your server allows it. Click an item, choose a quantity, then press **Buy** or **Sell**. Everything is paid through your bank account; a **Bank** block (or a Bank Card) from core turns coins into money and back. See the [player guide](docs/player-guide.md).

**Admins:**

1. Install core and the market, then start the server. Built-in prices for vanilla and many popular mods are on by default (each mod's file loads only if that mod is installed); your own files, imports and edits always override them. To start from an empty market instead, `/datapack disable "mod/variable_economy_market:default_prices"`.
2. Price your pack. From the server console, run `/market admin export`, fill in `base_price` in the exported `items.csv`, then run `/market admin import "<path to the csv>"`. In a big pack, run `/market admin suggest` too: it prices crafted items from their recipes, and its `suggested.csv` imports the same way (see [Pricing a whole modpack](docs/admin-guide.md#pricing-a-whole-modpack)). Or write catalog files by hand: files in `config/variable_economy/catalog/market/` apply to every world, and an entry only needs the fields it changes, such as `{ "item": "minecraft:diamond", "base_price": 600 }`.
3. Tune it in game. For example, `/market admin setprice minecraft:diamond 300` prices one item, and `/market admin tag minecraft:logs setprice 4` prices every log.
4. To make the Market Terminal the only way in, set `access_mode = "BLOCK_ONLY"` in `config/variable_economy_market-server.toml`.

The [admin guide](docs/admin-guide.md) covers every command, catalog field and setting.

## For add-on authors

Other mods can read the market's prices, trade for a player and listen for trades and catalog reloads through a Java API, `variableeconomy.market.api`; money, accounts and item locks are core's API. Price files and commands work without any Java. The [add-on guide](docs/api.md) covers both.

## Building from source

The mod compiles with Java 21, which Gradle's toolchain finds or downloads; the Gradle wrapper (Gradle 9, which itself runs on Java 17 or newer) is in the repo.

The market builds against core. `gradle.properties` says which core and where it comes from:

- `core_source=curseforge` (the default) uses the released core that `core_curseforge_project` and `core_curseforge_file` name, through [CurseMaven](https://www.cursemaven.com/): core 0.6.1 is `curse.maven:variable-economy-1818087:9710072`.
- `core_source=local` uses `variableeconomy:core:<core_version>` from your local Maven instead, for building against a core that isn't released: clone [variable-economy-core](https://github.com/The-Azotus/variable-economy-core), check out what you want, and run `gradlew publishToMavenLocal` there first. `-Pcore_source=local` sets it for one build.

Then:

| Command | Does |
| --- | --- |
| `gradlew build` | Compiles, checks the module boundary, and builds the mod jar, its sources jar and its javadoc jar in `build/libs/`. |
| `gradlew runGameTestServer` | Runs the market's GameTests with core loaded, then checks the log. `-PwithoutMods=all` (or a comma list of `create`, `ftbteams`, `progressivestages`, `kubejs`) runs without the optional mods. |
| `gradlew runClient`, `gradlew runServer` | A dev client or server with core and the market. |
| `gradlew publishToMavenLocal` | Publishes `variableeconomy:market:<version>`, which the contracts add-on builds against. |

To find your way around the code, start with [docs/architecture.md](docs/architecture.md).

## Licence

MIT; see [LICENSE](LICENSE).
