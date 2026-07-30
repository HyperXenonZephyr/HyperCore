package org.bukkit.command;

import java.util.Objects;

/**
 * Minimal stub of the Bukkit {@code Command} class.
 */
public class Command {
    private final String name;
    private String description = "";
    private String usage = "";

    protected Command(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = Objects.requireNonNullElse(description, "");
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = Objects.requireNonNullElse(usage, "");
    }
}
