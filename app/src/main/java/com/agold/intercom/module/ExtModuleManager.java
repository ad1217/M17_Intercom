package com.agold.intercom.module;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.storage.StorageManager;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule;
import vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModuleReadCallback;

/* loaded from: classes.dex */
public class ExtModuleManager {
    private static final String SEND_AUDIO = SystemProperties.get("ro.agold_intercom_send_audio", "start_send_1.ogg");
    private static Context mContext;
    private static ExtModuleManager sInstance;
    private int AUDIO_FRAME_SIZE;
    private IAguiExtModule mAguiExtModule;
    private final AudioManager mAudioManager;
    AudioTrack mAudioTrack;
    AudioTrack mExtAudioTrack;
    private final ExtModuleProtocol mExtModuleProtocol;
    private NotificationChannel mNotificationChannel;
    private final NotificationManager mNotificationManager;
    private File mPcmDir;
    private BufferedOutputStream mPcmRecordBOS;
    private int mPlayFrequency;
    private final PowerManager mPowerManager;
    private int mRecFrequency;
    private final SoundPool mSoundPool;
    private final PowerManager.WakeLock mWakeLock;
    private boolean mIsStopRecord = true;
    private boolean mIsPTTStopComplete = true;
    private boolean mIsStopPlay = true;
    private int mAudioState = 0;
    private int mCmdTypes = 0;
    private boolean mIsCmdStart = false;
    private boolean mIsMCUStarted = false;
    private boolean mIsUsbStarted = false;
    private boolean mIsPcmInStart = false;
    private boolean mIsPcmOutStart = false;
    private boolean mIsUpdatingDmr = false;
    private boolean mAllExit = true;
    private boolean mIsCurrMusicFocus = false;
    private Notification mIntercomNotification = new Notification();
    private final Notification mIntercomMsgNotification = new Notification();
    private String mAudioRecordPath = null;
    private String mCurrFirmware = null;
    private String mLastestFirmware = null;
    private final String mMcuFirmwareDir = "/vendor/data/mcu/";
    private String mCurrDmrFirmware = null;
    private final String mDMRFirmwareDir = null;
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
    private boolean mIsSettingFactory = false;
    private boolean mIsFirstDmrUpdate = true;
    private boolean mIsScanningChannels = false;
    private final byte[] mCmdWriteBuffer = new byte[1920];
    private boolean mIsInTestMode = false;
    private boolean mIsStopping = false;
    private final Handler mHandler = new Handler() { // from class: com.agold.intercom.module.ExtModuleManager.1
        @Override // android.os.Handler
        public void handleMessage(Message message) {
            switch (message.what) {
                case 1:
                    if (SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                        return;
                    }
                    ExtModuleManager.this.onManagerStarted();
                    return;
                case 2:
                    if (SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
                        return;
                    }
                    ExtModuleManager.this.onManagerStartTimeout();
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
                    ExtModuleManager.this.onSetChannelComplete();
                    return;
                case 6:
                    ExtModuleManager.this.onSetChannelTimeout();
                    return;
                case 7:
                    ExtModuleManager.this.onMcuStartComplete();
                    return;
                case 8:
                    ExtModuleManager.this.onResetFactoryStart();
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
                    ExtModuleManager.this.onGetIncallInfo(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
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
                    ExtModuleManager.this.onScanChannelsStart();
                    return;
                case 23:
                    ExtModuleManager.this.onScanChannelsComplete();
                    return;
                default:
            }
        }
    };
    private final HashMap<String, CallbackListener> mListenerMaps = new HashMap<>();
    private final Object mAudioLock = new Object();
    private boolean mHasNewMessage = false;
    private int mCallInStateChangedCount = 0;
    private int mOrgScanChannel = 0;

    /* loaded from: classes.dex */
    public interface CallbackListener {
        void onCallStateChanged(int i);

        void onChargeStateChanged();

        void onDmrUpdateFailed();

        void onDmrUpdateStateChanged(int i);

        void onGetIncallInfo(int i, int i2, int i3);

        void onManagerStartTimeout();

        void onManagerStarted();

        void onMcuStartComplete();

        void onMcuUpdateStateChanged(int i);

        void onMsgReceived();

        void onPlayStateChanged(int i);

        void onResetFactoryStart();

        void onScanChannelsComplete();

        void onScanChannelsStart();

        void onSetChannelComplete();

        void onSetChannelStart();

        void onSetChannelTimeout();

        void onUSBChargeChanged(boolean z);
    }

    static {
        new ReentrantLock();
        mContext = null;
        sInstance = null;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onManagerStarted() {
        Log.i("ExtModuleManager", "onManagerStarted");
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onManagerStarted();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onManagerStartTimeout() {
        Log.i("ExtModuleManager", "onManagerStartTimeout");
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onManagerStartTimeout();
        }
    }

    private void onCallStateChanged(int i) {
        Log.i("ExtModuleManager", "onCallStateChanged currState:" + i);
        Intent intent = new Intent("agui.intercom.intent.action.CALL_STATE_CHANGED");
        intent.putExtra("callstate", i);
        mContext.sendBroadcast(intent);
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onCallStateChanged(i);
        }
    }

    private void onPlayStateChanged(int i) {
        Log.i("ExtModuleManager", "onPlayStateChanged currState:" + i);
        Intent intent = new Intent("agui.intercom.intent.action.PLAY_STATE_CHANGED");
        intent.putExtra("playstate", i);
        mContext.sendBroadcast(intent);
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onPlayStateChanged(i);
        }
    }


