package com.unciv.utils

import kotlin.jvm.internal.CallableReference
import kotlin.jvm.internal.ClassBasedDeclarationContainer
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

/**
 *  A replacement for `lazy {}` that allows use as resettable cache.
 *
 *  Typical usage: `val foo by resettableLazy { bar() }; ::foo.resetLazy()`
 *
 *  Code mirrors [SynchronizedLazyImpl] closely, including adapted Kdoc on the accessor extensions.
 *  No support for the other [LazyThreadSafetyMode]s.
 *
 *  - Note this does ***not*** extend the default [Lazy] interface, but it has all the same elements.
 *    When doing so the compiler will redirect instantiation to its own 'publication' implementation.
 */
class ResettableLazy<out T> private constructor(private val initializer: () -> T, lock: Any?) {
    @Volatile
    private var _value: Any? = UninitializedValue

    private val lock = lock ?: this

    val value: T
        get() {
            val v1 = _value
            if (v1 !== UninitializedValue)
                @Suppress("UNCHECKED_CAST")
                return v1 as T
            return synchronized(lock) {
                val v2 = _value
                if (v2 !== UninitializedValue)
                    @Suppress("UNCHECKED_CAST")
                    v2 as T
                else {
                    val newValue = initializer()
                    _value = newValue
                    newValue
                }
            }
        }

    @Suppress("NOTHING_TO_INLINE") // implements the `by` notation and needs this signature
    inline operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    internal fun reset() = synchronized(lock) {
        _value = UninitializedValue
    }

    private fun isInitialized() = _value !== UninitializedValue
    override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."
    private object UninitializedValue

    companion object {
        /**
         * Creates a new instance of the [ResettableLazy] that uses the specified initialization function [initializer].
         *
         * Note: ResettableLazy only supports thread-safety mode [LazyThreadSafetyMode.SYNCHRONIZED].
         * If the initialization of a value throws an exception, it will attempt to reinitialize the value at next access.
         *
         * Note that the returned instance uses itself to synchronize on. Do not synchronize from external code on
         * the returned instance as it may cause accidental deadlock. Also this behavior can be changed in the future.
         */
        fun <T> resettableLazy (initializer: () -> T) = ResettableLazy(initializer, null)

        /**
         * Creates a new instance of the [ResettableLazy] that uses the specified initialization function [initializer].
         *
         * Note: ResettableLazy only supports thread-safety mode [LazyThreadSafetyMode.SYNCHRONIZED].
         * If the initialization of a value throws an exception, it will attempt to reinitialize the value at next access.
         *
         * The returned instance uses the specified [lock] object to synchronize on.
         */
        @Suppress("unused")
        fun <T> resettableLazy (lock: Any, initializer: () -> T) = ResettableLazy(initializer, lock)

        /**
         *  Reset the [ResettableLazy] feeding this field. Usage: `field::resetLazy()`
         *
         *  The cached value is forgotten, and the next time the property is accessed will call the initializer again.
         *
         *  @throws ClassCastException If called on a field not using the [ResettableLazy] delegate.
         *  @throws NullPointerException If called on a non-delegated field.
         */
        fun KProperty0<*>.resetLazy() = delegate().reset()

        /** Detect whether the [ResettableLazy] has been 'tripped' - Usage: `field::isLazyInitialized` */
        val KProperty0<*>.isLazyInitialized: Boolean get() = delegate().isInitialized()

        // This uses internals that may change, but avoids the 'kotlin-reflect' library which may have cross-platform issues
        // It can throw in many places (as, takeIf), but that's safety against stdlib changes
        private fun KProperty0<*>.delegate(): ResettableLazy<*> {
            val ref = this as CallableReference
            val clazz = (ref.owner as ClassBasedDeclarationContainer).jClass
            val fieldName = $$"$$name$delegate"
            val field = generateSequence(clazz) { it.superclass }          // may be declared in a superclass
                .firstNotNullOf { c -> c.declaredFields.firstOrNull { it.name == fieldName } }
            field.isAccessible = true
            val receiver = ref.boundReceiver.takeIf { it !== CallableReference.NO_RECEIVER }
            return field.get(receiver) as ResettableLazy<*>
        }
    }
}
