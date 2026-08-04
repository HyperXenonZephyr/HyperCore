package dev.hypercore.bridge.command;

import dev.hypercore.bridge.ipc.Packet;
import dev.hypercore.bridge.ipc.packet.CommandExecutePacket;
import dev.hypercore.bridge.ipc.packet.CommandExecuteResultPacket;
import dev.hypercore.bridge.ipc.packet.CommandRegistrySnapshotPacket;
import dev.hypercore.bridge.world.BridgeLink;
import dev.hypercore.orchestrator.HyperCoreRole;
import dev.hypercore.plugin.PluginCommandRegistry;
import dev.hypercore.plugin.PluginCommandSender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies command mirroring, cross-host execution forwarding, and result
 * delivery.
 */
class CommandProxyTest {

    private static final class FakeLink implements BridgeLink {
        boolean connected = true;
        final List<Packet> sent = new ArrayList<>();

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean send(Packet packet) {
            sent.add(packet);
            return true;
        }
    }

    private static PluginCommandRegistry registryWithCommand() {
        PluginCommandRegistry registry = new PluginCommandRegistry(new dev.hypercore.plugin.PluginPermissionService());
        registry.register("demo", new PluginCommandRegistry.CommandDefinition(
            "hello",
            List.of("hi"),
            "",
            "Says hello",
            "/hello <name>",
            (sender, label, arguments) -> {
                sender.sendMessage("Hello, " + (arguments.isEmpty() ? "world" : arguments.get(0)) + "!");
                return true;
            }
        ));
        return registry;
    }

    @Test
    void mirrorsRemoteCommandsUnderPrefixAndForwardsExecutions() {
        FakeLink link = new FakeLink();
        PluginCommandRegistry local = new PluginCommandRegistry(new dev.hypercore.plugin.PluginPermissionService());
        CommandProxy proxy = new CommandProxy(HyperCoreRole.FORGE_HOST, local, link, "xfabric");

        proxy.mirrorRemote(List.of(new CommandRegistrySnapshotPacket.CommandDescriptor(
            "hello", List.of("hi"), "", "Says hello", "/hello <name>", "demo"
        )));

        assertEquals(1, local.registeredCommands());
        // Dispatching the mirrored command must forward to the remote host.
        PluginCommandRegistry.DispatchResult result = local.dispatch(
            "xfabric_hello",
            List.of("Alex"),
            new PluginCommandSender() {
                @Override
                public String name() {
                    return "Console";
                }

                @Override
                public boolean operator() {
                    return true;
                }

                @Override
                public void sendMessage(String message) {
                }
            }
        );
        assertEquals(PluginCommandRegistry.DispatchStatus.EXECUTED, result.status());
        assertEquals(1, link.sent.size());
        CommandExecutePacket sent = (CommandExecutePacket) link.sent.get(0);
        assertEquals("hello", sent.label());
        assertEquals(List.of("Alex"), sent.arguments());
        assertEquals("Console", sent.senderName());
        assertTrue(sent.operator());
    }

    @Test
    void executesRemoteCommandLocallyAndReturnsResult() {
        FakeLink link = new FakeLink();
        PluginCommandRegistry registry = registryWithCommand();
        CommandProxy proxy = new CommandProxy(HyperCoreRole.FABRIC_HOST, registry, link, "xforge");

        proxy.handleExecute(new CommandExecutePacket(7, "hello", List.of("Bob"), "Steve", true, false));

        assertEquals(1, link.sent.size());
        CommandExecuteResultPacket result = (CommandExecuteResultPacket) link.sent.get(0);
        assertEquals(7, result.requestId());
        assertTrue(result.success());
        assertEquals("Hello, Bob!", result.message());
    }

    @Test
    void deliversResultMessagesToOriginalSender() {
        FakeLink link = new FakeLink();
        PluginCommandRegistry registry = registryWithCommand();
        CommandProxy proxy = new CommandProxy(HyperCoreRole.FORGE_HOST, registry, link, "xfabric");

        StringBuilder received = new StringBuilder();
        PluginCommandSender original = new PluginCommandSender() {
            @Override
            public String name() {
                return "Alex";
            }

            @Override
            public boolean operator() {
                return false;
            }

            @Override
            public void sendMessage(String message) {
                received.append(message);
            }
        };
        // Simulate an execution that was forwarded and answered.
        CommandExecutePacket execute = new CommandExecutePacket(3, "hello", List.of(), "Alex", false, false);
        // Reuse handleExecute to produce a request id mismatch is awkward here,
        // so exercise the pending path through a direct call instead:
        assertTrue(proxy.sendRemoteRequest(original, "hello", List.of()));
        assertFalse(link.sent.isEmpty());
        CommandExecutePacket sent = (CommandExecutePacket) link.sent.get(0);

        proxy.handleResult(new CommandExecuteResultPacket(sent.requestId(), true, "Hello from the remote host!"));
        assertEquals("Hello from the remote host!", received.toString());
    }

    @Test
    void unknownRemoteCommandRepliesWithFailure() {
        FakeLink link = new FakeLink();
        PluginCommandRegistry registry = new PluginCommandRegistry(new dev.hypercore.plugin.PluginPermissionService());
        CommandProxy proxy = new CommandProxy(HyperCoreRole.FORGE_HOST, registry, link, "xfabric");

        proxy.handleExecute(new CommandExecutePacket(1, "does-not-exist", List.of(), "Console", true, true));
        CommandExecuteResultPacket result = (CommandExecuteResultPacket) link.sent.get(0);
        assertFalse(result.success());
    }
}
