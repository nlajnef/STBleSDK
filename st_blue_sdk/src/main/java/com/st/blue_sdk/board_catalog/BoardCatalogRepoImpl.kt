/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */
package com.st.blue_sdk.board_catalog

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.content.getSystemService
import com.st.blue_sdk.BuildConfig
import com.st.blue_sdk.board_catalog.api.BoardCatalogApi
import com.st.blue_sdk.board_catalog.api.di.StAppVersion
import com.st.blue_sdk.board_catalog.db.BoardCatalogDao
import com.st.blue_sdk.board_catalog.di.Preferences
import com.st.blue_sdk.board_catalog.models.BoardCatalog
import com.st.blue_sdk.board_catalog.models.BoardDescription
import com.st.blue_sdk.board_catalog.models.BoardFirmware
import com.st.blue_sdk.board_catalog.models.DtmiContent
import com.st.blue_sdk.board_catalog.models.DtmiModel
import com.st.blue_sdk.board_catalog.models.toDtmiContent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.nio.charset.StandardCharsets
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardCatalogRepoImpl @Inject constructor(
    private val api: BoardCatalogApi,
    private val db: BoardCatalogDao,
    private val json: Json,
    private val coroutineScope: CoroutineScope,
    @StAppVersion private val stAppVersion: String,
    @Preferences private val pref: SharedPreferences,
    @ApplicationContext private val context: Context
) : BoardCatalogRepo {

    private var dtmiModelCache: MutableList<DtmiModel> = mutableListOf()
    private var cache: MutableList<BoardFirmware> = mutableListOf()
    private var cacheDescription: MutableList<BoardDescription> = mutableListOf()

    private val mutex = Mutex()

    init {
        coroutineScope.launch {
            getBoardCatalog()
        }
    }

    @Suppress("DEPRECATION")
    private val ConnectivityManager?.isCurrentlyConnected: Boolean
        get() = when (this) {
            null -> false
            else -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    activeNetwork
                        ?.let(::getNetworkCapabilities)
                        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        ?: false

                else -> activeNetworkInfo?.isConnected ?: false
            }
        }

    private fun checkRecoverSavedCustomDTMI(): List<DtmiContent>? {
        val recoveredModel: List<DtmiContent>? =
            try {
                val savedModelString = pref.getString(CUSTOM_DTMI, null)
                savedModelString?.let {
                    val recovered = json.decodeFromString<JsonArray>(savedModelString)
                        .mapNotNull { it.toDtmiContent() }
                    Log.i(TAG, "recovered model=$recovered")
                    recovered
                }
            } catch (ex: Exception) {
                Log.w(TAG, ex.localizedMessage, ex)
                null
            }
        return recoveredModel
    }

    private suspend fun needSync(): Boolean {
        mutex.withLock {
            if (context.getSystemService<ConnectivityManager>().isCurrentlyConnected.not()) return false
            if (cache.isEmpty()) return true

            val lastSyncStAppVersion = pref.getString(LAST_SYNC_VERSION_PREF, "")
            if (lastSyncStAppVersion != stAppVersion) return true

            val now = Date().time
            val lastSyncTimestamp = pref.getLong(LAST_SYNC_TIMESTAMP_PREF, 0L)
            val lastSyncDate = Date(lastSyncTimestamp)
            val minSyncInterval = Date(now - MIN_SYNC_INTERVAL)

            return if (lastSyncDate.before(minSyncInterval)) {
                try {
                    val remoteChecksum = api.getDBVersion().checksum
                    val localChecksum = pref.getString(LAST_SYNC_CHECKSUM_PREF, "")
                    val isEqual = remoteChecksum == localChecksum

                    pref.edit(commit = true) { putString(LAST_SYNC_CHECKSUM_PREF, remoteChecksum) }

                    return isEqual
                } catch (ex: Exception) {
                    Log.w(TAG, ex.localizedMessage, ex)

                    false
                }
            } else {
                false
            }
        }
    }

    private suspend fun sync(url: String? = null) {
        mutex.withLock {
            try {
                db.deleteAllEntries()
                db.deleteAllDescEntries()
                cache.clear()
                cacheDescription.clear()
                val firmwares = if (url != null)
                    api.getFirmwaresFromUrl(url + "catalog.json")
                else
                    api.getFirmwares()
                firmwares.bleListBoardFirmwareV1?.let {
                    cache.addAll(it)
                    db.add(it)
                }
                firmwares.bleListBoardFirmwareV2?.let {
                    cache.addAll(it)
                    db.add(it)
                }

                firmwares.boards?.let{
                    db.addDesc(it)
                    cacheDescription.addAll(it)
                }

                val savedBoardsModelString = pref.getString(CUSTOM_BOARDS_MODEL, null)
                savedBoardsModelString?.let {
                    val result = json.decodeFromString<BoardCatalog>(savedBoardsModelString)?.let { boardCatalog ->
                        boardCatalog.bleListBoardFirmwareV1?.let {
                            db.add(it)
                            cache.addAll(it)
                            //it.forEach { it2 -> cache.add(it2) }
                        }
                        boardCatalog.bleListBoardFirmwareV2?.let {
                            db.add(it)
                            cache.addAll(it)
                            //it.forEach { it2 -> cache.add(it2) }
                        }
                    }
                }

                val remoteChecksum = if (url != null) {
                    api.getDBVersionFromUrl(url + "chksum.json").checksum
                } else {
                    api.getDBVersion().checksum
                }
                pref.edit(commit = true) { putString(LAST_SYNC_VERSION_PREF, stAppVersion) }
                pref.edit(commit = true) { putLong(LAST_SYNC_TIMESTAMP_PREF, Date().time) }
                pref.edit(commit = true) { putString(LAST_SYNC_CHECKSUM_PREF, remoteChecksum) }
            } catch (ex: Exception) {
                Log.w(TAG, ex.localizedMessage, ex)
            }
        }
    }


    override suspend fun reset(url: String?) {
        sync(url)
    }

    override suspend fun getBoardCatalog(): List<BoardFirmware> {
        withContext(Dispatchers.IO) {
            if (cache.isEmpty()) {
                cache.addAll(db.getDeviceFirmwares())
            }

            if (needSync()) {
                sync()
            }
        }

        return cache
    }

    override suspend fun getBoardsDescription(): List<BoardDescription> {
        withContext(Dispatchers.IO) {
            if (cacheDescription.isEmpty()) {
                val retrievedBoardsDesc = db.getBoardsDescription()
                if(retrievedBoardsDesc!=null) {
                    cacheDescription.addAll(retrievedBoardsDesc)
                }
            }

            if (needSync()) {
                sync()
            }
        }

        return cacheDescription
    }

    override suspend fun getFwCompatible(deviceId: String): List<BoardFirmware> {
        if (needSync()) {
            sync()
        }
        return cache.filter { deviceId == it.bleDevId }
    }

    override suspend fun getFw(deviceId: String, fwName: String): List<BoardFirmware> {
        if (needSync()) {
            sync()
        }
        return cache.filter { it.fwName == fwName && deviceId == it.bleDevId }
    }

    override suspend fun getFwDetailsNode(deviceId: String, bleFwId: String): BoardFirmware? {
        if (needSync()) {
            sync()
        }
        return cache.find { it.bleFwId == bleFwId && deviceId == it.bleDevId }
    }

    override suspend fun getDtmiModel(deviceId: String, bleFwId: String): DtmiModel? {
        if (needSync()) {
            sync()
        }
        val cached = dtmiModelCache.find { it.bleFwId == bleFwId && deviceId == it.bleDevId }
        if (cached != null) return cached
        val find = cache.find { it.bleFwId == bleFwId && it.bleDevId == deviceId }

        return find?.dtmi?.replace(':', '/')?.replace(';', '-')?.let {
            if (it.contains(ST_DTMI)) {
                String.format(BuildConfig.DTMI_AZURE_DB_BASE_URL, it)
            } else {
                val isBeta = false
                if (isBeta) {
                    String.format(BuildConfig.DTMI_GITHUB_DB_BASE_URL_BETA, it)
                } else {
                    String.format(BuildConfig.DTMI_GITHUB_DB_BASE_URL, it)
                }
            }
        }?.let {
            try {
                Log.d(TAG, "DTMI Model url: $it")
                val element = DtmiModel(
                    bleFwId = bleFwId,
                    bleDevId = deviceId,
                    contents = api.getDtmiModel(it).map { jsonObj ->
                        jsonObj.toDtmiContent()
                    }
                )

                dtmiModelCache.add(element)

                element
            } catch (ex: Exception) {
                Log.w(TAG, ex.localizedMessage, ex)

                //if we have a customDTMI saved and FwId is ==0xFF we use it
                if (bleFwId == "0xFF") {
                    val recoveredModel = checkRecoverSavedCustomDTMI()
                    if (recoveredModel != null) {
                        val element = DtmiModel(
                            bleFwId = bleFwId,
                            bleDevId = deviceId,
                            contents = recoveredModel,
                            customDTMI = true
                        )
                        dtmiModelCache.add(element)

                        element
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
    }

    override suspend fun setDtmiModel(
        deviceId: String,
        bleFwId: String,
        fileUri: Uri,
        contentResolver: ContentResolver
    ): DtmiModel? {

        var result: DtmiModel? = null

        try {
            val inStream = contentResolver.openInputStream(fileUri)
            if (inStream != null) {
                val text = inStream.bufferedReader(StandardCharsets.ISO_8859_1).readText()
                inStream.close()
                result =
                    json.decodeFromString<JsonArray>(text).mapNotNull { it.toDtmiContent() }.let {
                        val index =
                            dtmiModelCache.indexOfFirst { model -> model.bleFwId == bleFwId && model.bleDevId == deviceId }
                        if (index < dtmiModelCache.size && index >= 0) {
                            dtmiModelCache.removeAt(index)
                        }

                        val element = DtmiModel(
                            bleFwId = bleFwId,
                            bleDevId = deviceId,
                            contents = it,
                            customDTMI = true
                        )

                        dtmiModelCache.add(element)

                        //Save the custom added model
                        pref.edit(commit = true) { putString(CUSTOM_DTMI, text) }

                        element
                    }
            }
        } catch (ex: Exception) {
            Log.w(TAG, ex.localizedMessage, ex)
        }

        return result
    }

    override suspend fun setBoardCatalog(
        fileUri: Uri,
        contentResolver: ContentResolver
    ): List<BoardFirmware> {

        try {
            val inStream = contentResolver.openInputStream(fileUri)
            if (inStream != null) {
                val text = inStream.bufferedReader(StandardCharsets.ISO_8859_1).readText()
                inStream.close()
                val result = json.decodeFromString<BoardCatalog>(text)?.let {  boardCatalog ->
                    cache.clear()
                    boardCatalog.bleListBoardFirmwareV1?.let {
                        db.add(it)
                        //it.forEach { it2 -> cache.add(it2) }
                    }
                    boardCatalog.bleListBoardFirmwareV2?.let {
                        db.add(it)
                        //it.forEach { it2 -> cache.add(it2) }
                    }
                    cache.addAll(db.getDeviceFirmwares())

                    //Save the custom added board models
                    pref.edit(commit = true) { putString(CUSTOM_BOARDS_MODEL, text) }
                }
            }
        } catch (ex: Exception) {
            Log.w(TAG, ex.localizedMessage, ex)
        }

        return cache
    }

    companion object {
        const val TAG = "BoardCatalogRepo"
        const val ST_DTMI = "dtmi:stmicroelectronics"
        const val LAST_SYNC_VERSION_PREF = "LAST_SYNC_VERSION_PREF"
        const val LAST_SYNC_TIMESTAMP_PREF = "LAST_SYNC_TIMESTAMP_PREF"
        const val LAST_SYNC_CHECKSUM_PREF = "LAST_SYNC_CHECKSUM_PREF"
        const val CUSTOM_DTMI = "CUSTOM_DTMI"
        const val CUSTOM_BOARDS_MODEL = "CUSTOM_BOARDS_MODEL"
        const val MIN_SYNC_INTERVAL = 24 * 60 * 60 * 1000L
    }
}
