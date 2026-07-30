package dev.hypercore.plugin.compat;

import dev.hypercore.plugin.ExternalPluginDescriptor;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitPluginYmlParserTest {

    @Test
    void parsesFlatDescriptorWithDependencies() {
        String yml = """
            name: ExamplePlugin
            version: '1.0.0'
            main: com.example.ExamplePlugin
            depend: [OtherPlugin]
            softdepend: [OptionalPlugin]
            """;
        ExternalPluginDescriptor descriptor = BukkitPluginYmlParser.parse(new StringReader(yml));

        assertEquals("exampleplugin", descriptor.plugin().id());
        assertEquals("ExamplePlugin", descriptor.plugin().name());
        assertEquals("1.0.0", descriptor.plugin().version());
        assertEquals("com.example.ExamplePlugin", descriptor.mainClass());
        assertEquals(List.of("otherplugin"), descriptor.depends());
        assertEquals(List.of("optionalplugin"), descriptor.softDepends());
        assertEquals(ExternalPluginDescriptor.CURRENT_API_VERSION, descriptor.apiVersion());
    }

    @Test
    void parsesBlockStyleListDependencies() {
        String yml = """
            name: BlockStyle
            version: '2.0'
            main: com.example.Block
            depend:
              - Alpha
              - Beta
            softdepend:
              - Gamma
            """;
        ExternalPluginDescriptor descriptor = BukkitPluginYmlParser.parse(new StringReader(yml));

        // ExternalPluginDescriptor normalizes dependency ids to lowercase
        assertEquals(List.of("alpha", "beta"), descriptor.depends());
        assertEquals(List.of("gamma"), descriptor.softDepends());
    }

    @Test
    void ignoresNestedCommandsAndPermissions() {
        String yml = """
            name: WithCommands
            version: '1.0'
            main: com.example.WithCommands
            commands:
              greet:
                description: Greet a player
                usage: /greet <player>
            permissions:
              greet.use:
                default: true
            """;
        ExternalPluginDescriptor descriptor = BukkitPluginYmlParser.parse(new StringReader(yml));

        assertEquals("withcommands", descriptor.plugin().id());
        assertEquals("com.example.WithCommands", descriptor.mainClass());
        assertTrue(descriptor.depends().isEmpty());
    }

    @Test
    void acceptsApiVersionButIgnoresIt() {
        String yml = """
            name: Versioned
            version: '1.0'
            main: com.example.Versioned
            api-version: '1.21'
            """;
        ExternalPluginDescriptor descriptor = BukkitPluginYmlParser.parse(new StringReader(yml));

        assertEquals(ExternalPluginDescriptor.CURRENT_API_VERSION, descriptor.apiVersion());
    }

    @Test
    void worksWithoutAnyDependencies() {
        String yml = """
            name: Standalone
            version: '0.1'
            main: com.example.Standalone
            """;
        ExternalPluginDescriptor descriptor = BukkitPluginYmlParser.parse(new StringReader(yml));

        assertTrue(descriptor.depends().isEmpty());
        assertTrue(descriptor.softDepends().isEmpty());
    }

    @Test
    void throwsOnMissingName() {
        String yml = """
            version: '1.0'
            main: com.example.Missing
            """;
        assertThrows(IllegalArgumentException.class, () ->
            BukkitPluginYmlParser.parse(new StringReader(yml))
        );
    }

    @Test
    void throwsOnMissingVersion() {
        String yml = """
            name: NoVersion
            main: com.example.NoVersion
            """;
        assertThrows(IllegalArgumentException.class, () ->
            BukkitPluginYmlParser.parse(new StringReader(yml))
        );
    }

    @Test
    void throwsOnMissingMain() {
        String yml = """
            name: NoMain
            version: '1.0'
            """;
        assertThrows(IllegalArgumentException.class, () ->
            BukkitPluginYmlParser.parse(new StringReader(yml))
        );
    }

    @Test
    void throwsOnNonMappingRoot() {
        String yml = "just a string";
        assertThrows(IllegalArgumentException.class, () ->
            BukkitPluginYmlParser.parse(new StringReader(yml))
        );
    }

    @Test
    void throwsOnInvalidPluginName() {
        // PluginDescriptor.normalizeId requires [a-z][a-z0-9_-]{1,63}
        // A name starting with a digit fails the pattern.
        String yml = """
            name: '123bad'
            version: '1.0'
            main: com.example.Bad
            """;
        assertThrows(IllegalArgumentException.class, () ->
            BukkitPluginYmlParser.parse(new StringReader(yml))
        );
    }
}
