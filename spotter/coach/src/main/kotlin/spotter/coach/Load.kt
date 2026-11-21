package spotter.coach

/**
 * What was held: [count] dumbbells of [lb] pounds each. "2×25" is two 25 lb dumbbells; "1×40" is
 * one 40 lb dumbbell. Never a bare total, because the same total is a different lift with one
 * dumbbell or two.
 *
 * [count] is null for imported history that never said whether its number was per dumbbell or the
 * total. Then [lb] is only the number as logged: [perHand] and [total] are null, so nothing can
 * step or convert from it, and it is weaker evidence.
 */
data class Load(val lb: Int, val count: Int?) {
    init {
        require(lb > 0) { "lb must be positive: $lb" }
        require(count == null || count == 1 || count == 2) { "count must be 1, 2 or unknown: $count" }
    }

    /** The weight of each dumbbell, or null when the count is unknown. */
    val perHand: Int? get() = if (count == null) null else lb

    val total: Int? get() = count?.let { lb * it }

    /**
     * One dumbbell held in both hands (a goblet squat) moved to two dumbbells: the heaviest pair
     * on the rack whose total is no more than it, so 1×40 becomes 2×20, never 2×35 or 2×40.
     *
     * Only for that shared-dumbbell case. A one-dumbbell, one-hand lift (a single-arm row) says
     * how much one hand moves, and relating it to a two-dumbbell lift is a variation factor's job,
     * not a load conversion. Null when this isn't one dumbbell or nothing on the rack fits.
     */
    fun splitSharedDumbbell(): Load? {
        if (count != 1) return null
        val each = Rack.atMost(lb / 2.0) ?: return null
        return Load(each, 2)
    }

    /** How the sheet writes it: "2×25", or "40 lb (count?)" when the count is unknown. */
    val label: String get() = if (count == null) "$lb lb (count?)" else "$count×$lb"
}
