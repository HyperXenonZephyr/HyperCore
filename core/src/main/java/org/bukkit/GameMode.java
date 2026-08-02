package org.bukkit;

/**
 * Represents the game mode of a player.
 *
 * <p>This is a minimal Bukkit-compatible enum. Values match the vanilla game
 * modes so that Bukkit plugins can configure players without depending on
 * loader-specific Minecraft classes.
 */
public enum GameMode {
    SURVIVAL(0),
    CREATIVE(1),
    ADVENTURE(2),
    SPECTATOR(3);

    private final int value;

    GameMode(int value) {
        this.value = value;
    }

    /**
     * Returns the vanilla numeric id for this game mode.
     */
    public int getValue() {
        return value;
    }

    /**
     * Looks up a game mode by its numeric vanilla id.
     *
     * @param value the vanilla game mode id
     * @return the matching game mode, or {@code null} if none
     */
    public static GameMode getByValue(int value) {
        for (GameMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        return null;
    }
}
