package com.selfcare.app.domain.inci

class InciConflictDetector(private val database: InciDatabase) {

    fun detectWithinProduct(
        productName: String,
        matches: List<InciMatch>,
        slot: String = SLOT_NONE
    ): List<DetectedConflict> {
        if (matches.size < 2) return emptyList()
        val results = mutableListOf<DetectedConflict>()
        val seenKeys = HashSet<String>()

        for (i in 0 until matches.size) {
            for (j in i + 1 until matches.size) {
                val a = matches[i]
                val b = matches[j]
                if (a.ingredientId == b.ingredientId) continue
                collectMatchingRules(
                    matchA = a,
                    matchB = b,
                    productAName = productName,
                    productBName = productName,
                    slot = slot,
                    seenKeys = seenKeys,
                    out = results
                )
            }
        }
        return results
    }

    fun detectAcrossProducts(
        products: List<ProductInciEntry>
    ): List<DetectedConflict> {
        val results = mutableListOf<DetectedConflict>()
        val seenKeys = HashSet<String>()

        for (product in products) {
            for (conflict in detectWithinProduct(product.name, product.matches, product.routineSlot)) {
                val key = conflictKey(conflict)
                if (seenKeys.add(key)) {
                    results.add(conflict)
                }
            }
        }

        for (i in 0 until products.size) {
            for (j in i + 1 until products.size) {
                val prodA = products[i]
                val prodB = products[j]
                val sharedSlots = sharedRoutineSlots(prodA.routineSlot, prodB.routineSlot)
                if (sharedSlots.isEmpty()) continue

                for (slot in sharedSlots) {
                    for (matchA in prodA.matches) {
                        for (matchB in prodB.matches) {
                            if (matchA.ingredientId == matchB.ingredientId) continue
                            collectMatchingRules(
                                matchA = matchA,
                                matchB = matchB,
                                productAName = prodA.name,
                                productBName = prodB.name,
                                slot = slot,
                                seenKeys = seenKeys,
                                out = results
                            )
                        }
                    }
                }
            }
        }

        return results
    }

    private fun collectMatchingRules(
        matchA: InciMatch,
        matchB: InciMatch,
        productAName: String,
        productBName: String,
        slot: String,
        seenKeys: MutableSet<String>,
        out: MutableList<DetectedConflict>
    ) {
        for (rule in database.conflicts) {
            val forward = matchesSide(matchA, rule.categoriesA, rule.ingredientIdsA) &&
                matchesSide(matchB, rule.categoriesB, rule.ingredientIdsB)
            val reverse = matchesSide(matchB, rule.categoriesA, rule.ingredientIdsA) &&
                matchesSide(matchA, rule.categoriesB, rule.ingredientIdsB)

            if (forward || reverse) {
                val conflict = DetectedConflict(
                    ruleId = rule.id,
                    severity = rule.severity,
                    title = rule.title,
                    recommendation = rule.recommendation,
                    productAName = productAName,
                    productBName = productBName,
                    ingredientAName = matchA.canonicalName,
                    ingredientBName = matchB.canonicalName,
                    slot = slot
                )
                if (seenKeys.add(conflictKey(conflict))) {
                    out.add(conflict)
                }
            }
        }
    }

    private fun matchesSide(
        match: InciMatch,
        categories: List<String>,
        ingredientIds: List<String>
    ): Boolean {
        val catMatch = categories.isNotEmpty() && match.category in categories
        val idMatch = ingredientIds.isNotEmpty() && match.ingredientId in ingredientIds
        return catMatch || idMatch
    }

    private fun sharedRoutineSlots(slotA: String, slotB: String): List<String> {
        val a = slotA.uppercase()
        val b = slotB.uppercase()
        if (a == SLOT_NONE || b == SLOT_NONE) return emptyList()

        val slotsA = expandSlot(a)
        val slotsB = expandSlot(b)
        return slotsA.intersect(slotsB).toList()
    }

    private fun expandSlot(slot: String): Set<String> = when (slot) {
        SLOT_MORNING -> setOf(SLOT_MORNING)
        SLOT_EVENING -> setOf(SLOT_EVENING)
        SLOT_BOTH -> setOf(SLOT_MORNING, SLOT_EVENING)
        else -> emptySet()
    }

    private fun conflictKey(c: DetectedConflict): String {
        val (p1, p2) = if (c.productAName <= c.productBName) {
            c.productAName to c.productBName
        } else {
            c.productBName to c.productAName
        }
        val (i1, i2) = if (c.ingredientAName <= c.ingredientBName) {
            c.ingredientAName to c.ingredientBName
        } else {
            c.ingredientBName to c.ingredientAName
        }
        return "${c.ruleId}|${c.slot}|$p1|$p2|$i1|$i2"
    }

    data class ProductInciEntry(
        val id: Long,
        val name: String,
        val routineSlot: String,
        val matches: List<InciMatch>
    )

    companion object {
        const val SLOT_MORNING = "MORNING"
        const val SLOT_EVENING = "EVENING"
        const val SLOT_BOTH = "BOTH"
        const val SLOT_NONE = "NONE"
    }
}
