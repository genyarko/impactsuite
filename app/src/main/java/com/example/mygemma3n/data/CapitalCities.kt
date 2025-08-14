package com.example.mygemma3n.data

import android.location.Location
import kotlin.math.*

/**
 * Capital Cities Database
 * Contains coordinates for all world capitals to help approximate user location
 * for online hospital searches when precise local data isn't available.
 */

data class CapitalCity(
    val country: String,
    val capital: String,
    val latitude: Double,
    val longitude: Double,
    val continent: String,
    val timeZone: String
) {
    /**
     * Calculate distance to this capital from given coordinates
     */
    fun distanceFrom(latitude: Double, longitude: Double): Double {
        val location1 = Location("").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        val location2 = Location("").apply {
            this.latitude = this@CapitalCity.latitude
            this.longitude = this@CapitalCity.longitude
        }
        return (location1.distanceTo(location2) / 1000.0) // Convert to kilometers
    }
}

object CapitalCities {
    
    private val capitals = listOf(
        // Africa
        CapitalCity("Algeria", "Algiers", 36.7538, 3.0588, "Africa", "CET"),
        CapitalCity("Angola", "Luanda", -8.8390, 13.2894, "Africa", "WAT"),
        CapitalCity("Benin", "Porto-Novo", 6.4969, 2.6283, "Africa", "WAT"),
        CapitalCity("Botswana", "Gaborone", -24.6282, 25.9231, "Africa", "CAT"),
        CapitalCity("Burkina Faso", "Ouagadougou", 12.3714, -1.5197, "Africa", "GMT"),
        CapitalCity("Burundi", "Gitega", -3.4264, 29.9306, "Africa", "CAT"),
        CapitalCity("Cameroon", "Yaoundé", 3.8480, 11.5021, "Africa", "WAT"),
        CapitalCity("Cape Verde", "Praia", 14.9177, -23.5092, "Africa", "CVT"),
        CapitalCity("Central African Republic", "Bangui", 4.3947, 18.5582, "Africa", "WAT"),
        CapitalCity("Chad", "N'Djamena", 12.1348, 15.0557, "Africa", "WAT"),
        CapitalCity("Comoros", "Moroni", -11.7172, 43.2473, "Africa", "EAT"),
        CapitalCity("Democratic Republic of the Congo", "Kinshasa", -4.4419, 15.2663, "Africa", "WAT"),
        CapitalCity("Republic of the Congo", "Brazzaville", -4.2634, 15.2429, "Africa", "WAT"),
        CapitalCity("Djibouti", "Djibouti", 11.8251, 42.5903, "Africa", "EAT"),
        CapitalCity("Egypt", "Cairo", 30.0444, 31.2357, "Africa", "EET"),
        CapitalCity("Equatorial Guinea", "Malabo", 3.7558, 8.7821, "Africa", "WAT"),
        CapitalCity("Eritrea", "Asmara", 15.3229, 38.9251, "Africa", "EAT"),
        CapitalCity("Eswatini", "Mbabane", -26.3054, 31.1367, "Africa", "SAST"),
        CapitalCity("Ethiopia", "Addis Ababa", 9.1450, 40.4897, "Africa", "EAT"),
        CapitalCity("Gabon", "Libreville", 0.4162, 9.4673, "Africa", "WAT"),
        CapitalCity("Gambia", "Banjul", 13.4549, -16.5790, "Africa", "GMT"),
        CapitalCity("Ghana", "Accra", 5.6037, -0.1870, "Africa", "GMT"),
        CapitalCity("Guinea", "Conakry", 9.6412, -13.5784, "Africa", "GMT"),
        CapitalCity("Guinea-Bissau", "Bissau", 11.8816, -15.6180, "Africa", "GMT"),
        CapitalCity("Ivory Coast", "Yamoussoukro", 6.8276, -5.2893, "Africa", "GMT"),
        CapitalCity("Kenya", "Nairobi", -1.2864, 36.8172, "Africa", "EAT"),
        CapitalCity("Lesotho", "Maseru", -29.3151, 27.4869, "Africa", "SAST"),
        CapitalCity("Liberia", "Monrovia", 6.2907, -10.7605, "Africa", "GMT"),
        CapitalCity("Libya", "Tripoli", 32.8872, 13.1913, "Africa", "EET"),
        CapitalCity("Madagascar", "Antananarivo", -18.8792, 47.5079, "Africa", "EAT"),
        CapitalCity("Malawi", "Lilongwe", -13.9626, 33.7741, "Africa", "CAT"),
        CapitalCity("Mali", "Bamako", 12.6392, -8.0029, "Africa", "GMT"),
        CapitalCity("Mauritania", "Nouakchott", 18.0735, -15.9582, "Africa", "GMT"),
        CapitalCity("Mauritius", "Port Louis", -20.1609, 57.5012, "Africa", "MUT"),
        CapitalCity("Morocco", "Rabat", 34.0209, -6.8416, "Africa", "WET"),
        CapitalCity("Mozambique", "Maputo", -25.9692, 32.5732, "Africa", "CAT"),
        CapitalCity("Namibia", "Windhoek", -22.9576, 17.0832, "Africa", "CAT"),
        CapitalCity("Niger", "Niamey", 13.5116, 2.1254, "Africa", "WAT"),
        CapitalCity("Nigeria", "Abuja", 9.0765, 7.3986, "Africa", "WAT"),
        CapitalCity("Rwanda", "Kigali", -1.9441, 30.0619, "Africa", "CAT"),
        CapitalCity("São Tomé and Príncipe", "São Tomé", 0.1864, 6.6131, "Africa", "GMT"),
        CapitalCity("Senegal", "Dakar", 14.7167, -17.4677, "Africa", "GMT"),
        CapitalCity("Seychelles", "Victoria", -4.6796, 55.4920, "Africa", "SCT"),
        CapitalCity("Sierra Leone", "Freetown", 8.4657, -13.2317, "Africa", "GMT"),
        CapitalCity("Somalia", "Mogadishu", 2.0469, 45.3182, "Africa", "EAT"),
        CapitalCity("South Africa", "Cape Town", -33.9249, 18.4241, "Africa", "SAST"),
        CapitalCity("South Sudan", "Juba", 4.8594, 31.5713, "Africa", "EAT"),
        CapitalCity("Sudan", "Khartoum", 15.5007, 32.5599, "Africa", "CAT"),
        CapitalCity("Tanzania", "Dodoma", -6.1630, 35.7516, "Africa", "EAT"),
        CapitalCity("Togo", "Lomé", 6.1256, 1.2255, "Africa", "GMT"),
        CapitalCity("Tunisia", "Tunis", 36.8065, 10.1815, "Africa", "CET"),
        CapitalCity("Uganda", "Kampala", 0.3476, 32.5825, "Africa", "EAT"),
        CapitalCity("Zambia", "Lusaka", -15.3875, 28.3228, "Africa", "CAT"),
        CapitalCity("Zimbabwe", "Harare", -17.8252, 31.0335, "Africa", "CAT"),

        // Asia
        CapitalCity("Afghanistan", "Kabul", 34.5553, 69.2075, "Asia", "AFT"),
        CapitalCity("Armenia", "Yerevan", 40.0691, 44.5151, "Asia", "AMT"),
        CapitalCity("Azerbaijan", "Baku", 40.4093, 49.8671, "Asia", "AZT"),
        CapitalCity("Bahrain", "Manama", 26.0667, 50.5577, "Asia", "AST"),
        CapitalCity("Bangladesh", "Dhaka", 23.8103, 90.4125, "Asia", "BST"),
        CapitalCity("Bhutan", "Thimphu", 27.4728, 89.6390, "Asia", "BTT"),
        CapitalCity("Brunei", "Bandar Seri Begawan", 4.5353, 114.7277, "Asia", "BNT"),
        CapitalCity("Cambodia", "Phnom Penh", 11.5449, 104.8922, "Asia", "ICT"),
        CapitalCity("China", "Beijing", 39.9042, 116.4074, "Asia", "CST"),
        CapitalCity("Cyprus", "Nicosia", 35.1856, 33.3823, "Asia", "EET"),
        CapitalCity("Georgia", "Tbilisi", 41.7151, 44.8271, "Asia", "GET"),
        CapitalCity("India", "New Delhi", 28.6139, 77.2090, "Asia", "IST"),
        CapitalCity("Indonesia", "Jakarta", -6.2088, 106.8456, "Asia", "WIB"),
        CapitalCity("Iran", "Tehran", 35.6892, 51.3890, "Asia", "IRST"),
        CapitalCity("Iraq", "Baghdad", 33.3152, 44.3661, "Asia", "AST"),
        CapitalCity("Israel", "Jerusalem", 31.7683, 35.2137, "Asia", "IST"),
        CapitalCity("Japan", "Tokyo", 35.6762, 139.6503, "Asia", "JST"),
        CapitalCity("Jordan", "Amman", 31.9454, 35.9284, "Asia", "EET"),
        CapitalCity("Kazakhstan", "Nur-Sultan", 51.1605, 71.4704, "Asia", "ALMT"),
        CapitalCity("Kuwait", "Kuwait City", 29.3759, 47.9774, "Asia", "AST"),
        CapitalCity("Kyrgyzstan", "Bishkek", 42.8746, 74.5698, "Asia", "KGT"),
        CapitalCity("Laos", "Vientiane", 17.9757, 102.6331, "Asia", "ICT"),
        CapitalCity("Lebanon", "Beirut", 33.8938, 35.5018, "Asia", "EET"),
        CapitalCity("Malaysia", "Kuala Lumpur", 3.1390, 101.6869, "Asia", "MYT"),
        CapitalCity("Maldives", "Malé", 4.1755, 73.5093, "Asia", "MVT"),
        CapitalCity("Mongolia", "Ulaanbaatar", 47.8864, 106.9057, "Asia", "ULAT"),
        CapitalCity("Myanmar", "Naypyidaw", 19.7633, 96.0785, "Asia", "MMT"),
        CapitalCity("Nepal", "Kathmandu", 27.7172, 85.3240, "Asia", "NPT"),
        CapitalCity("North Korea", "Pyongyang", 39.0392, 125.7625, "Asia", "KST"),
        CapitalCity("Oman", "Muscat", 23.5859, 58.4059, "Asia", "GST"),
        CapitalCity("Pakistan", "Islamabad", 33.7294, 73.0931, "Asia", "PKT"),
        CapitalCity("Palestine", "Ramallah", 31.9073, 35.2034, "Asia", "EET"),
        CapitalCity("Philippines", "Manila", 14.5995, 120.9842, "Asia", "PHT"),
        CapitalCity("Qatar", "Doha", 25.2854, 51.5310, "Asia", "AST"),
        CapitalCity("Saudi Arabia", "Riyadh", 24.7136, 46.6753, "Asia", "AST"),
        CapitalCity("Singapore", "Singapore", 1.3521, 103.8198, "Asia", "SGT"),
        CapitalCity("South Korea", "Seoul", 37.5665, 126.9780, "Asia", "KST"),
        CapitalCity("Sri Lanka", "Colombo", 6.9271, 79.8612, "Asia", "SLST"),
        CapitalCity("Syria", "Damascus", 33.5138, 36.2765, "Asia", "EET"),
        CapitalCity("Taiwan", "Taipei", 25.0330, 121.5654, "Asia", "CST"),
        CapitalCity("Tajikistan", "Dushanbe", 38.5598, 68.7870, "Asia", "TJT"),
        CapitalCity("Thailand", "Bangkok", 13.7563, 100.5018, "Asia", "ICT"),
        CapitalCity("Timor-Leste", "Dili", -8.5569, 125.5603, "Asia", "TLT"),
        CapitalCity("Turkey", "Ankara", 39.9334, 32.8597, "Asia", "TRT"),
        CapitalCity("Turkmenistan", "Ashgabat", 37.9601, 58.3261, "Asia", "TMT"),
        CapitalCity("United Arab Emirates", "Abu Dhabi", 24.2992, 54.6975, "Asia", "GST"),
        CapitalCity("Uzbekistan", "Tashkent", 41.2995, 69.2401, "Asia", "UZT"),
        CapitalCity("Vietnam", "Hanoi", 21.0285, 105.8542, "Asia", "ICT"),
        CapitalCity("Yemen", "Sana'a", 15.3694, 44.1910, "Asia", "AST"),

        // Europe
        CapitalCity("Albania", "Tirana", 41.3275, 19.8189, "Europe", "CET"),
        CapitalCity("Andorra", "Andorra la Vella", 42.5063, 1.5218, "Europe", "CET"),
        CapitalCity("Austria", "Vienna", 48.2082, 16.3738, "Europe", "CET"),
        CapitalCity("Belarus", "Minsk", 53.9045, 27.5615, "Europe", "MSK"),
        CapitalCity("Belgium", "Brussels", 50.8503, 4.3517, "Europe", "CET"),
        CapitalCity("Bosnia and Herzegovina", "Sarajevo", 43.8486, 18.3564, "Europe", "CET"),
        CapitalCity("Bulgaria", "Sofia", 42.6977, 23.3219, "Europe", "EET"),
        CapitalCity("Croatia", "Zagreb", 45.8150, 15.9819, "Europe", "CET"),
        CapitalCity("Czech Republic", "Prague", 50.0755, 14.4378, "Europe", "CET"),
        CapitalCity("Denmark", "Copenhagen", 55.6761, 12.5683, "Europe", "CET"),
        CapitalCity("Estonia", "Tallinn", 59.4370, 24.7536, "Europe", "EET"),
        CapitalCity("Finland", "Helsinki", 60.1699, 24.9384, "Europe", "EET"),
        CapitalCity("France", "Paris", 48.8566, 2.3522, "Europe", "CET"),
        CapitalCity("Germany", "Berlin", 52.5200, 13.4050, "Europe", "CET"),
        CapitalCity("Greece", "Athens", 37.9838, 23.7275, "Europe", "EET"),
        CapitalCity("Hungary", "Budapest", 47.4979, 19.0402, "Europe", "CET"),
        CapitalCity("Iceland", "Reykjavik", 64.1466, -21.9426, "Europe", "GMT"),
        CapitalCity("Ireland", "Dublin", 53.3498, -6.2603, "Europe", "GMT"),
        CapitalCity("Italy", "Rome", 41.9028, 12.4964, "Europe", "CET"),
        CapitalCity("Latvia", "Riga", 56.9496, 24.1052, "Europe", "EET"),
        CapitalCity("Liechtenstein", "Vaduz", 47.1410, 9.5209, "Europe", "CET"),
        CapitalCity("Lithuania", "Vilnius", 54.6872, 25.2797, "Europe", "EET"),
        CapitalCity("Luxembourg", "Luxembourg", 49.6117, 6.1319, "Europe", "CET"),
        CapitalCity("Malta", "Valletta", 35.8989, 14.5146, "Europe", "CET"),
        CapitalCity("Moldova", "Chișinău", 47.0105, 28.8638, "Europe", "EET"),
        CapitalCity("Monaco", "Monaco", 43.7384, 7.4246, "Europe", "CET"),
        CapitalCity("Montenegro", "Podgorica", 42.4304, 19.2594, "Europe", "CET"),
        CapitalCity("Netherlands", "Amsterdam", 52.3676, 4.9041, "Europe", "CET"),
        CapitalCity("North Macedonia", "Skopje", 41.9973, 21.4280, "Europe", "CET"),
        CapitalCity("Norway", "Oslo", 59.9139, 10.7522, "Europe", "CET"),
        CapitalCity("Poland", "Warsaw", 52.2297, 21.0122, "Europe", "CET"),
        CapitalCity("Portugal", "Lisbon", 38.7223, -9.1393, "Europe", "WET"),
        CapitalCity("Romania", "Bucharest", 44.4268, 26.1025, "Europe", "EET"),
        CapitalCity("Russia", "Moscow", 55.7558, 37.6176, "Europe", "MSK"),
        CapitalCity("San Marino", "San Marino", 43.9424, 12.4578, "Europe", "CET"),
        CapitalCity("Serbia", "Belgrade", 44.7866, 20.4489, "Europe", "CET"),
        CapitalCity("Slovakia", "Bratislava", 48.1486, 17.1077, "Europe", "CET"),
        CapitalCity("Slovenia", "Ljubljana", 46.0569, 14.5058, "Europe", "CET"),
        CapitalCity("Spain", "Madrid", 40.4168, -3.7038, "Europe", "CET"),
        CapitalCity("Sweden", "Stockholm", 59.3293, 18.0686, "Europe", "CET"),
        CapitalCity("Switzerland", "Bern", 46.9480, 7.4474, "Europe", "CET"),
        CapitalCity("Ukraine", "Kyiv", 50.4501, 30.5234, "Europe", "EET"),
        CapitalCity("United Kingdom", "London", 51.5074, -0.1278, "Europe", "GMT"),
        CapitalCity("Vatican City", "Vatican City", 41.9029, 12.4534, "Europe", "CET"),

        // North America
        CapitalCity("Antigua and Barbuda", "Saint John's", 17.1274, -61.8468, "North America", "AST"),
        CapitalCity("Bahamas", "Nassau", 25.0443, -77.3504, "North America", "EST"),
        CapitalCity("Barbados", "Bridgetown", 13.1939, -59.5432, "North America", "AST"),
        CapitalCity("Belize", "Belmopan", 17.2510, -88.7590, "North America", "CST"),
        CapitalCity("Canada", "Ottawa", 45.4215, -75.6972, "North America", "EST"),
        CapitalCity("Costa Rica", "San José", 9.9281, -84.0907, "North America", "CST"),
        CapitalCity("Cuba", "Havana", 23.1136, -82.3666, "North America", "CST"),
        CapitalCity("Dominica", "Roseau", 15.2976, -61.3900, "North America", "AST"),
        CapitalCity("Dominican Republic", "Santo Domingo", 18.4861, -69.9312, "North America", "AST"),
        CapitalCity("El Salvador", "San Salvador", 13.6929, -89.2182, "North America", "CST"),
        CapitalCity("Grenada", "Saint George's", 12.0564, -61.7485, "North America", "AST"),
        CapitalCity("Guatemala", "Guatemala City", 14.6349, -90.5069, "North America", "CST"),
        CapitalCity("Haiti", "Port-au-Prince", 18.5944, -72.3074, "North America", "EST"),
        CapitalCity("Honduras", "Tegucigalpa", 14.0723, -87.1921, "North America", "CST"),
        CapitalCity("Jamaica", "Kingston", 17.9970, -76.7936, "North America", "EST"),
        CapitalCity("Mexico", "Mexico City", 19.4326, -99.1332, "North America", "CST"),
        CapitalCity("Nicaragua", "Managua", 12.1364, -86.2514, "North America", "CST"),
        CapitalCity("Panama", "Panama City", 8.5380, -79.3762, "North America", "EST"),
        CapitalCity("Saint Kitts and Nevis", "Basseterre", 17.2948, -62.7258, "North America", "AST"),
        CapitalCity("Saint Lucia", "Castries", 14.0101, -60.9875, "North America", "AST"),
        CapitalCity("Saint Vincent and the Grenadines", "Kingstown", 13.1579, -61.2248, "North America", "AST"),
        CapitalCity("Trinidad and Tobago", "Port of Spain", 10.6596, -61.5089, "North America", "AST"),
        CapitalCity("United States", "Washington, D.C.", 38.9072, -77.0369, "North America", "EST"),

        // South America
        CapitalCity("Argentina", "Buenos Aires", -34.6118, -58.3960, "South America", "ART"),
        CapitalCity("Bolivia", "Sucre", -19.0196, -65.2619, "South America", "BOT"),
        CapitalCity("Brazil", "Brasília", -15.8267, -47.9218, "South America", "BRT"),
        CapitalCity("Chile", "Santiago", -33.4489, -70.6693, "South America", "CLT"),
        CapitalCity("Colombia", "Bogotá", 4.7110, -74.0721, "South America", "COT"),
        CapitalCity("Ecuador", "Quito", -0.1807, -78.4678, "South America", "ECT"),
        CapitalCity("Guyana", "Georgetown", 6.8013, -58.1551, "South America", "GYT"),
        CapitalCity("Paraguay", "Asunción", -25.2637, -57.5759, "South America", "PYT"),
        CapitalCity("Peru", "Lima", -12.0464, -77.0428, "South America", "PET"),
        CapitalCity("Suriname", "Paramaribo", 5.8520, -55.2038, "South America", "SRT"),
        CapitalCity("Uruguay", "Montevideo", -34.9011, -56.1645, "South America", "UYT"),
        CapitalCity("Venezuela", "Caracas", 10.4806, -66.9036, "South America", "VET"),

        // Oceania
        CapitalCity("Australia", "Canberra", -35.2809, 149.1300, "Oceania", "AEDT"),
        CapitalCity("Fiji", "Suva", -18.1248, 178.4501, "Oceania", "FJT"),
        CapitalCity("Kiribati", "Tarawa", 1.4518, 172.9717, "Oceania", "GILT"),
        CapitalCity("Marshall Islands", "Majuro", 7.1315, 171.1845, "Oceania", "MHT"),
        CapitalCity("Micronesia", "Palikir", 6.9248, 158.1611, "Oceania", "PONT"),
        CapitalCity("Nauru", "Yaren", -0.5477, 166.9209, "Oceania", "NRT"),
        CapitalCity("New Zealand", "Wellington", -41.2924, 174.7787, "Oceania", "NZDT"),
        CapitalCity("Palau", "Ngerulmud", 7.5006, 134.6244, "Oceania", "PWT"),
        CapitalCity("Papua New Guinea", "Port Moresby", -9.4438, 147.1803, "Oceania", "PGT"),
        CapitalCity("Samoa", "Apia", -13.8506, -171.7513, "Oceania", "WST"),
        CapitalCity("Solomon Islands", "Honiara", -9.4280, 159.9729, "Oceania", "SBT"),
        CapitalCity("Tonga", "Nuku'alofa", -21.1789, -175.1982, "Oceania", "TOT"),
        CapitalCity("Tuvalu", "Funafuti", -8.5243, 179.1942, "Oceania", "TVT"),
        CapitalCity("Vanuatu", "Port Vila", -17.7334, 168.3273, "Oceania", "VUT")
    )

