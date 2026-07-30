package dev.hypercore.bukkit;

import dev.hypercore.plugin.PluginCommandSender;

import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;

/**
 * Adapts a HyperCore {@link PluginCommandSender} to the Bukkit
 * {@link CommandSender} interface so that a Bukkit plugin's
 * {@link org.bukkit.command.CommandExecutor} receives a familiar sender.
 */
final class BukkitCommandSenderAdapter implements CommandSender {
    private final PluginCommandSender delegate;

    BukkitCommandSenderAdapter(PluginCommandSender delegate) {
        this.delegate = delegate;
    }

    @Override
    public void sendMessage(String message) {
        delegate.sendMessage(message);
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String message : messages) {
            delegate.sendMessage(message);
        }
    }

    @Override
    public String getName() {
        return delegate.name();
    }

    @Override
    public boolean hasPermission(String name) {
        return delegate.permissionOverride(name).orElseGet(delegate::operator);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return hasPermission(perm.getName());
    }
}
