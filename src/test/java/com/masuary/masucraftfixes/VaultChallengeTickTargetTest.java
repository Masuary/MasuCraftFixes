package com.masuary.masucraftfixes;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class VaultChallengeTickTargetTest {
    private static final String CHALLENGE_MANAGER = "iskallia/vault/core/vault/challenge/base/ChallengeManager";
    private static final String MIXIN = "com/masuary/masucraftfixes/mixin/VaultChallengeTickGuardMixin";
    private static final String WORLD_ARGUMENT = "(Lnet/minecraft/server/level/ServerLevel;)V";

    @Test
    public void injectorTargetsTheSyntheticCallbackBeforeChallengeDispatch() throws IOException {
        ClassNode mixin = readClass(MIXIN);
        MethodNode injector = mixin.methods.stream()
                .filter(method -> method.name.equals("masucraftfixes$skipInvalidWorldTick"))
                .findFirst().orElseThrow();
        AnnotationNode injection = findAnnotation(injector.visibleAnnotations,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(Boolean.TRUE, annotationValue(injection, "cancellable"));
        assertEquals(1, annotationValue(injection, "require"));
        List<?> injectionPoints = (List<?>) annotationValue(injection, "at");
        assertEquals("HEAD", annotationValue((AnnotationNode) injectionPoints.get(0), "value"));

        String selector = injectorSelector(injection);
        MethodNode callback = readClass(CHALLENGE_MANAGER).methods.stream()
                .filter(method -> (method.name + method.desc).equals(selector))
                .findFirst().orElseThrow(() -> new AssertionError("VH callback no longer matches " + selector));
        assertTrue("Expected the compiler-generated callback", (callback.access & Opcodes.ACC_SYNTHETIC) != 0);

        int dispatches = 0;
        for (AbstractInsnNode instruction : callback.instructions) {
            if (instruction instanceof MethodInsnNode invocation) {
                assertEquals(CHALLENGE_MANAGER, invocation.owner);
                assertEquals("onTick", invocation.name);
                assertEquals(WORLD_ARGUMENT, invocation.desc);
                assertEquals(Opcodes.INVOKEVIRTUAL, invocation.getOpcode());
                dispatches++;
            }
        }
        assertEquals("Guard must run before the only challenge tick dispatch", 1, dispatches);
    }

    @Test
    public void registerEventsStillReferencesTheGuardedCallback() throws IOException {
        ClassNode challengeManager = readClass(CHALLENGE_MANAGER);
        MethodNode registration = challengeManager.methods.stream()
                .filter(method -> method.name.equals("registerEvents") && method.desc.equals(WORLD_ARGUMENT))
                .findFirst().orElseThrow();
        for (AbstractInsnNode instruction : registration.instructions) {
            if (instruction instanceof InvokeDynamicInsnNode dynamicCall) {
                for (Object argument : dynamicCall.bsmArgs) {
                    if (argument instanceof Handle handle && handle.getOwner().equals(CHALLENGE_MANAGER)
                            && handle.getName().equals("lambda$registerEvents$3")
                            && handle.getDesc().equals("(Lnet/minecraft/server/level/ServerLevel;"
                                    + "Lnet/minecraftforge/event/TickEvent$ServerTickEvent;)V")) {
                        return;
                    }
                }
            }
        }
        fail("VH no longer registers the guarded server-tick callback");
    }

    @Test
    public void detachLoggingRunsAfterTheNativeDetachMethod() throws IOException {
        MethodNode injector = readClass(MIXIN).methods.stream()
                .filter(method -> method.name.equals("masucraftfixes$recordNativeDetach"))
                .findFirst().orElseThrow();
        AnnotationNode injection = findAnnotation(injector.visibleAnnotations,
                "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals("onDetach()V", injectorSelector(injection));
        assertEquals(1, annotationValue(injection, "require"));
        List<?> injectionPoints = (List<?>) annotationValue(injection, "at");
        assertEquals("TAIL", annotationValue((AnnotationNode) injectionPoints.get(0), "value"));
        assertTrue(readClass(CHALLENGE_MANAGER).methods.stream()
                .anyMatch(method -> method.name.equals("onDetach") && method.desc.equals("()V")));
    }

    @Test
    public void allShadowFieldsMatchTheExactVaultJar() throws IOException {
        ClassNode challengeManager = readClass(CHALLENGE_MANAGER);
        int validatedFields = 0;
        for (FieldNode field : readClass(MIXIN).fields) {
            if (field.visibleAnnotations == null || field.visibleAnnotations.stream()
                    .noneMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"))) {
                continue;
            }
            assertTrue("Missing VH field " + field.name + ":" + field.desc,
                    challengeManager.fields.stream().anyMatch(target -> target.name.equals(field.name)
                            && target.desc.equals(field.desc) && target.access == field.access));
            validatedFields++;
        }
        assertEquals("Expected all guard state and logging fields to be validated", 4, validatedFields);
    }

    private static ClassNode readClass(String name) throws IOException {
        // Inspect bytes without loading optional mod classes or bootstrapping Minecraft.
        try (InputStream input = VaultChallengeTickTargetTest.class.getClassLoader()
                .getResourceAsStream(name + ".class")) {
            assertNotNull("Missing class bytes for " + name, input);
            ClassNode classNode = new ClassNode();
            new ClassReader(input).accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return classNode;
        }
    }

    private static AnnotationNode findAnnotation(List<AnnotationNode> annotations, String descriptor) {
        assertNotNull("Missing annotations for " + descriptor, annotations);
        return annotations.stream().filter(annotation -> annotation.desc.equals(descriptor))
                .findFirst().orElseThrow(() -> new AssertionError("Missing annotation " + descriptor));
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (annotation.values.get(index).equals(name)) {
                return annotation.values.get(index + 1);
            }
        }
        throw new AssertionError("Missing annotation value " + name);
    }

    private static String injectorSelector(AnnotationNode injection) {
        List<?> selectors = (List<?>) annotationValue(injection, "method");
        assertEquals("Guard must match exactly one callback", 1, selectors.size());
        return (String) selectors.get(0);
    }
}
