# Architecture

A map of the code for contributors. For what the market does, read the [admin guide](admin-guide.md) and the [player guide](player-guide.md) first: the code follows them closely, and their terms (catalog entry, class, price factor, tag rule, item edit) are the ones used here. If you're writing a separate mod that uses this one, read the [add-on guide](api.md) instead.

The market (`variable_economy_market`) is one of four Variable Economy mods, each in its own repository: [core](https://github.com/The-Azotus/variable-economy-core) (`variable_economy`), which the others require, and the add-ons market, [contracts](https://github.com/The-Azotus/variable-economy-contracts) and [exchange](https://github.com/The-Azotus/variable-economy-exchange). Each has its own jar, mod id, `mods.toml`, config file, lang file, network channel and command root, and they talk to each other only through their API packages.

## Building and testing

Java 21 and NeoForge 21.1 for Minecraft 1.21.1. Gradle's toolchain finds or downloads Java 21; the Gradle wrapper is in the repo. The build is in `buildSrc` (`variable-economy.mod.gradle`, the same file in each of the four repositories); `gradle.properties` says which mod this is (`module`, `mod_id`, `mod_name`, `mod_version`), the shared versions, and which core it builds against. The `neoforge.mods.toml` is filled in from `src/main/templates`: it requires core from `core_version` up to the next minor version, loaded before the market, and Create as optional.

**Core.** The market builds against core. `core_source` in `gradle.properties` says where core comes from:

- `curseforge` (the default): the released core that `core_curseforge_project` and `core_curseforge_file` name, through [CurseMaven](https://www.cursemaven.com/); for example, core 0.6.1 is `curse.maven:variable-economy-1818087:9710072`. This mod requires core 0.6.3 or later, so until that core's file is set here, build with `-Pcore_source=local`.
- `local`: `variableeconomy:core:<core_version>` from your local Maven, for building against a core that isn't released. Clone [variable-economy-core](https://github.com/The-Azotus/variable-economy-core) and run `gradlew publishToMavenLocal` there first. `-Pcore_source=local` sets it for one build.

**Tasks.**

- `gradlew build` compiles, runs `checkModuleBoundaries` (see [Modules and their boundaries](#modules-and-their-boundaries)), and builds the jar, the sources jar and the javadoc jar in `build/libs/`. The javadoc covers only `variableeconomy.market.api`, with doclint on, so a broken link or bad HTML in its javadoc fails the build.
- `gradlew runGameTestServer` runs the market's GameTests (128 today) with core loaded, then `checkGameTestLog`, which fails if the run loaded other mods of ours than core and the market, or logged an error, or a warning of ours, before its tests began (its report is `build/reports/gameTestLog.txt`). Every run loads the optional mods (Create, and core's FTB Teams, ProgressiveStages and KubeJS); `-PwithoutMods=all` (or a comma list of `create`, `ftbteams`, `progressivestages`, `kubejs`) leaves them out, with a test world of its own. Worlds are in `run-gametest/`, or `run-gametest-without-<mods>/`. The run's `config/` starts from `src/gametest/config/`: a ProgressiveStages stage locking the dragon egg, which test players never have, so that mod doesn't write its own example stages (whose locks would touch the tests) and the tests can check a real lock.
- `gradlew runClient` / `runServer`: a dev client or server with core and the market.
- `gradlew publishToMavenLocal` publishes `variableeconomy:market:<mod_version>` with its sources and javadoc jars. The contracts add-on builds against its API this way. The POM names no dependencies: an add-on declares core itself.

**The GameTests** are their own source set, `src/gametest`, and never ship in the jar. They use the small example catalog, not the built-in prices (the GameTest run sets `variableeconomy.skipDefaultPrices`), so run them with `gradlew runGameTestServer`. The test world persists between runs, so tests undo what they change (edits, tag rules, config values) and compare stock, prices and money relatively.

**Writing a test.** Put it in `variableeconomy.market.gametest`. Each class is marked `@GameTestHolder(MarketModule.MOD_ID)` and `@PrefixGameTestTemplate(false)` and imports `CoreTestSupport.*` and `MarketTestSupport.*`; each test is a static `@GameTest(template = EMPTY)` method that ends with `helper.succeed()`. The `empty` structure is shipped under the market's namespace, since NeoForge looks a test's template up in its holder's namespace. From the support classes:

- `new CoreFixture(helper)` gives a fresh player with an empty inventory and an empty bank account; `new MarketFixture(helper)` also puts the market's prices back to base.
- `withEntry(entry, body)` swaps one listing while `body` runs, then puts the catalog back; `withFixedPrice(item, body)` does the same with supply and demand turned off.
- `marketEventsDuring(body)` records the market's events while `body` runs.
- `assertScreenFollowsTheBlock`, `assertRecipeAndFacing`, `assertSolidAndDropsItself` and `assertOwnNamespace` check the block and the lang file.
- A test that writes files makes its own folder with `Files.createTempDirectory` and removes it with `deleteTree` in a `finally`.

## Packages

All code is under `variableeconomy.market`.

### The module's shape

The market has the same shape as every Variable Economy module:

- `MarketModule`: the mod's `@Mod` class, with `MOD_ID` and `LOGGER`. Its constructor registers the server config, installs the market API, then registers its block, menu, packets, built-in data packs, catalog, commands, server events and tick work. `MarketClient` (in `client`) is its client-only `@Mod` class: the screen, the client config and the price tooltips.
- `MarketConfig`: the server config file, `variable_economy_market-server.toml`. Its `install` fills `MarketSettings`, which the rest of the module reads. `MarketSettings` also decides whether a player may use the commands under `access_mode`.
- One block and one screen: `block` (the block and its registration), `menu` (the menu the screen runs in), `network` (`MarketPayloads` with every packet, `MarketNetwork` registering them, `MarketSync` with what each player is sent and when, and the request handlers) and `client` (the screen, the client's copy of the market's state, and the market's own copy of small helpers such as `PriceFormat`). No screen code is shared with other modules.
- `service` for the rules, `data` for what's saved, `command` for its commands, its event poster (`MarketEvents`) and its result enum (`MarketService.Result`).

| Package | What's in it |
| --- | --- |
| `api`, `api.event` | The market's API: `MarketApi`, `MarketPrices` and `MarketTrading`, their types (`MarketQuote`, `TradeResult`, `MarketResult`) and events (`MarketTradeEvent`, `CatalogReloadedEvent`). See [The add-on API](#the-add-on-api). |
| `catalog` | What's listed and how it's priced. `MarketCatalog` loads catalog files and classes from datapacks and `config/variable_economy/catalog/`, each file changing only the fields it sets on lower files' entries; `ItemClasses` the class files; `CatalogOverlay` lays tag rules and item edits on top; `CatalogEntry` is one listed item; `CatalogJson` writes an entry out as catalog JSON and lays one layer's fields over another's (the loader's merge, the overlay and `/market admin info` all go through it); `FieldSource` records where each value came from (for `/market admin info`). |
| `service` | `MarketService` (trades), `MarketApiService` (the market API, over `MarketService` and `PriceModel`), `PriceModel` (the pricing maths, shared with the client), `PriceSuggestion` (`/market admin suggest`), `MarketDiagnostics` (`/market admin diagnose`), `MarketEvents`, and two small helpers for the screen, `MarketListing` and `OrderMath`. |
| `data` | Saved with the world: `MarketStockData` (stock, price factors, history, money counts), `MarketOverrides` (tag rules and item edits), `TradeHistory`. `MarketBackup` writes backups as files outside the save. |
| `io` | Spreadsheet export and import (`CatalogExport`, `CatalogImport`, `CatalogColumns`, `Csv`). |
| `block`, `menu`, `network`, `client` | The Market Terminal (`MarketTerminalBlock`), `MarketMenu`, `MarketPayloads`/`MarketNetwork`/`MarketSync`, and `MarketScreen`. A button in the screen's title row switches its list between the catalog (`MarketTab`) and the player's recent trades (`HistoryTab`); both extend `ScreenTab`, and the screen hands events to the open one. `SellBox` is the Selling box with the player's inventory, drawn along the bottom, or as a third `ScreenTab` when the window is too small for that. `MarketMenu` holds the player's 36 main-inventory slots only so vanilla keeps the client's inventory in sync; they can't be clicked, and its `clicked` ignores every click. `ClientMarketState` holds what the server last sent, `SellPriceTooltip` adds prices to item tooltips, `ClientConfig` is the client config. |
| `command` | `/market` (`MarketCommand`), catalog admin (`AdminCatalogCommands`) and backups (`AdminBackupCommands`). Some write saved data directly: admin `setstock`, `clearstock` and `prune` change stock (`prune` also deletes factors, history and edits), `resetprice` resets price factors, catalog edits change `MarketOverrides`, and `restore` goes through `MarketBackup.apply`. |
| `compat` | `CreateRecipes` (Create's recipes for `suggest`) and `MachineRecipes` (other mods' machine recipes, read by reflection). |

The market registers its commands under `/market`, from its own `RegisterCommandsEvent` listener, with its admin commands under `/market admin`.

The market's ids are in its own namespace: its block, item, menu, packets, recipe, loot table, money reasons and lang keys (`screen.variable_economy_market.…`). Its catalog data is the one exception: item classes, the files an import writes, the `config/variable_economy/` folder and the world's `variable_economy/backups/` folder keep the `variable_economy` namespace (`MarketModule.DATA_NAMESPACE`). Its lang file is `assets/variable_economy_market/lang/en_us.json`; a key core also shows (such as `balance`) is in the market's file under its own namespace.

A refused trade returns `MarketService.Result`. Each value needs a line in the lang file, `screen.variable_economy_market.result.<value in lower case>`, or the screen shows the raw key.

Every package except `api` and `api.event` is marked `@ApiStatus.Internal` in its `package-info.java`: add-ons mustn't use it, and it may change in any release. A new package gets a `package-info.java` with the same annotation.

## Modules and their boundaries

The market is a separate mod that may be installed without the other add-ons, and core without the market, so it talks to them only through API packages:

- The market's main code uses only its own package and core's API, `variableeconomy.core.api` (with `.event`).
- Its `api` packages use only its own `api` and `core.api`.
- Its GameTests (`variableeconomy.market.gametest`) may also reach into core's internals, which core's jar holds: tests reach into what they test.
- The market never refers to the contracts or exchange add-ons. The contracts add-on reads the market only through `variableeconomy.market.api`, so a change there reaches contracts too.

Gradle's dependencies enforce some of this: the market compiles against core, and against nothing of the other add-ons. The task `checkModuleBoundaries` (in `buildSrc`) checks the rest: it scans every source file for references to `variableeconomy.…` and fails the build on any that isn't allowed, such as main code using core outside `core.api`. `gradlew check`, and so `gradlew build`, runs it.

When the market needs something from core that isn't in `core.api`, it's added to core's API (with javadoc and a changelog line) rather than reached for in core's internals.

## Rules every change follows

These keep money safe in every combination of installed mods. Each service class repeats the ones it relies on at its top.

- **Server thread only.** Every trade, payout and bank call runs on the server main thread; the bank isn't thread-safe. The packet handlers in `MarketNetwork` run on the main thread (the registrar's default; work from any other thread goes through `IPayloadContext.enqueueWork` or `server.execute`), and `MarketService` and `MarketApiService` check with a `requireServerThread`, which throws `IllegalStateException` from another thread. Core's API checks too.
- **Check everything, then change.** A transaction makes every check before its first change, and after the first change no step may fail.
  - Sale (`MarketService.trySell`): check the listing and the item gate, recompute the price, check the total fits, compare it with the client's quote, count matching items with `PlayerItems.count`, check the account (it exists, the player may deposit into it, the payment won't overflow it); then `PlayerItems.remove` exactly `qty`, update stock (unless unlimited), and `Money.pay`.
  - Purchase (`tryBuy`): check the listing and the item gate, recompute the price, check the total fits, compare the quote, check stock (unless unlimited), check space with `PlayerItems.room`, check the account; then `Money.charge` (stop if it fails: the charge is itself the funds check), update stock, and `PlayerItems.insert`.
  - A new check goes before the first change: above the `Nothing has changed yet` comment in `trySell`, and before the charge in `tryBuy`.
- **Money only through core's `Money`.** Every balance change goes through core's `Money` API (`CoreApi.money()`): `pay` creates money, `charge` destroys it and is all-or-nothing. Market sales pay and purchases charge, each naming a `Reason` in the market's namespace. The market never pays out or takes coins, and never touches the bank in any other way.
- **No profit from a round trip.** Buying N then selling N back must never pay: markup, sell fee and price impact together see to it. `MarketDiagnostics` and the startup and reload check (`MarketModule.warnAboutArbitrage`, through `PriceModel.arbitrageRisk`) warn about settings that would allow one. `MarketPrices.lowestBuyCost` gives add-ons the least buying an item could ever cost, so whatever pays out for items (the contracts add-on caps its payouts with it) can stay below it.
- **Totals in `long`.** Prices and every amount are `long`, worked out with exact arithmetic; a total, stock or balance that would overflow is refused as `TOO_LARGE` before anything changes (`MarketService.fitsBank`, `checkedTotal`, and the stock check in `trySell`).
- **Main inventory only, and never drop items.** The market takes and gives items through core's `PlayerItems`: only the hotbar and the 27 storage slots count, and `room` is checked before `insert`, so output never lands on the ground. `remove` and `insert` check again and throw, changing nothing, if a caller skipped the check. Only plain copies count (`CoreApi.isPlain`, through `CatalogEntry.matches`).
- **Respect the item gate.** No module lets a player trade an item they haven't unlocked: the market asks `CoreApi.itemGate().isLocked`, hides a locked item from that player, and answers a trade in it exactly as for an unlisted one (`NOT_LISTED`).
- **Optional mods stay behind a bridge.** `compat.CreateRecipes` is the only class that imports Create, and it touches Create only from a nested `Present` class, so the market loads without it. `MachineRecipes` reads other mods' recipes by reflection and imports none of them.
- **Coins are never market items.** Selling one would create money from nothing. `MarketCatalog` refuses to list a coin, through `CoreApi.isCoin`, which also knows Create: Numismatics' coins by ID, in case a pack still has that mod.
- **Events after the change.** An event is posted only once its whole change is complete, through `MarketEvents`, and is never cancellable. Balance changes are collected by core and posted at the end of the tick. See [The add-on API](#the-add-on-api).

## How a trade flows

1. The player presses Buy in the market screen. The client works out the total with `PriceModel` (the same code the server runs) and sends it as a quote in a `MarketPayloads.TradeRequest`.
2. `MarketNetwork`'s handler runs on the server thread and calls `MarketSync.handleTradeRequest`, which checks the player still has the market screen open and calls `MarketService.tryBuy`. The account is the player's active one (`CoreApi.accounts().active(player)`) unless the caller names another.
3. `MarketService` checks the player is alive, the listing and the item gate, recomputes the price and compares it with the quote (`MarketService.Expected.orBetter`: a total worse for the player than the quote is `PRICE_CHANGED`; an equal or better one goes ahead), checks stock, inventory space and the account, charges the account through `Money`, updates stock, inserts the items with `PlayerItems`, and posts a `MarketTradeEvent`.
4. `MarketSync` sends the result to the player, the new stock and price to everyone with the screen open, and the player's recent trades.
5. At the end of the tick, core posts one `BalanceChangedEvent` for the account. The market listens for it, so an open market screen shows the new balance, as do the other modules' screens.

The Selling box sends one `MarketPayloads.SellBoxRequest` with a line per item (at most 36). `MarketSync.sellBox` refuses the whole request if it names an item twice, then sells each line as its own `trySell` with its quote, so a line that fails changes nothing and the others still go ahead; the player gets a `SellBoxOutcome` with each line's result. The box itself is only a marking on the client: the items stay in the player's inventory until the sale.

What the screen knows about each item (whether it can be sold and bought, its stock, its `PriceModel.Inputs` and its factor) comes from `MarketPayloads.Row`, built in `MarketSync.row`. `MarketTab` uses it to enable Buy and Sell, and `SellPriceTooltip` for prices. A new rule that refuses a trade must reach the client through `Row` too, so the button greys out instead of failing on every click. A new pricing value is often easiest to add to `PriceModel.Inputs`, whose network codec (in `MarketPayloads`) is written by hand.

## The add-on API

The market's `api` packages, `variableeconomy.market.api` and `variableeconomy.market.api.event`, are what other mods, and the contracts add-on, compile against. [api.md](api.md) is their guide for add-on authors.

The API is interfaces and plain types; the services implement them. `MarketApiService.install` puts the market's services into `MarketApi` while the mod is constructed. The screen and commands use the same services, so the API adds only:

- a server-thread check on every method that reads prices or trades (`IllegalStateException` otherwise);
- for trades through the API: the client sync the network code would otherwise do (`MarketSync.broadcastRow` for the item, and the player's trades for an open screen). Balances reach open screens through core's `BalanceChangedEvent`.

The API has its own types, so internal ones can change without breaking add-ons. `MarketApiService.result` maps `MarketService.Result` to `MarketResult` case by case; `PRICE_CHANGED`, which needs a quote from a screen and so never comes from the API, maps to `BANK_UNAVAILABLE`. A new internal `Result` value needs its own case there, and usually a new `MarketResult` value (and a changelog line).

**Events.** `MarketEvents` puts the market's events on `NeoForge.EVENT_BUS`, logging and ignoring a listener that throws. They're posted where the change happens, not in the API, so actions from the screen and commands post them too:

| Event | Posted in |
| --- | --- |
| `MarketTradeEvent` | `MarketService.trySell` and `tryBuy`, after the payment or the items are inserted |
| `CatalogReloadedEvent` | the end of `MarketModule.catalogReloaded`, after clients are sent the new catalog |

The rule: **an event is posted only after its whole change is complete**, as the last step. A listener may call back into the mod, so it must never see half a transaction. A new event follows the same pattern, is never cancellable, and is posted only for actions that succeeded.

**Changing the API.** The `api` packages are `@ApiStatus.Experimental` until 1.0; they may change in a 0.x minor release, but never silently. For any change to a public type or method in them, or to when an event fires:

1. Update the javadoc. `gradlew javadoc` must pass (doclint is on).
2. Update [api.md](api.md): the section that describes it, and a line in its changelog under the version that ships it.
3. Raise `mod_version`: a minor version (0.6 to 0.7) for a change that can break add-ons, at least a patch version otherwise. Releases are tagged `v<version>`.

Keep internal types out of the API's signatures, and keep every check before the first change, as the services do.

## Adding a catalog field

The list of catalog fields is written out by hand in several places, and a field missing from one of them is dropped without an error. For a new field:

1. `CatalogEntry`: add it to the record, its `with…` copies, and `Raw` (`Raw.CODEC` and `resolve`). A record codec takes at most 16 fields and `Raw.CODEC` already has 15, so after one more, put new fields in their own `MapCodec`, parsed beside `Raw` in `MarketCatalog.decodeWithoutClass`.
2. `CatalogJson.toJson`: write the field out.
3. `ItemClasses.FIELDS`, so classes can set it.
4. `CatalogColumns.OPTIONAL_FIELDS` (the import and the export's header) and the `values` map in `CatalogExport.row` (the export's cells).
5. `AdminCatalogCommands`: `FIELDS` (for `set`, with the same range as the codec), `INFO_FIELDS`, and `describeField`, which needs the value and label to show when nothing sets the field. A field with a server-wide default gets it from `MarketSettings` (read in `PriceModel.inputs`) and goes in the `"server config"` case; the key itself goes in `MarketConfig`, handed to `MarketSettings` in `MarketConfig.install`. Otherwise its built-in default is the `optionalFieldOf` default in the codec.
6. `MarketCatalog.PAIRED_FIELDS`, only if a rule compares it with another field (like `min_buy_markup` ≤ `markup`).
7. The rule itself goes in the service, following the order above, and anything the screen needs goes in `MarketPayloads.Row` or `PriceModel.Inputs`. If another add-on needs it, add it to the market API (as `MarketQuote.itemClass` is, for contracts).
8. The GameTests that call the `CatalogEntry` constructor directly (`grep "new CatalogEntry("`).
9. The [Catalog fields](admin-guide.md#catalog-fields) and [Spreadsheet format](admin-guide.md#spreadsheet-format) tables in the admin guide.

## Design notes

- **A separate mod.** The market doesn't fork or copy code from Create: Stock Market (AGPL-3.0); Create itself is optional, read only for `suggest`.
- **All payments through a bank account.** Sales and purchases pay into and charge an account through core's `Money`; coins only move in and out of the bank at core's Bank. Every price and amount is in bits.
- **One server-wide market.** Every player sees the same prices and stock. There are no per-player limits: price impact and classes do the limiting, and rare classes swing hard and recover slowly so dumping a pile of something valuable doesn't pay.
- **No difficulty presets.** Admins tune the market through classes, catalog fields and the server config.
- **Layers, not copies.** Catalog files, classes, tag rules and item edits each change only the fields they set, so a pack's or an admin's change never has to repeat what the built-in prices say, and `/market admin info` can show where every value came from.
