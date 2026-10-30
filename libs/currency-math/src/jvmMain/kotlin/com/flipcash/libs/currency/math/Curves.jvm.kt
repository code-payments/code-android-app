package com.flipcash.libs.currency.math

import com.flipcash.libs.currency.math.internal.loader.ClasspathTableLoader

/** Reads the tables the desktop build packs as JVM resources (the same files Android ships as assets). */
suspend fun Curves.initialize() = initialize(ClasspathTableLoader())
