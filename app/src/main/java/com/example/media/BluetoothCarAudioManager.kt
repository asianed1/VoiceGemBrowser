package com.example.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioDeviceCategory {
    CAR_STEREO,
    BLUETOOTH_HEADSET,
    WIRED_AUX,
    PHONE_SPEAKER
}

enum class CarAudioRoutingMode {
    A2DP_MEDIA,         // Standard high-fidelity stereo for modern car Bluetooth & head units
    BLUETOOTH_SCO,      // Hands-free call audio profile for older car radios without media streaming
    PHONE_INTERNAL      // Force phone speaker/mic
}

data class CarAudioStatus(
    val isConnected: Boolean = false,
    val deviceName: String = "Phone Speaker",
    val category: AudioDeviceCategory = AudioDeviceCategory.PHONE_SPEAKER,
    val routingMode: CarAudioRoutingMode = CarAudioRoutingMode.A2DP_MEDIA,
    val autoPauseOnDisconnect: Boolean = true
)

class BluetoothCarAudioManager(
    private val context: Context,
    private val onBecomingNoisy: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _status = MutableStateFlow(CarAudioStatus())
    val status: StateFlow<CarAudioStatus> = _status.asStateFlow()

    private var isNoisyReceiverRegistered = false
    private var isScoStarted = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshAudioDeviceState()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            stopSco()
            audioManager.mode = AudioManager.MODE_NORMAL
            refreshAudioDeviceState()
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.i("BluetoothCarAudio", "Audio becoming noisy: Car / Bluetooth disconnected while playing")
                stopSco()
                audioManager.mode = AudioManager.MODE_NORMAL
                if (_status.value.autoPauseOnDisconnect) {
                    onBecomingNoisy()
                }
                refreshAudioDeviceState()
            }
        }
    }

    init {
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        } catch (e: Exception) {
            Log.w("BluetoothCarAudio", "Failed to register audio device callback", e)
        }

        registerNoisyReceiver()
        refreshAudioDeviceState()
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            try {
                val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(noisyReceiver, filter)
                }
                isNoisyReceiverRegistered = true
            } catch (e: Exception) {
                Log.w("BluetoothCarAudio", "Error registering noisy receiver", e)
            }
        }
    }

    fun refreshAudioDeviceState() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var connectedDeviceName = "Phone Speaker"
        var isConnected = false
        var category = AudioDeviceCategory.PHONE_SPEAKER

        for (device in devices) {
            val type = device.type
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                device.productName?.toString() ?: ""
            } else ""

            val lowerName = name.lowercase()
            val isCarKeyword = lowerName.contains("car") || lowerName.contains("toyota") ||
                    lowerName.contains("honda") || lowerName.contains("ford") ||
                    lowerName.contains("audi") || lowerName.contains("bmw") ||
                    lowerName.contains("sync") || lowerName.contains("stereo") ||
                    lowerName.contains("automotive") || lowerName.contains("uconnect")

            when (type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                    isConnected = true
                    category = if (isCarKeyword) AudioDeviceCategory.CAR_STEREO else AudioDeviceCategory.CAR_STEREO
                    connectedDeviceName = if (name.isNotBlank()) name else "Car Stereo (Bluetooth A2DP)"
                    break
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    isConnected = true
                    category = AudioDeviceCategory.CAR_STEREO
                    connectedDeviceName = if (name.isNotBlank()) name else "Hands-Free Car Kit (Bluetooth SCO)"
                    break
                }
                AudioDeviceInfo.TYPE_AUX_LINE, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                    isConnected = true
                    category = AudioDeviceCategory.WIRED_AUX
                    connectedDeviceName = "Car AUX / Wired Audio"
                }
            }
        }

        _status.value = _status.value.copy(
            isConnected = isConnected,
            deviceName = connectedDeviceName,
            category = category
        )
    }

    fun setRoutingMode(mode: CarAudioRoutingMode) {
        _status.value = _status.value.copy(routingMode = mode)
        when (mode) {
            CarAudioRoutingMode.A2DP_MEDIA -> {
                stopSco()
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = false
            }
            CarAudioRoutingMode.BLUETOOTH_SCO -> {
                startSco()
            }
            CarAudioRoutingMode.PHONE_INTERNAL -> {
                stopSco()
                audioManager.mode = AudioManager.MODE_NORMAL
                audioManager.isSpeakerphoneOn = true
            }
        }
    }

    private fun startSco() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            isScoStarted = true
            Log.i("BluetoothCarAudio", "Bluetooth SCO hands-free mode engaged")
        } catch (e: Exception) {
            Log.w("BluetoothCarAudio", "Failed to engage Bluetooth SCO", e)
        }
    }

    private fun stopSco() {
        if (isScoStarted) {
            try {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
                audioManager.mode = AudioManager.MODE_NORMAL
                isScoStarted = false
                Log.i("BluetoothCarAudio", "Bluetooth SCO disengaged")
            } catch (e: Exception) {
                Log.w("BluetoothCarAudio", "Failed to stop Bluetooth SCO", e)
            }
        }
    }

    fun setAutoPauseOnDisconnect(enabled: Boolean) {
        _status.value = _status.value.copy(autoPauseOnDisconnect = enabled)
    }

    fun release() {
        try {
            stopSco()
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            Log.w("BluetoothCarAudio", "Error unregistering audio device callback", e)
        }

        if (isNoisyReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
                isNoisyReceiverRegistered = false
            } catch (e: Exception) {
                Log.w("BluetoothCarAudio", "Error unregistering noisy receiver", e)
            }
        }
    }
}
