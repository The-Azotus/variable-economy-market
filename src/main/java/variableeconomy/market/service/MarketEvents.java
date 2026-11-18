package variableeconomy.market.service;

import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;
import variableeconomy.market.MarketModule;

import java.util.List;

/**
 * Posts the market's events. Callers post only once their change is complete; a listener that throws is logged and
 * ignored here, so it can never interrupt or undo the market's work.
 */
public final class MarketEvents {
    private MarketEvents() {
    }

    /** Posts {@code event} on the NeoForge event bus. Never throws because of a listener. */
    public static void post(Event event) {
        try {
            NeoForge.EVENT_BUS.post(event);
        } catch (RuntimeException | LinkageError e) {
            MarketModule.LOGGER.error("A listener for {} threw; ignored", event.getClass().getName(), e);
        }
    }

    /** Posts each event in order, each one on its own, so one failing listener doesn't stop the rest. */
    public static void postAll(List<? extends Event> events) {
        for (Event event : events) {
            post(event);
        }
    }
}
