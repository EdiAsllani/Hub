package com.edi.hub.data.model

/** Where a product's details came from. USER rows are the learned household catalog. */
enum class ProductSource { OFF, USER }

/** Named [PantryLocation] to avoid confusion with `android.location.Location`. */
enum class PantryLocation { FRIDGE, FREEZER, PANTRY }

/**
 * Which of the two swipes resolved an item. Recorded beside `consumedAt` because waste
 * data cannot be reconstructed after the fact — see `design/spec.md` §7.5.
 */
enum class Disposition { CONSUMED, DISCARDED }

/** All deadline features are filters over the same due-date primitive. */
enum class DeadlineKind { WARRANTY, DOCUMENT, UPKEEP, BILL, VEHICLE, LENDING }
