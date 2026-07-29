package fixture.external;

import dev.hypercore.plugin.HyperPlugin;
import dev.hypercore.plugin.PluginContext;

public final class ValidPlugin implements HyperPlugin {
    @Override
    public void onLoad(PluginContext context) {
        assertContextClassLoader();
        context.runTask(this::assertContextClassLoader);
    }

    private void assertContextClassLoader() {
        if (Thread.currentThread().getContextClassLoader() != getClass().getClassLoader()) {
            throw new IllegalStateException("Plugin context class loader was not installed");
        }
    }
}
