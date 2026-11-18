/**
 * Events the market add-on posts on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}: trades, and catalog
 * reloads. They follow the same rules as core's events (see {@code variableeconomy.core.api.event}): posted only after
 * the change is complete, never cancellable, always on the server thread, and a listener that throws is logged and
 * ignored.
 */
@ApiStatus.Experimental
package variableeconomy.market.api.event;

import org.jetbrains.annotations.ApiStatus;
