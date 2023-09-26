/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */
package com.st.blue_sdk.board_catalog

import android.content.ContentResolver
import android.net.Uri
import com.st.blue_sdk.board_catalog.models.BoardDescription
import com.st.blue_sdk.board_catalog.models.BoardFirmware
import com.st.blue_sdk.board_catalog.models.DtmiModel

interface BoardCatalogRepo {
    suspend fun reset(url: String?=null)

    suspend fun getBoardCatalog(): List<BoardFirmware>

    suspend fun getBoardsDescription(): List<BoardDescription>

    suspend fun getFwDetailsNode(deviceId: String, bleFwId: String): BoardFirmware?

    suspend fun getFwCompatible(deviceId: String): List<BoardFirmware>

    suspend fun getFw(deviceId: String, fwName: String): List<BoardFirmware>

    suspend fun getDtmiModel(deviceId: String, bleFwId: String): DtmiModel?

    suspend fun setBoardCatalog(fileUri: Uri, contentResolver: ContentResolver): List<BoardFirmware>

    suspend fun setDtmiModel(
        deviceId: String,
        bleFwId: String,
        fileUri: Uri,
        contentResolver: ContentResolver
    ): DtmiModel?
}
