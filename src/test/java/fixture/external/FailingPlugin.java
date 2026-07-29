package fixture.external;

import dev.hypercore.plugin.HyperPlugin;
import dev.hypercore.plugin.PluginContext;

public final class FailingPlugin implements HyperPlugin {
    @Override
    public void onLoad(PluginContext context) {
        throw new IllegalStateException("expected external plugin failure");
    }
}
