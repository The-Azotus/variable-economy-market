# Changelog

What changed in each release of the market add-on, for players and server admins. Mod and add-on authors: the API's changes are in the [add-on guide's changelog](docs/api.md#changelog). The changes to Variable Economy as a whole, core and the other add-ons, are in [core's changelog](https://github.com/The-Azotus/variable-economy-core/blob/main/CHANGELOG.md).

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
