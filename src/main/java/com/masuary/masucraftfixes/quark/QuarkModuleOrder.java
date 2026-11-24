package com.masuary.masucraftfixes.quark;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quark keeps its modules in a HashMap keyed by Class, whose identity hash changes every JVM run, and registers each
 * module's content in that map's order. Block entity types, menus and recipe serializers are not saved to the world,
 * so their ids follow that order and differ between backends and between boots; clients that hop between backends
 * keep the ids of the first one. Ordering by class name makes the ids identical on every boot.
 */
public final class QuarkModuleOrder {

    private QuarkModuleOrder() {
    }

    public static <M> Map<Class<? extends M>, M> sortedByClassName(Map<Class<? extends M>, M> modules) {
        Map<Class<? extends M>, M> sortedModules = new LinkedHashMap<>();
        modules.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName()))
                .forEachOrdered(entry -> sortedModules.put(entry.getKey(), entry.getValue()));
        return sortedModules;
    }
}
