package com.agold.intercom.module

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Environment
import android.os.PowerManager
import android.os.RemoteException
import android.os.SystemProperties
import android.util.Log
import com.agold.intercom.utils.IComUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
import vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModuleReadCallback
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileReader
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.locks.ReentrantLock

private const val TAG = "ExtModuleManager"

enum class State {
    STOPPED,
    STARTING,
    READY,
    TIMEOUT,
}

sealed class ExtModuleManagerEvent {
    data class CallStateChanged(val state: Boolean) : ExtModuleManagerEvent()
    data class PlayStateChanged(val state: Boolean) : ExtModuleManagerEvent()
    data class USBChargeChanged(val charging: Boolean) : ExtModuleManagerEvent()
}

sealed class ModuleCommand {
    data class RecvFirmwareVersion(val bArr: ByteArray) : ModuleCommand()
    object McuInitFinished : ModuleCommand()
    object McuUpdateFinished : ModuleCommand()
    object McuStartFinished : ModuleCommand()
    data class McuErrorReport(val bArr: ByteArray) : ModuleCommand()
    data class McuStateReport(val bArr: ByteArray) : ModuleCommand()
    object DmrUpdated : ModuleCommand()
    object DmrUpdateFailed : ModuleCommand()
    data class Message(val bArr: ByteArray) : ModuleCommand()
    data class ModuleVersion(val version: String?) : ModuleCommand()
    data class Dmr09CallInfo(val bArr: ByteArray) : ModuleCommand()
    data class Dmr09Message(val bArr: ByteArray) : ModuleCommand()
    data class RecvSendStateChange(val state: Int) : ModuleCommand()
    data class MessageResponse(val b: Byte) : ModuleCommand()
    data class SetChannelComplete(val bArr: ByteArray) : ModuleCommand()
    data class ModuleResponse(val b: Byte) : ModuleCommand()
}

class ExtModuleManager(context: Context) {
    private val mAudioManager: AudioManager?
    private val mScope = CoroutineScope(Dispatchers.Default)
    private val mExtModuleProtocol: ExtModuleProtocol?
    private val mWakeLock: PowerManager.WakeLock
    private val mCmdWriteBuffer = ByteArray(1920)
    private val mAudioLock = Channel<Unit>(0)
    var mAudioTrack: AudioTrack? = null
    private var AUDIO_FRAME_SIZE = 1920
    private var mAguiExtModule: IAguiExtModule? = null
    private var mPcmRecordBOS: BufferedOutputStream? = null
    private var mPlayFrequency = 48000
    private var mRecFrequency = 48000
    private var mIsStopRecord = true
    private var mIsPTTStopComplete = true
    private var mIsStopPlay = true
    private var mAudioState = 0
    private var mIsCmdStart = false
    private var mIsMCUStarted = false
    private var mIsUsbStarted = false
    private var mIsPcmInStart = false
    private var mIsPcmOutStart = false
    private var mIsUpdatingDmr = false
    private var mAllExit = true
    private var mCurrentAudioFocusRequest: AudioFocusRequest? = null
    private var mAudioRecordPath: File? = null
    private var mCurrFirmware: String? = null
    private var mPreSetChannel = 0
    var isSetChannelFinished = false
        private set
    private var mCurrBatteryLevel = 100
    private var mCurrTemperature = 20
    private var mIsCharging = false
    private var mIsUSBCharge = false
    private var mIsLockDigitalSend = false
    private var mIsLockAnglogSend = false
    private var mStartPlayTime: Long = 0
    private var mStartCallTime: Long = 0
    private var mIsStopping = false
    private val mAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private var mHasNewMessage = false
    private var mCallInStateChangedCount = 0

    private val _moduleCommandEvents = MutableSharedFlow<ModuleCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val _mcuState = MutableStateFlow(State.STOPPED)
    val mcuState = _mcuState.asStateFlow()
    private val _channelState = MutableStateFlow(State.STOPPED)
    val channelState = _channelState.asStateFlow()

    init {
        Log.i(TAG, "ExtModuleManager constuct")
        val extModuleProtocol = ExtModuleProtocol()
        mExtModuleProtocol = extModuleProtocol
        extModuleProtocol.setExtModuleManager(this)
        mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = powerManager.newWakeLock(26, "Intercom")
        if (SystemProperties.getBoolean("ro.agold.extmodule.32k", false)) {
            mRecFrequency = 32000
            mPlayFrequency = 32000
            AUDIO_FRAME_SIZE = 1280
        }
    }

    private fun onCallStateChanged(i: Int) {
        Log.i(TAG, "onCallStateChanged currState:$i")
        val intent = Intent("agui.intercom.intent.action.CALL_STATE_CHANGED")
        intent.putExtra("callstate", i)
        mContext!!.sendBroadcast(intent)
    }

    private fun onPlayStateChanged(i: Int) {
        Log.i(TAG, "onPlayStateChanged currState:$i")
        val intent = Intent("agui.intercom.intent.action.PLAY_STATE_CHANGED")
        intent.putExtra("playstate", i)
        mContext!!.sendBroadcast(intent)
    }

    @get:Synchronized
    val aguiExtModule: IAguiExtModule?
        get() {
            if (mAguiExtModule == null) {
                try {
                    mAguiExtModule = IAguiExtModule.getService()
                } catch (e: RemoteException) {
                    Log.e(TAG, "Failed to get interface", e)
                }
                if (mAguiExtModule == null) {
                    Log.w(TAG, "AguiDevice HIDL not available")
                    return null
                }
            }
            return mAguiExtModule
        }

    private fun init(context: Context) {
        if (mContext == null) {
            mContext = context
        }
    }

    fun setContext(context: Context?) {
        mContext = context
    }

