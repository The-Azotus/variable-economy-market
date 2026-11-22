package variableeconomy.market.api.event;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/**
 * The market catalog was rebuilt: at server start, after {@code /reload} or {@code /market admin reload}, after an
 * import or a restore of a backup's edits, after every in-game admin edit, and when the market's server config file is
 * changed while the server runs (its pricing defaults apply to every item that doesn't set its own). The new prices are live and have been
 * sent to every player. Listen for it to re-read anything kept from the catalog, such as the list of listed items.
 * Prices also change with trades and over time, without this event: ask for one when it's needed.
 * <p>
 * One admin action can post it more than once (an edit the catalog rules refuse is applied, then undone), so treat it
 * as "the catalog may have changed", not as a count of changes.
 * <p>
 * Posted by Variable Economy; add-ons only listen for it.
 */
public final class CatalogReloadedEvent extends Event {
    private final MinecraftServer server;

    /**
     * Posted by Variable Economy.
     *
     * @param server the server whose catalog was rebuilt
     */
    public CatalogReloadedEvent(MinecraftServer server) {
        this.server = server;
    }

    /**
     * The server.
     *
     * @return the server whose catalog was rebuilt
     */
    public MinecraftServer getServer() {
        return server;
    }
}
