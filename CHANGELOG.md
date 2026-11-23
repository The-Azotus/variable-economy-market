# Changelog

What changed in each release of the market add-on, for players and server admins. Mod and add-on authors: the API's changes are in the [add-on guide's changelog](docs/api.md#changelog). The changes to Variable Economy as a whole, core and the other add-ons, are in [core's changelog](https://github.com/The-Azotus/variable-economy-core/blob/main/CHANGELOG.md).

## Unreleased

**Install with core 0.6.3 or a later 0.6.x.** The market's network protocol changed: the server and every client need this version.

- **The Selling box.** The market screen shows your inventory and a Selling box along the bottom. Click items to put them in the box (right-click for one, Shift-click for every copy), see what they're worth, and sell them all with one click. The items stay in your inventory until you sell, so closing the screen never loses anything. On a small window the box is a third view of the list, after Items and My trades.
- **No more "price changed" when the price moves your way.** A trade from the screen goes through if it pays at least, or costs at most, what the button showed. Only a price that moved against you asks you to confirm again.
- **The list's scrollbar can be dragged**: click it to jump there, or hold and drag.
- **Held** lists only items you hold that the market buys, as its tooltip said.
- **`/market sell` and `/market buy`** answer in plain sentences, the same ones the screen uses, and name the shared account the money went to or came from.
- **`/market stock`** without an item lists only the items in stock, by name, instead of every listed item. `/market admin info <item>` shows an item's stock.
- **Server config changes apply at once.** Saving `variable_economy_market-server.toml` while the server runs updates prices, open screens and tooltips, and re-runs the round-trip check.
- **Fixed:** a trade that reached the server after the player died, with the market screen still open, could charge them for items that were then lost. A dead player can no longer trade.
- **The example catalog** now starts its five items over, so with the built-in prices on, they really have default settings instead of their built-in classes, as the admin guide says.
- The admin guide describes the built-in prices as they ship: 55 mods, crafted items included, and which items trade only one way.

## 0.6.1 (2026-11-17)

- The market now lives in its own repository; nothing changes in game.
- The mod's jar no longer contains its test code.

## 0.6.0 (2026-11-14)

The mod was called Dynamic-Economy until this release. It is now **Variable Economy**, and it is four mods instead of one: core, which is required, and three optional add-ons. The market is one of them, **Variable Economy: Market** (`variable_economy_market`). Install it, alone or with the other add-ons, on top of core. [Core's changelog](https://github.com/The-Azotus/variable-economy-core/blob/main/CHANGELOG.md) describes the whole release.

**Before you update:** worlds from Dynamic-Economy can't be carried over. The mod's id changed, so its market, bank balances, coins and terminals don't load in a 0.6.0 world. Start a new world. Create: Numismatics is no longer supported: every world uses core's own coins and bank, counted in bits.

- Install the same mods on the server and on every client, all of the same version. A client missing an add-on the server has (or the other way round) can't join.
- Each mod has its own block and screen in place of the one tabbed Market Terminal. The market's is the **Market Terminal**, opening the market's screen alone; coins go in and out at core's **Bank** block, which also shows the leaderboard.
- The market has its own config file, `config/variable_economy_market-server.toml`, with its own `access_mode`, and its own commands under `/market`.
- The `contract_*` catalog fields are gone. Contracts come from the contracts add-on's **contract definitions** instead.
