package com.dashline.launcher

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class AirportReference(
    val name: String,
    val iata: String,
    val icao: String,
    val city: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
)

class TravelReferenceDatabase(private val appContext: Context) : SQLiteOpenHelper(appContext, "travel-reference.db", null, 1) {
    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE airports (iata TEXT PRIMARY KEY, name TEXT NOT NULL, icao TEXT, city TEXT, country TEXT, lat REAL NOT NULL, lon REAL NOT NULL)")
        database.execSQL("CREATE TABLE manual_stations (id INTEGER PRIMARY KEY AUTOINCREMENT, query TEXT NOT NULL COLLATE NOCASE UNIQUE, created_at INTEGER NOT NULL)")
        database.execSQL("CREATE INDEX manual_stations_query_idx ON manual_stations(query COLLATE NOCASE)")
        appContext.assets.open("airport_reference.csv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                val columns = line.split(',')
                if (columns.size == 7) {
                    val values = ContentValues().apply {
                        put("name", columns[0]); put("iata", columns[1]); put("icao", columns[2])
                        put("city", columns[3]); put("country", columns[4])
                        put("lat", columns[5].toDoubleOrNull()); put("lon", columns[6].toDoubleOrNull())
                    }
                    database.insertWithOnConflict("airports", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
        }
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun findAirports(query: String): List<AirportReference> {
        val term = "%${query.trim().replace("%", "\\%").replace("_", "\\_")}%"
        return readableDatabase.rawQuery(
            "SELECT name,iata,icao,city,country,lat,lon FROM airports WHERE iata LIKE ? OR icao LIKE ? OR name LIKE ? OR city LIKE ? ORDER BY CASE WHEN iata = ? THEN 0 ELSE 1 END, name LIMIT 12",
            arrayOf(query.trim().uppercase(), query.trim().uppercase(), term, term, query.trim().uppercase())
        ).use { cursor ->
            buildListCompat {
                while (cursor.moveToNext()) add(AirportReference(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getDouble(5), cursor.getDouble(6)))
            }
        }
    }

    fun saveManualStation(value: String): Boolean {
        val normalized = value.trim().take(100)
        if (normalized.isBlank()) return false
        val values = ContentValues().apply { put("query", normalized); put("created_at", System.currentTimeMillis()) }
        writableDatabase.insertWithOnConflict("manual_stations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        writableDatabase.execSQL("DELETE FROM manual_stations WHERE id NOT IN (SELECT id FROM manual_stations ORDER BY created_at DESC LIMIT 128)")
        return true
    }

    fun findManualStations(query: String): List<String> {
        val term = "%${query.trim().replace("%", "\\%").replace("_", "\\_")}%"
        return readableDatabase.rawQuery("SELECT query FROM manual_stations WHERE query LIKE ? ORDER BY query LIMIT 12", arrayOf(term)).use { cursor ->
            buildListCompat { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }

    private fun <T> buildListCompat(block: MutableList<T>.() -> Unit): List<T> = ArrayList<T>().apply(block)
}
