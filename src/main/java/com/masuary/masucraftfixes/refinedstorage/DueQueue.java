package com.masuary.masucraftfixes.refinedstorage;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Keys (compared by identity) that become due at a game time; scheduling a key again moves its due time. */
public final class DueQueue<KEY> {

    private final Map<KEY, Long> dueTimes = new IdentityHashMap<>();

    public void schedule(KEY key, long dueTime) {
        dueTimes.put(key, dueTime);
    }

    /** Removes and returns every key due at or before {@code now}; callers may schedule again while handling them. */
    public List<KEY> takeDue(long now) {
        List<KEY> dueKeys = new ArrayList<>();
        for (Map.Entry<KEY, Long> entry : dueTimes.entrySet()) {
            if (entry.getValue() <= now) {
                dueKeys.add(entry.getKey());
            }
        }
        for (KEY dueKey : dueKeys) {
            dueTimes.remove(dueKey);
        }
        return dueKeys;
    }

    public int size() {
        return dueTimes.size();
    }
}
