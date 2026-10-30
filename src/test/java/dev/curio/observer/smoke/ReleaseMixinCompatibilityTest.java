package dev.curio.observer.smoke;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/** Guards the class-file annotation encoding consumed by the user's Loader 0.18.5 / MixinExtras 0.5.3. */
class ReleaseMixinCompatibilityTest {
    private static final String MIXIN = "dev/curio/observer/mixin/WorldRendererMixin";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String SETUP_TERRAIN =
            "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V";

    @Test void compiledRedirectsUseScalarInjectionPoints() throws IOException {
        // Do not load a mixin class: inspect its original bytes before Fabric transforms the target.
        try (InputStream bytes = getClass().getClassLoader().getResourceAsStream(MIXIN + ".class")) {
            assertNotNull(bytes, "Missing compiled WorldRendererMixin");
            assertScalarInjectionPoints(bytes);
        }
    }

    @Test void remappedReleaseRetainsCompatibleAnnotationsAndReferences() throws Exception {
        String releasePath = System.getProperty("curio.releaseJar");
        assumeTrue(releasePath != null, "Run releaseMixinTest to inspect the packaged release JAR");
        try (JarFile release = new JarFile(Path.of(releasePath).toFile())) {
            try (InputStream bytes = entry(release, MIXIN + ".class")) {
                ClassNode mixin = assertScalarInjectionPoints(bytes);
                for (MethodNode method : mixin.methods) {
                    if (method.name.startsWith("curio$terrain")) {
                        assertEquals("(Lnet/minecraft/class_746;Lnet/minecraft/class_4184;Lnet/minecraft/class_4604;ZZ)D",
                                method.desc, "Release handlers must use intermediary Minecraft types");
                    }
                }
            }

            JsonObject config = json(release, "curio-observer.mixins.json");
            JsonObject mappings = json(release, config.get("refmap").getAsString())
                    .getAsJsonObject("mappings").getAsJsonObject(MIXIN);
            assertNotNull(mappings, "Release refmap must include the terrain mixin");
            assertEquals("Lnet/minecraft/class_761;method_3273(Lnet/minecraft/class_4184;Lnet/minecraft/class_4604;ZZ)V",
                    mappings.get(SETUP_TERRAIN).getAsString());
            assertEquals("Lnet/minecraft/class_746;method_23317()D",
                    mappings.get("Lnet/minecraft/client/network/ClientPlayerEntity;getX()D").getAsString());
            assertEquals("Lnet/minecraft/class_746;method_23318()D",
                    mappings.get("Lnet/minecraft/client/network/ClientPlayerEntity;getY()D").getAsString());
            assertEquals("Lnet/minecraft/class_746;method_23321()D",
                    mappings.get("Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D").getAsString());

            String loaderRequirement = json(release, "fabric.mod.json")
                    .getAsJsonObject("depends").get("fabricloader").getAsString();
            assertTrue(VersionPredicate.parse(loaderRequirement).test(Version.parse("0.18.5")),
                    "The release must support the tested Fabric Loader 0.18.5 runtime");
        }
    }

    private static ClassNode assertScalarInjectionPoints(InputStream bytes) throws IOException {
        ClassNode mixin = new ClassNode();
        new ClassReader(bytes).accept(mixin, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        int redirects = 0;
        for (MethodNode method : mixin.methods) {
            if (method.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!REDIRECT.equals(annotation.desc)) continue;
                redirects++;
                Object at = value(annotation, "at");
                // Fabric Mixin 0.17.4 changed Redirect.at to At[]. Older MixinExtras casts it directly
                // to AnnotationNode, so even a one-element array crashes during preApply.
                AnnotationNode injectionPoint = assertInstanceOf(AnnotationNode.class, at,
                        method.name + " must encode @Redirect.at as one annotation, not an array");
                assertEquals("Lorg/spongepowered/asm/mixin/injection/At;", injectionPoint.desc);
                assertEquals(List.of(SETUP_TERRAIN), value(annotation, "method"));
                assertEquals("INVOKE", value(injectionPoint, "value"));
            }
        }
        assertEquals(3, redirects, "All three terrain coordinate redirects must be checked");
        return mixin;
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static InputStream entry(JarFile jar, String name) throws IOException {
        var entry = jar.getJarEntry(name);
        assertNotNull(entry, "Missing release JAR entry: " + name);
        return jar.getInputStream(entry);
    }

    private static JsonObject json(JarFile jar, String name) throws IOException {
        try (var reader = new InputStreamReader(entry(jar, name), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
