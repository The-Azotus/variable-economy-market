# Add-on guide

This guide is for mod developers writing an add-on that uses the market of Variable Economy, and for pack makers who connect other mods to it with data files and commands. It assumes you know NeoForge modding, but nothing about this mod's code.

For what the market does, read the [admin guide](admin-guide.md) first. This guide links to it rather than repeating it. Accounts, money, item locks and the general rules of the API are Variable Economy's core: [core's add-on guide](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md) covers them, and this guide links to it.

## What the market offers

There are two ways to integrate:

- **Without Java.** Ship price files and item classes as data, and drive the market with commands (from functions, command blocks or KubeJS). This works in any version and needs no build setup. See [Without Java: data and commands](#without-java-data-and-commands).
- **With the Java API.** Read market prices, trade for a player, and listen for trades and catalog reloads. See [Market prices and trading](#market-prices-and-trading) and [Events](#events). For paying, charging and moving money, locking items and the rest, use core's API.

The Java API runs on the server only. A client-only mod can't use it.

| Mod | Mod ID | Artifact | Package | What's in it |
| --- | --- | --- | --- | --- |
| Market | `variable_economy_market` | `market` | `variableeconomy.market.api`, `variableeconomy.market.api.event` | Market prices and trading for a player, and the market's events. Start at `MarketApi`. |
| Core | `variable_economy` | `core` | `variableeconomy.core.api`, `variableeconomy.core.api.event` | Accounts, money, item locks, net-worth contributors, the audit log and helpers. Start at `CoreApi`. |

The market is an optional add-on on every server: a server may have core alone. Its package is there only when the market is installed, so check its mod ID before using it, even when your mod requires core (see [An optional dependency](#an-optional-dependency)).

### Stability

- The API is the packages in the table above. They're marked `@ApiStatus.Experimental` until 1.0: they may still change in a 0.x minor release (0.6 to 0.7, say). Every change to the market's is listed in this file's [changelog](#changelog), and core's in core's guide.
- A few members inside them are marked `@ApiStatus.Internal` (`MarketApi.install` and its `Services` type). They're for Variable Economy itself; don't use them.
- Everything else in the jar is internal. Those packages are marked `@ApiStatus.Internal` and may change in any release, a patch release included. Don't call them, even when a class is public: public there means "used by another package of this mod", not "supported".
- Data files and commands are described in the [admin guide](admin-guide.md), which is kept up to date with each release.

## Depending on the market

You build against core as well as the market: core's add-on guide, [Depending on Variable Economy](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md#depending-on-variable-economy), has the setup for core and its `neoforge.mods.toml` entry. This section adds the market.

The released jars are on CurseForge, and you can depend on them through [CurseMaven](https://www.cursemaven.com/), with the repository block from core's guide. The market is CurseForge project **1818103**; the market 0.6.1 is `curse.maven:variable-economy-market-1818103:9717524`, and core 0.6.1 `curse.maven:variable-economy-1818087:9710072`. Use those in place of the `variableeconomy:` coordinates below. CurseMaven serves only the mod jars, without sources or javadoc.

For the sources and javadoc, or to build against an unreleased market, publish the market to your local Maven from this repository: clone it, check out the release you target (tags start with `v`), and run `gradlew publishToMavenLocal`. That publishes `variableeconomy:market:<version>`, with the version from `mod_version` in `gradle.properties` (no `v`). It builds against core, so publish core to your local Maven first, from [its repository](https://github.com/The-Azotus/variable-economy-core), the same way. The market's artifact names no dependencies: declare core yourself. Use **0.6.0** or later: 0.5.0, the first version with an API, was Dynamic-Economy, before the mod was renamed and split, and its API is gone (see the [changelog](#changelog)).

A sources jar and a javadoc jar are published with it, so your IDE shows the javadoc and the source. The javadoc covers only the market's API packages.

### Gradle

Compile against core and the market, and put them on the dev runtime so they run in your dev client and server. The `localRuntime` configuration below (the NeoForge MDK template has it) keeps them out of your published POM, so your users don't inherit them as runtime dependencies.

Groovy (`build.gradle`):

```groovy
repositories {
    exclusiveContent {
        forRepository { mavenLocal() }
        filter { includeGroup 'variableeconomy' }
    }
}

configurations {
    localRuntime
    runtimeClasspath.extendsFrom localRuntime
}

dependencies {
    compileOnly 'variableeconomy:core:0.6.1'
    compileOnly 'variableeconomy:market:0.6.1'
    localRuntime 'variableeconomy:core:0.6.1'
    localRuntime 'variableeconomy:market:0.6.1'
}
```

Kotlin (`build.gradle.kts`):

```kotlin
repositories {
    exclusiveContent {
        forRepository { mavenLocal() }
        filter { includeGroup("variableeconomy") }
    }
}

val localRuntime: Configuration by configurations.creating
configurations.runtimeClasspath { extendsFrom(localRuntime) }

dependencies {
    compileOnly("variableeconomy:core:0.6.1")
    compileOnly("variableeconomy:market:0.6.1")
    localRuntime("variableeconomy:core:0.6.1")
    localRuntime("variableeconomy:market:0.6.1")
}
```

- The `exclusiveContent` block takes only that group from your local Maven, and nothing else from it.
- The market's own optional dependency (Create) isn't pulled in. Add it to your dev runtime yourself if your add-on works with it.
- To try your add-on against unreleased changes, run `gradlew publishToMavenLocal` on the branch you want instead of a tag; it publishes that branch's `mod_version`.

### neoforge.mods.toml

Declare the market in your mod's `META-INF/neoforge.mods.toml`, next to core, replacing `yourmod` with your mod ID. For example, core required and the market optional:

```toml
[[dependencies.yourmod]]
    modId="variable_economy"
    type="required"        # or "optional"
    versionRange="[0.6,)"
    ordering="AFTER"
    side="BOTH"

[[dependencies.yourmod]]
    modId="variable_economy_market"
    type="optional"
    versionRange="[0.6,)"
    ordering="AFTER"
    side="BOTH"
```

- Variable Economy's mods must be installed on the server and on every client, so use `side="BOTH"`.
- `ordering="AFTER"` makes NeoForge construct the market before your mod, so its API is ready in your mod's constructor.
- Since the API may change in a 0.x minor release, you can cap the range at the next minor version, such as `[0.6,0.7)`, and raise it once you've checked the changelog.

### An optional dependency

With `type="optional"`, your mod must load without the market. The market is optional on every server, so this applies even when your mod requires core. Java loads a class's referenced types when the class is first used, so:

1. Check `ModList.get().isLoaded("variable_economy_market")` before touching anything of the market's.
2. Keep every reference to the market's types (calls, event types, lambdas) in a class of its own that is loaded only after that check passes.
3. Register your event listeners from that class with `NeoForge.EVENT_BUS.addListener`, not with `@EventBusSubscriber`: NeoForge loads a subscriber class whether or not the mod is there.

The [worked example](#worked-example) is set up this way.

## Without Java: data and commands

### Price files

A mod can ship market prices for its own items in its jar, and a pack maker can ship them in any datapack. The format is in the admin guide's [Catalog files](admin-guide.md#catalog-files) and [Catalog fields](admin-guide.md#catalog-fields).

Put the file in **your own namespace**, such as `data/yourmod/market/yourmod.json`:

```json
{
  "required_mods": ["yourmod"],
  "entries": [
    { "item": "yourmod:copper_gear", "base_price": 40,    "class": "common_processed" },
    { "item": "yourmod:star_core",   "base_price": 20000, "class": "unique", "can_sell": false }
  ]
}
```

- **Never use `data/variable_economy/market/<name>.json`** with the name of a built-in file (such as `minecraft.json` or `create.json`). Only the highest pack's copy of a file ID is read, so your file would hide the whole built-in file, and every item it leaves out would lose its built-in price. See [Changing a built-in price](admin-guide.md#changing-a-built-in-price).
- `"required_mods"` skips the whole file quietly when any of the listed mods isn't installed. Use it for prices of another mod's items. NeoForge's `neoforge:conditions` isn't read in these files.
- Prices are in bits. The built-in scale is roughly cobblestone 1, a log 8, an iron ingot 32, a diamond 512, a nether star 16,384 (see [Pricing items](admin-guide.md#pricing-items)).
- Give each entry a [class](admin-guide.md#item-classes) rather than tuning numbers yourself. The built-in classes all pass the market's round-trip check.
- Coins can never be listed. An entry for one is refused.
- Check your file in game: `/market admin info <item>` shows each value and the file it came from, and `/market admin diagnose` lists skipped entries and price gaps, such as an item that sells for more than its ingredients cost to buy.

### Classes

A class file goes in `data/<namespace>/market_classes/<name>.json`, and entries refer to it as `<namespace>:<name>`, such as `"class": "yourmod:gears"`. A bare name means `variable_economy:`. The fields are in [Item classes](admin-guide.md#item-classes). A class replaces a class of the same ID whole, so don't put a file in `data/variable_economy/market_classes/`: it would replace a built-in class for every item that uses it.

### Where your files sit in the priority order

When several files list one item, each higher file changes only the fields it sets. From lowest to highest:

1. The market's built-in prices.
2. **Mods' own jar data**, yours included. A mod's jar counts as built-in.
3. The [config folder](admin-guide.md#the-config-folder-prices-for-every-world), `config/variable_economy/catalog/`, which modpacks use for pack-wide prices.
4. Every other datapack: the world's datapacks, and packs from OpenLoader, KubeJS and the like.

So prices in your jar beat the built-in ones for the same item, and pack makers and server admins can still change any of them without touching your jar. In-game edits and tag rules beat every file.

### Commands

The full list is in the admin guide's [Commands](admin-guide.md#commands). These are the useful ones for scripts:

| Command | Permission | Use |
| --- | --- | --- |
| `/market sell <item> <qty>`, `/market buy <item> <qty>` | everyone* | Trades for the player running it, at the current price. Returns the quantity traded, 0 on failure. |
| `/market admin setprice`, `set`, `unset`, `tag …` | 2 | Changes prices and fields with every check, an audit line and an update to every client. The safe way for a script to change prices. See [Editing prices in game](admin-guide.md#editing-prices-in-game). |
| `/market admin setstock <item> <qty>` | 2 | Sets an item's stock. |
| `/market admin reload` | 2 | Re-reads the catalog files after a script writes them. |

\* Operators only when the market's `access_mode` is `BLOCK_ONLY`. Functions and command blocks run at level 2, so they can use them in either mode.

The trade commands need a player as the source, so run them with `execute as`. Each command's return value works with `execute store`:

```mcfunction
execute as @p run market sell minecraft:wheat 64
execute as @p store result score @s sold run market sell minecraft:wheat 64
```

Core's commands read balances and give or take money (`/bank admin money`), and core's KubeJS bindings read balances and pay players; see core's guides. KubeJS scripts can also write price files, change item tags (which [tag rules](admin-guide.md#tag-rules-pricing-many-items-at-once) follow) and run the commands above.

## The Java API

The market's entry point is `variableeconomy.market.api.MarketApi`. It hands out its services as static getters:

| Call | Service |
| --- | --- |
| `MarketApi.prices()` | `MarketPrices`: what the market pays and charges. |
| `MarketApi.trading()` | `MarketTrading`: selling and buying for a player. |

Each service is one object for the whole game, so you may keep it in a field. Its methods take the server or player they act on. The services are ready once the market has been constructed, which NeoForge does before any mod that depends on it with `ordering="AFTER"`; called earlier, the getters throw `IllegalStateException`. `MarketApi` exists only with the market installed: check `variable_economy_market` first (see [An optional dependency](#an-optional-dependency)).

The market's API follows core's rules, which [core's add-on guide](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md) explains under [Concepts](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md#concepts): amounts are whole bits as a `long`; every method that reads prices or trades runs on the server thread and throws `IllegalStateException` otherwise (the getters may run on any thread); a call either does everything or changes nothing, and only `OK` changed anything; results, not exceptions. Accounts (`AccountId`, a player's active account), money calls and item locks are core's: see [Accounts](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md#accounts), [Money](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md#money) and [Item locks](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md#item-locks) in core's guide.

### Market prices and trading

`MarketApi.prices()` reads prices and changes nothing:

| Method | Returns |
| --- | --- |
| `Optional<MarketQuote> quote(MinecraftServer server, Item item)` | The item's terms now, as every player would trade it. Empty if the item isn't listed. Includes items locked for some players. |
| `Optional<MarketQuote> quote(ServerPlayer player, Item item)` | The same, but also empty if the item is locked for this player. |
| `List<Item> listedItems(MinecraftServer server)` | Every listed item, locked ones included, in no order. Unmodifiable, and a snapshot: ask again after a [`CatalogReloadedEvent`](#events). |
| `OptionalLong sellTotal(MinecraftServer server, Item item, int quantity)` | What selling that many now would pay, the price falling with each unit and the sell fee taken. |
| `OptionalLong buyTotal(MinecraftServer server, Item item, int quantity)` | What buying that many now would cost, the price rising with each unit. Stock isn't checked. |
| `OptionalLong normalSellTotal(MinecraftServer server, Item item, int quantity)` | What selling that many would pay from the base price, as if nobody had traded it lately: the item's normal value, steadier than `sellTotal`. |
| `OptionalLong lowestBuyCost(MinecraftServer server, Item item, int quantity)` | The least buying that many could ever cost with the catalog as it is. See [The money rule](#the-money-rule). |

- The totals are empty when the item isn't listed, the market doesn't trade it that way, or the total is too large to count. They ignore the per-request limit.
- A quantity below 1 throws `IllegalArgumentException`.
- Prices are the same for every player, and move with every trade and over time. Ask when you need one rather than keeping it.

A `MarketQuote` holds:

| Field | Meaning |
| --- | --- |
| `item` | The item. |
| `basePrice` | Its base price from the catalog. |
| `sellPrice` | What the market pays for the next unit. |
| `buyPrice` | What the market charges for the next unit. |
| `stock` | How many the market holds, as an `OptionalLong`; empty for unlimited stock. |
| `canSell`, `canBuy` | Whether players may sell it to the market, and buy it from the market. |
| `factor` | The supply-and-demand factor: 1.0 is the base price, lower after players sell, higher after they buy. |
| `itemClass` | The [item class](admin-guide.md#item-classes) the catalog gives the item, such as `variable_economy:common_resource`, as an `Optional<ResourceLocation>`; empty if it has none. |

Quote prices are for **one** unit: every unit in an order moves the price, so an order's total isn't the unit price times the quantity. Use `sellTotal`, `buyTotal` or the total in the `TradeResult`.

`MarketApi.trading()` trades for a player:

| Method | Does |
| --- | --- |
| `TradeResult sell(ServerPlayer player, Item item, int quantity)` | Sells from the player's inventory, paid into their active account. |
| `TradeResult sell(ServerPlayer player, Item item, int quantity, AccountId account)` | The same, paid into `account`, which the player must be a member of. |
| `TradeResult buy(ServerPlayer player, Item item, int quantity)` | Buys into the player's inventory, paid from their active account. |
| `TradeResult buy(ServerPlayer player, Item item, int quantity, AccountId account)` | The same, paid from `account`, which the player must be able to spend from. |

Trades work exactly as the market screen does: the same checks, price movement, stock, trade history and [`MarketTradeEvent`](#events). Afterwards the item's new price goes out to every player.

- They trade at the current price; there's no quote to confirm.
- Only the player's **main inventory** counts: the hotbar and the 27 storage slots. Armour, the offhand and modded slots are ignored.
- Only **plain copies** sell: no enchanted, damaged or renamed ones (apart from data the server's `ignored_components` setting ignores). Bought items always come plain.
- `buy` checks that every item fits before taking money. Items are never dropped on the ground.
- One call moves at most the server's `max_quantity_per_request` (640 by default); more is `TOO_LARGE`. Split larger amounts into several calls.
- The market's `access_mode` isn't checked: your add-on decides who may trade, and where.
- An item locked for the player answers `NOT_LISTED`, the same as an unlisted one, so the reply doesn't reveal it.
- Trades through `MarketTrading` count in `/market admin stats`, like the screen's.

`TradeResult` holds `result`, `quantity` (the units traded; 0 unless OK), `unitPrice` (what the first unit traded at, or would have; 0 if the trade failed before it was priced), `total` (what the whole order paid or cost; on `NO_FUNDS`, `NO_STOCK`, `NO_SPACE` and `NOT_ENOUGH_ITEMS`, what it would have; otherwise 0) and `account` (the account paid or charged, or that would have been).

### The money rule

Core's `pay` **creates** money and `charge` **destroys** it. The market itself has no balance: sales create money and purchases destroy it, and its markup, fees and price impact make sure that buying N items and selling them straight back never makes a profit.

So an add-on that gives money for items must do it one of two ways:

- **Sell them through the market**, with `MarketApi.trading().sell(...)`. The market's price impact and fees apply.
- **Take them and call core's `pay`, but pay less than `MarketApi.prices().lowestBuyCost(server, item, quantity)`** whenever that has a value. That is the least buying those units from the market could ever cost, so buying them to hand in can't profit. An empty result means the market sets no bound: the item isn't listed, or the market doesn't sell it.

Never pay the market's sell price, or any price of your own, for items the market sells cheaper. A shop block, a trade-in quest or a "sell everything" button that did would let players buy from the market and turn the items in for more, creating money without end. Refuse coins (`CoreApi.isCoin`) and take only plain copies (`CoreApi.isPlain`).

### Results

`MarketResult` is the market's result for trades. Only `OK` changed anything.

| Value | From | Means |
| --- | --- | --- |
| `OK` | all | Done. |
| `NOT_LISTED` | `sell`, `buy` | The item isn't listed, or is locked for this player. |
| `SELL_DISABLED` | `sell` | The market doesn't buy this item (`can_sell: false`). |
| `BUY_DISABLED` | `buy` | The market doesn't sell this item (`can_buy: false`). |
| `NOT_ENOUGH_ITEMS` | `sell` | The player's main inventory holds fewer plain copies than asked. |
| `NO_STOCK` | `buy` | The market holds fewer than asked. |
| `NO_SPACE` | `buy` | The items wouldn't fit in the player's main inventory. |
| `NO_FUNDS` | `buy` | The account can't cover it. |
| `TOO_LARGE` | `sell`, `buy` | A quantity above `max_quantity_per_request`, a total too large to count, or a sale that would push stock or a balance past what it can hold. |
| `INVALID_QUANTITY` | `sell`, `buy` | A quantity of zero or less. |
| `NOT_ALLOWED` | `sell`, `buy` with an account | The player can't use the named account that way. |
| `NO_ACCOUNT` | `sell`, `buy` with an account | The named account doesn't exist. |
| `BANK_UNAVAILABLE` | `sell`, `buy` | The bank can't be reached. Not returned today. |

Handle the values you care about and treat the rest as "didn't happen": a later version may add values. Core's money calls return core's `BankResult`, in core's guide.

## Events

The market posts these on `NeoForge.EVENT_BUS` (the game bus), in `variableeconomy.market.api.event`. Amounts are in bits.

| Event | Fires | Fields (getters) |
| --- | --- | --- |
| `MarketTradeEvent` | After every market trade, from the screen, a command or the API. | `getServer`, `getPlayer` (`ServerPlayer`), `getAccount` (paid or charged), `getItem` (`Item`), `isBuy` (false for a sale), `getQuantity`, `getUnitPrice` (the first unit's price), `getTotal` (what changed hands) |
| `CatalogReloadedEvent` | The catalog was rebuilt: at server start, after `/reload` or `/market admin reload`, after an import or a restore of a backup's edits, and after every in-game admin edit. | `getServer` |

They follow the rules for all of Variable Economy's events, in core's guide under [Events](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md#events): posted only after the whole change is complete, never cancellable, only for changes that happened, always on the server thread, and a listener that throws is logged and ignored. For the market in particular:

- **A trade's balance event comes after it.** Balance events wait for the end of the tick, so a market purchase (which takes the money, then gives the items) has finished before you hear of it, and a `MarketTradeEvent` comes before the account's `BalanceChangedEvent`.
- **Never cancellable.** To stop a trade before it happens, lock the item with an item lock rule (core's `CoreApi.itemGate()`), or decide in your own code whether to call the API.
- **`CatalogReloadedEvent` can fire more than once for one admin action** (an edit the catalog rules refuse is applied, then undone). Treat it as "the catalog may have changed", and re-read anything you cached from it, such as `listedItems`. Don't cache quotes: prices also move with trades, admin resets and recovery over time, which don't all post an event, so ask for a price when you need one.
- A listener that moves money or trades through the API causes further events. Guard against looping.

To subscribe, add a listener from a class that's loaded only when the market is installed:

```java
NeoForge.EVENT_BUS.addListener((MarketTradeEvent event) -> { /* ... */ });
```

## Worked example

A quest mod with an optional dependency on the market. It shows an item's price, sells turned-in wheat for a player and counts wheat sold for a quest. `QuestProgress` stands for the quest mod's own code. For what a quest mod does with core alone, such as paying rewards with `pay` or locking items, see core's add-on guide.

The mod class checks for the market and touches nothing of it:

```java
package com.example.questcoins;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

@Mod(QuestCoins.MOD_ID)
public final class QuestCoins {
    public static final String MOD_ID = "questcoins";
    /** Check this before calling anything in MarketHooks. The market needs core, so core is there too. */
    public static final boolean MARKET_LOADED = ModList.get().isLoaded("variable_economy_market");

    public QuestCoins(IEventBus modBus) {
        if (MARKET_LOADED) {
            MarketHooks.register();
        }
    }
}
```

Every reference to the market's API is in one class, loaded only when the market is there. The rest of the quest code calls it only behind the same check, for example `if (QuestCoins.MARKET_LOADED) MarketHooks.turnInWheat(player, 64);`.

```java
package com.example.questcoins;

import variableeconomy.core.api.CoreApi;
import variableeconomy.market.api.MarketApi;
import variableeconomy.market.api.MarketQuote;
import variableeconomy.market.api.TradeResult;
import variableeconomy.market.api.event.MarketTradeEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;

/** Everything that touches Variable Economy's market. Loaded only when it is installed. */
final class MarketHooks {
    private MarketHooks() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(MarketHooks::onMarketTrade);
    }

    /** "Blaze Rod sells for 24 bits", or empty if the market doesn't buy it from this player. */
    static Optional<Component> priceLine(ServerPlayer player, Item item) {
        return MarketApi.prices().quote(player, item)
                .filter(MarketQuote::canSell)
                .map(quote -> Component.empty()
                        .append(item.getDescription())
                        .append(" sells for ")
                        .append(CoreApi.formatMoney(quote.sellPrice())));
    }

    /** A turn-in quest: the wheat is sold to the market, never bought from the player with pay(). */
    static void turnInWheat(ServerPlayer player, int quantity) {
        TradeResult result = MarketApi.trading().sell(player, Items.WHEAT, quantity);
        switch (result.result()) {
            case OK -> player.sendSystemMessage(Component.literal("Sold " + result.quantity() + " wheat for ")
                    .append(CoreApi.formatMoney(result.total())));
            case NOT_ENOUGH_ITEMS -> player.sendSystemMessage(Component.literal("You don't have that much wheat"));
            default -> player.sendSystemMessage(Component.literal("The market won't take wheat right now"));
        }
    }

    /** Counts wheat sold any way, screen, command or API, toward a quest. */
    private static void onMarketTrade(MarketTradeEvent event) {
        if (!event.isBuy() && event.getItem() == Items.WHEAT) {
            QuestProgress.add(event.getPlayer(), "sell_wheat", event.getQuantity());
        }
    }
}
```

Its prices ship in its jar at `data/questcoins/market/questcoins.json`, as in [Price files](#price-files).

## Changelog

The market's API changes. Core's are in [core's add-on guide](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/api.md).

- **0.6.1 (2026-11-17):** the market is its own repository and release; nothing in its API changed. `gradlew publishToMavenLocal` in this repository publishes it as `variableeconomy:market:<version>`, and its artifact names no dependencies: declare core yourself.

- **0.6.0 (2026-11-14):** breaks every add-on. The mod is renamed from Dynamic-Economy to Variable Economy, split into four mods, and its API is replaced.
  - The market is a separate optional mod, `variable_economy_market`, on top of core (`variable_economy`). Check its mod ID before using its package.
  - Its blocks, items, menus, packets and money reasons are in its own namespace (for example `variable_economy_market:market_sale` for a sale's `Reason`); its catalog data, item classes and the `config/variable_economy/` folder keep the `variable_economy` namespace.
  - It has its own config file, `variable_economy_market-server.toml`, and its own command root, `/market`.
  - The old package `variableeconomy.api` (`dynamiceconomy.api` in 0.5), its `event` package and its facade `VariableEconomyApi` (`DynamicEconomyApi` in 0.5) are removed. The market's part is replaced by `variableeconomy.market.api` (prices and trading), with an `event` package; accounts, money and item locks by core's `variableeconomy.core.api`.
  - `VariableEconomyApi.quote` and `listedItems` → `MarketApi.prices()`; `sell` and `buy` → `MarketApi.trading()`.
  - The single `MarketResult` is split: core's `BankResult` for money and accounts, and the market's own `MarketResult` for trades. `INVALID_AMOUNT` is now only in `BankResult`; `PRICE_CHANGED` is gone, since the API always trades at the current price; `MarketResult` gains `NOT_ALLOWED` and `NO_ACCOUNT`.
  - `TradeResult` gains `account`.
  - The events move: `MarketTradeEvent` and `CatalogReloadedEvent` to `variableeconomy.market.api.event`. `MarketTradeEvent` gains `getAccount()`.
  - `MarketQuote` gains `itemClass()`. The internal `MarketPrices.contractTerms` and `ContractTerms` are gone, with the market's `contract_*` catalog fields: contracts come from the contracts add-on's contract definitions.
  - Amounts are `long` throughout: one call moves any amount, where 0.5 refused more than 2,147,483,647 bits at once.
  - New: `MarketPrices.sellTotal`, `buyTotal`, `normalSellTotal` and `lowestBuyCost`, and `MarketTrading.sell` and `buy` with a named account.

- **0.5.0:** first version of the API.
