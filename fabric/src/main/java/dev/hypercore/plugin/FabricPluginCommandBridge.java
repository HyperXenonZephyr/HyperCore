package dev.hypercore.plugin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Fabric copy of the plugin command bridge.
 *
 * <p>Registering external plugin commands into the brigadier dispatcher is
 * loader-agnostic at the core level (it goes through {@link PluginCommandRegistry});
 * only the sender adapter and the brigadier/Component symbols are Minecraft- specific.
 * This is a verbatim port of the Forge bridge with the sender renamed and the
 * logger switched from Mojang's LogUtils to SLF4J (Fabric does not ship
 * com.mojang.logging.LogUtils as a standalone API).
 */
public final class FabricPluginCommandBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(FabricPluginCommandBridge.class);

    private FabricPluginCommandBridge() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, PluginManager plugins) {
        for (PluginCommandRegistry.RegisteredCommand command : plugins.commands().commands()) {
            registerLabel(dispatcher, plugins, command.definition().name());
            for (String alias : command.definition().aliases()) {
                registerLabel(dispatcher, plugins, alias);
            }
        }
    }

    private static void registerLabel(
        CommandDispatcher<CommandSourceStack> dispatcher,
        PluginManager plugins,
        String label
    ) {
        if (dispatcher.getRoot().getChild(label) != null) {
            LOGGER.warn("Skipping plugin command label {} because it already exists", label);
            return;
        }

        dispatcher.register(literal(label)
            .executes(context -> dispatch(plugins, label, List.of(), context.getSource()))
            .then(argument("arguments", StringArgumentType.greedyString())
                .executes(context -> dispatch(
                    plugins,
                    label,
                    parseArguments(StringArgumentType.getString(context, "arguments")),
                    context.getSource()
                )))
        );
    }

    private static int dispatch(
        PluginManager plugins,
        String label,
        List<String> arguments,
        CommandSourceStack source
    ) {
        PluginCommandRegistry.DispatchResult result = plugins.commands().dispatch(
            label,
            arguments,
            new FabricCommandSender(source)
        );
        return result.success() ? 1 : 0;
    }

    private static List<String> parseArguments(String input) throws CommandSyntaxException {
        StringReader reader = new StringReader(input);
        List<String> arguments = new ArrayList<>();
        while (reader.canRead()) {
            reader.skipWhitespace();
            if (reader.canRead()) {
                arguments.add(reader.readString());
            }
        }
        return List.copyOf(arguments);
    }

    private record FabricCommandSender(CommandSourceStack source) implements PluginCommandSender {
        @Override
        public String name() {
            return source.getTextName();
        }

        @Override
        public boolean operator() {
            return source.hasPermission(2);
        }

        @Override
        public Optional<Boolean> permissionOverride(String permission) {
            return Optional.empty();
        }

        @Override
        public void sendMessage(String message) {
            source.sendSuccess(() -> Component.literal(message), false);
        }
    }
}
