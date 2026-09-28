package com.itkcraft.alarmclock.alarm

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** イヤホン（有線 / Bluetooth / USB）の接続判定 */
object AudioRouting {
    private val headphoneTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
    )

    fun isHeadphone(device: AudioDeviceInfo) = device.type in headphoneTypes

    fun connectedHeadphones(ctx: Context): List<AudioDeviceInfo> =
        ctx.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter(::isHeadphone)

    fun headphonesConnected(ctx: Context) = connectedHeadphones(ctx).isNotEmpty()

    fun describe(ctx: Context): String {
        val list = connectedHeadphones(ctx)
        return if (list.isEmpty()) "未接続" else list.joinToString { "${it.productName} (type=${it.type})" }
    }
}
