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
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.os.RemoteException
import android.os.SystemProperties
import android.util.Log
import com.agold.intercom.utils.IComUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import kotlin.concurrent.withLock

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
    private val mHandler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            when (message.what) {
                1 -> if (!SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                    // onManagerStarted();
                }

                2 -> if (!SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                    // onManagerStartTimeout();
                }

                3 -> if (!SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                    // onMcuUpdateStateChanged(1);
                }

                4 -> if (!SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                    // onMcuUpdateStateChanged(0);
                    // handleMcuUpdateFinished();
                }

                5 -> {} // onSetChannelComplete();
                6 -> onSetChannelTimeout()
                7 -> {} // onMcuStartComplete();
                8 -> {} // onResetFactoryStart();
                9 -> {
                    // onMsgReceived(message.arg1, (String) message.obj);
                    // onMsgReceived();
                }

                10 -> {} // onDmrUpdateStateChanged(1);
                11 -> {
                    // handleDmrStopped();
                    // onDmrUpdateStateChanged(0);
                }

                12 -> {} // handleGetDmrTimeout();
                13 -> handleCallInStateChanged(message.arg1)
                14 -> {
                    mIsPTTStopComplete = true
                }

                15 -> {} // onDmrUpdateFailed();
                16 -> {} // handleCallInFast();
                17 -> {
                    val str = message.obj as String
                    Log.i("ExtModuleManager", "MSG_GET_INCALL_INFO callInfo:$str")
                    // String[] split = str.split(":");
                    // onGetIncallInfo(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                }

                18 -> openPcmIn()
                19 -> closePcmIn()
                20 -> openPcmOut()
                21 -> closePcmOut()
                22 -> {} // onScanChannelsStart();
                23 -> {} // onScanChannelsComplete();
            }
        }
    }
    private var mHasNewMessage = false
    private var mCallInStateChangedCount = 0

    init {
        Log.i("ExtModuleManager", "ExtModuleManager constuct")
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
        Log.i("ExtModuleManager", "onCallStateChanged currState:$i")
        val intent = Intent("agui.intercom.intent.action.CALL_STATE_CHANGED")
        intent.putExtra("callstate", i)
        mContext!!.sendBroadcast(intent)
    }

    private fun onPlayStateChanged(i: Int) {
        Log.i("ExtModuleManager", "onPlayStateChanged currState:$i")
        val intent = Intent("agui.intercom.intent.action.PLAY_STATE_CHANGED")
        intent.putExtra("playstate", i)
        mContext!!.sendBroadcast(intent)
    }

    private fun onSetChannelTimeout() {
        Log.i("ExtModuleManager", "onSetChannelTimeout mIsCmdStart:$mIsCmdStart")
        if (mIsCmdStart) {
            resetMcu()
        }
        isSetChannelFinished = true
        mContext!!.sendBroadcast(Intent("agui.intercom.intent.action.START_TIMEOUT"))
    }

    @get:Synchronized
    val aguiExtModule: IAguiExtModule?
        get() {
            if (mAguiExtModule == null) {
                try {
                    mAguiExtModule = IAguiExtModule.getService()
                } catch (e: RemoteException) {
                    Log.e("ExtModuleManager", "Failed to get interface", e)
                }
                if (mAguiExtModule == null) {
                    Log.w("ExtModuleManager", "AguiDevice HIDL not available")
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
        Log.i(
            "ExtModuleManager",
            "start mAllExit:$mAllExit, mIsCmdStart:$mIsCmdStart, mIsMCUStarted:$mIsMCUStarted"
        )
        mAllExit = false
        mIsStopping = false
        startMcu()
        mScope.launch { createCmdReadThread() }
        mScope.launch { createCallInThread() }
        mScope.launch { createAudioPlayThread() }
        mScope.launch { createAudioRecordThread() }
        mHandler.removeMessages(1)
        mHandler.sendMessage(mHandler.obtainMessage(1))
        mHandler.removeMessages(2)
        mHandler.sendMessageDelayed(mHandler.obtainMessage(2), 30000L)
    }

    fun stop() {
        Log.i("ExtModuleManager", "stop-------")
        stopCom()
        mIsStopping = true
        mHandler.postDelayed({
            exit()
            if (mContext != null) {
//                    ExtModuleManager.mContext.stopService(new Intent(ExtModuleManager.mContext, IComService.class));
                Log.i("ExtModuleManager", "stopService----------")
            }
            mIsStopping = true
        }, 300L)
    }

    fun exit() {
        Log.i("ExtModuleManager", "exit mAudioState:$mAudioState")
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
            Log.e("ExtModuleManager", "sendBytes ex:$e")
        }
    }

    fun startMcu() {
        Log.i(
            "ExtModuleManager",
            "startMcu mIsMCUStarted:$mIsMCUStarted, mIsCmdStart:$mIsCmdStart, mAllExit:$mAllExit, mIsStopping:$mIsStopping"
        )
        if (mAllExit || mIsStopping) {
            return
        }
        Thread(Runnable {
            try {
                if (mIsMCUStarted) {
                    return@Runnable
                }
                try {
                    if (aguiExtModule != null) {
                        mAguiExtModule!!.startMcu()
                        mIsMCUStarted = true
                        mIsUpdatingDmr = false
                    }
                    mHandler.postDelayed({ handleMcuStartFinished() }, 2000L)
                } catch (e: Exception) {
                    Log.e("ExtModuleManager", "startMcu ex:$e")
                }
            } catch (e2: Exception) {
                Log.e("ExtModuleManager", "startMcu e:$e2")
            }
        }).start()
    }

    fun stopMcu() {
        Log.i("ExtModuleManager", "stopMcu")
        Thread {
            try {
                Log.i("ExtModuleManager", "stopMcu mAguiExtModule:$mAguiExtModule")
                if (aguiExtModule != null) {
                    Log.i("ExtModuleManager", "mAguiExtModule stopMcu")
                    mIsMCUStarted = false
                    mIsCmdStart = false
                    mAguiExtModule!!.stopMcu()
                }
            } catch (e: Exception) {
                Log.e("ExtModuleManager", "stopMcu e:$e")
            }
        }.start()
        isSetChannelFinished = false
    }

    fun resetMcu() {
        Log.i("ExtModuleManager", "resetMcu")
        stopCom()
        mHandler.postDelayed({ startMcu() }, 4000L)
    }

    fun startCom() {
        Log.i("ExtModuleManager", "startCom mIsCmdStart:$mIsCmdStart")
        if (mIsCmdStart) {
            return
        }
        mIsCmdStart = true
    }

    fun stopCom() {
        Log.i(
            "ExtModuleManager",
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

    private fun handleMcuStartFinished() {
        Log.i(
            "ExtModuleManager",
            "handleMcuStartFinished mIsSetChannelFinished:$isSetChannelFinished"
        )
        mIsUsbStarted = true
        isSetChannelFinished = false
        mHandler.removeMessages(7)
        val handler = mHandler
        handler.sendMessage(handler.obtainMessage(7))
        mHandler.removeMessages(2)
        val handler2 = mHandler
        handler2.sendMessageDelayed(handler2.obtainMessage(2), 30000L)
        startCom()
        mHandler.postDelayed({ mcuFirmwareVersion }, 1000L)
    }

    private val mcuFirmwareVersion: Unit
        get() {
            Log.i(
                "ExtModuleManager",
                "getMcuFirmwareVersion mCurrFirmware:$mCurrFirmware, mIsCmdStart:$mIsCmdStart"
            )
            if (mIsCmdStart) {
                mCurrFirmware = null
                val extModuleProtocol = mExtModuleProtocol
                extModuleProtocol?.getFirmwareVersion()
                mHandler.postDelayed({
                    if (mExtModuleProtocol != null) {
                        if (mCurrFirmware.isNullOrEmpty()) {
                            mExtModuleProtocol.getFirmwareVersion()
                        }
                    }
                }, 1000L)
                mHandler.postDelayed({
                    if (mExtModuleProtocol != null) {
                        if (mCurrFirmware.isNullOrEmpty()) {
                            mExtModuleProtocol.getFirmwareVersion()
                        }
                    }
                }, 2000L)
            }
        }

    private fun handleRecvFirmwareVersion(bArr: ByteArray) {
        Log.i("ExtModuleManager", "handleRecvFirmwareVersion mCurrFirmware:$mCurrFirmware")
        if ((mCurrFirmware.isNullOrEmpty()) && bArr.size > 8) {
            var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256
            }
            if (bytesToInt2 > 0) {
                val bArr2 = ByteArray(bytesToInt2)
                System.arraycopy(bArr, 8, bArr2, 0, bytesToInt2)
                mCurrFirmware = bArr2.toString(Charsets.UTF_8)
                Log.i(
                    "ExtModuleManager",
                    "handleRecvFirmwareVersion mCurrFirmware:$mCurrFirmware"
                )
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    mExtModuleProtocol!!.getDmr09ModuleSoftVersion()
                } else {
                    mExtModuleProtocol!!.getModuleSoftVersion()
                }
                mHandler.removeMessages(12)
                val handler = mHandler
                handler.sendMessageDelayed(handler.obtainMessage(12), 3000L)
            }
        }
    }

    private fun openPcmIn() {
        Thread {
            try {
                if (aguiExtModule != null) {
                    val openPcmIn = mAguiExtModule!!.openPcmIn()
                    Log.i("ExtModuleManager", "openPcmIn ret:$openPcmIn")
                    if (openPcmIn == 1) {
                        mIsPcmInStart = true
                    }
                }
            } catch (e: Exception) {
                Log.e("ExtModuleManager", "openPcmIn e:$e")
            }
        }.start()
    }

    private fun closePcmIn() {
        Thread {
            try {
                if (aguiExtModule != null) {
                    val closePcmIn = mAguiExtModule!!.closePcmIn()
                    Log.i("ExtModuleManager", "closePcmIn ret:$closePcmIn")
                    mIsPcmInStart = false
                }
            } catch (e: Exception) {
                Log.e("ExtModuleManager", "closePcmIn e:$e")
            }
        }.start()
    }

    private fun openPcmOut() {
        Log.i("ExtModuleManager", "openPcmOut mIsStopRecord:$mIsStopRecord")
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
                        Log.i("ExtModuleManager", "openPcmOut ret:$openPcmOut")
                        if (openPcmOut == 1) {
                            mIsPcmOutStart = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ExtModuleManager", "openPcmOut e:$e")
                }
            }.start()
        }
    }

    private fun closePcmOut() {
        Log.i("ExtModuleManager", "closePcmOut mIsStopRecord:" + mIsStopRecord)
        if (mIsStopRecord) {
            return
        }
        Thread {
            try {
                if (aguiExtModule != null) {
                    val closePcmOut = mAguiExtModule!!.closePcmOut()
                    Log.i("ExtModuleManager", "closePcmOut ret:$closePcmOut")
                    mAguiExtModule!!.stopPtt()
                }
            } catch (e: Exception) {
                Log.e("ExtModuleManager", "closePcmOut e:$e")
            }
        }.start()
        onCallStateChanged(0)
        mHandler.postDelayed({
            mIsStopRecord = true
            mIsPcmOutStart = false
        }, 500L)
        if (mIsStopPlay) {
            releaseMusicFocus()
        }
    }

    private suspend fun createCmdReadThread() {
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
                                mAguiExtModule!!.readTTyDevice(object :
                                    IAguiExtModuleReadCallback.Stub() {
                                    @Throws(RemoteException::class)
                                    override fun onReadDevice(bArr: ByteArray, i: Int) {
                                        if (i > 0) {
                                            Log.i(
                                                "ExtModuleManager",
                                                "onReadTTyDevice readSize:$i"
                                            )
                                            handleCmdResponse(bArr, i)
                                        }
                                    }
                                })
                            }
                        } catch (e: RemoteException) {
                            Log.e("ExtModuleManager", "createCmdReadThread ex:$e")
                        }
                        delay(100L)
                    }
                } catch (e2: Exception) { // TODO: more specific catch to avoid catching CancellationException
                    Log.e("ExtModuleManager", "createCmdReadThread e:$e2")
                    return@withContext
                }
            }
            Log.i("ExtModuleManager", "createCmdReadThread exit")
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
                                Log.i(
                                    "ExtModuleManager",
                                    "createCallInThread start detectAudioinState"
                                )
                                val detectAudioinState = aguiExtModule!!.detectAudioinState()
                                Log.i(
                                    "ExtModuleManager",
                                    "createCallInThread callInState:$detectAudioinState"
                                )
                                handleCallInStateChanged(detectAudioinState)
                            }
                        } catch (e: RemoteException) {
                            Log.e("ExtModuleManager", "createCallInThread ex:$e")
                        }
                        delay(100L)
                    }
                } catch (e2: Exception) {
                    Log.e("ExtModuleManager", "createCallInThread e:$e2")
                    return@withContext
                }
            }
            Log.i("ExtModuleManager", "createCallInThread exit")
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
                        Log.i(
                            "ExtModuleManager",
                            "createAudioPlayThread mAudioTrack:$mAudioTrack"
                        )
                        if (mAudioTrack == null) {
                            val minBufferSize = AudioTrack.getMinBufferSize(
                                mPlayFrequency,
                                AudioFormat.CHANNEL_OUT_STEREO,
                                AudioFormat.ENCODING_PCM_16BIT
                            )
                            Log.i(
                                "ExtModuleManager",
                                "createAudioTrack playBufSize:$minBufferSize"
                            )
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
                        Log.i(
                            "ExtModuleManager",
                            "createAudioPlayThread mAudioTrack:$mAudioTrack"
                        )
                        Log.i(
                            "ExtModuleManager",
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
                                                Log.i(
                                                    "ExtModuleManager",
                                                    "readPcmDevice readSize:$i"
                                                )
                                                try {
                                                    mAudioTrack?.write(bArr, 0, i)
                                                    mPcmRecordBOS?.write(bArr, 0, i)
                                                } catch (e: Exception) {
                                                    Log.e(
                                                        "ExtModuleManager",
                                                        "onReadDevice e:$e"
                                                    )
                                                }
                                            }
                                        }
                                    })
                                }
                            } catch (e: RemoteException) {
                                Log.e("ExtModuleManager", "createAudioPlayThread ex:$e")
                                Log.e(
                                    "ExtModuleManager",
                                    "createAudioPlayThread mAguiExtModule:" + mAguiExtModule
                                )
                                Log.e(
                                    "ExtModuleManager",
                                    "createAudioPlayThread IAguiExtModule.getService():" + IAguiExtModule.getService()
                                )
                                mAguiExtModule = null
                            }
                        }
                        mAudioTrack!!.stop()
                        mAudioTrack = null
                    }
                } catch (e2: Exception) {
                    Log.e("ExtModuleManager", "createAudioPlayThread e:$e2")
                    return@withContext
                }
            }
            Log.i("ExtModuleManager", "createAudioPlayThread exit")
        }
    }

    private suspend fun createAudioRecordThread() {
        withContext(Dispatchers.IO) {
            var read: Int
            while (isActive) {
                try {
                    if (mIsStopRecord) {
                        Log.i("ExtModuleManager", "createAudioRecordThread mAudioLock.receive!")
                        mAudioLock.receive()
                    }
                    if (!mIsPcmOutStart) {
                        delay(10L)
                    } else {
                        val bArr = ByteArray(1920)
                        Log.i(
                            "ExtModuleManager",
                            "createAudioRecordThread mAudioRecordPath:$mAudioRecordPath"
                        )
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
                    Log.i("ExtModuleManager", "createAudioRecordThread error:$e")
                }
            }
            Log.i("ExtModuleManager", "createAudioRecordThread exit")
        }
    }

    fun startPlay(): Int {
        Log.i("ExtModuleManager", "startPlay start mIsStopPlay:" + mIsStopPlay)
        return if (!mIsStopPlay) {
            Log.i("ExtModuleManager", "startPlay--->play is not stopped")
            0
        } else if (!mIsMCUStarted || !mIsCmdStart) {
            Log.i("ExtModuleManager", "startPlay--->cmd COM is not open!")
            0
        } else if (!mIsStopRecord) {
            Log.i("ExtModuleManager", "startPlay Record is not stop!")
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
            mHandler.removeMessages(18)
            val handler = mHandler
            handler.sendMessageDelayed(handler.obtainMessage(18), 100L)
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
                Log.e("ExtModuleManager", "startPlay ex:$e")
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
            Log.i("ExtModuleManager", "startPlay end-----------------")
            0
        }
    }

    fun stopPlay() {
        Log.i("ExtModuleManager", "stopPlay mIsStopPlay:" + mIsStopPlay)
        if (mIsStopPlay) {
            Log.i("ExtModuleManager", "stopPlay---> play is not started")
            return
        }
        mIsStopPlay = true
        mHandler.removeMessages(19)
        val handler = mHandler
        handler.sendMessage(handler.obtainMessage(19))
        try {
            if (mPcmRecordBOS != null) {
                mPcmRecordBOS!!.close()
                mPcmRecordBOS = null
            }
        } catch (e: Exception) {
            Log.e("ExtModuleManager", "stopPlay ex:$e")
        }
        //        int currChannel = this.mIComPreference.getCurrChannel(1);
//        int currArea = this.mIComPreference.getCurrArea(1);
//        long currentTimeMillis = System.currentTimeMillis() - this.mStartPlayTime;
//        Log.i("ExtModuleManager", "stopPlay channelId:" + currChannel + ", channelArea:" + currArea + ", recordPath:" + this.mAudioRecordPath + ", duration:" + currentTimeMillis + ", mStartPlayTime:" + this.mStartPlayTime + ", mIsSettingFactory:" + this.mIsSettingFactory);
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
        mHandler.postDelayed({
            if (mIsStopPlay && mHasNewMessage) {
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    mExtModuleProtocol!!.getDmr09MsgContent()
                } else {
                    mExtModuleProtocol!!.getMsgContent()
                }
                mHasNewMessage = false
            }
        }, 500L)
        Log.i("ExtModuleManager", "stopPlay end-----------------")
    }

    fun startRecord() {
        Log.i(
            "ExtModuleManager",
            "startRecord mAudioState:" + mAudioState + ", mIsStopRecord:" + mIsStopRecord + ", mIsLockDigitalSend:" + mIsLockDigitalSend + ", mIsLockAnglogSend:" + mIsLockAnglogSend + ", mIsSetChannelFinished:" + isSetChannelFinished
        )
        if (!mIsMCUStarted || !mIsCmdStart) {
            Log.i("ExtModuleManager", "startRecord cmd COM is not open!")
        } else if (!isSetChannelFinished) {
            Log.i("ExtModuleManager", "startRecord channel is not set finished!")
        } else if (!mIsStopRecord) {
            Log.i("ExtModuleManager", "startRecord is not stopped!")
        } else {
            if (mAudioState == 0) {
                mAudioState = 1
                if (requestMusicFocus() == 0) {
                    mAudioState = 0
                }
                mHandler.removeMessages(20)
                val handler2 = mHandler
                handler2.sendMessage(handler2.obtainMessage(20))
                onCallStateChanged(1)
            }
        }
    }

    private fun requestMusicFocus(): Int {
        Log.i(
            "ExtModuleManager",
            "requestMusicFocus mCurrentAudioFocusRequest:" + mCurrentAudioFocusRequest
        )
        if (mCurrentAudioFocusRequest != null) {
            return 1
        }
        if (mAudioManager != null) {
            val focusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(mAudioAttributes)
                    .build()
            val requestAudioFocus = mAudioManager.requestAudioFocus(focusRequest)
            Log.i("ExtModuleManager", "requestMusicFocus status:$requestAudioFocus")
            mCurrentAudioFocusRequest = focusRequest
            return 1
        }
        return 0
    }

    private fun releaseMusicFocus() {
        Log.i(
            "ExtModuleManager",
            "releaseMusicFocus mCurrentAudioFocusRequest:" + mCurrentAudioFocusRequest
        )
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
        Log.i("ExtModuleManager", "handleCmdResponse len:$length, strCmd:$byte2HexStrNoBlank")
        var i2 = 0
        while (i2 + 11 < length) {
            try {
                if (IComUtils.isIComCmd(bArr2, i2)) {
                    var bytesToInt2 =
                        IComUtils.bytesToInt2(byteArrayOf(bArr2[i2 + 6], bArr2[i2 + 7]))
                    if (bytesToInt2 < 0) {
                        bytesToInt2 += 256
                    }
                    Log.i(
                        "ExtModuleManager",
                        "handleCmdResponse index:$i2, currDataLen:$bytesToInt2"
                    )
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
                Log.e("ExtModuleManager", "handleCmdResponse e:$e")
                return length
            }
        }
        return i2
    }

    private fun parseCmd(bArr: ByteArray) {
        val byte2HexStr = IComUtils.byte2HexStr(bArr)
        Log.i("ExtModuleManager", "parseCmd strCmd:$byte2HexStr")
        if (IComUtils.isIComCmd(bArr)) {
            Log.i("ExtModuleManager", "parseCmd cmdByte[3]:" + bArr[3].toInt())
            if (bArr[3].toInt() == 0) {
                val b = bArr[4]
                Log.i("ExtModuleManager", "parseCmd sys cmd:" + b.toInt())
                when (b.toInt()) {
                    1 -> handleRecvFirmwareVersion(bArr)
                    4 -> handleMcuInitFinished()
                    5 -> {} // handleMcuUpdateFinished()
                    6 -> handleMcuStartFinished()
                    7 -> handleMcuErrorReport(bArr)
                    8 -> handleMcuStateReport(bArr)
                    9 -> {} // handleDmrUpdated()
                    10 -> {} // handleDmrUpdatFailed()
                }
            } else if (bArr[3].toInt() == 1) {
                val b2 = bArr[4]
                Log.i("ExtModuleManager", "parseCmd module cmd:" + b2.toInt())
                when (b2.toInt()) {
                    4 -> handleRecvSendStateChange(bArr[8].toInt())
                    17 -> {} // getMsgContent(bArr);
                    37 -> getModuleVersonContent(bArr)
                    43 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        // getDmr09CallInfo(bArr);
                    }

                    45 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        // getDmr09MsgContent(bArr);
                    }

                    6 -> handleRecvSendStateChange(bArr)
                    7 -> {} // handleMsgResponse(bArr[8]);
                    34 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        setChannelComplete(bArr)
                    }

                    35 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        setChannelComplete(bArr)
                    }

                    52 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        getModuleVersonContent(bArr)
                    }

                    53 -> setChannelComplete(bArr)
                    54 -> if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        // handleModuleResponse(bArr[8]);
                    } else {
                        setChannelComplete(bArr)
                    }
                }
            } else if (bArr[3].toInt() == 2 && bArr.size > 8) {
                var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
                Log.i("ExtModuleManager", "parseCmd dataLen:$bytesToInt2")
                if (bytesToInt2 < 0) {
                    bytesToInt2 += 256
                }
                if (bytesToInt2 > 0) {
                    val bArr2 = ByteArray(bytesToInt2)
                    System.arraycopy(bArr, 8, bArr2, 0, bytesToInt2)
                    val byte2HexStr2 = IComUtils.byte2HexStr(bArr2)
                    Log.i("ExtModuleManager", "parseCmd retData:$byte2HexStr2")
                }
            }
        }
    }

    private fun handleMcuInitFinished() {
        Log.i("ExtModuleManager", "handleMcuInitFinished")
    }

    private fun handleMcuErrorReport(bArr: ByteArray) {
        Log.i("ExtModuleManager", "handleMcuErrorReport")
        if (bArr.size <= 8 || IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7])) <= 0) {
            return
        }
        val b = bArr[8]
        Log.i("ExtModuleManager", "handleMcuErrorReport errorCode:" + b.toInt())
    }

    private fun handleMcuStateReport(bArr: ByteArray) {
        Log.i("ExtModuleManager", "handleMcuStateReport")
        if (bArr.size <= 8 || IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7])) <= 0) {
            return
        }
        val b = bArr[8]
        Log.i(
            "ExtModuleManager",
            "handleMcuStateReport cmdState:" + b.toInt() + ", mHasNewMessage:" + mHasNewMessage
        )
        if (b.toInt() == 1) {
            return
        }
        if (b.toInt() != 2) {
            if (b.toInt() == 3) {
                Log.i("ExtModuleManager", "handleMcuStateReport pcm error, we need to reset mcu!!!")
                resetMcu()
                return
            }
            return
        }
        mHandler.removeMessages(14)
        mHandler.postDelayed({ mIsPTTStopComplete = true }, 1000L)
        if (mIsStopRecord && mHasNewMessage) {
            if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                mExtModuleProtocol!!.getDmr09MsgContent()
            } else {
                mExtModuleProtocol!!.getMsgContent()
            }
            mHasNewMessage = false
        }
    }

    private fun getModuleVersonContent(bArr: ByteArray) {
        if (bArr.size > 8) {
            var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
            Log.i("ExtModuleManager", "getModuleVersonContent dataLen:$bytesToInt2")
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256
            }
            if (bytesToInt2 > 0) {
                val i = bytesToInt2 - 1
                val bArr2 = ByteArray(i)
                System.arraycopy(bArr, 8, bArr2, 0, i)
                val str = bArr2.toString(Charsets.UTF_8)
                Log.i("ExtModuleManager", "getModuleVersonContent module:$str")

//                Channel channel = new Channel();
//                this.setChannel(channel);
            }
        }
    }

    fun checkTemperature(): Int {
        Log.i("ExtModuleManager", "checkTemperature mCurrTemperature:" + mCurrTemperature)
        val i = mCurrTemperature
        if (i < -20) {
            return -1
        }
        return if (i > 550) 1 else 0
    }

    fun handleBatteryChanged(i: Int, i2: Int, z: Boolean, z2: Boolean) {
        Log.i(
            "ExtModuleManager",
            "handleBatteryChanged level:" + i + ", mCurrBatteryLevel:" + mCurrBatteryLevel
        )
        Log.i(
            "ExtModuleManager",
            "handleBatteryChanged temp:" + i2 + ", mCurrTemperature:" + mCurrTemperature
        )
        Log.i(
            "ExtModuleManager",
            "handleBatteryChanged isCharging:" + z + ", mIsCharging:" + mIsCharging
        )
        Log.i(
            "ExtModuleManager",
            "handleBatteryChanged isUsbCharge:" + z2 + ", mIsUSBCharge:" + mIsUSBCharge
        )
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
            "ExtModuleManager",
            "handleChargeConnected mIsCmdStart:" + mIsCmdStart + ", mIsSetChannelFinished:" + isSetChannelFinished
        )
        if (mIsCmdStart) {
            resetMcu()
        }
    }

    fun handleChargeDisconnected() {
        Log.i(
            "ExtModuleManager",
            "handleChargeDisconnected mIsCmdStart:" + mIsCmdStart + ", mIsSetChannelFinished:" + isSetChannelFinished
        )
        if (mIsCmdStart) {
            resetMcu()
        }
    }

    fun handleHeadsetPlugChanged(i: Int) {
        Log.i("ExtModuleManager", "handleHeadsetPlugChanged state:$i")
        if ((i == 1 || i == 0) && mIsCmdStart) {
            resetMcu()
        }
    }

    fun handleUsbDeviceDetached() {
        Log.i("ExtModuleManager", "handleUsbDeviceDetached")
        if (mIsCmdStart) {
            resetMcu()
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:13:0x0067  */ /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private fun setChannelComplete(bArr: ByteArray) {
        var success = false
        Log.i("ExtModuleManager", "setChannelComplete mPreSetChannel:" + mPreSetChannel)
        if (bArr.size > 8) {
            var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
            Log.i("ExtModuleManager", "setChannelComplete dataLen:$bytesToInt2")
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256
            }
            if (bytesToInt2 == 1) {
                val b = bArr[8]
                Log.i("ExtModuleManager", "setChannelComplete state:" + b.toInt())
                if (b.toInt() == 0) {
                    success = true
                }
            }
            if (!success) {
                mHandler.removeMessages(6)
                mHandler.sendMessage(mHandler.obtainMessage(6))
                return
            }
        }
        isSetChannelFinished = true
        mHandler.removeMessages(6)
        mHandler.removeMessages(2)
        if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
            mHandler.removeMessages(5)
            val handler2 = mHandler
            handler2.sendMessageDelayed(handler2.obtainMessage(5), 500L)
        } else {
            mHandler.removeMessages(5)
            val handler3 = mHandler
            handler3.sendMessage(handler3.obtainMessage(5))
        }
        // TODO
        val recvVolume = 6
        if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
            mExtModuleProtocol!!.setDmr09ReceiverVolume(recvVolume)
        } else {
            mExtModuleProtocol!!.setReceiverVolume(recvVolume)
        }
        stopPlay()
    }

    private fun handleCallInStateChanged(i: Int) {
        Log.i(
            "ExtModuleManager",
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
            "ExtModuleManager",
            "handleRecvSendStateChange state:" + i + ", mCallInStateChangedCount:" + mCallInStateChangedCount
        )
        if (i == 0 || i == 1) {
            handleCallInStateChanged(i)
            val i2 = mCallInStateChangedCount + 1
            mCallInStateChangedCount = i2
            if (i2 % 10 == 0) {
                mHandler.removeMessages(16)
                val handler = mHandler
                handler.sendMessage(handler.obtainMessage(16))
            }
        } else if (i == 2) {
            startPlay()
        } else if (i == 3) {
            stopPlay()
            if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                mHandler.postDelayed({
                    if (mIsStopPlay) {
                        mExtModuleProtocol!!.getDmr09MsgContent()
                    }
                }, 800L)
            }
        }
    }

    private fun handleRecvSendStateChange(bArr: ByteArray) {
        if (bArr.size > 8) {
            var bytesToInt2 = IComUtils.bytesToInt2(byteArrayOf(bArr[6], bArr[7]))
            Log.i("ExtModuleManager", "handleRecvSendStateChange dataLen:$bytesToInt2")
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256
            }
            Log.i("ExtModuleManager", "handleRecvSendStateChange 111 dataLen:$bytesToInt2")
        }
    }

    fun setChannel(channel: com.agold.intercom.data.Channel?) {
        Log.i(
            "ExtModuleManager",
            "setChannel mIsCmdStart:" + mIsCmdStart + ", mIsStopPlay:" + mIsStopPlay + ", mIsStopRecord:" + mIsStopRecord + ", mIsUsbStarted:" + mIsUsbStarted + ", mIsPTTStopComplete:" + mIsPTTStopComplete + ", channel:" + channel
        )
        if (channel != null && mIsCmdStart && mIsStopRecord && mIsUsbStarted && mIsPTTStopComplete) {
            val num = channel.num
            val type = channel.type
            Log.i("ExtModuleManager", "setChannel channelType:$type, channelNum:$num")
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
                mHandler.removeMessages(6)
                val handler = mHandler
                handler.sendMessageDelayed(handler.obtainMessage(6), 3000L)
            } else if (type == 0) {
//                int contactType = channel.getContactType();
//                int contactNum = channel.getContactNum();
//                String contactList = channel.getContactList();
//                Log.i("ExtModuleManager", "setChannel contactListStr:" + contactList + ", contactType:" + contactType + ", contactNumber:" + contactNum);
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
//                        Log.i("ExtModuleManager", "setChannel ex:" + e);
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
//                    Log.i("ExtModuleManager", "setChannel inboundSlot:" + inboundSlot + ", outboundSlot:" + outboundSlot + ", channelMode:" + channelMode + ", deviceId:" + deviceId + ", micGain:" + micGain);
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
        }
    }

    fun setAudioRecordPath(str: String) {
        // TODO
        val pttRecordEnable = 1
        Log.i(
            "ExtModuleManager",
            "setAudioRecordPath name:$str, isPttRecordEnable:$pttRecordEnable"
        )
        if (pttRecordEnable == 1) {
            mAudioRecordPath =
                File(
                    Companion.mContext!!.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    "record/$str.pcm"
                )
        } else {
            mAudioRecordPath = null
        }
        Log.i("ExtModuleManager", "setAudioRecordPath mAudioRecordPath:" + mAudioRecordPath)
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
                    Log.i("ExtModuleManager", "isAntennaInstalled hall file is not exist!")
                    return false
                }
                return try {
                    val readFile = readFile(file)
                    Log.i("ExtModuleManager", "isAntennaInstalled status = $readFile")
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