    fun start() {
        mScope.launch {
            _moduleCommandEvents.collect {
                Log.d(TAG, "Module Command: $it")
            }
        }

        Log.i(
            TAG,
            "start mAllExit:$mAllExit, mIsCmdStart:$mIsCmdStart, mIsMCUStarted:$mIsMCUStarted"
        )
        mAllExit = false
        mIsStopping = false
        mScope.launch { createCmdReadThread() }
        mScope.launch { createCallInThread() }
        mScope.launch { createAudioPlayThread() }
        mScope.launch { createAudioRecordThread() }

        mScope.launch { startMcu() }
    }

    fun stop() {
        Log.i(TAG, "stop-------")
        stopCom()
        _mcuState.value = State.STOPPED
        _channelState.value = State.STOPPED
        mIsStopping = true
        mScope.launch {
            delay(300)
            exit()
            if (mContext != null) {
//                    ExtModuleManager.mContext.stopService(new Intent(ExtModuleManager.mContext, IComService.class));
                Log.i(TAG, "stopService----------")
            }
            mIsStopping = true
        }
    }

    fun exit() {
        Log.i(TAG, "exit mAudioState:$mAudioState")
        mAllExit = true
        mScope.cancel()
        mIsStopRecord = true
        mIsPTTStopComplete = true
        mIsStopPlay = true
        mIsCmdStart = false
        mIsMCUStarted = false
        if (mWakeLock.isHeld) {
            mWakeLock.release()
        }
        if (mAudioTrack != null) {
            mAudioTrack!!.release()
            mAudioTrack = null
        }
    }

    fun sendBytes(bArr: ByteArray) {
        try {
            if (aguiExtModule != null) {
                System.arraycopy(bArr, 0, mCmdWriteBuffer, 0, bArr.size)
                mAguiExtModule!!.writeTTyDevice(mCmdWriteBuffer, bArr.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendBytes ex:$e")
        }
    }

    private suspend fun startMcu() {
        _mcuState.value = State.STARTING
        Log.i(
            TAG,
            "startMcu mIsMCUStarted:$mIsMCUStarted, mIsCmdStart:$mIsCmdStart, mAllExit:$mAllExit, mIsStopping:$mIsStopping"
        )
        Log.i(TAG, "startMcu aguiExtModule:$aguiExtModule")

        if (mAllExit || mIsStopping || mIsMCUStarted) {
            return
        }

        try {
            if (aguiExtModule != null) {
                mAguiExtModule!!.startMcu()
                mIsMCUStarted = true
                mIsUpdatingDmr = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "startMcu ex:$e")
        }

        // TODO: this is replicating the behaviour of the original code, but it seems a bit odd
        withTimeoutOrNull(2000) {
            _moduleCommandEvents.filterIsInstance(ModuleCommand.McuStartFinished::class).first()
        }
        Log.i(TAG, "handleMcuStartFinished mIsSetChannelFinished:$isSetChannelFinished")
        mIsUsbStarted = true
        isSetChannelFinished = false
        _mcuState.value = State.READY
        mIsCmdStart = true

        delay(1000)
        getMcuFirmwareVersion()
    }

    fun stopMcu() {
        Log.i(TAG, "stopMcu")
        mScope.launch {
            try {
                Log.i(TAG, "stopMcu mAguiExtModule:$mAguiExtModule")
                if (aguiExtModule != null) {
                    Log.i(TAG, "mAguiExtModule stopMcu")
                    mIsMCUStarted = false
                    mIsCmdStart = false
                    mAguiExtModule!!.stopMcu()
                }
            } catch (e: Exception) {
                Log.e(TAG, "stopMcu e:$e")
            }
        }
        isSetChannelFinished = false
    }

    fun resetMcu() {
        Log.i(TAG, "resetMcu")
        stopCom()

        mScope.launch {
            delay(4000L)
            startMcu()
        }
    }

    fun stopCom() {
        Log.i(
            TAG,
            "stopCom mIsCmdStart:$mIsCmdStart, mIsStopRecord:$mIsStopRecord, mIsStopPlay:$mIsStopPlay, mAudioState:$mAudioState"
        )
        if (!mIsStopPlay) {
            stopPlay()
        }
        if (!mIsStopRecord) {
//            stopRecord();
        }
        stopMcu()
    }


    private suspend fun getMcuFirmwareVersion() {
        Log.i(TAG, "getMcuFirmwareVersion mCurrFirmware:$mCurrFirmware, mIsCmdStart:$mIsCmdStart")
        if (mIsCmdStart) {
            mCurrFirmware = null
            mExtModuleProtocol?.getFirmwareVersion()
            // TODO: this could be cleaned up a bit
            delay(1000)
            if (mCurrFirmware.isNullOrEmpty()) {
                mExtModuleProtocol?.getFirmwareVersion()
            }
            delay(1000)
            if (mCurrFirmware.isNullOrEmpty()) {
                mExtModuleProtocol?.getFirmwareVersion()
            }
        }
    }

    private fun handleRecvFirmwareVersion(bArr: ByteArray) {
        Log.i(TAG, "handleRecvFirmwareVersion mCurrFirmware:$mCurrFirmware")
        if ((mCurrFirmware.isNullOrEmpty()) && bArr.size > 8) {
            var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256
            }
            if (bytesToInt2 > 0) {
                val bArr2 = ByteArray(bytesToInt2)
                System.arraycopy(bArr, 8, bArr2, 0, bytesToInt2)
                mCurrFirmware = bArr2.toString(Charsets.UTF_8)
                Log.i(TAG, "handleRecvFirmwareVersion mCurrFirmware:$mCurrFirmware")
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    mExtModuleProtocol!!.getDmr09ModuleSoftVersion()
                } else {
                    mExtModuleProtocol!!.getModuleSoftVersion()
                }
            }
        }
    }

