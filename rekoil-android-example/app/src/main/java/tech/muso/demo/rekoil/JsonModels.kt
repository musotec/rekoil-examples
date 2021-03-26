package tech.muso.demo.rekoil

import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory


/**
 * Following the format of:
 * https://alpaca.markets/docs/api-documentation/api-v2/market-data/alpaca-data-api-v1/bars/
 */
@JsonClass(generateAdapter = true)
data class Bars(@Transient val bars: MutableMap<String, List<AbstractCandle>> = mutableMapOf()) {

    companion object {
        @JvmStatic
        private val TYPE_CANDLE_LIST = Types.newParameterizedType(List::class.java, Candle::class.java)

        @JvmStatic
        private val TYPE_MAP_STOCK_CANDLES = Types.newParameterizedType(Map::class.java, String::class.java, TYPE_CANDLE_LIST)

        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        private val adapter: JsonAdapter<Map<String, List<AbstractCandle>>> = moshi.adapter(TYPE_MAP_STOCK_CANDLES)

        fun Bars.toJson(): String = adapter.toJson(this.bars)
        fun fromJson(string: String): Bars? = adapter.fromJson(string)?.let { Bars(it as MutableMap<String, List<AbstractCandle>>) }
    }

    operator fun get(key: String) = bars[key]
    operator fun set(key: String, value: List<AbstractCandle>) {
        bars[key] = value
    }
}

@JsonClass(generateAdapter = true)
data class Candle(
    @Json(name="t") override val timeSeconds: Int,
    @Json(name="o") override val open: Double,
    @Json(name="h") override val high: Double,
    @Json(name="l") override val low: Double,
    @Json(name="c") override val close: Double,
    @Json(name="v") override val volume: Int
) : AbstractCandle() {
    companion object {
        val NULL_CANDLE = Candle(-1, 0.0, 0.0, 0.0, 0.0, -1)
        fun Candle.isNull() = timeSeconds == -1
    }
}

/** Represents price changes over an unspecified time window. BigDecimal is not used for algorithm performance reasons. */
abstract class AbstractCandle {
    abstract val timeSeconds: Int
    abstract val open: Double
    abstract val high: Double
    abstract val low: Double
    abstract val close: Double
    abstract val volume: Int

    /**
     * Make a new Candle if we aren't a real Candle. (For polymorphic JSON conversion reasons).
     */
    fun toCandleList(): List<Candle> {
        if (this is Candle) return listOf(this)
        return listOf(Candle(timeSeconds, open, high, low, close, volume))
    }
}
