package com.getcode.util

infix fun <A, B, C> Pair<A, B>.to(that: C): Triple<A, B, C> = Triple(first, second, that)