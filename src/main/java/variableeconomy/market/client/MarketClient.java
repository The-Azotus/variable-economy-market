package variableeconomy.market.client;

import net.neoforged.fml.config.ModConfig;
import variableeconomy.market.MarketModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import variableeconomy.market.menu.MarketMenu;


/** The market's client side: its screen and the price line in item tooltips. Loaded only on the client. */
@Mod(value = MarketModule.MOD_ID, dist = Dist.CLIENT)
public final class MarketClient {
    /** The mod's client entry point, constructed on the client only. */
    public MarketClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, ClientConfig.FILE);
        modBus.addListener((RegisterMenuScreensEvent event) -> event.register(MarketMenu.TYPE.get(), MarketScreen::new));
        // Item tooltips show what the market pays, with any screen open or none.
        NeoForge.EVENT_BUS.addListener(SellPriceTooltip::onTooltip);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> ClientMarketState.reset());
    }
}
