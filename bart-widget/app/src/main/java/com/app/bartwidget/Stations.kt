package com.app.bartwidget

data class Station(val abbr: String, val name: String)

// Bundled from BART's station list (api.bart.gov stn.aspx?cmd=stns) so picking a station needs no
// network and nothing here ever depends on where the phone is.
val STATIONS: List<Station> = listOf(
    Station("12TH", "12th St./Oakland City Center"),
    Station("16TH", "16th St./Mission"),
    Station("19TH", "19th St./Oakland"),
    Station("24TH", "24th St./Mission"),
    Station("ANTC", "Antioch"),
    Station("ASHB", "Ashby"),
    Station("BALB", "Balboa Park"),
    Station("BAYF", "Bay Fair"),
    Station("BERY", "Berryessa/North San Jose"),
    Station("CAST", "Castro Valley"),
    Station("CIVC", "Civic Center/UN Plaza"),
    Station("COLS", "Coliseum"),
    Station("COLM", "Colma"),
    Station("CONC", "Concord"),
    Station("DALY", "Daly City"),
    Station("DBRK", "Downtown Berkeley"),
    Station("DUBL", "Dublin/Pleasanton"),
    Station("DELN", "El Cerrito del Norte"),
    Station("PLZA", "El Cerrito Plaza"),
    Station("EMBR", "Embarcadero"),
    Station("FRMT", "Fremont"),
    Station("FTVL", "Fruitvale"),
    Station("GLEN", "Glen Park"),
    Station("HAYW", "Hayward"),
    Station("LAFY", "Lafayette"),
    Station("LAKE", "Lake Merritt"),
    Station("MCAR", "MacArthur"),
    Station("MLBR", "Millbrae"),
    Station("MLPT", "Milpitas"),
    Station("MONT", "Montgomery St."),
    Station("NBRK", "North Berkeley"),
    Station("NCON", "North Concord/Martinez"),
    Station("OAKL", "Oakland International Airport (OAK)"),
    Station("ORIN", "Orinda"),
    Station("PCTR", "Pittsburg Center"),
    Station("PITT", "Pittsburg/Bay Point"),
    Station("PHIL", "Pleasant Hill/Contra Costa Centre"),
    Station("POWL", "Powell St."),
    Station("RICH", "Richmond"),
    Station("ROCK", "Rockridge"),
    Station("SBRN", "San Bruno"),
    Station("SFIA", "San Francisco International Airport (SFO)"),
    Station("SANL", "San Leandro"),
    Station("SHAY", "South Hayward"),
    Station("SSAN", "South San Francisco"),
    Station("UCTY", "Union City"),
    Station("WCRK", "Walnut Creek"),
    Station("WARM", "Warm Springs/South Fremont"),
    Station("WDUB", "West Dublin/Pleasanton"),
    Station("WOAK", "West Oakland"),
)

private val byAbbr = STATIONS.associateBy { it.abbr }

fun stationName(abbr: String): String = byAbbr[abbr]?.name ?: abbr
