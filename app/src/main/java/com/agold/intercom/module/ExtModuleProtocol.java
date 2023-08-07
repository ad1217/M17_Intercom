package com.agold.intercom.module;

import android.os.SystemProperties;
import android.util.Log;

import com.agold.intercom.utils.IComUtils;

public class ExtModuleProtocol {
    private ExtModuleManager mExtModuleManager;

    public void setExtModuleManager(ExtModuleManager extModuleManager) {
        this.mExtModuleManager = extModuleManager;
    }

    public void setReceiverVolume(int i) {
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{1, 2}, new byte[]{1}, new byte[]{(byte) i}));
    }

    public void sendMsg(int type, int callNum, String msg) {
        if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
            sendDmr09Msg(type, callNum, msg);
            return;
        }
        Log.i("ExtModuleProtocol", "sendMsg type:" + type + ", callNum:" + callNum + ", msg:" + msg);
        byte[] bArr = {1, 7};
        byte[] bArr2 = {1};
        byte[] intTo4Bytes = IComUtils.intTo4Bytes(callNum);
        byte[] gbEncoding2Bytes = IComUtils.gbEncoding2Bytes(msg);
        Log.i("ExtModuleProtocol", "sendMsg msgUnicode:" + IComUtils.byte2HexStr(gbEncoding2Bytes) + ", gbString:" + IComUtils.decodeUnicode(gbEncoding2Bytes));
        byte[] bArr3 = new byte[gbEncoding2Bytes.length + 4 + 4];
        bArr3[0] = (byte) type;
        bArr3[1] = intTo4Bytes[1];
        bArr3[2] = intTo4Bytes[2];
        bArr3[3] = intTo4Bytes[3];
        System.arraycopy(gbEncoding2Bytes, 0, bArr3, 4, gbEncoding2Bytes.length);
        for (int i4 = 0; i4 < 4; i4++) {
            bArr3[gbEncoding2Bytes.length + 4 + i4] = 0;
        }
        Log.i("ExtModuleProtocol", "sendMsg data:" + IComUtils.byte2HexStr(bArr3));
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(bArr, bArr2, bArr3));
    }

    public void getCallInfo() {
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{1, 16}, new byte[]{1}, new byte[]{1}));
    }

    public void getMsgContent() {
        if (SystemProperties.getBoolean("ro.agold.extmodule.dmr09", false)) {
            getDmr09MsgContent();
        } else {
            this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{1, 17}, new byte[]{1}, new byte[]{1}));
        }
    }

    public void getModuleSoftVersion() {
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{1, 37}, new byte[]{1}, new byte[]{1}));
    }

    public void setAnalogGroup(int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9) {
        byte[] bArr = {1, 53};
        byte[] bArr2 = {1};
        byte[] intTo4LittleEndianBytes = IComUtils.intTo4LittleEndianBytes(i4);
        byte[] intTo4LittleEndianBytes2 = IComUtils.intTo4LittleEndianBytes(i3);
        byte[] bArr3 = new byte[15];
        bArr3[0] = (byte) i;
        bArr3[1] = (byte) i2;
        System.arraycopy(intTo4LittleEndianBytes2, 0, bArr3, 2, 4);
        System.arraycopy(intTo4LittleEndianBytes, 0, bArr3, 6, 4);
        bArr3[10] = (byte) i5;
        bArr3[11] = (byte) i6;
        bArr3[12] = (byte) i7;
        bArr3[13] = (byte) i8;
        bArr3[14] = (byte) i9;
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(bArr, bArr2, bArr3));
    }

    public void setDigitalGroup(int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8, String str, int[] iArr) {
        byte[] bArr = {1, 54};
        byte[] bArr2 = {1};
        byte[] intTo4LittleEndianBytes = IComUtils.intTo4LittleEndianBytes(i3);
        byte[] intTo4LittleEndianBytes2 = IComUtils.intTo4LittleEndianBytes(i2);
        byte[] intTo4Bytes = IComUtils.intTo4Bytes(i4);
        byte[] intTo4Bytes2 = IComUtils.intTo4Bytes(i7);
        byte[] bArr3 = new byte[156];
        bArr3[0] = (byte) i;
        int i9 = 0;
        while (i9 < 4) {
            int i10 = i9 + 1;
            bArr3[i10] = intTo4LittleEndianBytes2[i9];
            i9 = i10;
        }
        System.arraycopy(intTo4LittleEndianBytes, 0, bArr3, 5, 4);
        System.arraycopy(intTo4Bytes, 0, bArr3, 9, 4);
        bArr3[13] = (byte) i5;
        bArr3[14] = (byte) i6;
        System.arraycopy(intTo4Bytes2, 0, bArr3, 15, 4);
        bArr3[19] = (byte) i8;
        for (int i14 = 0; i14 < 8; i14++) {
            bArr3[i14 + 20] = 0;
        }
        if (str != null) {
            byte[] bytes = str.getBytes();
            if (bytes.length > 0 && bytes.length <= 8) {
                System.arraycopy(bytes, 0, bArr3, 20, bytes.length);
            }
        }
        int i16 = 28;
        for (int i17 : iArr) {
            byte[] intTo4Bytes3 = IComUtils.intTo4Bytes(i17);
            System.arraycopy(intTo4Bytes3, 0, bArr3, 0 + i16, 4);
            i16 += 4;
        }
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(bArr, bArr2, bArr3));
    }

    public void getFirmwareVersion() {
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{0, 1}, new byte[]{1}, new byte[]{1}));
    }

    public void setDmr09AnalogGroup(int band, int power, int recvFreq, int sendFreq, int squelchLevel, int recvSubAudioType, int recvCtcssFreq, int sendSubAudioType, int sendCtcssFreq, int i10, int i11, int i12, int i13) {
        byte[] bArr = {1, 35};
        byte[] bArr2 = {1};
        byte[] intTo4LittleEndianBytes = IComUtils.intTo4LittleEndianBytes(sendFreq);
        byte[] intTo4LittleEndianBytes2 = IComUtils.intTo4LittleEndianBytes(recvFreq);
        byte[] bArr3 = new byte[19];
        System.arraycopy(intTo4LittleEndianBytes2, 0, bArr3, 0, 4);
        System.arraycopy(intTo4LittleEndianBytes, 0, bArr3, 4, 4);
        if (band == 128) {
            bArr3[8] = 1;
        } else {
            bArr3[8] = 0;
        }
        bArr3[9] = (byte) power;
        bArr3[10] = (byte) squelchLevel;
        bArr3[11] = (byte) (recvSubAudioType - 1);
        if (bArr3[11] == 0) {
            bArr3[12] = -1;
        } else {
            bArr3[12] = (byte) recvCtcssFreq;
        }
        bArr3[13] = (byte) (sendSubAudioType - 1);
        if (bArr3[13] == 0) {
            bArr3[14] = -1;
        } else {
            bArr3[14] = (byte) sendCtcssFreq;
        }
        bArr3[15] = (byte) i10;
        bArr3[16] = (byte) i11;
        bArr3[17] = (byte) i12;
        bArr3[18] = (byte) i13;
        Log.i("ExtModuleProtocol", "setDmr09AnalogGroup data:" + IComUtils.byte2HexStr(bArr3));
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(bArr, bArr2, bArr3));
    }

    public void setDmr09DigitalGroup(int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8, String str, int[] iArr, int i9, int i10, int i11, int i12, int i13, int i14, int i15) {
        byte[] bArr = {1, 34};
        byte[] bArr2 = {1};
        byte[] intTo4LittleEndianBytes = IComUtils.intTo4LittleEndianBytes(i3);
        byte[] intTo4LittleEndianBytes2 = IComUtils.intTo4LittleEndianBytes(i2);
        byte[] intTo4LittleEndianBytes3 = IComUtils.intTo4LittleEndianBytes(i4);
        byte[] intTo4LittleEndianBytes4 = IComUtils.intTo4LittleEndianBytes(i7);
        byte[] bArr3 = new byte[163];
        System.arraycopy(intTo4LittleEndianBytes2, 0, bArr3, 0, 4);
        System.arraycopy(intTo4LittleEndianBytes, 0, bArr3, 4, 4);
        System.arraycopy(intTo4LittleEndianBytes3, 0, bArr3, 8, 4);
        int i19 = 12;
        for (int i20 : iArr) {
            byte[] intTo4LittleEndianBytes5 = IComUtils.intTo4LittleEndianBytes(i20);
            System.arraycopy(intTo4LittleEndianBytes5, 0, bArr3, 0 + i19, 4);
            i19 += 4;
        }
        System.arraycopy(intTo4LittleEndianBytes4, 0, bArr3, 0 + i19, 4);
        int i23 = i19 + 4;
        if (i6 == 4) {
            bArr3[i23] = (byte) (i6 - 2);
        } else {
            bArr3[i23] = (byte) (i6 - 1);
        }
        int i24 = i23 + 1;
        bArr3[i24] = (byte) i;
        int i25 = i24 + 1;
        bArr3[i25] = (byte) i5;
        int i26 = i25 + 1;
        bArr3[i26] = (byte) i9;
        int i27 = i26 + 1;
        bArr3[i27] = (byte) i10;
        int i28 = i27 + 1;
        bArr3[i28] = (byte) i11;
        int i29 = i28 + 1;
        if (i8 == 255) {
            bArr3[i29] = 2;
        } else {
            bArr3[i29] = (byte) i8;
        }
        int i30 = i29 + 1;
        for (int i31 = 0; i31 < 8; i31++) {
            bArr3[i31 + i30] = 0;
        }
        if (str != null) {
            byte[] bytes = str.getBytes();
            if (bytes.length > 0 && bytes.length <= 8) {
                System.arraycopy(bytes, 0, bArr3, 0 + i30, bytes.length);
            }
        }
        int i33 = i30 + 8;
        bArr3[i33] = (byte) i12;
        int i34 = i33 + 1;
        bArr3[i34] = (byte) i13;
        int i35 = i34 + 1;
        bArr3[i35] = (byte) i14;
        bArr3[i35 + 1] = (byte) i15;
        Log.i("ExtModuleProtocol", "setDmr09DigitalGroup data:" + IComUtils.byte2HexStr(bArr3));
        byte[] makePackage = IComUtils.makePackage(bArr, bArr2, bArr3);
        Log.i("ExtModuleProtocol", "setDmr09DigitalGroup packByte:" + IComUtils.byte2HexStr(makePackage));
        this.mExtModuleManager.sendBytes(makePackage);
    }

    public void setDmr09ReceiverVolume(int i) {
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{1, 46}, new byte[]{1}, new byte[]{(byte) i}));
    }

    public void getDmr09ModuleSoftVersion() {
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{1, 52}, new byte[]{1}, new byte[]{1}));
    }

    public void getDmr09RecvCallInfo() {
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{1, 43}, new byte[]{1}, new byte[]{1}));
    }

    public void sendDmr09Msg(int i, int i2, String str) {
        Log.i("ExtModuleProtocol", "sendDmr09Msg type:" + i + ", callNum:" + i2 + ", msg:" + str);
        byte[] bArr = {1, 44};
        byte[] bArr2 = {1};
        byte[] intTo4Bytes = IComUtils.intTo4Bytes(i2);
        byte[] gbEncoding2Bytes = IComUtils.gbEncoding2Bytes(str);
        Log.i("ExtModuleProtocol", "sendDmr09Msg msgUnicode:" + IComUtils.byte2HexStr(gbEncoding2Bytes) + ", gbString:" + IComUtils.decodeUnicode(gbEncoding2Bytes));
        byte[] bArr3 = new byte[gbEncoding2Bytes.length + 5 + 4];
        bArr3[0] = (byte) i;
        if (i == 9) {
            bArr3[0] = 3;
        }
        bArr3[1] = intTo4Bytes[3];
        bArr3[2] = intTo4Bytes[2];
        bArr3[3] = intTo4Bytes[1];
        bArr3[4] = intTo4Bytes[0];
        System.arraycopy(gbEncoding2Bytes, 0, bArr3, 5, gbEncoding2Bytes.length);
        for (int i4 = 0; i4 < 4; i4++) {
            bArr3[gbEncoding2Bytes.length + 5 + i4] = 0;
        }
        Log.i("ExtModuleProtocol", "sendDmr09Msg data:" + IComUtils.byte2HexStr(bArr3));
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(bArr, bArr2, bArr3));
    }

    public void getDmr09MsgContent() {
        this.mExtModuleManager.sendBytes(IComUtils.makePackage(new byte[]{1, 45}, new byte[]{1}, new byte[]{1}));
    }
}
