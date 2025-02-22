package com.keychenga.util

import java.util.LinkedList

class LimitedLinkedList<E>(
    private val limit: Long,
) : LinkedList<E>() {

    override fun add(element: E): Boolean {
        if (size >= limit && !contains(element)) {
            removeFirst()
        }
        return super.add(element)
    }
}

fun MutableList<String>.getOrEmpty(i: Int): String =
    if (i < kotlin.math.min(8, this.size)) {
        get(i)
    } else {
        println("Exhausted on get, i=$i, size=$size, this=$this")
        ""
    }