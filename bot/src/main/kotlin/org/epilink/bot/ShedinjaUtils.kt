package org.epilink.bot

import guru.zoroark.shedinja.extensions.SynchronizedLazyPropertyWrapper
import guru.zoroark.shedinja.extensions.wrapIn
import kotlin.properties.ReadOnlyProperty

internal infix fun <T, V, R : Any> ReadOnlyProperty<T, V>.wrapInLazy(mapper: (V) -> R): SynchronizedLazyPropertyWrapper<T, R> =
    SynchronizedLazyPropertyWrapper(this wrapIn mapper)
