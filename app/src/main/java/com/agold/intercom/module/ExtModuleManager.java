package com.agold.intercom.module;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.SoundPool;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import com.agold.intercom.data.Channel;
import com.agold.intercom.utils.IComUtils;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.locks.ReentrantLock;

import vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule;
import vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModuleReadCallback;

public class ExtModuleManager {
    private static Context mContext;
    private static ExtModuleManager sInstance;

    static {
        new ReentrantLock();
        mContext = null;
        sInstance = null;
    }

    private final AudioManager mAudioManager;
    private final ExtModuleProtocol mExtModuleProtocol;
    private final SoundPool mSoundPool;
    private final PowerManager.WakeLock mWakeLock;
    private final byte[] mCmdWriteBuffer = new byte[1920];
    private final Object mAudioLock = new Object();
    AudioTrack mAudioTrack;
    AudioTrack mExtAudioTrack;
    private int AUDIO_FRAME_SIZE;
    private IAguiExtModule mAguiExtModule;
    private BufferedOutputStream mPcmRecordBOS;
    private int mPlayFrequency;
    private int mRecFrequency;
    private boolean mIsStopRecord = true;
    private boolean mIsPTTStopComplete = true;
    private boolean mIsStopPlay = true;
    private int mAudioState = 0;
    private boolean mIsCmdStart = false;
    private boolean mIsMCUStarted = false;
    private boolean mIsUsbStarted = false;
    private boolean mIsPcmInStart = false;
    private boolean mIsPcmOutStart = false;
    private boolean mIsUpdatingDmr = false;
    private boolean mAllExit = true;
    private AudioFocusRequest mCurrentAudioFocusRequest = null;
    private File mAudioRecordPath = null;
    private String mCurrFirmware = null;
    private int mPreSetChannel = 0;
    private boolean mIsSetChannelFinished = false;
    private int mCurrBatteryLevel = 100;
    private int mCurrTemperature = 20;
    private boolean mIsCharging = false;
    private boolean mIsUSBCharge = false;
    private boolean mIsLockDigitalSend = false;
    private boolean mIsLockAnglogSend = false;
    private long mStartPlayTime = 0;
    private long mStartCallTime = 0;
    private boolean mIsStopping = false;
    private final AudioAttributes mAudioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override // android.os.Handler
        public void handleMessage(Message message) {
            switch (message.what) {
                case 1:
                    if (SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                        return;
                    }
//                    ExtModuleManager.this.onManagerStarted();
                    return;
                case 2:
                    if (SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                        return;
                    }
//                    ExtModuleManager.this.onManagerStartTimeout();
                    return;
                case 3:
                    if (SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                        return;
                    }
//                    ExtModuleManager.this.onMcuUpdateStateChanged(1);
                    return;
                case 4:
                    if (SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                        return;
                    }
//                    ExtModuleManager.this.onMcuUpdateStateChanged(0);
//                    ExtModuleManager.this.handleMcuUpdateFinished();
                    return;
                case 5:
//                    ExtModuleManager.this.onSetChannelComplete();
                    return;
                case 6:
                    ExtModuleManager.this.onSetChannelTimeout();
                    return;
                case 7:
//                    ExtModuleManager.this.onMcuStartComplete();
                    return;
                case 8:
//                    ExtModuleManager.this.onResetFactoryStart();
                    return;
                case 9:
//                    ExtModuleManager.this.onMsgReceived(message.arg1, (String) message.obj);
//                    ExtModuleManager.this.onMsgReceived();
                    return;
                case 10:
//                    ExtModuleManager.this.onDmrUpdateStateChanged(1);
                    return;
                case 11:
//                    ExtModuleManager.this.handleDmrStopped();
//                    ExtModuleManager.this.onDmrUpdateStateChanged(0);
                    return;
                case 12:
//                    ExtModuleManager.this.handleGetDmrTimeout();
                    return;
                case 13:
                    ExtModuleManager.this.handleCallInStateChanged(message.arg1);
                    return;
                case 14:
                    ExtModuleManager.this.mIsPTTStopComplete = true;
                    return;
                case 15:
//                    ExtModuleManager.this.onDmrUpdateFailed();
                    return;
                case 16:
//                    ExtModuleManager.this.handleCallInFast();
                    return;
                case 17:
                    String str = (String) message.obj;
                    Log.i("ExtModuleManager", "MSG_GET_INCALL_INFO callInfo:" + str);
                    String[] split = str.split(":");
//                    ExtModuleManager.this.onGetIncallInfo(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
                    return;
                case 18:
                    ExtModuleManager.this.openPcmIn();
                    return;
                case 19:
                    ExtModuleManager.this.closePcmIn();
                    return;
                case 20:
                    ExtModuleManager.this.openPcmOut();
                    return;
                case 21:
                    ExtModuleManager.this.closePcmOut();
                    return;
                case 22:
//                    ExtModuleManager.this.onScanChannelsStart();
                    return;
                case 23:
//                    ExtModuleManager.this.onScanChannelsComplete();
                    return;
                default:
            }
        }
    };
    private boolean mHasNewMessage = false;
    private int mCallInStateChangedCount = 0;

    public ExtModuleManager(Context context) {
        this.mRecFrequency = 48000;
        this.mPlayFrequency = 48000;
        this.AUDIO_FRAME_SIZE = 1920;
        Log.i("ExtModuleManager", "ExtModuleManager constuct");
        ExtModuleProtocol extModuleProtocol = new ExtModuleProtocol();
        this.mExtModuleProtocol = extModuleProtocol;
        extModuleProtocol.setExtModuleManager(this);
        this.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.mWakeLock = powerManager.newWakeLock(26, "Intercom");
        this.mSoundPool = new SoundPool.Builder()
                .setMaxStreams(10)
                .setAudioAttributes(this.mAudioAttributes)
			    .build();
        if (SystemProperties.getBoolean("ro.agold.extmodule.32k", false)) {
            this.mRecFrequency = 32000;
            this.mPlayFrequency = 32000;
            this.AUDIO_FRAME_SIZE = 1280;
        }
    }

    public static synchronized ExtModuleManager getInstance(Context context) {
        ExtModuleManager extModuleManager;
        synchronized (ExtModuleManager.class) {
            if (sInstance == null) {
                sInstance = new ExtModuleManager(context);
            }
            if (sInstance != null) {
                sInstance.init(context);
            }
            extModuleManager = sInstance;
        }
        return extModuleManager;
    }

    private void onCallStateChanged(int i) {
        Log.i("ExtModuleManager", "onCallStateChanged currState:" + i);
        Intent intent = new Intent("agui.intercom.intent.action.CALL_STATE_CHANGED");
        intent.putExtra("callstate", i);
        mContext.sendBroadcast(intent);
    }

    private void onPlayStateChanged(int i) {
        Log.i("ExtModuleManager", "onPlayStateChanged currState:" + i);
        Intent intent = new Intent("agui.intercom.intent.action.PLAY_STATE_CHANGED");
        intent.putExtra("playstate", i);
        mContext.sendBroadcast(intent);
    }

    private void onSetChannelTimeout() {
        Log.i("ExtModuleManager", "onSetChannelTimeout mIsCmdStart:" + this.mIsCmdStart);
        if (this.mIsCmdStart) {
            resetMcu();
        }
        this.mIsSetChannelFinished = true;
        mContext.sendBroadcast(new Intent("agui.intercom.intent.action.START_TIMEOUT"));
    }

    public synchronized IAguiExtModule getAguiExtModule() {
        if (this.mAguiExtModule == null) {
            try {
                this.mAguiExtModule = IAguiExtModule.getService();
            } catch (RemoteException e) {
                Log.e("ExtModuleManager", "Failed to get interface", e);
            }
            if (this.mAguiExtModule == null) {
                Log.w("ExtModuleManager", "AguiDevice HIDL not available");
                return null;
            }
        }
        return this.mAguiExtModule;
    }

    private void init(Context context) {
        if (mContext == null) {
            mContext = context;
        }
    }

    public void setContext(Context context) {
        mContext = context;
    }

    public void start() {
        Log.i("ExtModuleManager", "start mAllExit:" + this.mAllExit + ", mIsCmdStart:" + this.mIsCmdStart + ", mIsMCUStarted:" + this.mIsMCUStarted);
        this.mAllExit = false;
        this.mIsStopping = false;
        startMcu();
        createCmdReadThread();
        createCallInThread();
        createAudioPlayThread();
        createAudioRecordThread();
        this.mHandler.removeMessages(1);
        this.mHandler.sendMessage(this.mHandler.obtainMessage(1));
        this.mHandler.removeMessages(2);
        this.mHandler.sendMessageDelayed(this.mHandler.obtainMessage(2), 30000L);
        this.createAudioTrack();
    }

    public void stop() {
        Log.i("ExtModuleManager", "stop-------");
        stopCom();
        this.mIsStopping = true;
        this.mHandler.postDelayed(() -> {
            ExtModuleManager.this.exit();
            if (ExtModuleManager.mContext != null) {
//                    ExtModuleManager.mContext.stopService(new Intent(ExtModuleManager.mContext, IComService.class));
                Log.i("ExtModuleManager", "stopService----------");
            }
            ExtModuleManager.this.mIsStopping = true;
        }, 300L);
    }

    public void exit() {
        Log.i("ExtModuleManager", "exit mAudioState:" + this.mAudioState);
        this.mAllExit = true;
        this.mIsStopRecord = true;
        this.mIsPTTStopComplete = true;
        this.mIsStopPlay = true;
        this.mIsCmdStart = false;
        this.mIsMCUStarted = false;
        PowerManager.WakeLock wakeLock = this.mWakeLock;
        if (wakeLock != null && wakeLock.isHeld()) {
            this.mWakeLock.release();
        }
        AudioTrack audioTrack = this.mAudioTrack;
        if (audioTrack != null) {
            audioTrack.release();
            this.mAudioTrack = null;
        }
        AudioTrack audioTrack2 = this.mExtAudioTrack;
        if (audioTrack2 != null) {
            audioTrack2.release();
            this.mExtAudioTrack = null;
        }
    }

    public void sendBytes(byte[] bArr) {
        try {
            if (getAguiExtModule() != null) {
                System.arraycopy(bArr, 0, this.mCmdWriteBuffer, 0, bArr.length);
                this.mAguiExtModule.writeTTyDevice(this.mCmdWriteBuffer, bArr.length);
            }
        } catch (Exception e) {
            Log.e("ExtModuleManager", "sendBytes ex:" + e);
        }
    }

    public void startMcu() {
        Log.i("ExtModuleManager", "startMcu mIsMCUStarted:" + this.mIsMCUStarted + ", mIsCmdStart:" + this.mIsCmdStart + ", mAllExit:" + this.mAllExit + ", mIsStopping:" + this.mIsStopping);
        if (this.mAllExit || this.mIsStopping) {
            return;
        }
        new Thread(() -> {
            try {
                if (ExtModuleManager.this.mIsMCUStarted) {
                    return;
                }
                try {
                    if (ExtModuleManager.this.getAguiExtModule() != null) {
                        ExtModuleManager.this.mAguiExtModule.startMcu();
                        ExtModuleManager.this.mIsMCUStarted = true;
                        ExtModuleManager.this.mIsUpdatingDmr = false;
                    }
                    ExtModuleManager.this.mHandler.postDelayed(ExtModuleManager.this::handleMcuStartFinished, 2000L);
                } catch (Exception e) {
                    Log.e("ExtModuleManager", "startMcu ex:" + e);
                }
            } catch (Exception e2) {
                Log.e("ExtModuleManager", "startMcu e:" + e2);
            }
        }).start();
    }

    public void stopMcu() {
        Log.i("ExtModuleManager", "stopMcu");
        new Thread(() -> {
            try {
                Log.i("ExtModuleManager", "stopMcu mAguiExtModule:" + ExtModuleManager.this.mAguiExtModule);
                if (ExtModuleManager.this.getAguiExtModule() != null) {
                    Log.i("ExtModuleManager", "mAguiExtModule stopMcu");
                    ExtModuleManager.this.mIsMCUStarted = false;
                    ExtModuleManager.this.mIsCmdStart = false;
                    ExtModuleManager.this.mAguiExtModule.stopMcu();
                }
            } catch (Exception e) {
                Log.e("ExtModuleManager", "stopMcu e:" + e);
            }
        }).start();
        this.mIsSetChannelFinished = false;
    }

    public void resetMcu() {
        Log.i("ExtModuleManager", "resetMcu");
        stopCom();
        this.mHandler.postDelayed(ExtModuleManager.this::startMcu, 4000L);
    }

    public void startCom() {
        Log.i("ExtModuleManager", "startCom mIsCmdStart:" + this.mIsCmdStart);
        if (this.mIsCmdStart) {
            return;
        }
        this.mIsCmdStart = true;
    }

    public void stopCom() {
        Log.i("ExtModuleManager", "stopCom mIsCmdStart:" + this.mIsCmdStart + ", mIsStopRecord:" + this.mIsStopRecord + ", mIsStopPlay:" + this.mIsStopPlay + ", mAudioState:" + this.mAudioState);
        if (!this.mIsStopPlay) {
            stopPlay();
        }
        if (!this.mIsStopRecord) {
//            stopRecord();
        }
        stopMcu();
    }

    private void handleMcuStartFinished() {
        Log.i("ExtModuleManager", "handleMcuStartFinished mIsSetChannelFinished:" + this.mIsSetChannelFinished);
        this.mIsUsbStarted = true;
        this.mIsSetChannelFinished = false;
        this.mHandler.removeMessages(7);
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(7));
        this.mHandler.removeMessages(2);
        Handler handler2 = this.mHandler;
        handler2.sendMessageDelayed(handler2.obtainMessage(2), 30000L);
        startCom();
        this.mHandler.postDelayed(ExtModuleManager.this::getMcuFirmwareVersion, 1000L);
    }

    private void getMcuFirmwareVersion() {
        Log.i("ExtModuleManager", "getMcuFirmwareVersion mCurrFirmware:" + this.mCurrFirmware + ", mIsCmdStart:" + this.mIsCmdStart);
        if (this.mIsCmdStart) {
            this.mCurrFirmware = null;
            ExtModuleProtocol extModuleProtocol = this.mExtModuleProtocol;
            if (extModuleProtocol != null) {
                extModuleProtocol.getFirmwareVersion();
            }
            this.mHandler.postDelayed(() -> {
                if (ExtModuleManager.this.mExtModuleProtocol != null) {
                    if (ExtModuleManager.this.mCurrFirmware == null || ExtModuleManager.this.mCurrFirmware.isEmpty()) {
                        ExtModuleManager.this.mExtModuleProtocol.getFirmwareVersion();
                    }
                }
            }, 1000L);
            this.mHandler.postDelayed(() -> {
                if (ExtModuleManager.this.mExtModuleProtocol != null) {
                    if (ExtModuleManager.this.mCurrFirmware == null || ExtModuleManager.this.mCurrFirmware.isEmpty()) {
                        ExtModuleManager.this.mExtModuleProtocol.getFirmwareVersion();
                    }
                }
            }, 2000L);
        }
    }

    private void handleRecvFirmwareVersion(byte[] bArr) {
        Log.i("ExtModuleManager", "handleRecvFirmwareVersion mCurrFirmware:" + mCurrFirmware);
        String str = this.mCurrFirmware;
        if ((str == null || str.isEmpty()) && bArr.length > 8) {
            int bytesToInt2 = IComUtils.bytesToInt2(new byte[]{bArr[6], bArr[7]});
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256;
            }
            if (bytesToInt2 > 0) {
                byte[] bArr2 = new byte[bytesToInt2];
                System.arraycopy(bArr, 8, bArr2, 0, bytesToInt2);
                this.mCurrFirmware = new String(bArr2);
                Log.i("ExtModuleManager", "handleRecvFirmwareVersion mCurrFirmware:" + this.mCurrFirmware);
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    this.mExtModuleProtocol.getDmr09ModuleSoftVersion();
                } else {
                    this.mExtModuleProtocol.getModuleSoftVersion();
                }
                this.mHandler.removeMessages(12);
                Handler handler = this.mHandler;
                handler.sendMessageDelayed(handler.obtainMessage(12), 3000L);
            }
        }
    }

    private void openPcmIn() {
        new Thread(() -> {
            try {
                if (ExtModuleManager.this.getAguiExtModule() != null) {
                    int openPcmIn = ExtModuleManager.this.mAguiExtModule.openPcmIn();
                    Log.i("ExtModuleManager", "openPcmIn ret:" + openPcmIn);
                    if (openPcmIn == 1) {
                        ExtModuleManager.this.mIsPcmInStart = true;
                    }
                }
            } catch (Exception e) {
                Log.e("ExtModuleManager", "openPcmIn e:" + e);
            }
        }).start();
    }

    private void closePcmIn() {
        new Thread(() -> {
            try {
                if (ExtModuleManager.this.getAguiExtModule() != null) {
                    int closePcmIn = ExtModuleManager.this.mAguiExtModule.closePcmIn();
                    Log.i("ExtModuleManager", "closePcmIn ret:" + closePcmIn);
                    ExtModuleManager.this.mIsPcmInStart = false;
                }
            } catch (Exception e) {
                Log.e("ExtModuleManager", "closePcmIn e:" + e);
            }
        }).start();
    }

    private void openPcmOut() {
        Log.i("ExtModuleManager", "openPcmOut mIsStopRecord:" + this.mIsStopRecord);
        if (this.mIsStopRecord) {
            synchronized (this.mAudioLock) {
                this.mIsStopRecord = false;
                this.mAudioLock.notify();
            }
            this.mIsStopRecord = false;
            this.mStartCallTime = System.currentTimeMillis();
            setAudioRecordPath("" + this.mStartCallTime);
            new Thread(() -> {
                try {
                    if (ExtModuleManager.this.getAguiExtModule() != null) {
                        ExtModuleManager.this.mAguiExtModule.startPtt();
                        int openPcmOut = ExtModuleManager.this.mAguiExtModule.openPcmOut();
                        Log.i("ExtModuleManager", "openPcmOut ret:" + openPcmOut);
                        if (openPcmOut == 1) {
                            ExtModuleManager.this.mIsPcmOutStart = true;
                        }
                    }
                } catch (Exception e) {
                    Log.e("ExtModuleManager", "openPcmOut e:" + e);
                }
            }).start();
        }
    }

    private void closePcmOut() {
        Log.i("ExtModuleManager", "closePcmOut mIsStopRecord:" + this.mIsStopRecord);
        if (this.mIsStopRecord) {
            return;
        }
        new Thread(() -> {
            try {
                if (ExtModuleManager.this.getAguiExtModule() != null) {
                    int closePcmOut = ExtModuleManager.this.mAguiExtModule.closePcmOut();
                    Log.i("ExtModuleManager", "closePcmOut ret:" + closePcmOut);
                    ExtModuleManager.this.mAguiExtModule.stopPtt();
                }
            } catch (Exception e) {
                Log.e("ExtModuleManager", "closePcmOut e:" + e);
            }
        }).start();
        onCallStateChanged(0);
        this.mHandler.postDelayed(() -> {
            ExtModuleManager.this.mIsStopRecord = true;
            ExtModuleManager.this.mIsPcmOutStart = false;
        }, 500L);
        if (this.mIsStopPlay) {
            releaseMusicFocus();
        }
    }

    private void createAudioTrack() {
        int minBufferSize = AudioTrack.getMinBufferSize(this.mPlayFrequency, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        Log.i("ExtModuleManager", "start playBufSize:" + minBufferSize);
        this.mExtAudioTrack = new AudioTrack.Builder()
                .setAudioAttributes(this.mAudioAttributes)
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(this.mPlayFrequency)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
    }

    private void createCmdReadThread() {
        new Thread(() -> {
            while (!ExtModuleManager.this.mAllExit) {
                try {
                    if (ExtModuleManager.this.mIsMCUStarted) {
                        if (ExtModuleManager.this.mIsUpdatingDmr) {
                            SystemClock.sleep(100L);
                        } else {
                            try {
                                if (ExtModuleManager.this.getAguiExtModule() != null) {
                                    ExtModuleManager.this.mAguiExtModule.readTTyDevice(new IAguiExtModuleReadCallback.Stub() { // from class: com.agold.intercom.module.ExtModuleManager.20.1
                                        @Override
                                        // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModuleReadCallback
                                        public void onReadDevice(byte[] bArr, int i) throws RemoteException {
                                            if (i > 0) {
                                                Log.i("ExtModuleManager", "onReadTTyDevice readSize:" + i);
                                                ExtModuleManager.this.handleCmdResponse(bArr, i);
                                            }
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Log.e("ExtModuleManager", "createCmdReadThread ex:" + e);
                            }
                            SystemClock.sleep(100L);
                        }
                    } else {
                        SystemClock.sleep(1000L);
                    }
                } catch (Exception e2) {
                    Log.e("ExtModuleManager", "createCmdReadThread e:" + e2);
                    return;
                }
            }
            Log.i("ExtModuleManager", "createCmdReadThread exit");
        }).start();
    }

    private void createCallInThread() {
        new Thread(() -> {
            while (!ExtModuleManager.this.mAllExit) {
                try {
                    if (ExtModuleManager.this.mIsMCUStarted) {
                        if (ExtModuleManager.this.mIsUpdatingDmr) {
                            SystemClock.sleep(100L);
                        } else {
                            try {
                                if (ExtModuleManager.this.getAguiExtModule() != null) {
                                    Log.i("ExtModuleManager", "createCallInThread start detectAudioinState");
                                    int detectAudioinState = ExtModuleManager.this.getAguiExtModule().detectAudioinState();
                                    Log.i("ExtModuleManager", "createCallInThread callInState:" + detectAudioinState);
                                    ExtModuleManager.this.handleCallInStateChanged(detectAudioinState);
                                }
                            } catch (Exception e) {
                                Log.e("ExtModuleManager", "createCallInThread ex:" + e);
                            }
                            SystemClock.sleep(100L);
                        }
                    } else {
                        SystemClock.sleep(1000L);
                    }
                } catch (Exception e2) {
                    Log.e("ExtModuleManager", "createCallInThread e:" + e2);
                    return;
                }
            }
            Log.i("ExtModuleManager", "createCallInThread exit");
        }).start();
    }

    private void createAudioPlayThread() {
        new Thread(() -> {
            while (!ExtModuleManager.this.mAllExit) {
                try {
                    if (!ExtModuleManager.this.mIsStopPlay) {
                        if (!ExtModuleManager.this.mIsPcmInStart) {
                            SystemClock.sleep(10L);
                        } else {
                            Log.i("ExtModuleManager", "createAudioPlayThread mAudioTrack:" + ExtModuleManager.this.mAudioTrack);
                            if (ExtModuleManager.this.mAudioTrack == null) {
                               ExtModuleManager.this.createAudioTrack();
                            }
                            Log.i("ExtModuleManager", "createAudioPlayThread mAudioTrack getState:" + ExtModuleManager.this.mAudioTrack.getState() + ", getPlayState:" + ExtModuleManager.this.mAudioTrack.getPlayState());
                            ExtModuleManager.this.mAudioTrack.play();
                            while (ExtModuleManager.this.mIsPcmInStart) {
                                try {
                                    if (ExtModuleManager.this.getAguiExtModule() != null) {
                                        ExtModuleManager.this.mAguiExtModule.readPcmDevice(new IAguiExtModuleReadCallback.Stub() { // from class: com.agold.intercom.module.ExtModuleManager.22.1
                                            @Override
                                            // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModuleReadCallback
                                            public void onReadDevice(byte[] bArr, int i) throws RemoteException {
                                                if (i > 0) {
                                                    Log.i("ExtModuleManager", "readPcmDevice readSize:" + i);
                                                    try {
                                                        if (ExtModuleManager.this.mAudioTrack != null) {
                                                            ExtModuleManager.this.mAudioTrack.write(bArr, 0, i);
                                                        }
                                                        if (ExtModuleManager.this.mPcmRecordBOS != null) {
                                                            ExtModuleManager.this.mPcmRecordBOS.write(bArr, 0, i);
                                                        }
                                                    } catch (Exception e) {
                                                        Log.e("ExtModuleManager", "onReadDevice e:" + e);
                                                    }
                                                }
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    Log.e("ExtModuleManager", "createAudioPlayThread ex:" + e);
                                    Log.e("ExtModuleManager", "createAudioPlayThread mAguiExtModule:" + ExtModuleManager.this.mAguiExtModule);
                                    Log.e("ExtModuleManager", "createAudioPlayThread IAguiExtModule.getService():" + IAguiExtModule.getService());
                                    ExtModuleManager.this.mAguiExtModule = null;
                                }
                            }
                            ExtModuleManager.this.mAudioTrack.stop();
                            ExtModuleManager.this.mAudioTrack = null;
                        }
                    } else {
                        SystemClock.sleep(10L);
                    }
                } catch (Exception e2) {
                    Log.e("ExtModuleManager", "createAudioPlayThread e:" + e2);
                    return;
                }
            }
            Log.i("ExtModuleManager", "createAudioPlayThread exit");
        }).start();
    }

    private void createAudioRecordThread() {
        new Thread(() -> {
            int read;
            synchronized (ExtModuleManager.this.mAudioLock) {
                while (!ExtModuleManager.this.mAllExit) {
                    try {
                        if (ExtModuleManager.this.mIsStopRecord) {
                            Log.i("ExtModuleManager", "createAudioRecordThread mAudioLock.wait!");
                            ExtModuleManager.this.mAudioLock.wait();
                        }
                        if (!ExtModuleManager.this.mIsPcmOutStart) {
                            SystemClock.sleep(10L);
                        } else {
                            byte[] bArr = new byte[1920];
                            Log.i("ExtModuleManager", "createAudioRecordThread mAudioRecordPath:" + ExtModuleManager.this.mAudioRecordPath);
                            File audioRecordPathParent = ExtModuleManager.this.mAudioRecordPath.getParentFile();
                            if (audioRecordPathParent != null && !audioRecordPathParent.exists()) {
                                audioRecordPathParent.mkdirs();
                            }
                            BufferedOutputStream bufferedOutputStream = null;
                            if (ExtModuleManager.this.mAudioRecordPath != null) {
                                if (ExtModuleManager.this.mAudioRecordPath.exists()) {
                                    ExtModuleManager.this.mAudioRecordPath.delete();
                                }
                                ExtModuleManager.this.mAudioRecordPath.createNewFile();
                                bufferedOutputStream = new BufferedOutputStream(Files.newOutputStream(ExtModuleManager.this.mAudioRecordPath.toPath()));
                            }
                            AudioRecord audioRecord = new AudioRecord(1, ExtModuleManager.this.mRecFrequency, 12, 2, AudioRecord.getMinBufferSize(ExtModuleManager.this.mRecFrequency, 12, 2));
                            audioRecord.startRecording();
                            while (ExtModuleManager.this.mIsPcmOutStart && (read = audioRecord.read(bArr, 0, ExtModuleManager.this.AUDIO_FRAME_SIZE)) >= 0) {
                                if (ExtModuleManager.this.getAguiExtModule() != null) {
                                    ExtModuleManager.this.mAguiExtModule.writePcmDevice(bArr, read);
                                }
                                if (bufferedOutputStream != null) {
                                    bufferedOutputStream.write(bArr, 0, read);
                                }
                            }
                            if (bufferedOutputStream != null) {
                                bufferedOutputStream.close();
                            }
                            audioRecord.stop();
                            audioRecord.release();
                        }
                    } catch (Exception e) {
                        Log.i("ExtModuleManager", "createAudioRecordThread error:" + e);
                    }
                }
            }
            Log.i("ExtModuleManager", "createAudioRecordThread exit");
        }).start();
    }

    public int startPlay() {
        Log.i("ExtModuleManager", "startPlay start mIsStopPlay:" + this.mIsStopPlay);
        if (!this.mIsStopPlay) {
            Log.i("ExtModuleManager", "startPlay--->play is not stopped");
            return 0;
        } else if (!this.mIsMCUStarted || !this.mIsCmdStart) {
            Log.i("ExtModuleManager", "startPlay--->cmd COM is not open!");
            return 0;
        } else if (!this.mIsStopRecord) {
            Log.i("ExtModuleManager", "startPlay Record is not stop!");
            return 0;
        } else {
//            Channel currChannel = getCurrChannel();
//            if (currChannel != null && currChannel.getType() == 0) {
//                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
//                    this.mExtModuleProtocol.getDmr09RecvCallInfo();
//                } else {
//                    this.mExtModuleProtocol.getCallInfo();
//                }
//            }
            this.mIsStopPlay = false;
            this.mHandler.removeMessages(18);
            Handler handler = this.mHandler;
            handler.sendMessageDelayed(handler.obtainMessage(18), 100L);
            requestMusicFocus();
            this.mStartPlayTime = System.currentTimeMillis();
            setAudioRecordPath("" + this.mStartPlayTime);
            onPlayStateChanged(1);
//            if (this.mIsScanningChannels) {
//                this.mHandler.removeMessages(23);
//                Handler handler2 = this.mHandler;
//                handler2.sendMessage(handler2.obtainMessage(23));
//                this.mIsScanningChannels = false;
//            }
            try {
                File audioRecordPathParent = this.mAudioRecordPath.getParentFile();
                if (audioRecordPathParent != null && !audioRecordPathParent.exists()) {
                    audioRecordPathParent.mkdirs();
                }
            } catch (Exception e) {
                Log.e("ExtModuleManager", "startPlay ex:" + e);
            }
            if (this.mAudioRecordPath == null) {
                return 0;
            }
            File pcmFile = this.mAudioRecordPath;
            if (pcmFile.exists()) {
                pcmFile.delete();
            }
            // TODO: better error handling
            try {
                pcmFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            try {
                this.mPcmRecordBOS = new BufferedOutputStream(new FileOutputStream(pcmFile));
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            Log.i("ExtModuleManager", "startPlay end-----------------");
            return 0;
        }
    }

    public void stopPlay() {
        Log.i("ExtModuleManager", "stopPlay mIsStopPlay:" + this.mIsStopPlay);
        if (this.mIsStopPlay) {
            Log.i("ExtModuleManager", "stopPlay---> play is not started");
            return;
        }
        this.mIsStopPlay = true;
        this.mHandler.removeMessages(19);
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(19));
        try {
            if (this.mPcmRecordBOS != null) {
                this.mPcmRecordBOS.close();
                this.mPcmRecordBOS = null;
            }
        } catch (Exception e) {
            Log.e("ExtModuleManager", "stopPlay ex:" + e);
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
        this.mStartPlayTime = 0L;
        onPlayStateChanged(0);
        if (this.mAudioState == 0) {
            releaseMusicFocus();
        }

        this.mHandler.postDelayed(() -> {
            if (ExtModuleManager.this.mIsStopPlay && ExtModuleManager.this.mHasNewMessage) {
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    ExtModuleManager.this.mExtModuleProtocol.getDmr09MsgContent();
                } else {
                    ExtModuleManager.this.mExtModuleProtocol.getMsgContent();
                }
                ExtModuleManager.this.mHasNewMessage = false;
            }
        }, 500L);
        Log.i("ExtModuleManager", "stopPlay end-----------------");
    }

    public int startRecord() {
        Log.i("ExtModuleManager", "startRecord mAudioState:" + this.mAudioState + ", mIsStopRecord:" + this.mIsStopRecord + ", mIsLockDigitalSend:" + this.mIsLockDigitalSend + ", mIsLockAnglogSend:" + this.mIsLockAnglogSend + ", mIsSetChannelFinished:" + this.mIsSetChannelFinished);
        if (!this.mIsMCUStarted || !this.mIsCmdStart) {
            Log.i("ExtModuleManager", "startRecord cmd COM is not open!");
            return 0;
        } else if (!this.mIsSetChannelFinished) {
            Log.i("ExtModuleManager", "startRecord channel is not set finished!");
            return 0;
        } else if (!this.mIsStopRecord) {
            Log.i("ExtModuleManager", "startRecord is not stopped!");
            return 0;
        } else {
            if (this.mAudioState == 0) {
                this.mAudioState = 1;
                if (requestMusicFocus() == 0) {
                    this.mAudioState = 0;
                    return 0;
                }
                this.mHandler.removeMessages(20);
                Handler handler2 = this.mHandler;
                handler2.sendMessage(handler2.obtainMessage(20));
                onCallStateChanged(1);
            }
            return 0;
        }
    }

    private int requestMusicFocus() {
        Log.i("ExtModuleManager", "requestMusicFocus mCurrentAudioFocusRequest:" + this.mCurrentAudioFocusRequest);
        if (this.mCurrentAudioFocusRequest != null) {
            return 1;
        }
        if (this.mAudioManager != null) {
            AudioFocusRequest focusRequest = new AudioFocusRequest
				.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
				.setAudioAttributes(this.mAudioAttributes)
				.build();
            int requestAudioFocus = this.mAudioManager.requestAudioFocus(focusRequest);
            Log.i("ExtModuleManager", "requestMusicFocus status:" + requestAudioFocus);
            this.mCurrentAudioFocusRequest = focusRequest;
            return 1;
        }
        return 0;
    }

    private void releaseMusicFocus() {
        Log.i("ExtModuleManager", "releaseMusicFocus mCurrentAudioFocusRequest:" + this.mCurrentAudioFocusRequest);
        if (this.mAudioManager == null || this.mCurrentAudioFocusRequest == null) {
            return;
        }
        this.mAudioManager.abandonAudioFocusRequest(this.mCurrentAudioFocusRequest);
        this.mCurrentAudioFocusRequest = null;
    }

    private int handleCmdResponse(byte[] response, int length) {
        byte[] bArr2 = new byte[length];
        System.arraycopy(response, 0, bArr2, 0, length);
        String byte2HexStrNoBlank = IComUtils.byte2HexStrNoBlank(bArr2);
        Log.i("ExtModuleManager", "handleCmdResponse len:" + length + ", strCmd:" + byte2HexStrNoBlank);
        int i2 = 0;
        while (i2 + 11 < length) {
            try {
                if (IComUtils.isIComCmd(bArr2, i2)) {
                    int bytesToInt2 = IComUtils.bytesToInt2(new byte[]{bArr2[i2 + 6], bArr2[i2 + 7]});
                    if (bytesToInt2 < 0) {
                        bytesToInt2 += 256;
                    }
                    Log.i("ExtModuleManager", "handleCmdResponse index:" + i2 + ", currDataLen:" + bytesToInt2);
                    if (i2 + bytesToInt2 + 11 > length) {
                        // this didn't decompile correctly, so it was reconstructed by hand and may not be accurate
                        byte[] r8 = new byte[]{bArr2[i2 + 3], bArr2[i2 + 4]};
                        if (r8[0] != 1 || r8[1] != 6) {
                            return i2;
                        }
                        return length;
                    }
                    int i3 = bytesToInt2 + 11;
                    byte[] bArr3 = new byte[i3];
                    System.arraycopy(bArr2, i2, bArr3, 0, i3);
                    parseCmd(bArr3);
                    i2 += i3;
                } else {
                    i2++;
                }
            } catch (Exception e) {
                Log.e("ExtModuleManager", "handleCmdResponse e:" + e);
                return length;
            }
        }
        return i2;
    }

    private void parseCmd(byte[] bArr) {
        String byte2HexStr = IComUtils.byte2HexStr(bArr);
        Log.i("ExtModuleManager", "parseCmd strCmd:" + byte2HexStr);
        if (IComUtils.isIComCmd(bArr)) {
            Log.i("ExtModuleManager", "parseCmd cmdByte[3]:" + ((int) bArr[3]));
            if (bArr[3] == 0) {
                byte b = bArr[4];
                Log.i("ExtModuleManager", "parseCmd sys cmd:" + ((int) b));
                if (b == 1) {
                    handleRecvFirmwareVersion(bArr);
                    return;
                }
                switch (b) {
                    case 4:
                        handleMcuInitFinished();
                        return;
                    case 5:
//                        handleMcuUpdateFinished();
                        return;
                    case 6:
                        handleMcuStartFinished();
                        return;
                    case 7:
                        handleMcuErrorReport(bArr);
                        return;
                    case 8:
                        handleMcuStateReport(bArr);
                        return;
                    case 9:
//                        handleDmrUpdated();
                        return;
                    case 10:
//                        handleDmrUpdatFailed();
                        return;
                    default:
                        return;
                }
            }
            if (bArr[3] == 1) {
                byte b2 = bArr[4];
                Log.i("ExtModuleManager", "parseCmd module cmd:" + ((int) b2));
                if (b2 == 4) {
                    handleRecvSendStateChange(bArr[8]);
                } else if (b2 == 17) {
//                    getMsgContent(bArr);
                } else if (b2 == 37) {
                    getModuleVersonContent(bArr);
                } else if (b2 == 43) {
                    if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
//                        getDmr09CallInfo(bArr);
                    }
                } else if (b2 == 45) {
                    if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
//                        getDmr09MsgContent(bArr);
                    }
                } else if (b2 == 6) {
                    handleRecvSendStateChange(bArr);
                } else if (b2 == 7) {
//                    handleMsgResponse(bArr[8]);
                } else if (b2 == 34) {
                    if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        setChannelComplete(bArr);
                    }
                } else if (b2 == 35) {
                    if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        setChannelComplete(bArr);
                    }
                } else {
                    switch (b2) {
                        case 52:
                            if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                                getModuleVersonContent(bArr);
                                return;
                            }
                            return;
                        case 53:
                            setChannelComplete(bArr);
                            return;
                        case 54:
                            if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
//                                handleModuleResponse(bArr[8]);
                                return;
                            } else {
                                setChannelComplete(bArr);
                                return;
                            }
                        default:
                    }
                }
            } else if (bArr[3] == 2 && bArr.length > 8) {
                int bytesToInt2 = IComUtils.bytesToInt2(new byte[]{bArr[6], bArr[7]});
                Log.i("ExtModuleManager", "parseCmd dataLen:" + bytesToInt2);
                if (bytesToInt2 < 0) {
                    bytesToInt2 += 256;
                }
                if (bytesToInt2 > 0) {
                    byte[] bArr2 = new byte[bytesToInt2];
                    System.arraycopy(bArr, 8, bArr2, 0, bytesToInt2);
                    String byte2HexStr2 = IComUtils.byte2HexStr(bArr2);
                    Log.i("ExtModuleManager", "parseCmd retData:" + byte2HexStr2);
                }
            }
        }
    }

    private void handleMcuInitFinished() {
        Log.i("ExtModuleManager", "handleMcuInitFinished");
    }

    private void handleMcuErrorReport(byte[] bArr) {
        Log.i("ExtModuleManager", "handleMcuErrorReport");
        if (bArr.length <= 8 || IComUtils.bytesToInt2(new byte[]{bArr[6], bArr[7]}) <= 0) {
            return;
        }
        byte b = bArr[8];
        Log.i("ExtModuleManager", "handleMcuErrorReport errorCode:" + ((int) b));
    }

    private void handleMcuStateReport(byte[] bArr) {
        Log.i("ExtModuleManager", "handleMcuStateReport");
        if (bArr.length <= 8 || IComUtils.bytesToInt2(new byte[]{bArr[6], bArr[7]}) <= 0) {
            return;
        }
        byte b = bArr[8];
        Log.i("ExtModuleManager", "handleMcuStateReport cmdState:" + ((int) b) + ", mHasNewMessage:" + this.mHasNewMessage);
        if (b == 1) {
            return;
        }
        if (b != 2) {
            if (b == 3) {
                Log.i("ExtModuleManager", "handleMcuStateReport pcm error, we need to reset mcu!!!");
                resetMcu();
                return;
            }
            return;
        }
        this.mHandler.removeMessages(14);
        this.mHandler.postDelayed(() -> ExtModuleManager.this.mIsPTTStopComplete = true, 1000L);
        if (this.mIsStopRecord && this.mHasNewMessage) {
            if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                this.mExtModuleProtocol.getDmr09MsgContent();
            } else {
                this.mExtModuleProtocol.getMsgContent();
            }
            this.mHasNewMessage = false;
        }
    }

    private void getModuleVersonContent(byte[] bArr) {
        if (bArr.length > 8) {
            int bytesToInt2 = IComUtils.bytesToInt2(new byte[]{bArr[6], bArr[7]});
            Log.i("ExtModuleManager", "getModuleVersonContent dataLen:" + bytesToInt2);
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256;
            }
            if (bytesToInt2 > 0) {
                int i = bytesToInt2 - 1;
                byte[] bArr2 = new byte[i];
                System.arraycopy(bArr, 8, bArr2, 0, i);
                String str = new String(bArr2);
                Log.i("ExtModuleManager", "getModuleVersonContent module:" + str);

//                Channel channel = new Channel();
//                this.setChannel(channel);
            }
        }
    }

    public int checkTemperature() {
        Log.i("ExtModuleManager", "checkTemperature mCurrTemperature:" + this.mCurrTemperature);
        int i = this.mCurrTemperature;
        if (i < -20) {
            return -1;
        }
        return i > 550 ? 1 : 0;
    }

    public void handleBatteryChanged(int i, int i2, boolean z, boolean z2) {
        Log.i("ExtModuleManager", "handleBatteryChanged level:" + i + ", mCurrBatteryLevel:" + this.mCurrBatteryLevel);
        Log.i("ExtModuleManager", "handleBatteryChanged temp:" + i2 + ", mCurrTemperature:" + this.mCurrTemperature);
        Log.i("ExtModuleManager", "handleBatteryChanged isCharging:" + z + ", mIsCharging:" + this.mIsCharging);
        Log.i("ExtModuleManager", "handleBatteryChanged isUsbCharge:" + z2 + ", mIsUSBCharge:" + this.mIsUSBCharge);
        this.mCurrBatteryLevel = i;
        this.mCurrTemperature = i2;
        this.mIsUSBCharge = z2;
        if (this.mIsCharging != z) {
            if (z) {
                handleChargeConnected();
            } else {
                handleChargeDisconnected();
            }
            this.mIsCharging = z;
        }
        int i3 = this.mCurrBatteryLevel;
        if (i3 >= 30) {
            this.mIsLockDigitalSend = false;
            this.mIsLockAnglogSend = false;
        } else if (i3 >= 15) {
            this.mIsLockDigitalSend = true;
            this.mIsLockAnglogSend = false;
        } else {
            this.mIsLockDigitalSend = true;
            this.mIsLockAnglogSend = true;
        }
    }

    public void handleChargeConnected() {
        Log.i("ExtModuleManager", "handleChargeConnected mIsCmdStart:" + this.mIsCmdStart + ", mIsSetChannelFinished:" + this.mIsSetChannelFinished);
        if (this.mIsCmdStart) {
            resetMcu();
        }
    }

    public void handleChargeDisconnected() {
        Log.i("ExtModuleManager", "handleChargeDisconnected mIsCmdStart:" + this.mIsCmdStart + ", mIsSetChannelFinished:" + this.mIsSetChannelFinished);
        if (this.mIsCmdStart) {
            resetMcu();
        }
    }

    public void handleHeadsetPlugChanged(int i) {
        Log.i("ExtModuleManager", "handleHeadsetPlugChanged state:" + i);
        if ((i == 1 || i == 0) && this.mIsCmdStart) {
            resetMcu();
        }
    }

    public void handleUsbDeviceDetached() {
        Log.i("ExtModuleManager", "handleUsbDeviceDetached");
        if (this.mIsCmdStart) {
            resetMcu();
        }
    }

    public boolean isSetChannelFinished() {
        return this.mIsSetChannelFinished;
    }

    /* JADX WARN: Removed duplicated region for block: B:13:0x0067  */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    private void setChannelComplete(byte[] bArr) {
        boolean success = false;
        Log.i("ExtModuleManager", "setChannelComplete mPreSetChannel:" + this.mPreSetChannel);
        if (bArr.length > 8) {
            int bytesToInt2 = IComUtils.bytesToInt2(new byte[]{bArr[6], bArr[7]});
            Log.i("ExtModuleManager", "setChannelComplete dataLen:" + bytesToInt2);
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256;
            }
            if (bytesToInt2 == 1) {
                byte b = bArr[8];
                Log.i("ExtModuleManager", "setChannelComplete state:" + ((int) b));
                if (b == 0) {
                    success = true;
                }
            }
            if (!success) {
                this.mHandler.removeMessages(6);
                this.mHandler.sendMessage(this.mHandler.obtainMessage(6));
                return;
            }
        }
        this.mIsSetChannelFinished = true;
        this.mHandler.removeMessages(6);
        this.mHandler.removeMessages(2);
        if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
            this.mHandler.removeMessages(5);
            Handler handler2 = this.mHandler;
            handler2.sendMessageDelayed(handler2.obtainMessage(5), 500L);
        } else {
            this.mHandler.removeMessages(5);
            Handler handler3 = this.mHandler;
            handler3.sendMessage(handler3.obtainMessage(5));
        }
        // TODO
        int recvVolume = 6;
        if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
            this.mExtModuleProtocol.setDmr09ReceiverVolume(recvVolume);
        } else {
            this.mExtModuleProtocol.setReceiverVolume(recvVolume);
        }
        stopPlay();
    }

    private void handleCallInStateChanged(int i) {
        Log.i("ExtModuleManager", "handleCallInStateChanged state:" + i + ", mCallInStateChangedCount:" + this.mCallInStateChangedCount);
        this.mCallInStateChangedCount = 0;
        if (i == 0) {
            startPlay();
        } else if (i == 1) {
            stopPlay();
        }
    }

    private void handleRecvSendStateChange(int i) {
        Log.i("ExtModuleManager", "handleRecvSendStateChange state:" + i + ", mCallInStateChangedCount:" + this.mCallInStateChangedCount);
        if (i == 0 || i == 1) {
            handleCallInStateChanged(i);
            int i2 = this.mCallInStateChangedCount + 1;
            this.mCallInStateChangedCount = i2;
            if (i2 % 10 == 0) {
                this.mHandler.removeMessages(16);
                Handler handler = this.mHandler;
                handler.sendMessage(handler.obtainMessage(16));
            }
        } else if (i == 2) {
            startPlay();
        } else if (i == 3) {
            stopPlay();
            if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                this.mHandler.postDelayed(() -> {
                    if (ExtModuleManager.this.mIsStopPlay) {
                        ExtModuleManager.this.mExtModuleProtocol.getDmr09MsgContent();
                    }
                }, 800L);
            }
        }
    }

    private void handleRecvSendStateChange(byte[] bArr) {
        if (bArr.length > 8) {
            int bytesToInt2 = IComUtils.bytesToInt2(new byte[]{bArr[6], bArr[7]});
            Log.i("ExtModuleManager", "handleRecvSendStateChange dataLen:" + bytesToInt2);
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256;
            }
            Log.i("ExtModuleManager", "handleRecvSendStateChange 111 dataLen:" + bytesToInt2);
        }
    }

    public void setChannel(Channel channel) {
        Log.i("ExtModuleManager", "setChannel mIsCmdStart:" + this.mIsCmdStart + ", mIsStopPlay:" + this.mIsStopPlay + ", mIsStopRecord:" + this.mIsStopRecord + ", mIsUsbStarted:" + this.mIsUsbStarted + ", mIsPTTStopComplete:" + this.mIsPTTStopComplete + ", channel:" + channel);
        if (channel != null && this.mIsCmdStart && this.mIsStopRecord && this.mIsUsbStarted && this.mIsPTTStopComplete) {
            int num = channel.getNum();
            int type = channel.getType();
            Log.i("ExtModuleManager", "setChannel channelType:" + type + ", channelNum:" + num);
            if (type == 1) {
                if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                    this.mExtModuleProtocol.setDmr09AnalogGroup(channel.getBand(), channel.getPower(), (int) channel.getRecvFreq(), (int) channel.getSendFreq(), channel.getSquelchLevel(), channel.getRecvSubAudioType(), channel.getRecvCtcssFreq(), channel.getSendSubAudioType(), channel.getSendCtcssFreq(), 1, 8, 2, 2);
                } else {
                    this.mExtModuleProtocol.setAnalogGroup(channel.getBand(), channel.getPower(), (int) channel.getRecvFreq(), (int) channel.getSendFreq(), channel.getSquelchLevel(), channel.getRecvSubAudioType(), channel.getRecvCtcssFreq(), channel.getSendSubAudioType(), channel.getSendCtcssFreq());
                }
                this.mPreSetChannel = num;
                this.mIsSetChannelFinished = false;
                this.mHandler.removeMessages(6);
                Handler handler = this.mHandler;
                handler.sendMessageDelayed(handler.obtainMessage(6), 3000L);
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

    public void setAudioRecordPath(String str) {
        // TODO
        int pttRecordEnable = 1;
        Log.i("ExtModuleManager", "setAudioRecordPath name:" + str + ", isPttRecordEnable:" + pttRecordEnable);
        if (pttRecordEnable == 1) {
            this.mAudioRecordPath = new File(Environment.getExternalStorageDirectory(), "Download/record/" + str + ".pcm");
        } else {
            this.mAudioRecordPath = null;
        }
        Log.i("ExtModuleManager", "setAudioRecordPath mAudioRecordPath:" + this.mAudioRecordPath);
    }

    public void playSound(int i) {
        Log.i("ExtModuleManager", "playSound mSoundPool:" + this.mSoundPool + ", soundID:" + i);
        SoundPool soundPool = this.mSoundPool;
        if (soundPool != null) {
            soundPool.play(i, 1.0f, 1.0f, 1, 0, 1.0f);
        }
    }

    private String readFile(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
        while (true) {
            String readLine = bufferedReader.readLine();
            if (readLine != null) {
                sb.append(readLine);
            } else {
                bufferedReader.close();
                return sb.toString();
            }
        }
    }

    public boolean isAntennaInstalled() {
        if (SystemProperties.getBoolean("ro.agold.extmodule.hal", false)) {
            File file = new File("/sys/hall_status/hall_status");
            if (!file.exists()) {
                Log.i("ExtModuleManager", "isAntennaInstalled hall file is not exist!");
                return false;
            }
            try {
                String readFile = readFile(file);
                Log.i("ExtModuleManager", "isAntennaInstalled status = " + readFile);
                return !readFile.contains("0");
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

//    public void showInstallAntennaToast(Context context) {
//        Toast.makeText(context, (int) R.string.install_antenna, Toast.LENGTH_SHORT).show();
//    }


}