    /**
     * Find the nearest capital city to given coordinates
     */
    fun findNearestCapital(latitude: Double, longitude: Double): CapitalCity {
        return capitals.minByOrNull { it.distanceFrom(latitude, longitude) }
            ?: capitals.first() // Fallback to first capital if something goes wrong
    }

    /**
     * Find the nearest capitals within a given radius (in kilometers)
     */
    fun findNearestCapitals(latitude: Double, longitude: Double, maxDistanceKm: Double = 1000.0): List<CapitalCity> {
        return capitals
            .filter { it.distanceFrom(latitude, longitude) <= maxDistanceKm }
            .sortedBy { it.distanceFrom(latitude, longitude) }
    }

    /**
     * Get all capitals in a specific continent
     */
    fun getCapitalsByContinent(continent: String): List<CapitalCity> {
        return capitals.filter { it.continent.equals(continent, ignoreCase = true) }
    }

    /**
     * Get a capital by country name
     */
    fun getCapitalByCountry(country: String): CapitalCity? {
        return capitals.find { it.country.equals(country, ignoreCase = true) }
    }

    /**
     * Get all capitals (for debugging/reference)
     */
    fun getAllCapitals(): List<CapitalCity> = capitals

    /**
     * Search capitals by name (case-insensitive)
     */
    fun searchCapitals(query: String): List<CapitalCity> {
        return capitals.filter { 
            it.capital.contains(query, ignoreCase = true) || 
            it.country.contains(query, ignoreCase = true) 
        }
    }
}