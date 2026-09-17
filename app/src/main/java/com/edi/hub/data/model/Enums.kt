package com.edi.hub.data.model

/** Where a product's details came from. USER rows are the learned household catalog. */
enum class ProductSource { OFF, USER }

/**
 * Named [PantryUnit] rather than `Unit`: an `enum class Unit` in this package would
 * shadow `kotlin.Unit` for every file in it.
 */
enum class PantryUnit { PCS, G, ML }

/** Named [PantryLocation] to avoid confusion with `android.location.Location`. */
enum class PantryLocation { FRIDGE, FREEZER, PANTRY }
