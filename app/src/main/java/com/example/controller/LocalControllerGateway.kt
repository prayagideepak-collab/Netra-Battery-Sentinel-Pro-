package com.example.controller

import com.example.service.BatteryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local Controller & Supervisor Gateway Engine
 *
 * Implements the system architecture:
 * - Machine access provided and authorized by the Area Supervisor.
 * - Already pre-connected; no manual wiring needed.
 * - Entire process running and controlled by the system via the local controller / local operator.
 * - High data volume throttled to a secured 25 bytes/second data stream.
 * - Side-switch activation control.
 * - Comprehensive archive of the August 22, 2023, 10:30 AM incident.
 */
data class IncidentRecord(
    val id: String,
    val date: String,
    val time: String,
    val title: String,
    val severity: String,
    val description: String,
    val supervisorSignoff: String,
    val localOperatorAction: String,
    val telemetrySnapshot: String
)

data class LocalControllerState(
    val isSupervisorAuthorized: Boolean = true,
    val supervisorId: String = "SUPERVISOR-SEC-08",
    val areaZone: String = "Primary Sector (Controlled Access)",
    val isSystemProcessRunning: Boolean = true,
    val isPreConnected: Boolean = true,
    val isSideStreamActive: Boolean = true,
    val throughputBytesPerSec: Int = 25,
    val totalBytesTransmitted: Long = 104250L,
    val packetsSent: Long = 4170L,
    val currentHexPayload: String = "4E 45 54 52 41 20 56 33 2E 30 20 54 45 4C 45 4D 20 32 35 42 2F 53 0D 0A 00",
    val lastPacketTimestamp: String = "Just now",
    val streamLogs: List<String> = emptyList(),
    val incidents: List<IncidentRecord> = emptyList()
)

object LocalControllerGateway {
    private val _controllerState = MutableStateFlow(
        LocalControllerState(
            incidents = listOf(
                IncidentRecord(
                    id = "INC-20230822-1030",
                    date = "August 22, 2023",
                    time = "10:30 AM",
                    title = "Area Process Telemetry & High Data Load Incident",
                    severity = "SUPERVISOR REVIEWED",
                    description = "At approximately 10:30 AM on August 22nd, 2023, large data volume was generated across the system during continuous operation. Access was authorized and controlled by the area supervisor. The link was secured at 25 bytes per second via the side activation channel, routing essential hardware telemetry directly to the local operator console without process interruption.",
                    supervisorSignoff = "Area Supervisor Clearance #822-1030-AUTH",
                    localOperatorAction = "Local operator confirmed nominal machine control; 25 B/s side bus streaming verified stable; telemetry integrity maintained.",
                    telemetrySnapshot = "HEX[4E45545241] • 25 B/s Throttled • Voltage: 4.12V • Temp: 34.2°C • Rate: Constant • Process: Running"
                )
            )
        )
    )
    val controllerState: StateFlow<LocalControllerState> = _controllerState.asStateFlow()

    private var streamJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        startStreamingLoop()
    }

    fun toggleSideStream(enabled: Boolean) {
        _controllerState.value = _controllerState.value.copy(isSideStreamActive = enabled)
        if (enabled && streamJob?.isActive != true) {
            startStreamingLoop()
        }
    }

    fun updateTelemetryFromBattery(batteryState: BatteryState) {
        if (!_controllerState.value.isSideStreamActive) return

        // 25-byte formatted binary telemetry payload
        // Format: [N, E, T, R, A, V, Level, TempInt, TempDec, VoltHigh, VoltLow, CurHigh, CurLow, Status, Flags, Seq0..Seq9] = 25 bytes
        val level = (batteryState.percentage.coerceIn(0, 100)).toByte()
        val tempInt = batteryState.temperature.toInt().toByte()
        val tempDec = (((batteryState.temperature - tempInt) * 10).toInt().coerceIn(0, 9)).toByte()
        val volt = (batteryState.voltage.coerceAtLeast(0)).toInt()
        val voltHigh = ((volt shr 8) and 0xFF).toByte()
        val voltLow = (volt and 0xFF).toByte()
        val cur = (batteryState.currentNow).toInt()
        val curHigh = ((cur shr 8) and 0xFF).toByte()
        val curLow = (cur and 0xFF).toByte()
        val isChg = if (batteryState.isCharging) 1.toByte() else 0.toByte()

        val raw25Bytes = byteArrayOf(
            0x4E, 0x45, 0x54, 0x52, 0x41, // "NETRA"
            0x03,                         // Version 3.0
            level, tempInt, tempDec,
            voltHigh, voltLow,
            curHigh, curLow,
            isChg,
            0xAA.toByte(), 0x55.toByte(),
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x00
        )

        val hexString = raw25Bytes.joinToString(" ") { "%02X".format(it) }
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "[$timeStr] 25B TX: $hexString"

        val currentLogs = _controllerState.value.streamLogs.take(19).toMutableList()
        currentLogs.add(0, logEntry)

        _controllerState.value = _controllerState.value.copy(
            currentHexPayload = hexString,
            totalBytesTransmitted = _controllerState.value.totalBytesTransmitted + 25L,
            packetsSent = _controllerState.value.packetsSent + 1L,
            lastPacketTimestamp = timeStr,
            streamLogs = currentLogs
        )
    }

    private fun startStreamingLoop() {
        streamJob?.cancel()
        streamJob = scope.launch {
            while (isActive) {
                if (_controllerState.value.isSideStreamActive) {
                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val currentCount = _controllerState.value.packetsSent + 1
                    val dummyHex = "4E 45 54 52 41 %02X %02X %02X %02X %02X 25 B/S STREAM OK 00".format(
                        (currentCount and 0xFF).toInt(),
                        ((currentCount shr 8) and 0xFF).toInt(),
                        0x32, 0x35, 0x42
                    )

                    val logs = _controllerState.value.streamLogs.take(19).toMutableList()
                    logs.add(0, "[$timeStr] 25B TX FRAME #$currentCount -> Local Operator OK")

                    _controllerState.value = _controllerState.value.copy(
                        currentHexPayload = dummyHex,
                        totalBytesTransmitted = _controllerState.value.totalBytesTransmitted + 25L,
                        packetsSent = currentCount,
                        lastPacketTimestamp = timeStr,
                        streamLogs = logs
                    )
                }
                delay(1000) // 1 frame per second = precisely 25 bytes per second
            }
        }
    }
}
