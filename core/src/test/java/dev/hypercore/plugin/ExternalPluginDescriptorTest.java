package dev.hypercore.plugin;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalPluginDescriptorTest {
    @Test
    void parsesAndNormalizesStructuredDescriptor() {
        ExternalPluginDescriptor descriptor = ExternalPluginDescriptor.parse(new StringReader("""
            {
              "id": "Example_Plugin",
              "name": "Example Plugin",
              "version": "1.2.3",
              "apiVersion": 1,
              "main": "example.PluginMain",
              "depends": ["Core_Dep"],
              "softDepends": ["Optional_Dep"]
            }
            """));

        assertEquals("example_plugin", descriptor.plugin().id());
        assertEquals("example.PluginMain", descriptor.mainClass());
        assertEquals(List.of("core_dep"), descriptor.depends());
        assertEquals(List.of("optional_dep"), descriptor.softDepends());
    }

    @Test
    void rejectsUnsupportedApiAndInvalidDependencies() {
        assertThrows(IllegalArgumentException.class, () -> parseWith("\"apiVersion\": 2,"));
        assertThrows(IllegalArgumentException.class, () -> parseWith("\"depends\": [\"demo\"],"));
        assertThrows(IllegalArgumentException.class, () -> parseWith("\"depends\": [\"base\", \"BASE\"],"));
    }

    private static ExternalPluginDescriptor parseWith(String extraField) {
        return ExternalPluginDescriptor.parse(new StringReader("""
            {
              "id": "demo",
              "name": "Demo",
              "version": "1.0",
              %s
              "main": "example.PluginMain"
            }
            """.formatted(extraField)));
    }
}
