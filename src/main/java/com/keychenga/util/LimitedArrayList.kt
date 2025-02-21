package com.keychenga.util

class LimitedArrayList<E>(
    private val limit: Int,
) : ArrayList<E>(limit) {

    override fun add(element: E): Boolean {
        if (size >= limit && !contains(element)) {
            removeFirst()
        }
        return super.add(element)
    }
}