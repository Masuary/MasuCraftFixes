package com.masuary.masucraftfixes.refinedstorage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DueQueueTest {

    @Test
    void dueKeysAreTakenAndRemovedOthersStay() {
        DueQueue<String> queue = new DueQueue<>();
        queue.schedule("early", 10);
        queue.schedule("late", 30);

        assertEquals(List.of("early"), queue.takeDue(20));
        assertEquals(1, queue.size());
        assertEquals(List.of("late"), queue.takeDue(30));
        assertEquals(0, queue.size());
    }

    @Test
    void reschedulingPostponesTheSameKey() {
        DueQueue<String> queue = new DueQueue<>();
        queue.schedule("network", 10);
        queue.schedule("network", 25);

        assertTrue(queue.takeDue(20).isEmpty());
        assertEquals(List.of("network"), queue.takeDue(25));
    }

    @Test
    void keysAreComparedByIdentity() {
        DueQueue<String> queue = new DueQueue<>();
        String first = new String("same");
        String second = new String("same");
        queue.schedule(first, 5);
        queue.schedule(second, 5);

        assertEquals(2, queue.takeDue(5).size());
    }
}