    private fun openPcmIn() {
        Thread {
            try {
                if (aguiExtModule != null) {
                    val openPcmIn = mAguiExtModule!!.openPcmIn()
                    Log.i(TAG, "openPcmIn ret:$openPcmIn")
                    if (openPcmIn == 1) {
                        mIsPcmInStart = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "openPcmIn e:$e")
            }
        }.start()
    }

    private fun closePcmIn() {
        Thread {
            try {
                if (aguiExtModule != null) {
                    val closePcmIn = mAguiExtModule!!.closePcmIn()
                    Log.i(TAG, "closePcmIn ret:$closePcmIn")
                    mIsPcmInStart = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "closePcmIn e:$e")
            }
        }.start()
    }

    private fun openPcmOut() {
        Log.i(TAG, "openPcmOut mIsStopRecord:$mIsStopRecord")
        if (mIsStopRecord) {
            mIsStopRecord = false
            mAudioLock.trySend(Unit)
            mIsStopRecord = false
            mStartCallTime = System.currentTimeMillis()
            setAudioRecordPath("" + mStartCallTime)
            Thread {
                try {
                    if (aguiExtModule != null) {
                        mAguiExtModule!!.startPtt()
                        val openPcmOut = mAguiExtModule!!.openPcmOut()
                        Log.i(TAG, "openPcmOut ret:$openPcmOut")
                        if (openPcmOut == 1) {
                            mIsPcmOutStart = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "openPcmOut e:$e")
                }
            }.start()
        }
    }

    private fun closePcmOut() {
        Log.i(TAG, "closePcmOut mIsStopRecord:" + mIsStopRecord)
        if (mIsStopRecord) {
            return
        }
        Thread {
            try {
                if (aguiExtModule != null) {
                    val closePcmOut = mAguiExtModule!!.closePcmOut()
                    Log.i(TAG, "closePcmOut ret:$closePcmOut")
                    mAguiExtModule!!.stopPtt()
                }
            } catch (e: Exception) {
                Log.e(TAG, "closePcmOut e:$e")
            }
        }.start()
        onCallStateChanged(0)
        mScope.launch {
            delay(500)
            mIsStopRecord = true
            mIsPcmOutStart = false
        }
        if (mIsStopPlay) {
            releaseMusicFocus()
        }
    }

    private suspend fun createCmdReadThread() {
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    if (!mIsMCUStarted) {
                        // Log.i(TAG, "createCmdReadThread: waiting for MCU Start")
                        delay(100L)
                    } else if (mIsUpdatingDmr) {
                        delay(100L)
                    } else {
                        try {
                            if (aguiExtModule != null) {
                                mAguiExtModule!!.readTTyDevice(object :
                                    IAguiExtModuleReadCallback.Stub() {
                                    @Throws(RemoteException::class)
                                    override fun onReadDevice(bArr: ByteArray, i: Int) {
                                        if (i > 0) {
                                            Log.i(TAG, "onReadTTyDevice readSize:$i")
                                            handleCmdResponse(bArr, i)
                                        }
                                    }
                                })
                            }
                        } catch (e: RemoteException) {
                            Log.e(TAG, "createCmdReadThread ex:$e")
                        }
                        delay(100L)
                    }
                } catch (e2: Exception) { // TODO: more specific catch to avoid catching CancellationException
                    Log.e(TAG, "createCmdReadThread e:$e2")
                    return@withContext
                }
            }
            Log.i(TAG, "createCmdReadThread exit")
        }
    }

    private suspend fun createCallInThread() {
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    if (!mIsMCUStarted) {
                        delay(1000L)
                    } else if (mIsUpdatingDmr) {
                        delay(100L)
                    } else {
                        try {
                            if (aguiExtModule != null) {
                                Log.i(TAG, "createCallInThread start detectAudioinState")
                                val detectAudioinState = aguiExtModule!!.detectAudioinState()
                                Log.i(TAG, "createCallInThread callInState:$detectAudioinState")
                                handleCallInStateChanged(detectAudioinState)
                            }
                        } catch (e: RemoteException) {
                            Log.e(TAG, "createCallInThread ex:$e")
                        }
                        delay(100L)
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "createCallInThread e:$e2")
                    return@withContext
                }
            }
            Log.i(TAG, "createCallInThread exit")
        }
    }

    private suspend fun createAudioPlayThread() {
        withContext(Dispatchers.IO) {
            while (isActive) {
                try {
                    if (mIsStopPlay) {
                        delay(10L)
                    } else if (!mIsPcmInStart) {
                        delay(10L)
                    } else {
                        Log.i(TAG, "createAudioPlayThread mAudioTrack:$mAudioTrack")
                        if (mAudioTrack == null) {
                            val minBufferSize = AudioTrack.getMinBufferSize(
                                mPlayFrequency,
                                AudioFormat.CHANNEL_OUT_STEREO,
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            Log.i(TAG, "createAudioTrack playBufSize:$minBufferSize")
                            mAudioTrack = AudioTrack.Builder()
                                .setAudioAttributes(mAudioAttributes)
                                .setAudioFormat(
                                    AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(mPlayFrequency)
                                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                                        .build()
                                )
                                .setBufferSizeInBytes(minBufferSize)
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build()
                        }
                        Log.i(TAG, "createAudioPlayThread mAudioTrack:$mAudioTrack")
                        Log.i(
                            TAG,
                            "createAudioPlayThread mAudioTrack getState:" + mAudioTrack!!.state + ", getPlayState:" + mAudioTrack!!.playState
                        )
                        mAudioTrack!!.play()
                        while (mIsPcmInStart && isActive) {
                            try {
                                if (aguiExtModule != null) {
                                    mAguiExtModule!!.readPcmDevice(object :
                                        IAguiExtModuleReadCallback.Stub() {
                                        @Throws(RemoteException::class)
                                        override fun onReadDevice(bArr: ByteArray, i: Int) {
                                            if (i > 0) {
                                                Log.i(TAG, "readPcmDevice readSize:$i")
                                                try {
                                                    mAudioTrack?.write(bArr, 0, i)
                                                    mPcmRecordBOS?.write(bArr, 0, i)
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "onReadDevice e:$e")
                                                }
                                            }
                                        }
                                    })
                                }
                            } catch (e: RemoteException) {
                                Log.e(TAG, "createAudioPlayThread ex:$e")
                                Log.e(TAG, "createAudioPlayThread mAguiExtModule:" + mAguiExtModule)
                                Log.e(
                                    TAG,
                                    "createAudioPlayThread IAguiExtModule.getService():" + IAguiExtModule.getService()
                                )
                                mAguiExtModule = null
                            }
                        }
                        mAudioTrack!!.stop()
                        mAudioTrack = null
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "createAudioPlayThread e:$e2")
                    return@withContext
                }
            }
            Log.i(TAG, "createAudioPlayThread exit")
        }
    }

    private suspend fun createAudioRecordThread() {
        withContext(Dispatchers.IO) {
            var read: Int
            while (isActive) {
                try {
                    if (mIsStopRecord) {
                        Log.i(TAG, "createAudioRecordThread mAudioLock.receive!")
                        mAudioLock.receive()
                    }
                    if (!mIsPcmOutStart) {
                        delay(10L)
                    } else {
                        val bArr = ByteArray(1920)
                        Log.i(TAG, "createAudioRecordThread mAudioRecordPath:$mAudioRecordPath")
                        val audioRecordPathParent = mAudioRecordPath!!.parentFile
                        if (audioRecordPathParent != null && !audioRecordPathParent.exists()) {
                            audioRecordPathParent.mkdirs()
                        }
                        var bufferedOutputStream: BufferedOutputStream? = null
                        if (mAudioRecordPath != null) {
                            if (mAudioRecordPath!!.exists()) {
                                mAudioRecordPath!!.delete()
                            }
                            mAudioRecordPath!!.createNewFile()
                            bufferedOutputStream = BufferedOutputStream(
                                Files.newOutputStream(
                                    mAudioRecordPath!!.toPath()
                                )
                            )
                        }
                        val audioRecord = AudioRecord(
                            1, mRecFrequency, 12, 2, AudioRecord.getMinBufferSize(
                                mRecFrequency, 12, 2
                            )
                        )
                        audioRecord.startRecording()
                        while (mIsPcmOutStart) {
                            read = audioRecord.read(bArr, 0, AUDIO_FRAME_SIZE)
                            if (read < 0) {
                                break
                            }
                            aguiExtModule?.writePcmDevice(bArr, read)
                            bufferedOutputStream?.write(bArr, 0, read)
                        }
                        bufferedOutputStream?.close()
                        audioRecord.stop()
                        audioRecord.release()
                    }
                } catch (e: Exception) {
                    Log.i(TAG, "createAudioRecordThread error:$e")
                }
            }
            Log.i(TAG, "createAudioRecordThread exit")
        }
    }

    fun startPlay(): Int {
        Log.i(TAG, "startPlay start mIsStopPlay:" + mIsStopPlay)
        return if (!mIsStopPlay) {
            Log.i(TAG, "startPlay--->play is not stopped")
            0
        } else if (!mIsMCUStarted || !mIsCmdStart) {
            Log.i(TAG, "startPlay--->cmd COM is not open!")
            0
        } else if (!mIsStopRecord) {
            Log.i(TAG, "startPlay Record is not stop!")
            0
        } else {
//            Channel currChannel = getCurrChannel();
//            if (currChannel != null && currChannel.getType() == 0) {
//                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
//                    this.mExtModuleProtocol.getDmr09RecvCallInfo();
//                } else {
//                    this.mExtModuleProtocol.getCallInfo();
//                }
//            }
            mIsStopPlay = false
            mScope.launch {
                delay(100)
                openPcmIn()
            }
            requestMusicFocus()
            mStartPlayTime = System.currentTimeMillis()
            setAudioRecordPath("" + mStartPlayTime)
            onPlayStateChanged(1)
//            if (this.mIsScanningChannels) {
//                this.mHandler.removeMessages(23);
//                Handler handler2 = this.mHandler;
//                handler2.sendMessage(handler2.obtainMessage(23));
//                this.mIsScanningChannels = false;
//            }
            try {
                val audioRecordPathParent = mAudioRecordPath!!.parentFile
                if (audioRecordPathParent != null && !audioRecordPathParent.exists()) {
                    audioRecordPathParent.mkdirs()
                }
            } catch (e: Exception) {
                Log.e(TAG, "startPlay ex:$e")
            }
            if (mAudioRecordPath == null) {
                return 0
            }
            val pcmFile: File = mAudioRecordPath!!
            if (pcmFile.exists()) {
                pcmFile.delete()
            }
            // TODO: better error handling
            try {
                pcmFile.createNewFile()
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            try {
                mPcmRecordBOS = BufferedOutputStream(FileOutputStream(pcmFile))
            } catch (e: FileNotFoundException) {
                throw RuntimeException(e)
            }
            Log.i(TAG, "startPlay end-----------------")
            0
        }
    }

    fun stopPlay() {
        Log.i(TAG, "stopPlay mIsStopPlay:" + mIsStopPlay)
        if (mIsStopPlay) {
            Log.i(TAG, "stopPlay---> play is not started")
            return
        }
        mIsStopPlay = true
        mScope.launch { closePcmIn() }
        try {
            if (mPcmRecordBOS != null) {
                mPcmRecordBOS!!.close()
                mPcmRecordBOS = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "stopPlay ex:$e")
        }
        //        int currChannel = this.mIComPreference.getCurrChannel(1);
//        int currArea = this.mIComPreference.getCurrArea(1);
//        long currentTimeMillis = System.currentTimeMillis() - this.mStartPlayTime;
//        Log.i(TAG, "stopPlay channelId:" + currChannel + ", channelArea:" + currArea + ", recordPath:" + this.mAudioRecordPath + ", duration:" + currentTimeMillis + ", mStartPlayTime:" + this.mStartPlayTime + ", mIsSettingFactory:" + this.mIsSettingFactory);
//        if (this.mAudioRecordPath != null && !this.mIsSettingFactory) {
//            if (currentTimeMillis < 1500) {
//                deleteRecordFile(this.mAudioRecordPath);
//            } else {
//                IComRecordDB.getInstance(mContext).addRecord(new Record(currChannel, currArea, 1, this.mAudioRecordPath, currentTimeMillis, this.mStartPlayTime));
//            }
//        }
        mStartPlayTime = 0L
        onPlayStateChanged(0)
        if (mAudioState == 0) {
            releaseMusicFocus()
        }
        mScope.launch {
            delay(500)
            if (mIsStopPlay && mHasNewMessage) {
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    mExtModuleProtocol!!.getDmr09MsgContent()
                } else {
                    mExtModuleProtocol!!.getMsgContent()
                }
                mHasNewMessage = false
            }
        }
        Log.i(TAG, "stopPlay end-----------------")
    }

    fun startRecord() {
        Log.i(
            TAG,
            "startRecord mAudioState:" + mAudioState + ", mIsStopRecord:" + mIsStopRecord + ", mIsLockDigitalSend:" + mIsLockDigitalSend + ", mIsLockAnglogSend:" + mIsLockAnglogSend + ", mIsSetChannelFinished:" + isSetChannelFinished
        )
        if (!mIsMCUStarted || !mIsCmdStart) {
            Log.i(TAG, "startRecord cmd COM is not open!")
        } else if (!isSetChannelFinished) {
            Log.i(TAG, "startRecord channel is not set finished!")
        } else if (!mIsStopRecord) {
            Log.i(TAG, "startRecord is not stopped!")
        } else {
            if (mAudioState == 0) {
                mAudioState = 1
                if (requestMusicFocus() == 0) {
                    mAudioState = 0
                }
                mScope.launch { openPcmOut() }
                onCallStateChanged(1)
            }
        }
    }

    private fun requestMusicFocus(): Int {
        Log.i(TAG, "requestMusicFocus mCurrentAudioFocusRequest:" + mCurrentAudioFocusRequest)
        if (mCurrentAudioFocusRequest != null) {
            return 1
        }
        if (mAudioManager != null) {
            val focusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(mAudioAttributes)
                    .build()
            val requestAudioFocus = mAudioManager.requestAudioFocus(focusRequest)
            Log.i(TAG, "requestMusicFocus status:$requestAudioFocus")
            mCurrentAudioFocusRequest = focusRequest
            return 1
        }
        return 0
    }

    private fun releaseMusicFocus() {
        Log.i(TAG, "releaseMusicFocus mCurrentAudioFocusRequest:" + mCurrentAudioFocusRequest)
        if (mAudioManager == null || mCurrentAudioFocusRequest == null) {
            return
        }
        mAudioManager.abandonAudioFocusRequest(mCurrentAudioFocusRequest!!)
        mCurrentAudioFocusRequest = null
    }

    private fun handleCmdResponse(response: ByteArray, length: Int): Int {
        val bArr2 = ByteArray(length)
        System.arraycopy(response, 0, bArr2, 0, length)
        val byte2HexStrNoBlank = IComUtils.byte2HexStrNoBlank(bArr2)
        Log.i(TAG, "handleCmdResponse len:$length, strCmd:$byte2HexStrNoBlank")
        var i2 = 0
        while (i2 + 11 < length) {
            try {
                if (IComUtils.isIComCmd(bArr2, i2)) {
                    var bytesToInt2 =
                        IComUtils.bytesToInt2(byteArrayOf(bArr2[i2 + 6], bArr2[i2 + 7]))
                    if (bytesToInt2 < 0) {
                        bytesToInt2 += 256
                    }
                    Log.i(TAG, "handleCmdResponse index:$i2, currDataLen:$bytesToInt2")
                    if (i2 + bytesToInt2 + 11 > length) {
                        // this didn't decompile correctly, so it was reconstructed by hand and may not be accurate
                        val r8 = byteArrayOf(bArr2[i2 + 3], bArr2[i2 + 4])
                        return if (r8[0].toInt() != 1 || r8[1].toInt() != 6) {
                            i2
                        } else length
                    }
                    val i3 = bytesToInt2 + 11
                    val bArr3 = ByteArray(i3)
                    System.arraycopy(bArr2, i2, bArr3, 0, i3)
                    parseCmd(bArr3)
                    i2 += i3
                } else {
                    i2++
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleCmdResponse e:$e")
                return length
            }
        }
        return i2
    }

    private fun parseCmd(bArr: ByteArray) {
        val byte2HexStr = IComUtils.byte2HexStr(bArr)
        Log.i(TAG, "parseCmd strCmd:$byte2HexStr")
        if (IComUtils.isIComCmd(bArr)) {
            Log.i(TAG, "parseCmd cmdByte[3]:" + bArr[3].toInt())
            if (bArr[3].toInt() == 0) {
                val b = bArr[4]
                Log.i(TAG, "parseCmd sys cmd:" + b.toInt())
                when (b.toInt()) {
                    1 -> {
                        handleRecvFirmwareVersion(bArr)
                        _moduleCommandEvents.tryEmit(ModuleCommand.RecvFirmwareVersion(bArr))
                    }

                    4 -> {
                        Log.i(TAG, "handleMcuInitFinished")
                        _moduleCommandEvents.tryEmit(ModuleCommand.McuInitFinished)
                    }

                    5 -> _moduleCommandEvents.tryEmit(ModuleCommand.McuUpdateFinished)
                    6 -> _moduleCommandEvents.tryEmit(ModuleCommand.McuStartFinished)
                    7 -> {
                        _moduleCommandEvents.tryEmit(ModuleCommand.McuErrorReport(bArr))
                        handleMcuErrorReport(bArr)
                    }

                    8 -> {
                        _moduleCommandEvents.tryEmit(ModuleCommand.McuStateReport(bArr))
                        handleMcuStateReport(bArr)
                    }

                    9 -> _moduleCommandEvents.tryEmit(ModuleCommand.DmrUpdated)

                    10 -> _moduleCommandEvents.tryEmit(ModuleCommand.DmrUpdateFailed)
                }
            } else if (bArr[3].toInt() == 1) {
                val b2 = bArr[4]
                Log.i(TAG, "parseCmd module cmd:" + b2.toInt())
                when (b2.toInt()) {
                    4 -> {
                        handleRecvSendStateChange(bArr[8].toInt())
                        _moduleCommandEvents.tryEmit(ModuleCommand.RecvSendStateChange(bArr[8].toInt()))
                    }

                    17 -> _moduleCommandEvents.tryEmit(ModuleCommand.Message(bArr))
                    37 -> {
                        val version = getModuleVersonContent(bArr);
                        _moduleCommandEvents.tryEmit(ModuleCommand.ModuleVersion(version))
                    }

                    43 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        _moduleCommandEvents.tryEmit(ModuleCommand.Dmr09CallInfo(bArr))
                    }

                    45 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        _moduleCommandEvents.tryEmit(ModuleCommand.Dmr09Message(bArr))
                    }

                    6 -> {
                        // TODO: This method doesn't seem to do anything
                        handleRecvSendStateChange(bArr)
                        // _moduleCommandEvents.tryEmit(ModuleCommand.RecvSendStateChange(bArr))
                    }

                    7 -> _moduleCommandEvents.tryEmit(ModuleCommand.MessageResponse(bArr[8]))
                    34 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        _moduleCommandEvents.tryEmit(ModuleCommand.SetChannelComplete(bArr))
                    }

                    35 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        _moduleCommandEvents.tryEmit(ModuleCommand.SetChannelComplete(bArr))
                    }

                    52 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        val version = getModuleVersonContent(bArr);
                        _moduleCommandEvents.tryEmit(ModuleCommand.ModuleVersion(version))
                    }

                    53 -> _moduleCommandEvents.tryEmit(ModuleCommand.SetChannelComplete(bArr))
                    54 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        _moduleCommandEvents.tryEmit(ModuleCommand.ModuleResponse(bArr[8]))
                    } else {
                        _moduleCommandEvents.tryEmit(ModuleCommand.SetChannelComplete(bArr))
                    }
                }
            } else if (bArr[3].toInt() == 2 && bArr.size > 8) {
                var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
                Log.i(TAG, "parseCmd dataLen:$bytesToInt2")
                if (bytesToInt2 < 0) {
                    bytesToInt2 += 256
                }
                if (bytesToInt2 > 0) {
                    val bArr2 = ByteArray(bytesToInt2)
                    System.arraycopy(bArr, 8, bArr2, 0, bytesToInt2)
                    val byte2HexStr2 = IComUtils.byte2HexStr(bArr2)
                    Log.i(TAG, "parseCmd retData:$byte2HexStr2")
                }
            }
        }
    }

    private fun handleMcuErrorReport(bArr: ByteArray) {
        Log.i(TAG, "handleMcuErrorReport")
        if (bArr.size <= 8 || IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7])) <= 0) {
            return
        }
        val b = bArr[8]
        Log.i(TAG, "handleMcuErrorReport errorCode:" + b.toInt())
    }

    private fun handleMcuStateReport(bArr: ByteArray) {
        Log.i(TAG, "handleMcuStateReport")
        if (bArr.size <= 8 || IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7])) <= 0) {
            return
        }
        val b = bArr[8]
        Log.i(
            TAG,
            "handleMcuStateReport cmdState:" + b.toInt() + ", mHasNewMessage:" + mHasNewMessage
        )
        if (b.toInt() == 1) {
            return
        }
        if (b.toInt() != 2) {
            if (b.toInt() == 3) {
                Log.i(TAG, "handleMcuStateReport pcm error, we need to reset mcu!!!")
                resetMcu()
                return
            }
            return
        }
        mScope.launch {
            delay(1000)
            mIsPTTStopComplete = true
        }
        if (mIsStopRecord && mHasNewMessage) {
            if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                mExtModuleProtocol!!.getDmr09MsgContent()
            } else {
                mExtModuleProtocol!!.getMsgContent()
            }
            mHasNewMessage = false
        }
    }

    private fun getModuleVersonContent(bArr: ByteArray): String? {
        if (bArr.size > 8) {
            var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
            Log.i(TAG, "getModuleVersonContent dataLen:$bytesToInt2")
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256
            }
            if (bytesToInt2 > 0) {
                val i = bytesToInt2 - 1
                val bArr2 = ByteArray(i)
                System.arraycopy(bArr, 8, bArr2, 0, i)
                val str = bArr2.toString(Charsets.UTF_8)
                Log.i(TAG, "getModuleVersonContent module:$str")

//                Channel channel = new Channel();
//                this.setChannel(channel);
                return str
            }
        }
        return null
    }

    fun checkTemperature(): Int {
        Log.i(TAG, "checkTemperature mCurrTemperature:" + mCurrTemperature)
        val i = mCurrTemperature
        if (i < -20) {
            return -1
        }
        return if (i > 550) 1 else 0
    }

    fun handleBatteryChanged(i: Int, i2: Int, z: Boolean, z2: Boolean) {
        Log.i(TAG, "handleBatteryChanged level:" + i + ", mCurrBatteryLevel:" + mCurrBatteryLevel)
        Log.i(TAG, "handleBatteryChanged temp:" + i2 + ", mCurrTemperature:" + mCurrTemperature)
        Log.i(TAG, "handleBatteryChanged isCharging:" + z + ", mIsCharging:" + mIsCharging)
        Log.i(TAG, "handleBatteryChanged isUsbCharge:" + z2 + ", mIsUSBCharge:" + mIsUSBCharge)
        mCurrBatteryLevel = i
        mCurrTemperature = i2
        mIsUSBCharge = z2
        if (mIsCharging != z) {
            if (z) {
                handleChargeConnected()
            } else {
                handleChargeDisconnected()
            }
            mIsCharging = z
        }
        val i3 = mCurrBatteryLevel
        if (i3 >= 30) {
            mIsLockDigitalSend = false
            mIsLockAnglogSend = false
        } else if (i3 >= 15) {
            mIsLockDigitalSend = true
            mIsLockAnglogSend = false
        } else {
            mIsLockDigitalSend = true
            mIsLockAnglogSend = true
        }
    }

    fun handleChargeConnected() {
        Log.i(
            TAG,
            "handleChargeConnected mIsCmdStart:" + mIsCmdStart + ", mIsSetChannelFinished:" + isSetChannelFinished
        )
        if (mIsCmdStart) {
            resetMcu()
        }
    }

    fun handleChargeDisconnected() {
        Log.i(
            TAG,
            "handleChargeDisconnected mIsCmdStart:" + mIsCmdStart + ", mIsSetChannelFinished:" + isSetChannelFinished
        )
        if (mIsCmdStart) {
            resetMcu()
        }
    }

    fun handleHeadsetPlugChanged(i: Int) {
        Log.i(TAG, "handleHeadsetPlugChanged state:$i")
        if ((i == 1 || i == 0) && mIsCmdStart) {
            resetMcu()
        }
    }

    fun handleUsbDeviceDetached() {
        Log.i(TAG, "handleUsbDeviceDetached")
        if (mIsCmdStart) {
            resetMcu()
        }
    }

    private fun handleCallInStateChanged(i: Int) {
        Log.i(
            TAG,
            "handleCallInStateChanged state:" + i + ", mCallInStateChangedCount:" + mCallInStateChangedCount
        )
        mCallInStateChangedCount = 0
        if (i == 0) {
            startPlay()
        } else if (i == 1) {
            stopPlay()
        }
    }

    private fun handleRecvSendStateChange(i: Int) {
        Log.i(
            TAG,
            "handleRecvSendStateChange state:" + i + ", mCallInStateChangedCount:" + mCallInStateChangedCount
        )
        if (i == 0 || i == 1) {
            handleCallInStateChanged(i)
            val i2 = mCallInStateChangedCount + 1
            mCallInStateChangedCount = i2
            if (i2 % 10 == 0) {
                // mScope.launch { handleCallInFast() }
            }
        } else if (i == 2) {
            startPlay()
        } else if (i == 3) {
            stopPlay()
            if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                mScope.launch {
                    delay(800)
                    if (mIsStopPlay) {
                        mExtModuleProtocol!!.getDmr09MsgContent()
                    }
                }
            }
        }
    }

    private fun handleRecvSendStateChange(bArr: ByteArray) {
        if (bArr.size > 8) {
            var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
            Log.i(TAG, "handleRecvSendStateChange dataLen:$bytesToInt2")
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256
            }
            Log.i(TAG, "handleRecvSendStateChange 111 dataLen:$bytesToInt2")
        }
    }

    suspend fun setChannel(channel: com.agold.intercom.data.Channel?) {
        Log.i(
            TAG,
            "setChannel mIsCmdStart:" + mIsCmdStart + ", mIsStopPlay:" + mIsStopPlay + ", mIsStopRecord:" + mIsStopRecord + ", mIsUsbStarted:" + mIsUsbStarted + ", mIsPTTStopComplete:" + mIsPTTStopComplete + ", channel:" + channel
        )
        if (channel != null && mIsCmdStart && mIsStopRecord && mIsUsbStarted && mIsPTTStopComplete) {
            val num = channel.num
            val type = channel.type
            Log.i(TAG, "setChannel channelType:$type, channelNum:$num")
            if (type == 1) {
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    mExtModuleProtocol!!.setDmr09AnalogGroup(
                        channel.band,
                        channel.power,
                        channel.recvFreq.toInt(),
                        channel.sendFreq.toInt(),
                        channel.squelchLevel,
                        channel.recvSubAudioType,
                        channel.recvCtcssFreq,
                        channel.sendSubAudioType,
                        channel.sendCtcssFreq,
                        1,
                        8,
                        2,
                        2
                    )
                } else {
                    mExtModuleProtocol!!.setAnalogGroup(
                        channel.band,
                        channel.power,
                        channel.recvFreq.toInt(),
                        channel.sendFreq.toInt(),
                        channel.squelchLevel,
                        channel.recvSubAudioType,
                        channel.recvCtcssFreq,
                        channel.sendSubAudioType,
                        channel.sendCtcssFreq
                    )
                }
                mPreSetChannel = num
                isSetChannelFinished = false
            } else if (type == 0) {
//                int contactType = channel.getContactType();
//                int contactNum = channel.getContactNum();
//                String contactList = channel.getContactList();
//                Log.i(TAG, "setChannel contactListStr:" + contactList + ", contactType:" + contactType + ", contactNumber:" + contactNum);
//                if (contactList == null) {
//                    contactList = "";
//                    for (int i = 0; i < 32; i++) {
//                        if (i != 0) {
//                            contactList = contactList + ":0";
//                        } else if (contactType == 2) {
//                            contactList = contactList + channel.getContactNum();
//                        } else {
//                            contactList = contactList + 1;
//                        }
//                    }
//                }
//                String[] split = contactList.split(":");
//                int[] iArr = new int[split.length];
//                for (int i2 = 0; i2 < split.length; i2++) {
//                    try {
//                        iArr[i2] = Integer.parseInt(split[i2]);
//                    } catch (Exception e) {
//                        Log.i(TAG, "setChannel ex:" + e);
//                    }
//                }
//                String encryptKey = channel.getEncryptKey();
//                IComPreference iComPreference = this.mIComPreference;
//                int deviceId = iComPreference != null ? iComPreference.getDeviceId(1) : 1;
//                int i3 = contactType == 4 ? 16777210 : contactNum;
//                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
//                    int inboundSlot = channel.getInboundSlot();
//                    int outboundSlot = channel.getOutboundSlot();
//                    int channelMode = channel.getChannelMode();
//                    int micGain = channel.getMicGain();
//                    Log.i(TAG, "setChannel inboundSlot:" + inboundSlot + ", outboundSlot:" + outboundSlot + ", channelMode:" + channelMode + ", deviceId:" + deviceId + ", micGain:" + micGain);
//                    this.mExtModuleProtocol.setDmr09DigitalGroup(channel.getPower(), (int) channel.getRecvFreq(), (int) channel.getSendFreq(), deviceId, channel.getColorCode(), contactType, i3, channel.getEncryptSwitch(), encryptKey, iArr, inboundSlot, outboundSlot, channelMode, 1, 7, micGain, 2);
//                } else {
//                    this.mExtModuleProtocol.setDigitalGroup(channel.getPower(), (int) channel.getRecvFreq(), (int) channel.getSendFreq(), deviceId, channel.getColorCode(), contactType, i3, channel.getEncryptSwitch(), encryptKey, iArr);
//                }
//                this.mPreSetChannel = num;
//                this.mIsSetChannelFinished = false;
//                this.mHandler.removeMessages(6);
//                Handler handler2 = this.mHandler;
//                handler2.sendMessageDelayed(handler2.obtainMessage(6), 3000L);
            }

            try {
                val bArr = withTimeout(3000) {
                    _moduleCommandEvents.filterIsInstance(ModuleCommand.SetChannelComplete::class)
                        .first()
                }.bArr

                var success = false
                Log.i(TAG, "setChannelComplete mPreSetChannel:" + mPreSetChannel)
                if (bArr.size > 8) {
                    var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
                    Log.i(TAG, "setChannelComplete dataLen:$bytesToInt2")
                    if (bytesToInt2 < 0) {
                        bytesToInt2 += 256
                    }
                    if (bytesToInt2 == 1) {
                        val b = bArr[8]
                        Log.i(TAG, "setChannelComplete state:" + b.toInt())
                        if (b.toInt() == 0) {
                            success = true
                        }
                    }
                    if (!success) {
                        // TODO: this is technically failure, not timeout
                        _channelState.value = State.TIMEOUT
                        return
                    }
                }
                isSetChannelFinished = true
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    mScope.launch {
                        // TODO: not sure why this has a delay
                        delay(500)
                        _channelState.value = State.READY
                    }
                } else {
                    _channelState.value = State.READY
                }
                // TODO
                val recvVolume = 6
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    mExtModuleProtocol!!.setDmr09ReceiverVolume(recvVolume)
                } else {
                    mExtModuleProtocol!!.setReceiverVolume(recvVolume)
                }
                stopPlay()
            } catch (e: TimeoutCancellationException) {
                Log.i(TAG, "onSetChannelTimeout mIsCmdStart:$mIsCmdStart")
                if (mIsCmdStart) {
                    resetMcu()
                }
                isSetChannelFinished = true
                mContext!!.sendBroadcast(Intent("agui.intercom.intent.action.START_TIMEOUT"))
                _channelState.value = State.TIMEOUT
            }
        }
    }

    fun setAudioRecordPath(str: String) {
        // TODO
        val pttRecordEnable = 1
        Log.i(TAG, "setAudioRecordPath name:$str, isPttRecordEnable:$pttRecordEnable")
        if (pttRecordEnable == 1) {
            mAudioRecordPath =
                File(
                    Companion.mContext!!.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "record/$str.pcm"
                )
        } else {
            mAudioRecordPath = null
        }
        Log.i(TAG, "setAudioRecordPath mAudioRecordPath:" + mAudioRecordPath)
    }

    @Throws(Exception::class)
    private fun readFile(file: File): String {
        val sb = StringBuilder()
        val bufferedReader = BufferedReader(FileReader(file))
        while (true) {
            val readLine = bufferedReader.readLine()
            if (readLine != null) {
                sb.append(readLine)
            } else {
                bufferedReader.close()
                return sb.toString()
            }
        }
    }

    val isAntennaInstalled: Boolean
        get() {
            if (SystemProperties.getBoolean("ro.agold.extmodule.hal", false)) {
                val file = File("/sys/hall_status/hall_status")
                if (!file.exists()) {
                    Log.i(TAG, "isAntennaInstalled hall file is not exist!")
                    return false
                }
                return try {
                    val readFile = readFile(file)
                    Log.i(TAG, "isAntennaInstalled status = $readFile")
                    !readFile.contains("0")
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
            return true
        }

    // public void showInstallAntennaToast(Context context) {
    //     Toast.makeText(context, (int) R.string.install_antenna, Toast.LENGTH_SHORT).show();
    // }

    companion object {
        private var mContext: Context? = null
        private var sInstance: ExtModuleManager? = null

        init {
            ReentrantLock()
            mContext = null
            sInstance = null
        }

        @Synchronized
        fun getInstance(context: Context): ExtModuleManager {
            synchronized(ExtModuleManager::class.java) {
                if (sInstance == null) {
                    sInstance = ExtModuleManager(context)
                }
                sInstance!!.init(context)
                return sInstance!!
            }
        }
    }
}
