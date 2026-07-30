package fixture.external;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Bukkit shim test fixture: a plugin that extends the minimal
 * {@link JavaPlugin} stub rather than implementing HyperCore's
 * {@code HyperPlugin} directly. It records lifecycle, command dispatch, and
 * scheduler activity into public instance fields so
 * {@code BukkitPluginAdapterTest} can assert on them after driving the adapter
 * through a {@code PluginManager}.
 *
 * <p>It is also packaged into a JAR by
 * {@code ExternalPluginLoaderTest#loadsJavaPluginFromBukkitJar} to verify the
 * end-to-end loader path: {@code plugin.yml} → {@code BukkitPluginAdapter} →
 * command registration.
 */
public final class ExampleBukkitPlugin extends JavaPlugin {
    public final List<String> lifecycle = new ArrayList<>();
    public final List<String> greetings = new ArrayList<>();
    public final List<String> senderMessages = new ArrayList<>();
    public volatile boolean taskRan;

    @Override
    protected void onLoad() {
        lifecycle.add("load");
        // The adapter must inject all of these before onLoad fires.
        if (getServer() == null) {
            throw new IllegalStateException("Bukkit server was not injected by the adapter");
        }
        if (getLogger() == null) {
            throw new IllegalStateException("Logger was not injected by the adapter");
        }
        if (getName() == null || getName().isBlank()) {
            throw new IllegalStateException("Plugin name was not injected by the adapter");
        }
        if (getDataFolder() == null) {
            throw new IllegalStateException("Data folder was not injected by the adapter");
        }
    }

    @Override
    protected void onEnable() {
        lifecycle.add("enable");

        PluginCommand greet = getCommand("greet");
        if (greet == null) {
            throw new IllegalStateException("greet command was not registered from plugin.yml");
        }
        greet.setDescription(greet.getDescription());
        greet.setExecutor((sender, command, label, args) -> {
            String target = args.length > 0 ? args[0] : "world";
            greetings.add(target);
            sender.sendMessage("Hello, " + target + "!");
            return true;
        });

        // Exercise the Bukkit static accessor + scheduler bridge. The server
        // reference was installed by the adapter's onLoad, so Bukkit.getServer()
        // is safe to call here.
        Bukkit.getScheduler().runTask(this, () -> {
            taskRan = true;
            lifecycle.add("tick");
        });
    }

    @Override
    protected void onDisable() {
        lifecycle.add("disable");
    }
}
