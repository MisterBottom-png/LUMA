package com.orbit.app.ui.screens.item

/**
 * A type change replaces the item screen with one for the new type. The Undo for that
 * change is handed over here, so the new screen can offer it.
 */
class PendingTypeChanges {
    private var pending: Pair<Long, TypeConversionSnapshot>? = null

    @Synchronized
    internal fun put(itemId: Long, snapshot: TypeConversionSnapshot) {
        pending = itemId to snapshot
    }

    @Synchronized
    internal fun take(itemId: Long): TypeConversionSnapshot? =
        pending?.takeIf { it.first == itemId }?.second?.also { pending = null }
}
