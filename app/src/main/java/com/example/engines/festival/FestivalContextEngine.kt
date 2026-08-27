package com.example.engines.festival

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class FestivalRecord(
    val festivalId: String,
    val festivalName: String,
    val date: String, // yyyy-MM-dd or recurring MM-dd
    val country: String = "Global",
    val state: String = "All",
    val city: String = "All",
    val themeMode: String = "DIWALI",
    val description: String = "Festival Celebration",
    val confidence: Float = 1.0f,
    val dataTimestamp: Long = System.currentTimeMillis()
)

enum class FestivalEngineState {
    AVAILABLE,
    UNAVAILABLE,
    NO_LOCATION,
    NO_FESTIVAL,
    DATA_STALE,
    THEME_RESULT_READY,
    ASSET_GENERATING,
    ASSET_READY,
    FALLBACK_ACTIVE,
    WAITING_FOR_OPPORTUNITY
}

object FestivalContextEngine {
    private const val TAG = "FestivalContextEngine"

    private val _engineState = MutableStateFlow(FestivalEngineState.THEME_RESULT_READY)
    val engineState: StateFlow<FestivalEngineState> = _engineState.asStateFlow()

    private val _activeFestivals = MutableStateFlow<List<FestivalRecord>>(emptyList())
    val activeFestivals: StateFlow<List<FestivalRecord>> = _activeFestivals.asStateFlow()

    private val _currentFestival = MutableStateFlow<FestivalRecord?>(null)
    val currentFestival: StateFlow<FestivalRecord?> = _currentFestival.asStateFlow()

    private val _currentLocationInfo = MutableStateFlow(LocationInfo(null, null, null, "UNAVAILABLE", 0L))
    val currentLocationInfo: StateFlow<LocationInfo> = _currentLocationInfo.asStateFlow()

    private var lastSuccessfulImportTimestamp: Long = 0L

    data class LocationInfo(
        val country: String?,
        val state: String?,
        val city: String?,
        val source: String, // "GPS", "CELLULAR_NETWORK", "CACHED", "UNAVAILABLE"
        val timestamp: Long
    )

    private val verifiedFestivalDatabase = mutableListOf(
        FestivalRecord("independence_day", "Independence Day Celebration", "08-15", "India", "All", "All", "INDEPENDENCE", "National freedom day with Tiranga themes"),
        FestivalRecord("raksha_bandhan", "Raksha Bandhan", "08-19", "India", "All", "All", "DIWALI", "Sacred bond of protection and love"),
        FestivalRecord("janmashtami", "Krishna Janmashtami", "08-26", "India", "All", "All", "SOLAR_GOLD", "Celebration of divine joy and light"),
        FestivalRecord("ganesh_utsav", "Ganesh Chaturthi", "09-14", "India", "Maharashtra", "Mumbai", "GANESH_CHATURTHI", "Modak, Marigold and divine wisdom"),
        FestivalRecord("navratri", "Navratri & Durga Puja", "10-12", "India", "All", "All", "NAVRATRI", "9 nights of divine energy, dance, and victory"),
        FestivalRecord("dussehra", "Vijayadashami / Dussehra", "10-21", "India", "All", "All", "SOLAR_GOLD", "Triumph of good over evil"),
        FestivalRecord("diwali", "Diwali Festival of Lights", "11-08", "India", "All", "All", "DIWALI", "Lamps, prosperity, and golden blessings"),
        FestivalRecord("christmas", "Merry Christmas", "12-25", "Global", "All", "All", "CHRISTMAS", "Pine trees, gifts, joy, and peace"),
        FestivalRecord("new_year", "Happy New Year Celebration", "01-01", "Global", "All", "All", "NEW_YEAR", "Sparkles, resolutions, and fresh starts"),
        FestivalRecord("makar_sankranti", "Makar Sankranti & Pongal", "01-14", "India", "All", "All", "MAKAR_SANKRANTI", "Harvest kites and solar transitions"),
        FestivalRecord("republic_day", "Republic Day", "01-26", "India", "All", "All", "INDEPENDENCE", "Constitution Day parade and patriotism"),
        FestivalRecord("maha_shivratri", "Maha Shivratri", "02-26", "India", "All", "All", "AURORA_PURPLE", "Night of cosmic meditation and stillness"),
        FestivalRecord("holi", "Holi Festival of Colors", "03-03", "India", "All", "All", "HOLI", "Splash of vibrant gulal, colors, and joy"),
        FestivalRecord("gudi_padwa", "Gudi Padwa & Ugadi", "03-19", "India", "Maharashtra", "All", "SOLAR_GOLD", "Traditional Spring New Year"),
        FestivalRecord("eid_al_fitr", "Eid-ul-Fitr Mubarak", "04-10", "Global", "All", "All", "EID", "Crescent moon, brotherhood, and blessings"),
        FestivalRecord("baisakhi", "Baisakhi Harvest Festival", "04-14", "India", "Punjab", "All", "SOLAR_GOLD", "Vibrant harvest beats and golden wheat"),
        FestivalRecord("earth_day", "World Earth Day", "04-22", "Global", "All", "All", "FOREST_EMERALD", "Green energy conservation and ecology")
    )

    init {
        // Initial detection
        evaluateTodayFestival()
    }

    fun evaluateTodayFestival(): FestivalRecord? {
        val calendar = Calendar.getInstance()
        val monthDayFormat = SimpleDateFormat("MM-dd", Locale.US)
        val todayMonthDay = monthDayFormat.format(calendar.time)

        val match = verifiedFestivalDatabase.find { it.date == todayMonthDay }

        _currentFestival.value = match
        _activeFestivals.value = if (match != null) listOf(match) else emptyList()
        _engineState.value = if (match != null) FestivalEngineState.THEME_RESULT_READY else FestivalEngineState.NO_FESTIVAL
        return match
    }

    fun importCalendarFestival(name: String, dateMonthDay: String, themeMode: String, description: String) {
        val record = FestivalRecord(
            festivalId = "custom_${System.currentTimeMillis()}",
            festivalName = name,
            date = dateMonthDay,
            themeMode = themeMode,
            description = description
        )
        verifiedFestivalDatabase.add(0, record)
        _currentFestival.value = record
        _activeFestivals.value = listOf(record)
        _engineState.value = FestivalEngineState.THEME_RESULT_READY
        Log.i(TAG, "Imported custom calendar festival: $name ($dateMonthDay, theme: $themeMode)")
    }

    fun evaluateLocationContext(context: Context, lastKnownLocation: Location?) {
        try {
            var country: String? = null
            var state: String? = null
            var city: String? = null
            var source = "UNAVAILABLE"

            if (lastKnownLocation != null) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lastKnownLocation.latitude, lastKnownLocation.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        country = addr.countryName
                        state = addr.adminArea
                        city = addr.locality ?: addr.subAdminArea
                        source = "GPS"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "GPS Geocoder lookup failed", e)
                }
            }

            if (country == null) {
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    val networkCountry = telephonyManager?.networkCountryIso
                    if (!networkCountry.isNullOrEmpty()) {
                        country = Locale("", networkCountry).displayCountry
                        source = "CELLULAR_NETWORK"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Cellular lookup failed", e)
                }
            }

            if (country == null) {
                country = Locale.getDefault().displayCountry
                source = "CACHED_OR_DEFAULT"
            }

            val locInfo = LocationInfo(country, state, city, source, System.currentTimeMillis())
            _currentLocationInfo.value = locInfo
            evaluateTodayFestival()
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating location context", e)
        }
    }
}
