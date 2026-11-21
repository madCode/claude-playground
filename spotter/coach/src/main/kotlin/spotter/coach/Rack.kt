package spotter.coach

/**
 * The studio's dumbbells, in pounds. One "step" is the next one in this list, not a fixed amount.
 * Dumbbells only: a Y-bell is another implement, and stepping it onto this rack would mix the two.
 */
object Rack {
    val dumbbells: List<Int> = listOf(3, 5, 8, 10, 12, 15, 20, 25, 30, 35, 40, 45, 50)

    /** The next dumbbell heavier than [lb], or null above the heaviest. */
    fun up(lb: Int): Int? = dumbbells.firstOrNull { it > lb }

    /** The next dumbbell lighter than [lb], or null below the lightest. */
    fun down(lb: Int): Int? = dumbbells.lastOrNull { it < lb }

    /** The heaviest dumbbell no heavier than [lb], or null if even the lightest is too heavy. */
    fun atMost(lb: Double): Int? = dumbbells.lastOrNull { it <= lb }
}
