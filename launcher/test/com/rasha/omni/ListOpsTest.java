package com.rasha.omni;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ListOpsTest {
    static void run() {
        List<String> l = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        T.ok(ListOps.move(l, "b", 1) == 2 && l.equals(Arrays.asList("a", "c", "b", "d")), "move right by one");
        T.ok(ListOps.move(l, "b", -2) == 0 && l.equals(Arrays.asList("b", "a", "c", "d")), "move left clamps at the start");
        T.ok(ListOps.move(l, "d", 5) == 3 && l.equals(Arrays.asList("b", "a", "c", "d")), "move right clamps at the end");
        T.ok(ListOps.move(l, "zzz", 1) == -1 && l.size() == 4, "unknown id is a no-op");
        for (int i = 0; i < 50; i++) ListOps.move(l, "a", (i % 3) - 1);
        T.ok(l.size() == 4 && new java.util.HashSet<>(l).size() == 4, "repeated moves never duplicate or lose items");
    }
}
