package com.masuary.masucraftfixes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasuCraftFixesMixinPluginTest {

    @Test
    void vaultIntegrationsCheckIsWiredToTheRegisteredNetworkListenerMixin() throws IOException {
        JsonObject mixinConfig = readMixinConfig();
        String mixinPackage = mixinConfig.get("package").getAsString();
        List<String> registeredMixins = new ArrayList<>();
        for (JsonElement mixin : mixinConfig.getAsJsonArray("mixins")) {
            registeredMixins.add(mixinPackage + "." + mixin.getAsString());
        }

        assertEquals(MasuCraftFixesMixinPlugin.class.getName(), mixinConfig.get("plugin").getAsString());
        assertTrue(registeredMixins.contains(MasuCraftFixesMixinPlugin.NETWORK_LISTENER_MIXIN), registeredMixins.toString());
        assertTrue(MasuCraftFixesMixinPlugin.NETWORK_LISTENER_MIXIN.startsWith(MasuCraftFixesMixinPlugin.REFINED_STORAGE_MIXIN_PACKAGE));
        assertTrue(Files.isRegularFile(Path.of("src/main/java",
                MasuCraftFixesMixinPlugin.NETWORK_LISTENER_MIXIN.replace('.', '/') + ".java")));
    }

    private static JsonObject readMixinConfig() throws IOException {
        try (InputStream configStream = MasuCraftFixesMixinPluginTest.class.getResourceAsStream("/mixins.masucraftfixes.json")) {
            assertNotNull(configStream, "mixins.masucraftfixes.json is not on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(configStream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