    /* JADX INFO: Access modifiers changed from: private */
    public void onSetChannelComplete() {
        String str;
        String str2;
        Channel currChannel;
        Log.i("ExtModuleManager", "onSetChannelComplete");

        for (String str4 : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str4).onSetChannelComplete();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onSetChannelTimeout() {
        Log.i("ExtModuleManager", "onSetChannelTimeout mIsCmdStart:" + this.mIsCmdStart);
        if (this.mIsCmdStart) {
            resetMcu();
        }
        this.mIsSetChannelFinished = true;
        mContext.sendBroadcast(new Intent("agui.intercom.intent.action.START_TIMEOUT"));
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onSetChannelTimeout();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onMcuStartComplete() {
        Log.i("ExtModuleManager", "onMcuStartComplete");
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onMcuStartComplete();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onResetFactoryStart() {
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onResetFactoryStart();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onScanChannelsStart() {
        Log.i("ExtModuleManager", "onScanChannelsStart");
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onScanChannelsStart();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onScanChannelsComplete() {
        Log.i("ExtModuleManager", "onScanChannelsComplete");
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onScanChannelsComplete();
        }
    }

    private void onChargeStateChanged() {
        Log.i("ExtModuleManager", "onChargeStateChanged");
        if (SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
            return;
        }
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onChargeStateChanged();
        }
    }

    private void onUSBChargeChanged(boolean z) {
        if (SystemProperties.getBoolean("ro.agold.extmodule.cts", false)) {
            return;
        }
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onUSBChargeChanged(z);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void onGetIncallInfo(int i, int i2, int i3) {
        for (String str : this.mListenerMaps.keySet()) {
            this.mListenerMaps.get(str).onGetIncallInfo(i, i2, i3);
        }
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

    public ExtModuleManager(Context context) {
        this.mRecFrequency = 48000;
        this.mPlayFrequency = 48000;
        this.AUDIO_FRAME_SIZE = 1920;
        Log.i("ExtModuleManager", "ExtModuleManager constuct");
        ExtModuleProtocol extModuleProtocol = new ExtModuleProtocol();
        this.mExtModuleProtocol = extModuleProtocol;
        extModuleProtocol.setExtModuleManager(this);
        StorageManager storageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        this.mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.mPowerManager = powerManager;
        this.mWakeLock = powerManager.newWakeLock(26, "Intercom");
        this.mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.mSoundPool = new SoundPool(10, 1, 5);
        if (SystemProperties.getBoolean("ro.agold.extmodule.32k", false)) {
            this.mRecFrequency = 32000;
            this.mPlayFrequency = 32000;
            this.AUDIO_FRAME_SIZE = 1280;
        }
    }

    public ExtModuleProtocol getExtModuleProtocol() {
        return this.mExtModuleProtocol;
    }

    public boolean isScreenOn() {
        PowerManager powerManager = this.mPowerManager;
        if (powerManager != null) {
            return powerManager.isScreenOn();
        }
        return false;
    }

    public void setCallbackListener(CallbackListener callbackListener, String str) {
        synchronized (this.mListenerMaps) {
            Iterator<String> it = this.mListenerMaps.keySet().iterator();
            if (it.hasNext()) {
                String next = it.next();
                this.mListenerMaps.get(next);
                this.mListenerMaps.remove(next);
            }
            this.mListenerMaps.put(str, callbackListener);
        }
    }

    public void removeCallbackListener(String str) {
        synchronized (this.mListenerMaps) {
            Iterator<String> it = this.mListenerMaps.keySet().iterator();
            if (it.hasNext()) {
                String next = it.next();
                this.mListenerMaps.get(next);
                this.mListenerMaps.remove(next);
            }
        }
    }

    public void start() {
        Log.i("ExtModuleManager", "start mAllExit:" + this.mAllExit + ", mIsCmdStart:" + this.mIsCmdStart + ", mIsMCUStarted:" + this.mIsMCUStarted);
        this.mAllExit = false;
        this.mIsFirstDmrUpdate = true;
        this.mIsInTestMode = false;
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
        int minBufferSize = AudioTrack.getMinBufferSize(this.mPlayFrequency, 12, 2);
        Log.i("ExtModuleManager", "start playBufSize:" + minBufferSize);
        this.mExtAudioTrack = new AudioTrack(3, this.mPlayFrequency, 12, 2, minBufferSize, 1);
    }

    public void stop() {
        Log.i("ExtModuleManager", "stop-------");
        stopCom();
        this.mIsStopping = true;
        this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.3
            @Override // java.lang.Runnable
            public void run() {
                ExtModuleManager.this.exit();
                if (ExtModuleManager.mContext != null) {
//                    ExtModuleManager.mContext.stopService(new Intent(ExtModuleManager.mContext, IComService.class));
                    Log.i("ExtModuleManager", "stopService----------");
                }
                ExtModuleManager.this.mIsStopping = true;
            }
        }, 300L);
    }

    public int exit() {
        Log.i("ExtModuleManager", "exit mAudioState:" + this.mAudioState);
        this.mAllExit = true;
        this.mIsStopRecord = true;
        this.mIsPTTStopComplete = true;
        this.mIsStopPlay = true;
        this.mIsCmdStart = false;
        this.mIsMCUStarted = false;
        this.mIsFirstDmrUpdate = true;
        this.mIsInTestMode = false;
        this.mCmdTypes = 0;
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
        return 0;
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
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.5
            @Override // java.lang.Runnable
            public void run() {
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
                        ExtModuleManager.this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.5.1
                            @Override // java.lang.Runnable
                            public void run() {
                                ExtModuleManager.this.handleMcuStartFinished();
                            }
                        }, 2000L);
                    } catch (Exception e) {
                        Log.e("ExtModuleManager", "startMcu ex:" + e);
                    }
                } catch (Exception e2) {
                    Log.e("ExtModuleManager", "startMcu e:" + e2);
                }
            }
        }).start();
    }

    public void stopMcu() {
        Log.i("ExtModuleManager", "stopMcu");
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.6
            @Override // java.lang.Runnable
            public void run() {
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
            }
        }).start();
        this.mIsSetChannelFinished = false;
    }

    public void resetMcu() {
        Log.i("ExtModuleManager", "resetMcu");
        stopCom();
        this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.7
            @Override // java.lang.Runnable
            public void run() {
                ExtModuleManager.this.startMcu();
            }
        }, 4000L);
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

    /* JADX INFO: Access modifiers changed from: private */
    public void handleMcuStartFinished() {
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
        this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.8
            @Override // java.lang.Runnable
            public void run() {
                ExtModuleManager.this.getMcuFirmwareVersion();
            }
        }, 1000L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void getMcuFirmwareVersion() {
        Log.i("ExtModuleManager", "getMcuFirmwareVersion mCurrFirmware:" + this.mCurrFirmware + ", mIsCmdStart:" + this.mIsCmdStart);
        if (this.mIsCmdStart) {
            this.mCurrFirmware = null;
            ExtModuleProtocol extModuleProtocol = this.mExtModuleProtocol;
            if (extModuleProtocol != null) {
                extModuleProtocol.getFirmwareVersion();
            }
            this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.9
                @Override // java.lang.Runnable
                public void run() {
                    if (ExtModuleManager.this.mExtModuleProtocol != null) {
                        if (ExtModuleManager.this.mCurrFirmware == null || ExtModuleManager.this.mCurrFirmware.length() <= 0) {
                            ExtModuleManager.this.mExtModuleProtocol.getFirmwareVersion();
                        }
                    }
                }
            }, 1000L);
            this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.10
                @Override // java.lang.Runnable
                public void run() {
                    if (ExtModuleManager.this.mExtModuleProtocol != null) {
                        if (ExtModuleManager.this.mCurrFirmware == null || ExtModuleManager.this.mCurrFirmware.length() <= 0) {
                            ExtModuleManager.this.mExtModuleProtocol.getFirmwareVersion();
                        }
                    }
                }
            }, 2000L);
        }
    }

    private void handleRecvFirmwareVersion(byte[] bArr) {
        Log.i("ExtModuleManager", "handleRecvFirmwareVersion mCurrFirmware:" + mCurrFirmware);
        String str = this.mCurrFirmware;
        if ((str == null || str.length() <= 0) && bArr.length > 8) {
            int bytesToInt2 = IComUtils.bytesToInt2(new byte[]{bArr[6], bArr[7]});
            if (bytesToInt2 < 0) {
                bytesToInt2 += 256;
            }
            if (bytesToInt2 > 0) {
                byte[] bArr2 = new byte[bytesToInt2];
                for (int i = 0; i < bytesToInt2; i++) {
                    bArr2[i] = bArr[i + 8];
                }
                this.mCurrFirmware = new String(bArr2);
                Log.i("ExtModuleManager", "handleRecvFirmwareVersion mCurrFirmware:" + this.mCurrFirmware + ", mLastestFirmware:" + this.mLastestFirmware);
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


    /* JADX INFO: Access modifiers changed from: private */
    public void openPcmIn() {
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.15
            @Override // java.lang.Runnable
            public void run() {
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
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closePcmIn() {
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.16
            @Override // java.lang.Runnable
            public void run() {
                try {
                    if (ExtModuleManager.this.getAguiExtModule() != null) {
                        int closePcmIn = ExtModuleManager.this.mAguiExtModule.closePcmIn();
                        Log.i("ExtModuleManager", "closePcmIn ret:" + closePcmIn);
                        ExtModuleManager.this.mIsPcmInStart = false;
                    }
                } catch (Exception e) {
                    Log.e("ExtModuleManager", "closePcmIn e:" + e);
                }
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void openPcmOut() {
        Log.i("ExtModuleManager", "openPcmOut mIsStopRecord:" + this.mIsStopRecord);
        if (this.mIsStopRecord) {
            synchronized (this.mAudioLock) {
                this.mIsStopRecord = false;
                this.mAudioLock.notify();
            }
            this.mIsStopRecord = false;
            this.mStartCallTime = System.currentTimeMillis();
            setAudioRecordPath("" + this.mStartCallTime);
            new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.17
                @Override // java.lang.Runnable
                public void run() {
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
                }
            }).start();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closePcmOut() {
        Log.i("ExtModuleManager", "closePcmOut mIsStopRecord:" + this.mIsStopRecord);
        if (this.mIsStopRecord) {
            return;
        }
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.18
            @Override // java.lang.Runnable
            public void run() {
                try {
                    if (ExtModuleManager.this.getAguiExtModule() != null) {
                        int closePcmOut = ExtModuleManager.this.mAguiExtModule.closePcmOut();
                        Log.i("ExtModuleManager", "closePcmOut ret:" + closePcmOut);
                        ExtModuleManager.this.mAguiExtModule.stopPtt();
                    }
                } catch (Exception e) {
                    Log.e("ExtModuleManager", "closePcmOut e:" + e);
                }
            }
        }).start();
        onCallStateChanged(0);
        this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.19
            @Override // java.lang.Runnable
            public void run() {
                ExtModuleManager.this.mIsStopRecord = true;
                ExtModuleManager.this.mIsPcmOutStart = false;
            }
        }, 500L);
        if (this.mIsStopPlay) {
            releaseMusicFocus();
        }
    }

    private void createCmdReadThread() {
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.20
            @Override // java.lang.Runnable
            public void run() {
                while (!ExtModuleManager.this.mAllExit) {
                    try {
                        if (ExtModuleManager.this.mIsMCUStarted) {
                            if (ExtModuleManager.this.mIsUpdatingDmr) {
                                SystemClock.sleep(100L);
                            } else {
                                try {
                                    if (ExtModuleManager.this.getAguiExtModule() != null) {
                                        ExtModuleManager.this.mAguiExtModule.readTTyDevice(new IAguiExtModuleReadCallback.Stub() { // from class: com.agold.intercom.module.ExtModuleManager.20.1
                                            @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModuleReadCallback
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
            }
        }).start();
    }

    private void createCallInThread() {
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.21
            @Override // java.lang.Runnable
            public void run() {
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
            }
        }).start();
    }

    private void createAudioPlayThread() {
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.22
            @Override // java.lang.Runnable
            public void run() {
                while (!ExtModuleManager.this.mAllExit) {
                    try {
                        if (!ExtModuleManager.this.mIsStopPlay) {
                            if (!ExtModuleManager.this.mIsPcmInStart) {
                                SystemClock.sleep(10L);
                            } else {
                                Log.i("ExtModuleManager", "createAudioPlayThread mAudioTrack:" + ExtModuleManager.this.mAudioTrack);
                                if (ExtModuleManager.this.mAudioTrack == null) {
                                    int minBufferSize = AudioTrack.getMinBufferSize(ExtModuleManager.this.mPlayFrequency, 12, 2);
                                    Log.i("ExtModuleManager", "createAudioPlayThread start playBufSize:" + minBufferSize);
                                    ExtModuleManager.this.mAudioTrack = new AudioTrack(3, ExtModuleManager.this.mPlayFrequency, 12, 2, minBufferSize, 1);
                                }
                                Log.i("ExtModuleManager", "createAudioPlayThread mAudioTrack getState:" + ExtModuleManager.this.mAudioTrack.getState() + ", getPlayState:" + ExtModuleManager.this.mAudioTrack.getPlayState());
                                ExtModuleManager.this.mAudioTrack.play();
                                while (ExtModuleManager.this.mIsPcmInStart) {
                                    try {
                                        if (ExtModuleManager.this.getAguiExtModule() != null) {
                                            ExtModuleManager.this.mAguiExtModule.readPcmDevice(new IAguiExtModuleReadCallback.Stub() { // from class: com.agold.intercom.module.ExtModuleManager.22.1
                                                @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModuleReadCallback
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
            }
        }).start();
    }

    private void createAudioRecordThread() {
        new Thread(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.23
            @Override // java.lang.Runnable
            public void run() {
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
                                File file = new File("/sdcard/Download/record/");
                                if (!file.exists()) {
                                    file.mkdirs();
                                }
                                BufferedOutputStream bufferedOutputStream = null;
                                if (ExtModuleManager.this.mAudioRecordPath != null) {
                                    File file2 = new File(ExtModuleManager.this.mAudioRecordPath);
                                    if (file2.exists()) {
                                        file2.delete();
                                    }
                                    file2.createNewFile();
                                    bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(file2));
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
            }
        }).start();
    }

    public boolean isCmdStarted() {
        return this.mIsCmdStart;
    }

    public boolean isRecordStoped() {
        return this.mIsStopRecord;
    }

    public boolean isPttStoped() {
        return this.mIsPTTStopComplete;
    }

    public boolean isPlayStoped() {
        return this.mIsStopPlay;
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
            if (this.mIsScanningChannels) {
                this.mHandler.removeMessages(23);
                Handler handler2 = this.mHandler;
                handler2.sendMessage(handler2.obtainMessage(23));
                this.mIsScanningChannels = false;
            }
            try {
                File file = new File("/sdcard/Download/record/");
                this.mPcmDir = file;
                if (!file.exists()) {
                    this.mPcmDir.mkdirs();
                }
            } catch (Exception e) {
                Log.e("ExtModuleManager", "startPlay ex:" + e);
            }
            if (this.mAudioRecordPath == null) {
                return 0;
            }
            File pcmFile = new File(this.mAudioRecordPath);
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

    public int stopPlay() {
        Log.i("ExtModuleManager", "stopPlay mIsStopPlay:" + this.mIsStopPlay);
        if (this.mIsStopPlay) {
            Log.i("ExtModuleManager", "stopPlay---> play is not started");
            return 0;
        }
        this.mIsStopPlay = true;
        this.mHandler.removeMessages(19);
        Handler handler = this.mHandler;
        handler.sendMessage(handler.obtainMessage(19));
        String str = this.mAudioRecordPath;
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
//        Log.i("ExtModuleManager", "stopPlay channelId:" + currChannel + ", channelArea:" + currArea + ", recordPath:" + str + ", duration:" + currentTimeMillis + ", mStartPlayTime:" + this.mStartPlayTime + ", mIsSettingFactory:" + this.mIsSettingFactory);
//        if (str != null && !this.mIsSettingFactory) {
//            if (currentTimeMillis < 1500) {
//                deleteRecordFile(str);
//            } else {
//                IComRecordDB.getInstance(mContext).addRecord(new Record(currChannel, currArea, 1, str, currentTimeMillis, this.mStartPlayTime));
//            }
//        }
        this.mStartPlayTime = 0L;
        onPlayStateChanged(0);
        if (this.mAudioState == 0) {
            releaseMusicFocus();
        }
        this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.24
            @Override // java.lang.Runnable
            public void run() {
                if (ExtModuleManager.this.mIsStopPlay && ExtModuleManager.this.mHasNewMessage) {
                    if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
                        ExtModuleManager.this.mExtModuleProtocol.getDmr09MsgContent();
                    } else {
                        ExtModuleManager.this.mExtModuleProtocol.getMsgContent();
                    }
                    ExtModuleManager.this.mHasNewMessage = false;
                }
            }
        }, 500L);
        Log.i("ExtModuleManager", "stopPlay end-----------------");
        return 0;
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
        Log.i("ExtModuleManager", "requestMusicFocus mIsCurrMusicFocus:" + this.mIsCurrMusicFocus);
        if (this.mIsCurrMusicFocus) {
            return 1;
        }
        AudioManager audioManager = this.mAudioManager;
        if (audioManager != null) {
            int requestAudioFocus = audioManager.requestAudioFocus(null, 3, 4);
            Log.i("ExtModuleManager", "requestMusicFocus status:" + requestAudioFocus);
            this.mIsCurrMusicFocus = true;
            return 1;
        }
        return 0;
    }

    private void releaseMusicFocus() {
        Log.i("ExtModuleManager", "releaseMusicFocus mIsCurrMusicFocus:" + this.mIsCurrMusicFocus);
        AudioManager audioManager = this.mAudioManager;
        if (audioManager == null || !this.mIsCurrMusicFocus) {
            return;
        }
        audioManager.abandonAudioFocus(null);
        this.mIsCurrMusicFocus = false;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public int handleCmdResponse(byte[] response, int length) {
        byte[] bArr2 = new byte[length];
        System.arraycopy(response, 0, bArr2, 0, length);
        String byte2HexStrNoBlank = IComUtils.byte2HexStrNoBlank(bArr2);
        Log.i("ExtModuleManager", "handleCmdResponse len:" + length + ", strCmd:" + byte2HexStrNoBlank);
        int i2 = 0;
        while (true) {
            if (i2 + 11 >= length) {
                break;
            }
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
        this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.28
            @Override // java.lang.Runnable
            public void run() {
                ExtModuleManager.this.mIsPTTStopComplete = true;
            }
        }, 1000L);
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
                for (int i2 = 0; i2 < i; i2++) {
                    bArr2[i2] = bArr[i2 + 8];
                }
                String str = new String(bArr2);
                Log.i("ExtModuleManager", "getModuleVersonContent module:" + str);

//                Channel channel = new Channel();
//                this.setChannel(channel);
            }
        }
    }

    public int getCurrBatteryLevel() {
        return this.mCurrBatteryLevel;
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
        onUSBChargeChanged(z2);
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
            onChargeStateChanged();
            resetMcu();
        }
    }

    public void handleChargeDisconnected() {
        Log.i("ExtModuleManager", "handleChargeDisconnected mIsCmdStart:" + this.mIsCmdStart + ", mIsSetChannelFinished:" + this.mIsSetChannelFinished);
        if (this.mIsCmdStart) {
            onChargeStateChanged();
            resetMcu();
        }
    }

    public void handleHeadsetPlugChanged(int i) {
        Log.i("ExtModuleManager", "handleHeadsetPlugChanged state:" + i);
        if ((i == 1 || i == 0) && this.mIsCmdStart) {
            onChargeStateChanged();
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

    /* JADX INFO: Access modifiers changed from: private */
    public void handleCallInStateChanged(int i) {
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
                this.mHandler.postDelayed(new Runnable() { // from class: com.agold.intercom.module.ExtModuleManager.33
                    @Override // java.lang.Runnable
                    public void run() {
                        if (ExtModuleManager.this.mIsStopPlay) {
                            ExtModuleManager.this.mExtModuleProtocol.getDmr09MsgContent();
                        }
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

    public AudioTrack getAudioTrack() {
        return this.mExtAudioTrack;
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
            this.mAudioRecordPath = "/sdcard/Download/record/" + str + ".pcm";
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

//    public void showInstallAntennaToast(Context context) {
//        Toast.makeText(context, (int) R.string.install_antenna, Toast.LENGTH_SHORT).show();
//    }

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
}