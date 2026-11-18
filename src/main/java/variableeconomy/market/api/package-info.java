/**
 * The market add-on's API: what the market pays and charges for items, and trading with it for a player. Start at
 * {@link variableeconomy.market.api.MarketApi}. Accounts and money are core's: see {@code variableeconomy.core.api}.
 * <p>
 * The market is one server-wide market: prices are the same for every player and move with supply and demand. Prices
 * are in bits, as {@code long}. Only plain copies of an item trade (no enchanted, damaged or renamed ones; data the
 * server's {@code ignored_components} setting names doesn't stop a sale), and coins are never listed.
 * <p>
 * <b>Rules.</b> Every call checks everything before it changes anything, so a result other than {@code OK} changed
 * nothing. Items locked for a player by core's {@link variableeconomy.core.api.ItemGate} answer as if unlisted.
 * <p>
 * <b>Threads.</b> Every method that reads prices or trades must run on the server thread, and throws
 * {@link java.lang.IllegalStateException} otherwise. {@link variableeconomy.market.api.MarketApi}'s getters, which
 * say they may be called from any thread, are the exception.
 * <p>
 * <b>Optional.</b> The market is an add-on, so a mod that uses this package should check
 * {@code ModList.get().isLoaded("variable_economy_market")} first and keep its calls behind a class that loads only
 * then.
 * <p>
 * <b>Stability.</b> Experimental until 1.0: it may still change in a 0.x minor release, and every change is listed in
 * the add-on guide's changelog ({@code docs/api.md}).
 */
@ApiStatus.Experimental
package variableeconomy.market.api;

import org.jetbrains.annotations.ApiStatus;
