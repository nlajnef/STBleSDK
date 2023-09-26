@file:UseSerializers(BoardStatusTypeSerializer::class)

package com.st.blue_sdk.board_catalog.models


import com.st.blue_sdk.board_catalog.api.serializers.BoardStatusTypeSerializer
import kotlinx.serialization.UseSerializers

import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(
    primaryKeys = ["ble_dev_id"],
    tableName = "board_description"
)

@Serializable
data class BoardDescription(
    @ColumnInfo(name = "ble_dev_id")
    @SerialName(value = "ble_dev_id")
    val bleDevId: String,
    @ColumnInfo(name = "nfc_dev_id")
    @SerialName(value = "nfc_dev_id")
    val usb_dev_id: String?=null,
    @ColumnInfo(name = "usb_dev_id")
    @SerialName(value = "usb_dev_id")
    val usbDevId: String?=null,
    @ColumnInfo(name = "unique_dev_id")
    @SerialName(value = "unique_dev_id")
    val uniqueDevId: Int,
    @ColumnInfo(name = "brd_name")
    @SerialName(value = "brd_name")
    val boardName: String,
    @ColumnInfo(name = "components")
    @SerialName(value = "components")
    val components: List<String>?=null,
    @ColumnInfo(name = "friendly_name")
    @SerialName(value = "friendly_name")
    val friendlyName: String,
    @ColumnInfo(name = "status")
    @SerialName(value = "status")
    val status: BoardStatus,
    @ColumnInfo(name = "description")
    @SerialName(value = "description")
    val description: String?=null,
    @ColumnInfo(name = "documentation_url")
    @SerialName(value = "documentation_url")
    val docURL: String? = null,
    @ColumnInfo(name = "order_url")
    @SerialName(value = "order_url")
    val orderURL: String? = null,
    @ColumnInfo(name = "video_url")
    @SerialName(value = "video_url")
    val videoURL: String? = null
)
