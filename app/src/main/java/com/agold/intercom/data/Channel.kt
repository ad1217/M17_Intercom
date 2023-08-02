package com.agold.intercom.data

data class Channel(
       val area: Int = 0,
       val band: Int = 0,
       val channelMode: Int = 0,
       val colorCode: Int = 0,
       val contactList: String? = null,
       val contactNum: Int = 0,
       val contactType: Int = 0,
       val deviceId: Int = 0,
       val encryptKey: String? = null,
       val encryptSwitch: Int = 0,
       val inboundSlot: Int = 0,
       val micGain: Int = 0,
       val monitor: Int = 0,
       val name: String? = null,
       val num: Int = 0,
       val outboundSlot: Int = 0,
       val power: Int = 0,
       val powerSave: Int = 0,
       val recvCtcssFreq: Int = 0,
       val recvFreq: Long = 0,
       val recvSubAudioType: Int = 0,
       val relay: Int = 0,
       val sendCtcssFreq: Int = 0,
       val sendFreq: Long = 0,
       val sendSubAudioType: Int = 0,
       val squelchLevel: Int = 0,
       val type: Int = 1, // default to analog
       val volume: Int = 0
    )