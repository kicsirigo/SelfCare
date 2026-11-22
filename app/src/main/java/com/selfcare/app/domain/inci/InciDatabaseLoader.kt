package com.selfcare.app.domain.inci

import android.content.Context
import android.content.res.AssetManager
import kotlinx.serialization.json.Json

object InciDatabaseLoader {
    const val DEFAULT_ASSET_NAME = "inci_database.json"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun loadFromJson(rawJson: String): InciDatabase {
        return json.decodeFromString(InciDatabase.serializer(), rawJson)
    }

    fun loadFromAssets(
        assetManager: AssetManager,
        assetName: String = DEFAULT_ASSET_NAME
    ): InciDatabase {
        val rawJson = assetManager.open(assetName).bufferedReader().use { it.readText() }
        return loadFromJson(rawJson)
    }

    fun loadFromContext(
        context: Context,
        assetName: String = DEFAULT_ASSET_NAME
    ): InciDatabase = loadFromAssets(context.assets, assetName)
}
