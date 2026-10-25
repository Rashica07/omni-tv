package com.rasha.omni.gallery

import java.util.Random

/** The order photos are shown in. Pure, so it is tested. Shuffle never repeats a photo until all have been shown, and never shows the same one twice in a row across rounds. */
class SlideOrder(private val size: Int, private val shuffle: Boolean, seed: Long = System.nanoTime()) {
    private val rnd = Random(seed)
    private var round = ArrayList<Int>()
    private var pos = 0
    private var last = -1

    fun next(): Int {
        if (size <= 0) return -1
        if (pos >= round.size) {
            round = ArrayList((0 until size).toList())
            if (shuffle) round.shuffle(rnd)
            if (shuffle && size > 1 && round[0] == last) java.util.Collections.swap(round, 0, 1 + rnd.nextInt(size - 1))
            pos = 0
        }
        last = round[pos++]
        return last
    }
}
