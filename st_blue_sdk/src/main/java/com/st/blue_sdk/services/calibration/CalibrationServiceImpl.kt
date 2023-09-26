/*
 * Copyright (c) 2022(-0001) STMicroelectronics.
 * All rights reserved.
 * This software is licensed under terms that can be found in the LICENSE file in
 * the root directory of this software component.
 * If no LICENSE file comes with this software, it is provided AS-IS.
 */

package com.st.blue_sdk.services.calibration

import android.util.Log
import com.st.blue_sdk.features.CalibrationStatus
import com.st.blue_sdk.features.Feature
import com.st.blue_sdk.features.GetCalibration
import com.st.blue_sdk.features.StartCalibration
import com.st.blue_sdk.services.NodeServiceConsumer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalibrationServiceImpl @Inject constructor(
    private val nodeServiceConsumer: NodeServiceConsumer
) : CalibrationService {

    companion object {
        const val TAG = "CalibrationImpl"
        const val START_COMMAND = "startMagnCalib"
        //const val GET_COMMAND = "getMagnCalibStatus"
        val STATUS_PARSER: Pattern = Pattern.compile("magnCalibStatus (\\d+)")
    }

    override suspend fun startCalibration(feature: Feature<*>, nodeId: String): CalibrationStatus {
        val service = nodeServiceConsumer.getNodeService(nodeId)
            ?: throw IllegalStateException("Unable to find NodeService for $nodeId")

        val debugService = service.debugService
        var result = CalibrationStatus(feature = feature, status = false)
        val response = service.writeFeatureCommand(StartCalibration(feature = feature))
        if (response is CalibrationStatus) {
            result = response
        } else {
            //The SensorTile.box and SensorTile.box-Pro don't have Configuration BLE Char
            debugService.write(START_COMMAND)
        }
        return result
    }

    override suspend fun getCalibration(feature: Feature<*>, nodeId: String): CalibrationStatus {
        val service = nodeServiceConsumer.getNodeService(nodeId)
            ?: throw IllegalStateException("Unable to find NodeService for $nodeId")
        var result = CalibrationStatus(feature = feature, status = false)
        val response = service.writeFeatureCommand(GetCalibration(feature = feature))
        if (response is CalibrationStatus) {
            result = response
        }
        return result
    }
}