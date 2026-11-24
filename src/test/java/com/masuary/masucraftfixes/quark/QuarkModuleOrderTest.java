package com.masuary.masucraftfixes.quark;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class QuarkModuleOrderTest {

    @Test
    void modulesComeOutInClassNameOrderWithTheirInstances() {
        Map<Class<? extends Number>, Number> modules = new HashMap<>();
        Integer integerModule = 1;
        Long longModule = 2L;
        Double doubleModule = 3.0;
        modules.put(Long.class, longModule);
        modules.put(Integer.class, integerModule);
        modules.put(Double.class, doubleModule);

        Map<Class<? extends Number>, Number> sortedModules = QuarkModuleOrder.sortedByClassName(modules);

        assertEquals(List.of(Double.class, Integer.class, Long.class), new ArrayList<>(sortedModules.keySet()));
        assertSame(integerModule, sortedModules.get(Integer.class));
        assertSame(longModule, sortedModules.get(Long.class));
        assertSame(doubleModule, sortedModules.get(Double.class));
    }

    @Test
    void insertionOrderDoesNotChangeTheResult() {
        Map<Class<? extends Number>, Number> forwardInsertion = new HashMap<>();
        forwardInsertion.put(Byte.class, (byte) 1);
        forwardInsertion.put(Short.class, (short) 2);
        forwardInsertion.put(Float.class, 3f);
        Map<Class<? extends Number>, Number> reverseInsertion = new HashMap<>();
        reverseInsertion.put(Float.class, 3f);
        reverseInsertion.put(Short.class, (short) 2);
        reverseInsertion.put(Byte.class, (byte) 1);

        assertEquals(new ArrayList<>(QuarkModuleOrder.sortedByClassName(forwardInsertion).keySet()),
                new ArrayList<>(QuarkModuleOrder.sortedByClassName(reverseInsertion).keySet()));
    }
}
