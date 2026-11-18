# Admin guide

This guide is for server admins running the market add-on of Variable Economy. It covers:

- how players reach the market
- how to price items, from a spreadsheet, catalog files or in-game commands
- item classes, which give groups of items shared pricing behaviour
- how prices move, every catalog field and config option, and every `/market` command

For what players see, read the [player guide](player-guide.md). For mods that build on the market, and for connecting other mods to it with data files, commands or Java, read the [add-on guide](api.md). The bank, coins, accounts, `/pay`, the leaderboard, the audit log, money-supply stats and the KubeJS bindings are Variable Economy's core: see [core's admin guide](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/admin-guide.md).

## Installing

The market (`variable_economy_market-<version>.jar`, mod id `variable_economy_market`) is an add-on: it needs [Variable Economy core](https://github.com/The-Azotus/variable-economy-core) (`variable_economy`) 0.6.1 or a later 0.6.x, so update them together. Install both on the server and on every client. It works with any of the other add-ons, [Contracts](https://github.com/The-Azotus/variable-economy-contracts) and [Exchange](https://github.com/The-Azotus/variable-economy-exchange), or with neither.

The market adds the Market Terminal, catalogs and prices, price tooltips and `/market`. Its settings are in its own config file, `config/variable_economy_market-server.toml` ([Server config](#server-config)).

## How the market works

The market is owned by the server. Players sell items to it and buy items from it, and all money moves through their bank accounts (core's bank: see [core's admin guide](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/admin-guide.md)). Every player sees the same prices and the same stock. The market never pays out or takes coins.

- **Stock.** Items players sell go into one shared stock, and players buy from that stock. Items marked *unlimited* are always in stock instead.
- **Prices.** Each item has a base price. The market pays a little less than the going rate and charges a markup on top.
- **Supply and demand.** Selling an item pushes its price down and buying pushes it up. Prices drift back toward base over time.

**Where an item's settings come from**, weakest to strongest:

1. **Built-in defaults and the server config.** The fixed defaults are `markup` 0.5, `sell_fee` 0, `can_sell` and `can_buy` true, `start_stock` 0, `unlimited_stock` false, `dynamic` true, and `min_buy_markup` equal to `markup`. Supply and demand (`sell_impact`, `buy_impact`, `recovery`, `floor`, `ceiling`) take their defaults from the market's server config, `config/variable_economy_market-server.toml`. `info` shows these as `[default]`, `[same as markup]` or `[server config]`.
2. **The item's [class](#item-classes)**, such as `rare_resource`.
3. **The catalog file's `defaults` block.**
4. **The item's catalog entry**: JSON in a datapack or the [config folder](#the-config-folder-prices-for-every-world), including the built-in prices and what the spreadsheet import writes. When several files list one item, each higher file changes only the fields it sets (see [Changing a built-in price](#changing-a-built-in-price)).
5. **[Tag rules](#tag-rules-pricing-many-items-at-once)**: in-game rules such as "every log costs 4 bits". Where rules overlap, later ones win.
6. **[Item edits](#editing-prices-in-game)**: in-game changes to one item.

Each layer only changes the fields it sets; everything else comes from the layers below it. So pricing one item by hand always beats a tag rule, and a tag rule that only sets a price leaves the item's class alone. Tag rules and item edits are saved with the world. Like catalog files, they never touch stock or price factors: a new `base_price` applies at once, at the item's current factor. `/market admin info <item>` shows which layer each of an item's values came from.

**Coins can never be listed in the market**: core's own, since selling one would create money from nothing, and Create: Numismatics' coins, should your pack have that mod, since selling them would turn its money into bits. A catalog file, spreadsheet row, item edit or tag rule that lists one is refused with a warning.

## Letting players in

The **Market Terminal** block opens the market's screen: the catalog, the selected item's chart and prices, Buy and Sell, and the player's recent trades. `/market` opens the same screen from anywhere. A screen opened from the block closes if the player walks more than about 8 blocks away or the block is broken.

Choose whether players may use the commands with `access_mode` in `config/variable_economy_market-server.toml`. Core and the other add-ons each have their own `access_mode` in their own config file, so you can, say, keep `/bank` and make the market block-only.

| `access_mode` | Players | Operators and console |
| --- | --- | --- |
| `COMMAND_AND_BLOCK` (default) | The Market Terminal and every player command | Everything |
| `BLOCK_ONLY` | The Market Terminal only. The bare `/market` tells them to find a Market Terminal; the other player commands (`/market sell`, `buy`, `stock`, `price`) are hidden. | Everything |

"Operators" here means permission level 2. The setting takes effect as soon as you save the file, with no restart. A player who opened the screen by command is closed out if you switch to `BLOCK_ONLY` while they have it open.

### The Market Terminal

The Market Terminal is 8 Bits around a crafting table:

```
Bit  Bit            Bit
Bit  Crafting table Bit
Bit  Bit            Bit
```

| Block | ID | Recipe |
| --- | --- | --- |
| Market Terminal | `variable_economy_market:market_terminal` | `variable_economy_market:market_terminal_from_bits` |

To change or remove the recipe, override it in a datapack: the recipe `variable_economy_market:market_terminal_from_bits` is the file `data/variable_economy_market/recipe/market_terminal_from_bits.json`. To hand the block out yourself, use `/give`, such as `/give @p variable_economy_market:market_terminal`. It faces whoever placed it, breaks by hand and always drops itself, faster with an axe.

### Locked items: ProgressiveStages

With [ProgressiveStages](https://modrinth.com/mod/progressivestages) 3.1.1 or a later 3.x installed, an item locked for a player is hidden from that player in the market. Nothing needs setting up: the market uses the item locks you already defined in ProgressiveStages, through core.

- **Hidden means hidden.** The item isn't in the player's list or search, its market price isn't shown in their tooltips, and `/market price` and `/market stock` act as if it isn't listed.
- **Enforced by the server.** Buying or selling a locked item is refused as *"That item is not on the market"*, the same answer as for an unlisted item, so the reply doesn't reveal it.
- **Prices are shared.** Everyone trades at the same price and from the same stock. A locked item still moves in price when others trade it.
- **Updates live.** When a player gains or loses a stage, their market updates on the next tick, even with the screen open. Team stages update every member.
- **Locked means a missing stage.** Creative mode, ProgressiveStages' per-category enforcement switches and structure-only locks don't change what the market shows.
- **Console and admins.** `/market admin` commands and the server console see every item.

Without ProgressiveStages, nothing changes. Other mods can hide items the same way through core's item locks (see the [add-on guide](api.md)). An item is hidden when ProgressiveStages or any add-on locks it.

## Pricing items

There are three ways to price items, and you can mix them:

| Way | Best for |
| --- | --- |
| [Spreadsheet export and import](#pricing-from-a-spreadsheet) | Pricing a whole modpack at once. |
| [Catalog files](#catalog-files) | Keeping prices in files you version or share: in the [config folder](#the-config-folder-prices-for-every-world) for every world, or in one world's datapack. |
| [In-game commands](#editing-prices-in-game) | Quick changes, and whole tags such as "all logs". |

**Built-in prices.** The mod ships prices for vanilla and a set of popular mods in a datapack that is **on** by default. It has one price file per mod, and each file loads only if its mod is installed, so it works in any modpack. It sits below the config folder and every other datapack, so your own catalog files, imports and edits always override it. To run your own prices from scratch, turn it off with `/datapack disable "mod/variable_economy_market:default_prices"`.

The built-in prices cover raw materials: ores, logs, crops, mob drops and loot. Run [`/market admin suggest`](#suggesting-prices-from-recipes) to price the crafted items in your pack from them. The scale is roughly: cobblestone 1 bit, a log 8, an iron ingot 32, a diamond 512, a nether star 16,384. To change some of these prices without turning the pack off, see [Changing a built-in price](#changing-a-built-in-price).

The market buys ore-stage items (raw ores and raw ore blocks, crushed ores, Mekanism crystals, shards, clumps and dirty dusts, metal dusts, Spectrum pure metals) but doesn't sell them (`can_buy: false`). Ore processing in mods such as Mekanism turns one raw ore into up to three ingots, so selling raw ore would let players buy it, process it and sell the ingots at a profit. Players who want metal buy ingots. Ores themselves (the blocks) aren't listed: placing one and mining it with Fortune multiplies it.

The mod also includes a small **example catalog**: cobblestone and oak log (unlimited stock), iron, diamond and nether star, at the built-in prices but with default settings instead of classes. It's a datapack that is **off** until you enable it:

- `/datapack enable "mod/variable_economy_market:example_catalog"` turns it on; `/datapack disable "mod/variable_economy_market:example_catalog"` turns it off.
- Its file, `data/variable_economy/market/example_catalog.json`, is a handy starting point for your own catalog.

### Pricing from a spreadsheet

1. Run `/market admin export` from the server console or as a level-4 operator. It writes a folder like `config/variable_economy/exports/2026-10-23_09-30-00/` containing:
   - `items.csv`: every item in the game, one row each. Listed items have the values their catalog files, tag rules and in-game edits set. A value that comes from the item's [class](#item-classes) or a built-in default is left blank, so importing the sheet later doesn't pin it: change the `class` column and the class's values follow.
   - `templates/<modid>.json`: one catalog file per mod, listing only unlisted items, each at `base_price: 0`.
2. Open `items.csv` in a spreadsheet. Fill in `base_price` for anything you want on the market, and change any other column you like. Leave `base_price` blank for items you don't want.
3. Save it somewhere inside `config/variable_economy/`. Either CSV encoding works, UTF-8 or Excel's default.
4. Check it: `/market admin import "exports/2026-10-23_09-30-00/items.csv" preview`. This writes nothing. It reports how many entries would be written, how many rows were skipped and why any were rejected.
5. Import it: run the same command without `preview`. To import into the [config folder](#the-config-folder-prices-for-every-world) instead, so the prices apply to every world, add `config`: `/market admin import "exports/2026-10-23_09-30-00/items.csv" config` (and `config preview` to check it first).

The import writes one file per mod, `imported_<modid>.json`. By default it goes into a world datapack at `<world>/datapacks/variable_economy_catalog/`, which the first import creates and enables. With `config`, it goes into `config/variable_economy/catalog/market/`. An import rewrites each mod's `imported_<modid>.json` from that mod's rows in this CSV only, so items an earlier import listed that this CSV leaves out go back to what lower files say (usually the built-in prices), or leave the market if nothing else lists them; the old file is kept as `<name>.json.bak`. Files for mods with no imported rows (none, or all blank or rejected) are left alone. The catalog reloads straight away either way. Importing never changes stock or price factors: a new `base_price` applies at once, at the item's current factor.

Each imported row **replaces** the item's entry from lower files, such as the built-in prices, rather than changing only some fields: the spreadsheet showed every field the entry sets, so a cell you cleared means "not set". The files are marked with `"replace": true` for this (see [Catalog files](#catalog-files)).

Put the path in quotes, since it contains slashes. To export one mod only, use `/market admin export <modid>`.

Items whose imported field is also set by an in-game edit or a [tag rule](#tag-rules-pricing-many-items-at-once) keep that value, since both beat catalog files. The import lists items with edits, but not items a tag rule changes; `/market admin info <item>` shows those. Drop an edit with `/market admin revert <item>`. A tag rule can't exempt one item: give the item its own edit, or change the rule (`/market admin tag <tag> unset <field>` or `revert`).

The import also lists items a higher catalog file still changes. For example, an import into the config folder doesn't override an earlier import into the world's datapack, which sits above the config folder. `/market admin info <item>` shows which file it is; delete or edit that file to let the imported value through.

The [spreadsheet format](#spreadsheet-format) section has the details on columns and what the importer accepts.

### Suggesting prices from recipes

In a big modpack, you only need to price the raw materials (ores, logs, crops, mob drops, loot) yourself. The market can work out the rest from the game's recipes:

1. Price the raw materials, by any of the ways above.
2. Run `/market admin suggest` (or `/market admin suggest <modid>` for one mod) from the console or as a level-4 operator. It writes a folder like `config/variable_economy/exports/2026-10-23_09-30-00/` (its reply gives the name) holding `suggested.csv`, `craft_profits.txt`, `chain_profits.txt`, `chain_details.txt` and `skipped_recipes.txt`. Nothing on the market changes.
3. Review the file in a spreadsheet, then import it like any other CSV, for example `/market admin import "exports/2026-10-23_09-30-00/suggested.csv" config`. The import leaves out the `catalog` rows (items already listed) that you haven't changed, as described below the column table.

[Pricing a whole modpack](#pricing-a-whole-modpack) below puts these steps, and the checks after them, in order.

For each unlisted item, `suggest` finds its cheapest recipe, adds up the cheapest priced ingredients, divides by how many the recipe makes, and adds 10% for processing. It repeats until nothing gets cheaper, so long crafting chains are priced from the bottom up. It reads every recipe type that lists its items, including modded machines such as Create's presses and mills, and lists any recipe type it couldn't use, with the reason, in `skipped_recipes.txt`. Items made only by those recipe types show as **No recipe makes it**: price them by hand if they should be listed. The craft-profit checks can't see those recipes either, so look over any skipped type that makes valuable items. Each suggestion also gets a class: `rare_processed` if rare ingredients make up much of its cost, otherwise `common_processed`. [How suggest works](#how-suggest-works) below has the details, for when a price looks wrong.

Prices are rounded down and the 10% is less than any built-in markup, so buying the ingredients from the market, crafting and selling the result never pays. If your catalog gives any item a markup (or `min_buy_markup`) below 10%, `suggest` uses that smaller figure instead and says so in its reply.

Items from Chipped and Rechiseled (decorative block variants, thousands each) are left out of the market: `suggest` never prices them and leaves them out of `suggested.csv`. You can still list one by hand.

`suggested.csv` has the same columns as `items.csv`, with the suggested `base_price` and `class` filled in, plus these columns for reference (the importer reads only `price_source`):

| Column | Meaning |
| --- | --- |
| `price_source` | `catalog` (already listed; the row holds its current values), `suggested`, or blank (no price could be worked out) |
| `exact_cost` | The worked-out cost before rounding down |
| `recipe`, `recipe_type` | The recipe the price came from |
| `depth` | How many crafting steps from a priced item |
| `note` | Anything worth checking; see below |

**`catalog` rows.** An import of `suggested.csv` leaves out each `catalog` row that still matches the item's current values, and says how many it left out. Importing them would only copy their values (usually the built-in prices) into the import file, where they'd stay fixed: later updates to the built-in prices wouldn't reach those items. Two kinds of `catalog` row are imported anyway:

- a row you changed in the sheet, such as a new `base_price`: it's imported like a `suggested` row, replacing the item's entry;
- a row with any value from the file the import rewrites (`imported_<modid>.json` in the same place, the config folder or the world's datapack), such as a suggestion an earlier import wrote. Leaving it out would drop the item from that file.

Only `suggested.csv` has this column, so an `items.csv` import is unaffected: every priced row in it is imported.

`/market admin diagnose` also reads the recipes, and warns about any listed item whose ingredients can be bought from the market for less than the item sells for. A player could buy the ingredients, craft and sell at a profit forever. The fix is to raise the ingredients' prices, lower the item's, or set `can_sell` false on it. `suggest` writes the same list to `craft_profits.txt` (listed items only: the suggestions are checked in `chain_profits.txt`, and show up in `craft_profits.txt` once imported), in three groups:

- **Price gaps** (warnings): fix them as above.
- **Check by hand** (notes): recipe types that may take fluids, extra items or ingredient amounts the check can't see, such as Spectrum's fusion shrine. These are often false alarms.
- **Recipes that make more of their own ingredient** (notes), such as a machine turning 1 cobblestone into 2: no price can stop these, since any price makes 2 worth more than 1. They're generators, and sell impact limits what they earn.

`suggest` also writes `chain_profits.txt`, a check that follows several recipes in a row, as if every suggestion had been imported. It lists items that can be bought and processed into more than they cost, with the chain of recipes, for example a stone that crushes into ore which smelts into an ingot. The first item in a chain is usually priced too low for what it processes into: raise it, or set `can_buy` false so the market doesn't sell it. Listed items come first; suggested items often follow from them and clear when the listed item is fixed. Chains marked `[check]` include a recipe type that may hide inputs; complete chains are looked for first, so a bigger `[check]` chain can't hide a real one. `chain_details.txt` lists every step, with its amounts, of the suggestions raised furthest above their recipe cost and of the most profitable chains; a recipe read with the wrong amounts shows up there as a step that makes more than it takes.

Notes to look out for:

- **Worth under 1 bit:** prices are whole bits, so these are left blank. Most such items (sticks, buttons) don't need to be on the market.
- **This recipe type may leave out fluids or ingredient amounts:** some machines take fluids, or keep amounts where the market can't see them, so the cost may be too low.
- **Converts back into …:** the item and its ingredient turn into each other, like iron blocks and ingots. If both are listed, a player whose ingots have dropped in price can craft blocks and sell those at full price instead. List only one form, or set `can_sell` false on the other.
- **Price kept falling through a recipe loop:** a set of recipes makes the item out of less than itself, so the suggestion is too low. Price it by hand. This should be rare: an item is never priced from ingredients that were themselves priced from it, so a loop such as one plank sawn into a door and the door sawn back into two planks is cut where it closes. The loop itself still works in game, so consider removing one of its recipes.
- **No recipe makes it** and **every recipe for it needs an unpriced item:** price these by hand if they should be listed, then run `suggest` again.

**Re-running after a fix.** `suggest` never reprices an item that's already listed, and imported suggestions count as listed. To reprice them from new ingredient prices, delete the `imported_<modid>.json` files the import wrote, run `/market admin reload`, then run `suggest` and import again.

#### Pricing a whole modpack

The whole process, from the built-in prices to a finished pack that applies to every world:

1. Put your own prices for raw materials, and any fixes, in one file in the [config folder](#the-config-folder-prices-for-every-world), such as `config/variable_economy/catalog/market/pack_prices.json`. Files there apply in name order, so pick a name that sorts after `imported_`: then its entries change what an import writes, instead of being replaced by it. Run `/market admin reload`.
2. Run `/market admin suggest`.
3. In `suggested.csv`, the rows noted **No recipe makes it** or **every recipe for it needs an unpriced item** are what's left to price. Add the ones you want listed to `pack_prices.json`.
4. Fix the price gaps in `craft_profits.txt` and the first item of each chain in `chain_profits.txt`, also in `pack_prices.json`.
5. Reload and repeat steps 2–4 until nothing new turns up. Each run prices from the current files, so fixes carry down the chains.
6. Import the last `suggested.csv`: run `/market admin import "exports/<folder>/suggested.csv" config preview`, then the same command without `preview`.
7. Run `/market admin diagnose` to check the result. Put any later fix in `pack_prices.json`; to reprice the suggestions, see **Re-running after a fix** above.

#### How suggest works

You don't need this to use `suggest`; it explains the cases where a suggested price may surprise you.

**Costing a recipe.** For each ingredient slot, `suggest` takes the cheapest priced item that fits. It subtracts anything the recipe gives back (the empty bucket from a milk bucket), adds fuel for furnace recipes, and divides by how many the recipe makes. The 10% for processing is added once, to the finished cost, however many steps the chain has. Added at every step it would outgrow the markup, and buying an early item, processing it many times and selling the result would profit.

**Raising to what an item makes.** Each suggestion is then raised, if needed, to what the item can be processed into, divided by its markup. A cheap route to an item (an ore-processing chain that multiplies, say) would otherwise let players buy it for less than what it crafts into, such as 9 nuggets for less than an ingot. Such items are noted in `suggested.csv` with the item they process into.

- A raise stops at what the item costs to make from bought ingredients, or it would sell for more than that (coarse dirt made for 1.10 raised to 2). An item worth more than that profits at any price, so it keeps its price and shows up in `chain_profits.txt`.
- Raised prices then count as the least those items cost, and everything is priced and raised once more. So steel made from iron dust is priced from the dust's raised price, not its cheap ore-processing cost.
- The raise follows at most 3 recipes, enough for the conversions it's for; `chain_profits.txt` follows at most 6. A pack has many accepted generators (quarries, ProjectE's philosopher's stone, ore rebuilt from dust), and a long enough chain through them multiplies without bound.

**Small and reusable inputs.** Items worth under 1 bit stay unpriced: rounded up to 1, they would sell for more than they cost to make. An input a machine uses up only some of the time (a quarry drill) counts one run's output, not the many runs it lasts. A tool the crafting grid hands back (a knife) isn't credited with what it helps make.

**Modded machines.**

- Create's machine recipes are read closely: chance outputs count at their average, and a sequenced assembly counts its starting item plus what every step adds, once per loop, with its output at the recipe's success chance.
- Modern Industrialization, Immersive Engineering, Mekanism and Oritech keep their machine recipes' inputs, amounts or outputs outside the usual place, so `suggest` reads them directly. It leaves out catalysts the machine never uses up (such as Immersive Engineering's press molds).
- Only a machine's main output is priced. Byproducts aren't subtracted, which errs towards a higher price.
- Energy and machine fuel aren't priced, and recipes that use up fluids or Mekanism chemicals are marked for review.

**Generators.** Some recipes make things out of almost nothing, so they're treated as generators and only price items nothing else makes: Oritech's particle collider (its real cost is energy on an endgame scale; it turns netherrack into netherite scrap), Modern Industrialization's quarry (it mines blocks rather than processing its drill), Mekanism's sawmill (it makes more wood than it takes) and Create's haunting (sand into soul sand for free).

**Recipes that may hide inputs** (see the craft-profit groups above) only price items that no fully listed recipe makes, so a ritual that leaves its main ingredient out can't undercut the ordinary recipe. What they price can still make a fully listed recipe that uses it cheaper.

**Class.** A suggestion is `rare_processed` if its rare ingredients are worth at least half its cost, or at least as much as the cheapest rare item in the catalog; otherwise `common_processed`. A trace of rare material deep in a long chain doesn't make a cheap item rare.

### Catalog files

Catalog files live in any datapack at `data/<namespace>/market/<name>.json`, or in the [config folder](#the-config-folder-prices-for-every-world):

```json
{
  "defaults": { "markup": 0.5, "sell_fee": 0.0 },
  "entries": [
    { "item": "minecraft:cobblestone", "base_price": 1,     "unlimited_stock": true },
    { "item": "minecraft:iron_ingot",  "base_price": 32 },
    { "item": "minecraft:diamond",     "base_price": 512,   "markup": 0.25 },
    { "item": "minecraft:nether_star", "base_price": 16384, "can_sell": false }
  ]
}
```

- `entries` is required: the items to list.
- `defaults` is optional. Its fields apply to every entry in that file unless the entry sets the field itself. It can't contain `item`. Because `defaults` beats the class, a `defaults` that sets `markup` or `sell_fee`, as in the example above, overrides those values for every entry in the file that names a class; leave such fields out of `defaults` in a file that uses classes.
- `required_mods` is optional: a list of mod IDs, such as `["create"]`. If any of them isn't installed, the whole file is skipped without a warning. Use it for a price file that belongs to one mod.
- **Several files for one item:** files are applied from lowest priority to highest: the built-in prices, then the mods' own data, then the config folder, then every other datapack (the world's datapacks, the example catalog if enabled, and packs other mods add) in their `/datapack list` order. Within one datapack or the config folder, files apply in order of their path, compared as text, and the log warns about the repeat. Each later file changes **only the fields it sets** and keeps the rest; see [Changing a built-in price](#changing-a-built-in-price).
- **How a later file's fields combine with a lower one's:** each file's `defaults` are first filled into its own entries, and the result is laid over the lower files. So a later file's `defaults` beat a lower file's entry for the same field. A class never beats a value a file set: giving an item a new `class` in your file changes only the fields no file sets.
- `"replace": true` on an entry makes it start over instead: every field lower files set, `class` included, is dropped, so repeat any you want to keep; fields this entry doesn't set come from its own class, the server config or the built-in defaults. Put it in `defaults` to apply it to every entry in the file. The spreadsheet import writes its files this way.
- **Reloading:** after editing files, run `/market admin reload` to reload just the market, or `/reload` to reload all datapacks (use `/reload` after adding a new datapack). Players get the new prices at once: an open market screen and item tooltips update without reopening or rejoining.
- **Bad entries are skipped, not fatal.** An unknown item, an out-of-range value or a missing field skips only that entry, with a log warning naming the file and entry. (An entry that changes an item a lower file lists may leave out `base_price`.) If a lower file already lists the item, its entry stays as it was. Entries with `base_price` 0 or below are skipped with one summary warning, so a half-filled template is safe to load.
- **A file that can't be read as JSON** (a missing brace or quote, a trailing comma inside an entry) is skipped whole, and lower files' entries for its items stay. A trailing comma after the last entry only adds an empty entry, skipped with `entry N: must be an object`. The log and `/market admin diagnose` show it as an error, `Market file … could not be read: …` (`Catalog file …` for the config folder), with the line and column of the mistake.

You can also start from a file in `templates/` from an export.

### The config folder: prices for every world

Datapacks belong to one world. Catalog files in `config/variable_economy/catalog/` apply to **every world** on the server or in the modpack, which is the easy way for a modpack to ship its own prices:

```
config/variable_economy/catalog/
  market/            catalog files, such as my_prices.json (subfolders are fine)
  market_classes/    item classes, such as rare_resource.json
```

- The files are the same format as in a datapack; there's no `pack.mcmeta` or `data/` folder to set up. The mod creates both folders on first start.
- **Filling it:** write files by hand ([Catalog files](#catalog-files) has the format, and [Changing a built-in price](#changing-a-built-in-price) a one-line example), or import a spreadsheet straight into it with `/market admin import "<path>" config`. The import writes `market/imported_<modid>.json`, one per mod; see [Pricing from a spreadsheet](#pricing-from-a-spreadsheet) for the steps and the [spreadsheet format](#spreadsheet-format) for the columns.
- **Priority:** `/datapack list` shows packs lowest priority first, and a pack listed later overrides one listed earlier. The folder sits just above the built-in packs: the built-in prices and the mods' own data (`vanilla`, `mod_data`, `mod/<modid>`). Other datapacks are normally above it, whichever mod adds them: the world's datapacks, the example catalog, and packs from mods such as OpenLoader, Paxi or KubeJS. So the folder sets the pack-wide prices, and any datapack can still change them. A pack that sits below a built-in pack is below the folder too: a world datapack enabled with `/datapack enable … first`, a built-in pack disabled and enabled again with `… last`, or another mod's optional pack that it adds at the bottom of the list.
- **Order inside the folder:** files apply in name order, so a hand-written `my_prices.json` changes what `imported_<modid>.json` sets, while a file that sorts before `imported_` is replaced by the import without a word. The import lists the items a later file such as `my_prices.json` changes, among those "changed by a higher catalog file".
- File names may use only lowercase letters, digits, `_`, `-` and `.`; a file with another name is skipped with a warning. In `/market admin info`, a file `market/my_prices.json` shows as `file config:my_prices`.
- **Shipping it in a modpack:** include `config/variable_economy/catalog/` in the pack's `config/` folder. The mod never changes your files there; only `import … config` writes to it (`market/imported_<modid>.json`, keeping the old one as `.bak`), so don't name your own files `imported_*`.
- A class file takes the mod's namespace: `market_classes/rare_resource.json` replaces the built-in `rare_resource` class, and `market_classes/gems.json` adds a class called `gems`.
- The folder is read whenever the catalog loads: at start, `/reload` and `/market admin reload`. An empty folder does nothing. Only the server reads it, so players don't need it.

The market's data keeps the `variable_economy` namespace of Variable Economy as a whole, not the add-on's own: this folder, the built-in prices and classes (`data/variable_economy/…`) and the files an import writes.

### Changing a built-in price

An entry in your own catalog file **changes only the fields it sets**, and the built-in entry keeps the rest. To raise the diamond's price and leave its class, markup and everything else alone, this is the whole file:

```json
{ "entries": [ { "item": "minecraft:diamond", "base_price": 600 } ] }
```

Put it in the [config folder](#the-config-folder-prices-for-every-world) for every world, or in a world's datapack. Run `/market admin info <item>` first to see whether the built-in prices list the item, and with which class and values. An entry for an item they don't list still needs a `base_price`. To drop a built-in field rather than change it, give the entry `"replace": true` and every field you want.

`/market admin info <item>` shows where each value came from. Built-in entries show as `file variable_economy:<modid>` (`variable_economy:minecraft` for vanilla). With the file above saved as `market/my_prices.json` in the config folder, `info minecraft:diamond` shows `base_price = 600 [file config:my_prices; overrides 512 from file variable_economy:minecraft]`.

**Don't copy a built-in file to the same path** in a datapack (`data/variable_economy/market/<modid>.json`). Like any datapack file, the copy replaces the whole built-in file instead of merging, so every item it leaves out loses its built-in price. Use another file name, or the config folder. The built-in files are in the mod's jar (open it as a zip) under `default_prices/data/variable_economy/market/`, and `items.csv` from [`/market admin export`](#pricing-from-a-spreadsheet) shows their values.

You can also edit in game, which works the same way: `/market admin set minecraft:diamond markup 0.8`, or for a whole tag, `/market admin tag c:gems set markup 0.8`. See [Editing prices in game](#editing-prices-in-game). Edits and tag rules are saved with the world, though, so for every world put the change in a config-folder file instead, and `/market admin revert <item>` (or `tag <tag> revert`) any edit, so it doesn't hide the file's value.

### Item classes

A class is a named set of catalog settings shared by a group of items, so similar items behave alike without repeating the same numbers on every entry. Give an entry a class with `"class"`:

```json
{
  "entries": [
    { "item": "minecraft:cobblestone", "base_price": 1,     "class": "common_resource" },
    { "item": "minecraft:diamond",     "base_price": 512,   "class": "rare_resource" },
    { "item": "minecraft:nether_star", "base_price": 16384, "class": "unique", "can_sell": false }
  ]
}
```

A class fills in only the fields that nothing stronger sets (see [where an item's settings come from](#how-the-market-works)): it beats the server config, and the file's `defaults`, the entry itself, tag rules and in-game edits all beat it. In the example, the nether star keeps its own `can_sell: false` and takes everything else from `unique`. `class` itself can be set at any of those levels, for example once in a file's `defaults`, or for a whole tag with `/market admin tag <tag> set class rare_resource`.

Classes are the main protection against someone finding a way to get a lot of a valuable item. Rare classes move their price a lot per unit sold and recover slowly, so dumping a big pile pays far less than its base price. There are no per-player limits: the price does the limiting.

The mod ships these classes. Every built-in price entry names one, and sets nothing else except `base_price` and sometimes `can_buy` or `can_sell`; the built-in files have no `defaults` block. So the class decides everything else: diamonds are `rare_resource`, cobblestone is `common_resource`, and `/market admin info <item>` shows any item's class. Every class passes the round-trip check (see [Keeping it fair](#supply-and-demand)); change them to suit how fast your server's economy should move:

| Class | For | Stock | Markup | Impact per unit | Units to drop 10% | Recovery per step | Half recovered after | Floor – ceiling |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `common_resource` | Raw materials gathered in bulk: stone, logs, sand | unlimited | 0.5 | 0.0002 | ~530 | 0.1 | ~7 min | 0.3 – 2 |
| `common_processed` | Common items made from those: planks, glass, ingots | normal | 0.4 | 0.0005 | ~210 | 0.08 | ~8 min | 0.25 – 3 |
| `farmable` | Crops and mob drops that farms produce endlessly | unlimited | 0.5 | 0.001 | ~105 | 0.05 | ~14 min | 0.1 – 2 |
| `rare_resource` | Scarce raw materials: diamonds, ancient debris | normal | 1.0 | 0.005 | ~21 | 0.02 | ~35 min | 0.15 – 5 |
| `rare_processed` | Valuable made items: netherite, precision mechanisms | normal | 1.0 | 0.003 | ~35 | 0.03 | ~23 min | 0.15 – 5 |
| `rare_loot` | Found, not made: music discs, enchanted books, totems | normal | 1.5 | 0.02 | ~5 | 0.01 | ~70 min | 0.1 – 8 |
| `unique` | One-of-a-kind: nether stars, dragon eggs | normal | 2.0 | 0.08 | 1–2 | 0.005 | ~2.3 h | 0.1 – 6 |
| *no class* | Server config defaults | normal | 0.5 | 0.0005 | ~210 | 0.05 | ~14 min | 0.2 – 5 |

"Units to drop 10%" is how many units sold in a row take the price down by a tenth. "Half recovered after" is how long, at the default 1-minute step, a price takes to win back half of any drop.

With the [contracts add-on](https://github.com/The-Azotus/variable-economy-contracts) installed, its built-in contract definitions ask for items by class; see its admin guide.

"Impact per unit" is both `sell_impact` and `buy_impact`. "Unlimited" stock is `"unlimited_stock": true`; "normal" means the class doesn't set it. The table is every value the built-in classes set: none sets `sell_fee` or `min_buy_markup`, so those come from the entry or the defaults. For a sense of scale, at the default 1-minute recovery step:

- Selling 1,000 cobblestone lowers its sell price to about 82%, and it's back to normal within about half an hour.
- Selling 100 diamonds lowers theirs to about 61%, and it takes about 35 minutes to recover even half the drop.
- Selling 10 nether stars lowers theirs to about 43%, and half the drop takes over 2 hours to come back.

See [Supply and demand](#supply-and-demand) for the formulas.

**Tuning.** What to change, in a class, a catalog entry or with `/market admin set`:

| You want | Change |
| --- | --- |
| Prices to swing more with each sale | Raise `sell_impact` and `buy_impact` together. Units to drop 10% ≈ 0.1 ÷ impact. |
| Prices to swing less | Lower `sell_impact` and `buy_impact` together. |
| Prices to bounce back faster | Raise `recovery`. Steps to recover half a drop ≈ 0.7 ÷ recovery. For every item at once, lower `interval_ticks` in the server config. |
| A price that can't crash too far | Raise `floor`. Selling never pays less than `base_price × floor` (less any `sell_fee`). |
| Dumped items to become nearly worthless | Lower `floor`. |
| A price that never moves | `dynamic: false`. |
| The market never to run out | `unlimited_stock: true`. |
| Less money flowing into the economy | Add a `sell_fee`, raise `markup`, or lower farmable items' base prices. See [Where the money goes](#where-the-money-goes). |
| An item players can sell but not buy, or the reverse | `can_buy: false` or `can_sell: false`. |

**A sell fee on everything.** There's no server-wide `sell_fee` setting. Every built-in price and every `suggest` row names a class, and the built-in files set no fee, so put the fee in the classes: copy each built-in class file into `config/variable_economy/catalog/market_classes/` (keeping its name) and add `"sell_fee": 0.05`. A class file replaces the whole class, so keep its other values; `/market admin classes` prints them. Items with no class, or in classes of your own, need the fee in their class, entry, a file's `defaults` or a tag rule. Check an item with `/market admin info <item>`.

**Making your own.** A class is a file in any datapack at `data/<namespace>/market_classes/<name>.json`. It may set any catalog field except `item`, `base_price` and `class`, plus an optional `description`:

```json
{
  "description": "Create machine parts",
  "markup": 0.6,
  "sell_impact": 0.002,
  "buy_impact": 0.002,
  "recovery": 0.04
}
```

Refer to it as `<namespace>:<name>`. A name without a namespace, such as `rare_resource`, means `variable_economy:rare_resource`. To change a built-in class, put a file with the same name in the [config folder](#the-config-folder-prices-for-every-world) (`market_classes/rare_resource.json`) or at the same path in your own datapack (`data/variable_economy/market_classes/rare_resource.json`). Unlike catalog entries, it replaces the whole class, so give every value the class should have. `/market admin classes` prints each class's values as JSON; in game, click a class's values to copy them as a class file (hovering shows where to save it), or copy them from the server console. The built-in files are in the mod's jar at `data/variable_economy/market_classes/`. For example, the built-in `rare_resource` is:

```json
{
  "description": "Scarce raw materials (diamonds, ancient debris). Each sale moves the price noticeably and it recovers slowly.",
  "markup": 1.0, "sell_impact": 0.005, "buy_impact": 0.005, "recovery": 0.02,
  "floor": 0.15, "ceiling": 5.0
}
```

`/market admin classes` doesn't show which file a class came from, so give your copy its own `description` to see that it took effect.

- **Checks:** class values must pass the same rules as catalog entries. A class with a bad value is skipped, and so is every entry naming it. Unknown fields in a class file are ignored with a warning. All of these show in `/market admin diagnose`.
- **Reloading:** `/market admin reload` re-reads classes along with the catalog files, and every member is repriced. Stock and price factors are never touched.
- **Keeping it fair:** keep `sell_impact` and `buy_impact` equal, and below `markup`. Even equal impacts, if large, can let a player sell a batch and buy it back for less; `/market admin diagnose` warns about any item where that's possible. All the built-in classes pass.
- `/market admin classes` lists every class, its values and how many listed items use it.

### Editing prices in game

These commands need operator level 2. Items can be typed with tab completion, such as `minecraft:iron_ingot`.

| Command | What it does |
| --- | --- |
| `/market admin setprice <item> <price>` | Sets the base price. Also lists an item that isn't on the market yet. |
| `/market admin set <item> <field> <value>` | Sets any [catalog field](#catalog-fields), such as `set minecraft:diamond markup 0.25`, `set minecraft:dirt can_buy false` or `set minecraft:diamond class rare_loot`. |
| `/market admin unset <item> <field>` | Drops one of your edits, so tag rules and the catalog files decide that field again. |
| `/market admin remove <item>` | Takes the item off the market. Its stock is kept for if you list it again. |
| `/market admin revert <item>` | Drops all your edits for the item, back to what the catalog files and tag rules say. |
| `/market admin clearstock <item>` | Empties the item's stock; it stays listed. |
| `/market admin info <item>` | For a listed item, every field's value and where it comes from: a catalog file (and whether from the entry itself or the file's `defaults`), a tag rule, your edit, the item's class, the server config, or the built-in default. It also shows any lower-layer values a field overrides. For an unlisted item, it shows what each layer says. |

For example, `info minecraft:oak_log` might show `base_price = 9 [item edit; overrides 4 from tag rule #minecraft:logs, then 8 from file variable_economy:example_catalog]` and `markup = 0.5 [file variable_economy:example_catalog defaults]`.

Every edit is checked before it's saved. If it would break a rule, it's refused with the reason and nothing changes; for example, a `min_buy_markup` above the `markup`, or a field set on an item that has no price. The round-trip check (see [Keeping it fair](#supply-and-demand)) never refuses anything; it only warns, in the server log and in `/market admin diagnose`. So you can change `sell_impact` and `buy_impact` one command at a time, and check `diagnose` once both are set.

### Tag rules: pricing many items at once

A tag rule sets fields for every item in an item tag. It's **live**: when a mod adds items to the tag later, they follow the rule too. Common tags include `minecraft:logs`, `minecraft:planks`, `minecraft:wool`, `c:ingots` and `c:ores`; tab completion lists them all.

| Command | What it does |
| --- | --- |
| `/market admin tag <tag> setprice <price>` | Every item in the tag gets this base price. Unlisted members are listed with the default settings. |
| `/market admin tag <tag> set <field> <value>` | Sets a field for the tag's members. Without a price, it only changes members that are already listed. |
| `/market admin tag <tag> unset <field>` | Drops one field from the rule. |
| `/market admin tag <tag> remove` | Takes every member off the market, except items with their own edits. |
| `/market admin tag <tag> revert` | Deletes the rule. |
| `/market admin tag <tag> info` | Shows the tag's size, how many members are listed, and its rule. |
| `/market admin tags` | Lists every tag rule in the order they apply. |

**Example:** `/market admin tag minecraft:logs setprice 4` prices every log at 4 bits. `/market admin setprice minecraft:birch_log 9` then makes birch the exception. Oak logs keep `unlimited_stock: true` from the catalog file, because the rule only sets the price.

Where rules overlap, rules created later win. Item edits always win over rules.

**Preview first.** A tag can hold dozens of items, so `setprice`, `set`, `remove` and `revert` all accept `preview` at the end. For example, `/market admin tag minecraft:logs setprice 4 preview` says how many of the tag's items would be newly listed, taken off, changed or unaffected, with examples (removals and changes first). It also says whether the change would be refused. Nothing is saved.

## Checking the catalog

`/market admin diagnose` looks over the catalog, your edits and the saved market data, and lists what it finds, ten per page (`/market admin diagnose 2` for the next page). It never changes anything. In a big pack, the craft-profit warnings are easier to read in `craft_profits.txt`, which [`/market admin suggest`](#suggesting-prices-from-recipes) writes; the other findings appear only here. Contract definitions aren't checked here; the contracts add-on has its own command for them.

| Level | Means | Examples |
| --- | --- | --- |
| **Error** | Something was left out of the market. | A catalog entry skipped (unknown item, bad value); an in-game edit that can't be applied. |
| **Warning** | Very likely a mistake. | A round trip that makes a profit; an item that sells for more than its ingredients cost to buy; an item that can be bought but never sold, with 0 stock, so it can never be bought; an item stuck at its price floor or ceiling; a tag rule matching no items; an edit that sets the same value as the layer below it. |
| **Note** | Possibly deliberate; worth a look. | Unlimited items that can be sold (farms can sell them forever); items listed but neither buyable nor sellable; tag rules covering more than 64 items; unpriced template entries; saved data for items that no longer exist. |

**Removed mods.** When you remove a mod, the market keeps its items' saved stock, prices, history and edits. `/market admin prune` lists those items, and `/market admin prune confirm` deletes their data. The overall money counts are kept. A backup of stock, prices and edits is taken automatically first (see [Backups](#backups-and-restoring)).

**Price resets.** `/market admin resetprice all preview` shows how many items would go back to base price, the furthest-moved first. `/market admin resetprice tag <tag>` resets only that tag's items, and also takes `preview`.

## Audit log

Every admin change to the market is logged to core's audit log, `<world>/variable_economy/audit.log`, one JSON line each, which every Variable Economy mod writes to. Each line records when, who (player name and UUID, or `Server` for the console), the action, its target, the result, and the old and new values where that makes sense. The market logs:

- catalog edits and tag-rule changes, including ones refused for breaking a rule
- `setstock`, `clearstock`, `resetprice`, `reload`, `export`, `import` (with the files written), `prune` (with the items removed), `backup` and `restore`

Previews and read-only commands (`info`, `diagnose`, `stats`) aren't logged. Trades aren't either: they're in each account's history, which is core's.

`/bank admin audit` shows the newest ten in chat; `/bank admin audit 2` shows the next ten. [Core's admin guide](https://github.com/The-Azotus/variable-economy-core/blob/main/docs/admin-guide.md) describes the log in full.

## Backups and restoring

`/market admin backup` saves the market's **stock**, **price factors** and **in-game edits** (item edits and tag rules) to `<world>/variable_economy/backups/<date-time>.json`. `/market admin backups` lists them, newest first.

Backups don't include catalog files (an import keeps `.json.bak` copies of those), price history or money counts. A world backup still covers everything.

To restore, name a backup and which part to restore: `stock`, `prices`, `edits` or `all`:

1. `/market admin restore <name> all` checks the backup and says what restoring would change. It also says how long ago the backup was taken, and how many admin actions have been logged since. Nothing is changed.
2. `/market admin restore <name> all confirm` restores it. The current state is backed up first (as `<date-time>_auto-restore`), so you can undo the restore by restoring that.

A damaged backup (unreadable, or with a negative stock, a missing field, and so on) is refused as a whole, with the reasons. Nothing is partly restored. Restored prices are as they were when the backup was taken. They then start recovering from the moment of the restore, rather than catching up on the time in between.

Automatic backups (taken before a restore or a prune) are kept up to the newest 20; older automatic ones are deleted. Backups you make yourself are never deleted.

## Catalog fields

Prices are whole numbers of **bits**, the unit of core's bank and coins. These fields are used in catalog files, spreadsheet columns and `/market admin set`.

| Field | Default | What it does |
| --- | --- | --- |
| `item` | *required* | The exact item ID, such as `minecraft:iron_ingot`. Enchanted, damaged or renamed copies are never accepted (apart from data listed in the `ignored_components` setting of core's server config, `config/variable_economy-server.toml`). |
| `class` | none | An [item class](#item-classes) that fills in the fields nothing else sets, such as `rare_resource`. |
| `replace` | `false` | Catalog files only: `true` starts the entry over instead of changing only the fields it sets on an entry from a lower file. See [Catalog files](#catalog-files). |
| `base_price` | *required*, unless a lower file already lists the item ([Changing a built-in price](#changing-a-built-in-price)) | What the market pays for one unit at normal supply, in bits. At least 1; 0 or below means "not priced yet". |
| `markup` | `0.5` | Added on top when the market sells. `0.5` means buyers pay 150% of base. 0 to 1000. |
| `sell_fee` | `0.0` | Fraction kept back from what the market pays, such as `0.1` for 10%. Nobody receives it; it's a money sink. 0 to 0.99. |
| `can_sell` | `true` | Whether players may sell this item to the market. |
| `can_buy` | `true` | Whether players may buy this item from the market. |
| `start_stock` | `0` | Stock the market starts with, given only the first time the item appears. Later changes don't affect existing stock; use `/market admin setstock`. Ignored for unlimited items. |
| `unlimited_stock` | `false` | The market always has this item to sell, and items sold to it simply disappear. Good for common resources. |
| `dynamic` | `true` | Whether supply and demand move this item's price. `false` keeps it fixed at base. |
| `min_buy_markup` | same as `markup` | Unlimited items only: the lowest markup the buy price can fall to as players sell the item. If you set it, it must be above 0 and no higher than `markup`. |
| `sell_impact` | server config | How much each unit sold lowers the price. See [Supply and demand](#supply-and-demand). |
| `buy_impact` | server config | How much each unit bought raises the price. |
| `recovery` | server config | How quickly the price returns to base. |
| `floor` | server config | The lowest the price factor can fall, 0.001 to 1. |
| `ceiling` | server config | The highest the price factor can rise, 1 to 1000. |

There are no `contract_*` fields. Contracts are defined by the [contracts add-on](https://github.com/The-Azotus/variable-economy-contracts)'s own contract definitions, which can ask for items by market class and pay a multiple of an item's `base_price`.

## Supply and demand

Each item has a **price factor**, written `s`, that starts at 1.0. The two prices at any moment are:

- **Market pays:** `base_price × s × (1 − sell_fee)`
- **Market charges:** `base_price × s × (1 + markup)`

Unlimited items charge `base_price × max(s × (1 + markup), 1 + min_buy_markup)` instead, so their buy price never drops below that minimum. By default the minimum equals the normal price, so it stays fixed.

**Every unit in an order moves the price.** Each unit sold multiplies `s` by `1 − sell_impact`, never going below `floor`. Each unit bought multiplies it by `1 + buy_impact`, never above `ceiling`. Each unit is valued at the price it trades at, and the order's exact total is then rounded once: down when the market pays, up when it charges. Splitting an order never earns more or costs less than one large order. Buying unlimited items never moves their price.

**Prices recover in steps.** Every `interval_ticks` of game time (1 minute by default), every item's factor moves `recovery` of the way back to 1.0. Steps happen at the same moment for every item. Game time stops while the server is stopped, so downtime doesn't count.

**Example (default settings):** the example catalog's iron has `base_price: 32`, `markup: 0.5` and no class. Before any trades, the market pays 32 and charges 48. Selling 640 ingots in one order pays about 17,530 bits in total, not 20,480, and leaves `s` at about 0.73: the market then pays 23 and charges 35. After an hour without trades, iron is back to about 99% of base.

**Keeping it fair.** At startup and after every reload, the server log warns about any item whose settings would let a player make money from a round trip in either order: buying and immediately selling back, or selling and buying back. To stay safe:

- keep `buy_impact` no higher than `markup`, and no higher than `sell_impact / (1 − sell_impact)`;
- don't set `buy_impact` far below `sell_impact`. Otherwise a player can sell a large amount, crashing the price, and buy the same items back at the bottom for less than they were paid. For example, the default settings with `buy_impact = 0` let 3,000 iron be sold and bought back at a profit. How far below is safe depends on `markup`, `sell_fee`, `floor` and `ceiling`, so there's no fixed threshold.

The default config's values and every built-in class are safe: their impacts are equal and small. Large equal impacts with a low markup can still allow a sell-and-buy-back profit. A `sell_fee`, a higher `markup`, a higher `floor` or a lower `ceiling` only make round trips less profitable. Unlimited and fixed-price items can't profit from a round trip and aren't checked. The server log repeats the check after every reload or edit, and `/market admin diagnose` runs it on demand. It's cautious, so it may warn about an item that can't quite profit; look there after changing impacts.

## Where the money goes

The market has **no balance of its own**. It's a source and a sink, not a bank:

- When a player **sells**, the payout is **new money** added to the economy.
- When a player **buys**, what they pay **leaves the economy**. Nobody receives it.
- `sell_fee` doesn't collect anything: it just means less new money is created per sale.
- Admin commands (`setstock`, `clearstock`, `resetprice`, catalog edits) never create or remove money. Only core's `/bank admin money give` and `take` do, outside the market.

`/market admin stats` shows the lifetime totals: money paid to sellers (created), money paid by buyers (removed), items moved each way, and the net amount the market has added to (or removed from) the economy, plus the five busiest items. `/market admin stats <item>` shows one item. Counting starts when a world first runs a version with this command; earlier trades aren't included. Only market trades count (add-ons' trades for a player included); money given or taken by command, or paid and charged by add-ons, doesn't. A steadily growing net means players are mostly selling, so money is piling up. Raise markups, add a `sell_fee`, or lower base prices of farmable items to slow it. Core's `/bank admin stats` shows the whole money supply, the market's sales and purchases among it.

**Cheap items get coarse.** Payments are whole bits, so an item with a `base_price` of 1 or 2 has little room to move. A single unit sold below normal price rounds down to 0, though bulk orders still pay because the total is rounded only once. The screen and tooltips show such prices with decimals (e.g. *0.99*) so players can see what's happening. Give common items a base of 8 or 16 if you want their movements to show.

## Tooltips

With the market installed, every player's item tooltips show what the market pays for items it buys, for example *"Value: 32 bits (636 for 20)"* on a stack of 20. Players can turn this off with `show_sell_price_in_tooltips = false` in their own `config/variable_economy_market-client.toml`. Items the market doesn't buy show nothing.

## Server config

The market's server config is `config/variable_economy_market-server.toml`, created on first start. Changes apply when you save the file. A key such as `pricing.floor` is `floor` in the file's `[pricing]` section. Catalog entries can override everything in `[pricing]` except `interval_ticks`.

| Option | Default | Range | What it does |
| --- | --- | --- | --- |
| `access_mode` | `COMMAND_AND_BLOCK` | `COMMAND_AND_BLOCK`, `BLOCK_ONLY` | How players reach the market: by command and block, or by the Market Terminal only; see [Letting players in](#letting-players-in). |
| `max_quantity_per_request` | 640 | 1 – 1,000,000 | Largest quantity one buy or sell can move. |
| `pricing.sell_impact` | 0.0005 | 0 – 0.5 | Each unit sold multiplies the factor by `1 − sell_impact`. At the default, selling 640 drops a price to about 73%. |
| `pricing.buy_impact` | 0.0005 | 0 – 0.5 | Each unit bought multiplies the factor by `1 + buy_impact`. Keep it close to `sell_impact`; see [Keeping it fair](#supply-and-demand). |
| `pricing.recovery` | 0.05 | 0 – 1 | Fraction of the gap back to 1.0 closed at each step. |
| `pricing.interval_ticks` | 1200 | 1 – 1,728,000 | Game ticks between recovery steps. 20 ticks is 1 second. |
| `pricing.floor` | 0.2 | 0.001 – 1 | Lowest factor selling can reach. |
| `pricing.ceiling` | 5.0 | 1 – 1000 | Highest factor buying can reach. |

Which item data doesn't stop a copy counting as plain, for market sales and the sell-price tooltips, is core's `ignored_components` setting, in `config/variable_economy-server.toml` (by default `["relics:data"]`, so relics can be sold). The data is lost with the sold item; bought items always come fresh.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/market` | everyone* | Opens the Market Terminal's screen. |
| `/market stock [item]`, `/market price <item>` | everyone* | Shows stock, or an item's prices and price factor. |
| `/market sell <item> <qty>`, `/market buy <item> <qty>` | everyone* | Trades without the screen, at whatever the price is when the command runs. Unlike the screen, there's no "price changed, confirm again" step. Use `BLOCK_ONLY` if you don't want players trading this way. |
| `/market admin setstock <item> <qty>` | 2 | Sets an item's stock. Refused for unlimited items. |
| `/market admin clearstock <item>` | 2 | Empties an item's stock. |
| `/market admin resetprice <item>`, `all [preview]` or `tag <tag> [preview]` | 2 | Puts price factors back to 1.0. |
| `/market admin setprice`, `set`, `unset`, `remove`, `revert`, `info` | 2 | In-game editing; see [Editing prices in game](#editing-prices-in-game). |
| `/market admin diagnose [page]` | 2 | Lists catalog problems and things worth checking; see [Checking the catalog](#checking-the-catalog). |
| `/market admin prune [confirm]` | 2 | Lists, then deletes, saved data for items that no longer exist (backing up first). |
| `/market admin backup`, `/market admin backups` | 2 | Saves, or lists, backups of stock, prices and edits; see [Backups](#backups-and-restoring). |
| `/market admin restore <name> <stock\|prices\|edits\|all> [confirm]` | 2 | Shows what restoring would change, then restores with `confirm`. |
| `/market admin tag <tag> ...`, `/market admin tags` | 2 | Tag rules; see [Tag rules](#tag-rules-pricing-many-items-at-once). |
| `/market admin classes` | 2 | Lists the [item classes](#item-classes), their values and how many listed items use each. In game, click a class's values to copy it as a class file. |
| `/market admin reload` | 2 | Re-reads the catalog files. Stock and in-game edits are untouched. |
| `/market admin stats [item]` | 2 | Money created and removed by the market, and items moved; see [Where the money goes](#where-the-money-goes). |
| `/market admin export [modid]` | 4 | Writes `items.csv` and templates to `config/variable_economy/exports/<timestamp>/`. |
| `/market admin import "<path>" [config] [preview]` | 4 | Imports a priced CSV into the world's datapack, or with `config` into the config folder for every world; `preview` writes nothing. The path is relative to `config/variable_economy/`. |
| `/market admin suggest [modid]` | 4 | Works out prices for unlisted items from their recipes and writes `suggested.csv`; see [Suggesting prices from recipes](#suggesting-prices-from-recipes). |

\* Operators only when `access_mode` is `BLOCK_ONLY`: players are told to use a Market Terminal instead (see [Letting players in](#letting-players-in)). Permission 4 means the server console or a level-4 operator, since those commands read and write files on the server.

## Spreadsheet format

`items.csv` columns, in export order:

| Column | Used on import? | Notes |
| --- | --- | --- |
| `item_id` | Yes, required | The item to list. |
| `mod_id`, `display_name`, `max_stack_size`, `in_catalog` | No | For your reference while pricing. |
| `base_price` | Yes, required | Whole number, 1 or more. Blank means "don't import this row". |
| `class` | Yes | An [item class](#item-classes), such as `rare_resource`. Blank means none. |
| `unlimited_stock`, `markup`, `min_buy_markup`, `sell_fee`, `can_sell`, `can_buy`, `start_stock`, `dynamic` | Yes | Blank means "use the class's value, or else the default". |
| `sell_impact`, `buy_impact`, `recovery`, `floor`, `ceiling` | Yes | Per-item overrides; blank means "use the class's value, or else the server config". |

The importer is forgiving about how the file was saved:

- **Columns:** any order; extra columns are ignored; header names aren't case-sensitive.
- **Delimiters:** commas or semicolons, detected from the header row.
- **Decimals:** a decimal comma works, so `0,5` reads as 0.5.
- **True/false columns:** `true`/`false`, `yes`/`no` or `1`/`0`.
- **Quoting and encoding:** quoted fields may contain commas or line breaks; UTF-8 (with or without a byte-order mark) and Excel's Windows encoding are both read.

**Rows are rejected,** with a reason and line number, when:

- the item ID isn't a real item,
- `base_price` isn't a whole number of 1 or more,
- a value is out of range or unreadable,
- `min_buy_markup` breaks its rule, or
- the same item appears twice.

The rest of the file still imports. The first 15 reasons appear in chat, and all of them in the server log.

## Troubleshooting

| You see | Meaning |
| --- | --- |
| `N unpriced market entries (base_price 0 or below) skipped` | Template entries still waiting for a price. Harmless. |
| `Market file … entry N: unknown item …; skipped` | The item doesn't exist, usually because its mod isn't installed. |
| `Market file … entry N: Value … outside of range …; skipped` | A field's value is out of range; see [Catalog fields](#catalog-fields). |
| `Market file … changes (or replaces) … listed earlier in … of the same pack` | Two files in one datapack, or in the config folder, list the same item; the later name's fields win. |
| `Market file … entry N: …; skipped (the entry from … stays)` | A change to an item a lower file lists broke a rule; the lower entry is used unchanged. If the change is fine on its own but clashes with a value it didn't set, such as its `min_buy_markup` above the lower file's `markup`, the message goes on `It conflicts with …` and names that value as `from file <id>`, `from file <id> defaults` or `from the item's class or the default`: set that field in your entry too, or add `"replace": true`. Only one rule spans fields: `min_buy_markup` must be above 0 and no higher than `markup`. |
| `Market file … could not be read` or `Catalog file … could not be read` | The file isn't valid JSON, so the whole file is skipped and lower files' entries stay. The message gives the line and column of the mistake. |
| `Catalog file … in config/variable_economy/catalog: file names may use only …` | Rename the file in lowercase, without spaces. |
| `Market admin edit not applied: …` | A saved in-game edit no longer fits, for example because a catalog file changed. Check `/market admin info <item>`. |
| `Market item …: a round trip may make a profit` | The item's impacts, markup and fee allow a profit from buying then selling back, or selling then buying back; see [Supply and demand](#supply-and-demand). |
| `Market file … entry N: … is a coin, and coins can never be listed` | Coins can't be sold to the market; remove the entry. |
| A player sees "The market is only available at a Market Terminal" | `access_mode` is `BLOCK_ONLY`. |
| A listed item shows 0 under Have and can't be sold, though the player holds one | Another mod adds data to every copy (as Relics does). Find the data's ID (F3+H shows component counts; `/data get entity @s Inventory` shows the IDs) and add it to `ignored_components` in core's `config/variable_economy-server.toml`. |
