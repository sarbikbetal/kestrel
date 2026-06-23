package com.sarbikbetal.kestrel.domain

/**
 * Maps an aircraft's ICAO 24-bit address (the `hex` field) to its country of
 * registration, using the ICAO address-block allocation. Only well-established
 * ranges are included; unknown addresses return null so we show no flag rather
 * than a wrong one.
 */
data class CountryInfo(val iso2: String, val name: String) {
    /** Unicode regional-indicator flag emoji, e.g. "US" -> the US flag. */
    val flag: String
        get() = iso2.uppercase()
            .map { 0x1F1E6 + (it.code - 'A'.code) }
            .joinToString("") { String(Character.toChars(it)) }
}

object CountryFlags {

    private data class Range(val start: Int, val end: Int, val info: CountryInfo)

    private fun r(start: Int, end: Int, iso2: String, name: String) =
        Range(start, end, CountryInfo(iso2, name))

    // Sorted by start; lookup is a linear scan (small table, runs rarely).
    private val ranges: List<Range> = listOf(
        r(0x008000, 0x00FFFF, "ZA", "South Africa"),
        r(0x010000, 0x017FFF, "EG", "Egypt"),
        r(0x018000, 0x01FFFF, "LY", "Libya"),
        r(0x020000, 0x027FFF, "MA", "Morocco"),
        r(0x028000, 0x02FFFF, "TN", "Tunisia"),
        r(0x0A0000, 0x0A7FFF, "DZ", "Algeria"),
        r(0x0AC000, 0x0ACFFF, "CO", "Colombia"),
        r(0x0D0000, 0x0D7FFF, "MX", "Mexico"),
        r(0x100000, 0x1FFFFF, "RU", "Russia"),
        r(0x201000, 0x2013FF, "NA", "Namibia"),
        r(0x300000, 0x33FFFF, "IT", "Italy"),
        r(0x340000, 0x37FFFF, "ES", "Spain"),
        r(0x380000, 0x3BFFFF, "FR", "France"),
        r(0x3C0000, 0x3FFFFF, "DE", "Germany"),
        r(0x400000, 0x43FFFF, "GB", "United Kingdom"),
        r(0x440000, 0x447FFF, "AT", "Austria"),
        r(0x448000, 0x44FFFF, "BE", "Belgium"),
        r(0x450000, 0x457FFF, "BG", "Bulgaria"),
        r(0x458000, 0x45FFFF, "DK", "Denmark"),
        r(0x460000, 0x467FFF, "FI", "Finland"),
        r(0x468000, 0x46FFFF, "GR", "Greece"),
        r(0x470000, 0x477FFF, "HU", "Hungary"),
        r(0x478000, 0x47FFFF, "NO", "Norway"),
        r(0x480000, 0x487FFF, "NL", "Netherlands"),
        r(0x488000, 0x48FFFF, "PL", "Poland"),
        r(0x490000, 0x497FFF, "PT", "Portugal"),
        r(0x498000, 0x49FFFF, "CZ", "Czechia"),
        r(0x4A0000, 0x4A7FFF, "RO", "Romania"),
        r(0x4A8000, 0x4AFFFF, "SE", "Sweden"),
        r(0x4B0000, 0x4B7FFF, "CH", "Switzerland"),
        r(0x4B8000, 0x4BFFFF, "TR", "Turkey"),
        r(0x4C0000, 0x4C7FFF, "RS", "Serbia"),
        r(0x4CA000, 0x4CAFFF, "IE", "Ireland"),
        r(0x4CC000, 0x4CCFFF, "IS", "Iceland"),
        r(0x4D0000, 0x4D03FF, "LU", "Luxembourg"),
        r(0x500000, 0x5003FF, "SM", "San Marino"),
        r(0x501000, 0x5013FF, "LV", "Latvia"),
        r(0x502000, 0x5023FF, "LT", "Lithuania"),
        r(0x503000, 0x5033FF, "HR", "Croatia"),
        r(0x504000, 0x5043FF, "SI", "Slovenia"),
        r(0x505000, 0x5053FF, "SK", "Slovakia"),
        r(0x508000, 0x50FFFF, "UA", "Ukraine"),
        r(0x510000, 0x5103FF, "BY", "Belarus"),
        r(0x710000, 0x717FFF, "SA", "Saudi Arabia"),
        r(0x718000, 0x71FFFF, "KR", "South Korea"),
        r(0x738000, 0x73FFFF, "IL", "Israel"),
        r(0x740000, 0x747FFF, "BH", "Bahrain"),
        r(0x748000, 0x74FFFF, "QA", "Qatar"),
        r(0x750000, 0x757FFF, "MY", "Malaysia"),
        r(0x758000, 0x75FFFF, "PH", "Philippines"),
        r(0x760000, 0x767FFF, "PK", "Pakistan"),
        r(0x768000, 0x76FFFF, "SG", "Singapore"),
        r(0x780000, 0x7BFFFF, "CN", "China"),
        r(0x7C0000, 0x7FFFFF, "AU", "Australia"),
        r(0x800000, 0x83FFFF, "IN", "India"),
        r(0x840000, 0x87FFFF, "JP", "Japan"),
        r(0x880000, 0x887FFF, "TH", "Thailand"),
        r(0x888000, 0x88FFFF, "VN", "Vietnam"),
        r(0x896000, 0x896FFF, "AE", "United Arab Emirates"),
        r(0x8A0000, 0x8A7FFF, "ID", "Indonesia"),
        r(0xA00000, 0xAFFFFF, "US", "United States"),
        r(0xC00000, 0xC3FFFF, "CA", "Canada"),
        r(0xC80000, 0xC87FFF, "NZ", "New Zealand"),
        r(0xE00000, 0xE3FFFF, "AR", "Argentina"),
        r(0xE40000, 0xE7FFFF, "BR", "Brazil"),
        r(0xE80000, 0xE80FFF, "CL", "Chile"),
        r(0xE84000, 0xE84FFF, "EC", "Ecuador"),
        r(0xE94000, 0xE94FFF, "PE", "Peru"),
    )

    fun lookup(hex: String?): CountryInfo? {
        val code = hex?.trim()?.toIntOrNull(16) ?: return null
        return ranges.firstOrNull { code in it.start..it.end }?.info
    }
}
