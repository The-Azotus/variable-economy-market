# Variable Economy: Market

A server-wide market for Minecraft servers, for NeoForge 1.21.1. Players sell items to the server and buy them back from one shared stock, and prices rise and fall with supply and demand. All money moves through each player's bank account, counted in **bits**.

This repository is the **market**, an optional add-on for [Variable Economy](https://github.com/The-Azotus/variable-economy-core). It adds:

- **One market for the whole server.** Everything players sell goes into a shared stock that everyone buys from, at the same prices for every player.
- **Prices that move.** Selling an item lowers its price and buying raises it. Prices drift back toward normal over time, and buying something and selling it straight back always loses a little, so there's no free profit.
- **Item classes.** Groups of items share pricing behaviour: common resources stay stable, while rare items swing hard and recover slowly, so dumping a pile of something valuable doesn't pay.
- **The Market Terminal.** A craftable block that opens the market's screen: a searchable list with prices, stock and a price chart, Buy and Sell, your recent trades, and a **Selling box** for selling many different items at once. Players can also open it with `/market` if the server allows it.
- **Prices in tooltips.** Any item the market buys shows what it pays, wherever the item appears.
- **Built-in prices** for vanilla and 54 popular mods, each loading only if its mod is installed.
- **Tools to price a whole modpack.** Export every item to a spreadsheet, fill in prices and import it back; let the market suggest prices for crafted items from their recipes; or edit prices in game, one item at a time or for a whole tag such as "all logs".
- **Checks and backups for admins.** A diagnosis of the catalog, money-supply stats for the market, and backups of stock, prices and edits.
- **Hooks for packs and mods.** Price files in any datapack, commands for functions, and a Java API with events.

## The four mods

Variable Economy is four mods. **Core** is required. The **market**, **contracts** and **exchange** are optional add-ons, and any combination of them works on top of core.

| Mod | Mod id | Adds | Repository |
| --- | --- | --- | --- |
| Core | `variable_economy` | The coins and bank, the Bank block and Bank Card, accounts, `/pay`, account history, the leaderboard, money-supply stats, `/bank` | [variable-economy-core](https://github.com/The-Azotus/variable-economy-core) |
| Market | `variable_economy_market` | A server-owned market whose prices move with supply and demand: the Market Terminal, prices and price tooltips, `/market` | This one |
| Contracts | `variable_economy_contracts` | Delivery contracts with deposits: the Contract Board, `/contracts`. With the market, contracts can pay by market prices. | [variable-economy-contracts](https://github.com/The-Azotus/variable-economy-contracts) |
| Exchange | `variable_economy_exchange` | Player buy and sell orders: the Exchange block, `/exchange` | [variable-economy-exchange](https://github.com/The-Azotus/variable-economy-exchange) |

Each mod has its own block, screen, config file and commands. If a mod isn't installed, its features are simply absent.

## Requirements and installation

- [Variable Economy core](https://github.com/The-Azotus/variable-economy-core) **0.6.2** or a later 0.6.x (up to, but not including, 0.7).
- Minecraft 1.21.1 with NeoForge 21.1.228 or newer, and Java 21.
- Optional: [Create](https://modrinth.com/mod/create) 6.0.10 or a later 6.0.x. With it, the market's price suggestions read Create's machine recipes more closely.
- Core's own optional mods work with the market too: with [ProgressiveStages](https://modrinth.com/mod/progressivestages), items a player hasn't unlocked are hidden from them in the market; FTB Teams parties and named accounts can trade through a shared account.

Install core and the market on the **server and every client**, all of the same version. Clients need them for the screen and the tooltips. Put `variable_economy_market-<version>.jar` in the mods folder; the `-sources` and `-javadoc` jars are for developers.

What changed in each version is in the [changelog](CHANGELOG.md). Worlds from Dynamic-Economy, the mod's name before 0.6.0, can't be carried over.

## Using it

### The Market Terminal

Craft a **Market Terminal** from 8 Bits around a crafting table, and right-click it. Or type `/market`, if the server allows it. A screen opened from the block closes if you walk more than about 8 blocks away.

```
Bit  Bit            Bit
Bit  Crafting table Bit
Bit  Bit            Bit
```

### The screen

- **The list** shows each item's stock (**∞** never runs out), what the market charges (**Buy**), what it pays (**Sell**), how many you have, and a ▲ or ▼ when the price is above or below normal.
- **Search** by name, by mod with `@create`, or by tag with `#ingots`. **Sort** by name, cheapest, best pay or stock, and **filter** to items in stock, buyable, sellable, or held (items you have that the market buys).
- **Click an item** to see its price chart and prices. Set a quantity (Shift changes it by 16), or use **Max** (the most you can buy) and **All** (every copy you hold). The **Buy** and **Sell** buttons show the exact total.
- **The Selling box**, along the bottom with your inventory: click items to put them in the box (right-click for one, Shift-click for every copy), see what they're worth, and sell them all with one click. Until you sell, the items stay in your inventory, so closing the screen never loses anything.
- **My trades** (the button in the title row) lists your last 50 trades.

You never get a worse deal than the screen shows. If the price moves your way just as you click, the trade goes through at the better price; if it moves against you, nothing happens and the button shows the new total.

### How prices move

- Every item sold lowers the price a little, and every item bought raises it. A big order slides the price as it goes, so 640 items sell for less than 640 times the price of one, and splitting an order never gets a better total.
- Every minute or so, each price moves part of the way back toward normal.
- Buying something and selling it straight back always loses money.

### What the market accepts

- **Only plain items**: not enchanted, damaged or renamed. What you buy always comes fresh.
- **Only your main inventory**: the hotbar and the 27 storage slots.
- **Only orders that fit.** A purchase that doesn't fit in your inventory is refused; nothing is ever dropped on the ground.
- **Never coins.** Coins go in and out of your account at a Bank.

Everything is paid into, and taken from, your active bank account. Core's Bank block (or a Bank Card) turns coins into money and back.

## Commands

Player commands follow the market's `access_mode`: with `BLOCK_ONLY`, only operators can use them, and `/market` tells players to find a Market Terminal.

| Command | What it does |
| --- | --- |
| `/market` | Opens the market screen. |
| `/market sell <item> <qty>`, `/market buy <item> <qty>` | Trades without the screen, at the current price. Returns the quantity traded, for `execute store`. |
| `/market stock` | Lists the items in stock. Items with unlimited stock aren't listed, since they never run out. |
| `/market stock <item>`, `/market price <item>` | One item's stock, or its prices and price factor. |

Admin commands need permission level 2, except the three that read and write files, which need level 4 (the server console or a level-4 operator):

| Command | What it does |
| --- | --- |
| `/market admin setprice <item> <price>` | Prices one item in game. |
| `/market admin set <item> <field> <value>`, `unset <item> <field>` | Sets or clears any catalog field of one item. |
| `/market admin remove <item>`, `revert <item>` | Takes an item off the market, or undoes your in-game edits to it. |
| `/market admin info <item>` | Every value of an item, where each came from, and its stock. |
| `/market admin tag <tag> setprice\|set\|unset\|remove\|revert\|info …`, `tags` | Tag rules: one change for every item in a tag, such as `minecraft:logs`. Most take `preview`. |
| `/market admin classes` | The item classes, their values and how many items use each. |
| `/market admin setstock <item> <qty>`, `clearstock <item>` | Sets or empties an item's stock. |
| `/market admin resetprice <item>`, `all [preview]`, `tag <tag> [preview]` | Puts prices back to normal. |
| `/market admin diagnose [page]` | Catalog problems and things worth checking, such as an item that sells for more than its ingredients cost. |
| `/market admin stats [item]` | Money the market created and removed, and items moved. |
| `/market admin backup`, `backups`, `restore <name> <stock\|prices\|edits\|all> [confirm]` | Backs up, lists and restores stock, prices and edits. |
| `/market admin prune [confirm]` | Removes saved data for items that no longer exist, after a backup. |
| `/market admin reload` | Re-reads the catalog files. |
| `/market admin export [modid]` (level 4) | Writes every item to `items.csv`, with templates, in `config/variable_economy/exports/<time>/`. |
| `/market admin import "<path>" [config] [preview]` (level 4) | Imports a priced CSV into the world, or with `config` for every world; `preview` writes nothing. |
| `/market admin suggest [modid]` (level 4) | Prices unlisted crafted items from their recipes, into `suggested.csv` for you to review and import. |

Every command, with every option, is in the [admin guide](docs/admin-guide.md#commands).

## Configuration

The market's settings are in `config/variable_economy_market-server.toml`, created on first start. Changes apply as soon as the file is saved: prices update and open screens are refreshed.

| Setting | Default | What it does |
| --- | --- | --- |
| `access_mode` | `COMMAND_AND_BLOCK` | `BLOCK_ONLY` keeps players to the Market Terminal. |
| `max_quantity_per_request` | `640` | The most one buy or sell can move. |
| `[pricing]` `sell_impact`, `buy_impact` | `0.0005`, `0.0005` | How much each unit sold lowers the price, and each unit bought raises it. |
| `[pricing]` `recovery`, `interval_ticks` | `0.05`, `1200` | How much of the way back to normal a price moves at each step, and how often steps happen (1,200 ticks is one minute). |
| `[pricing]` `floor`, `ceiling` | `0.2`, `5.0` | How low and how high a price can go, as a share of its base price. |

These are the defaults for items whose class or entry doesn't set its own. Players can hide the price line in tooltips with `show_sell_price_in_tooltips = false` in `config/variable_economy_market-client.toml`. Which item data doesn't stop a copy counting as plain is core's `ignored_components` setting. Every setting, with its range, is in the [admin guide](docs/admin-guide.md#server-config).

## Pricing a pack

### Catalog files

Prices live in catalog files: JSON in any datapack at `data/<namespace>/market/<name>.json`, or in `config/variable_economy/catalog/market/` for every world. Prices are whole bits.

```json
{
  "defaults": { "sell_fee": 0.05 },
  "entries": [
    { "item": "minecraft:cobblestone", "base_price": 1,     "class": "common_resource" },
    { "item": "minecraft:diamond",     "base_price": 512,   "class": "rare_resource" },
    { "item": "minecraft:nether_star", "base_price": 16384, "class": "unique", "can_sell": false }
  ]
}
```

An entry's fields are `item`, `base_price`, `class`, `markup`, `sell_fee`, `can_sell`, `can_buy`, `start_stock`, `unlimited_stock`, `dynamic`, `min_buy_markup`, and the price movement fields `sell_impact`, `buy_impact`, `recovery`, `floor` and `ceiling`. A file's `defaults` apply to all its entries, and `"required_mods"` skips the file when a mod is missing. The [admin guide](docs/admin-guide.md#catalog-fields) explains each field.

When several files list one item, each higher file changes only the fields it sets. From weakest to strongest: the server config, the item's class, the built-in prices, mods' own price files, the config folder, other datapacks, then tag rules and in-game edits. So an admin's change always wins, and changing one built-in price needs only `{ "item": "minecraft:diamond", "base_price": 600 }`.

### Item classes

A class gives a group of items the same price behaviour. The built-in ones:

| Class | For | Stock | Markup | Units sold to drop the price 10% |
| --- | --- | --- | --- | --- |
| `common_resource` | Raw materials gathered in bulk: stone, logs, sand | unlimited | 0.5 | ~530 |
| `common_processed` | Common items made from those: planks, glass, ingots | normal | 0.4 | ~210 |
| `farmable` | Crops and mob drops that farms produce endlessly | unlimited | 0.5 | ~105 |
| `rare_resource` | Scarce raw materials: diamonds, ancient debris | normal | 1.0 | ~21 |
| `rare_processed` | Valuable made items: netherite, precision mechanisms | normal | 1.0 | ~35 |
| `rare_loot` | Found, not made: music discs, enchanted books, totems | normal | 1.5 | ~5 |
| `unique` | One-of-a-kind: nether stars, dragon eggs | normal | 2.0 | 1–2 |

Rare classes also recover more slowly. You can change these or add your own in `market_classes/` files.

### The built-in prices

The market ships about 9,300 prices for vanilla and 54 mods (Create, Mekanism, Applied Energistics 2, Ars Nouveau, Farmer's Delight and others), raw materials and crafted items alike, each with a class. They are on by default and load only for installed mods; your own files, imports and edits always beat them. `/datapack disable "mod/variable_economy_market:default_prices"` turns them off. A small example catalog, `mod/variable_economy_market:example_catalog`, is off until you enable it.

### A whole modpack in three steps

1. From the server console, run `/market admin export` and open the `items.csv` it writes in a spreadsheet. Fill in `base_price` for anything you want on the market that has no price yet.
2. Run `/market admin suggest`. It prices the crafted items no file lists from their recipes, into a `suggested.csv` for you to review.
3. Import each sheet: `/market admin import "<path>" config preview` to check it, then the same without `preview`. The path is relative to `config/variable_economy/`, and with `config` the prices apply to every world.

Then tune in game, for example `/market admin setprice minecraft:diamond 300`, or `/market admin tag minecraft:logs setprice 4` for every log. `/market admin diagnose` points out anything worth checking. The [admin guide](docs/admin-guide.md#pricing-items) walks through each step.

## For pack makers

- **Price files** can go in any datapack, including KubeJS's and OpenLoader's, or in `config/variable_economy/catalog/market/`. Give each entry a class rather than tuning numbers yourself: the built-in classes all pass the market's check against round-trip profits.
- **Commands from functions.** Functions and command blocks run at level 2, so they can use the admin commands. The trade commands need a player as the source:

  ```mcfunction
  execute as @p store result score @s sold run market sell minecraft:wheat 64
  market admin setprice minecraft:diamond 300
  ```

- **KubeJS** scripts can write price files, change item tags (which tag rules follow) and run the commands above. Balances, payments and charges are core's KubeJS bindings: see [core's README](https://github.com/The-Azotus/variable-economy-core#kubejs).

A mod that ships prices for its own items puts them in its own namespace, such as `data/yourmod/market/yourmod.json`. The [add-on guide](docs/api.md#without-java-data-and-commands) has the details.

## For mod authors

The market has a server-side Java API in `variableeconomy.market.api` (events in `variableeconomy.market.api.event`). Money, accounts and item locks are [core's API](https://github.com/The-Azotus/variable-economy-core#for-mod-authors). It is marked experimental until 1.0, and every change is listed in the [add-on guide's changelog](docs/api.md#changelog).

### Depending on the market

The released jars are on CurseForge (the market is project 1818103, core 1818087) and can be used through [CurseMaven](https://www.cursemaven.com/):

```groovy
repositories {
    exclusiveContent {
        forRepository { maven { url = 'https://cursemaven.com' } }
        filter { includeGroup 'curse.maven' }
    }
}

dependencies {
    compileOnly 'curse.maven:variable-economy-1818087:<core file id>'
    compileOnly 'curse.maven:variable-economy-market-1818103:<market file id>'
}
```

Then declare it in your `neoforge.mods.toml`, usually as optional, next to core:

```toml
[[dependencies.yourmod]]
    modId="variable_economy_market"
    type="optional"
    versionRange="[0.6,)"
    ordering="AFTER"
    side="BOTH"
```

As an optional dependency, check `ModList.get().isLoaded("variable_economy_market")` before touching the market, and keep every reference to its types in a class loaded only after that check. For sources and javadoc, clone this repository and run `gradlew publishToMavenLocal`; it publishes `variableeconomy:market:<version>`. The [add-on guide](docs/api.md#depending-on-the-market) gives the file IDs and the full setup.

### The API

Everything starts at `MarketApi`:

| Call | What it gives |
| --- | --- |
| `MarketApi.prices()` | `MarketPrices`: `quote(server or player, item)`, `listedItems`, `sellTotal` and `buyTotal` (what N units pay or cost now), `normalSellTotal` (from the base price), and `lowestBuyCost` (the least N units could ever cost to buy). |
| `MarketApi.trading()` | `MarketTrading`: `sell` and `buy` for a player, from their main inventory, through their active account or a named one. |

A `MarketQuote` holds an item's base price, sell and buy price, stock (empty when unlimited), whether it can be sold and bought, its price factor and its class. Trades return a `TradeResult` with a `MarketResult` (`OK`, `NOT_LISTED`, `NOT_ENOUGH_ITEMS`, `NO_FUNDS`, `NO_SPACE`…), the quantity, the total and the account.

The rules:

- **Server thread only.** Every method that reads prices or trades must run on the server thread, and throws otherwise.
- **Results, not exceptions.** Only `OK` changed anything; any other result left the market, the account and the inventory as they were.
- **Amounts are `long` bits.** Trades move at most the server's `max_quantity_per_request` at once.
- **Never pay for items outside the market for more than it charges.** Give money for items either by selling them through `MarketApi.trading()`, or by paying less than `lowestBuyCost` with core's `pay`. Otherwise players buy from the market and hand the items to you for a profit. The [money rule](docs/api.md#the-money-rule) explains it.

### Events

Posted on `NeoForge.EVENT_BUS` after the change is complete, and never cancellable:

| Event | Fires when |
| --- | --- |
| `MarketTradeEvent` | A player bought or sold, by any route: the screen, a command or the API. It gives the player, account, item, `isBuy`, quantity, unit price and total. |
| `CatalogReloadedEvent` | The catalog may have changed: at start, after a reload, an import, a restore, an in-game edit or a server config change. Re-read anything you keep from it. |

### Example

```java
/** On the server thread: what selling a stack of wheat pays now, and selling a player's turned-in wheat. */
static void turnIn(ServerPlayer player) {
    MarketPrices prices = MarketApi.prices();
    prices.quote(player, Items.WHEAT).ifPresent(quote ->
            player.sendSystemMessage(Component.literal("Wheat sells for ").append(CoreApi.formatMoney(quote.sellPrice()))));
    TradeResult sold = MarketApi.trading().sell(player, Items.WHEAT, 64);
    if (sold.ok()) {
        player.sendSystemMessage(Component.literal("Sold 64 wheat for ").append(CoreApi.formatMoney(sold.total())));
    }
}

static void listen() {
    NeoForge.EVENT_BUS.addListener((MarketTradeEvent event) -> {
        if (!event.isBuy() && event.getItem() == Items.WHEAT) {
            // count event.getQuantity() towards a quest for event.getPlayer()
        }
    });
}
```

The [add-on guide](docs/api.md) covers every method, result and event, with a complete worked example of a mod that depends on the market optionally.

## Guides

| You are | Read |
| --- | --- |
| A player | [Player guide](docs/player-guide.md) |
| A server admin | [Admin guide](docs/admin-guide.md) |
| A mod or modpack author | [Add-on guide](docs/api.md) |
| Reading the code | [Architecture](docs/architecture.md) |

## Building from source

Run `gradlew build` with Java 21. Gradle's toolchain finds an installed JDK or downloads one, and the Gradle wrapper (Gradle 9, which runs on Java 17 or newer) is in the repository. The build compiles the mod, checks the module boundary (see [docs/architecture.md](docs/architecture.md)), and puts the jar, a sources jar and a javadoc jar of the API in `build/libs/`.

The market builds against core, and `gradle.properties` says which core:

- `core_source=curseforge` (the default) uses the released core that `core_curseforge_project` and `core_curseforge_file` name, through CurseMaven.
- `core_source=local` uses `variableeconomy:core:<core_version>` from your local Maven instead: clone [variable-economy-core](https://github.com/The-Azotus/variable-economy-core), check out what you want, and run `gradlew publishToMavenLocal` there first. `-Pcore_source=local` sets it for one build.

Then:

- `gradlew runGameTestServer` runs the market's automated tests on a GameTest server with core loaded, then checks that it started cleanly. `-PwithoutMods=all`, or a comma-separated list of `create`, `ftbteams`, `progressivestages` and `kubejs`, leaves the optional mods out.
- `gradlew runClient` and `gradlew runServer` start a dev client or server with core and the market.
- `gradlew publishToMavenLocal` publishes `variableeconomy:market:<version>` to your local Maven, which the contracts add-on builds against.

Released versions are tagged in git, for example `v0.6.1`.

## Licence

MIT; see [LICENSE](LICENSE).
