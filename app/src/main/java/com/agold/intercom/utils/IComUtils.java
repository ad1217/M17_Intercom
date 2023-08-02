package com.agold.intercom.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;
/* loaded from: classes.dex */
public class IComUtils {
    private static final byte[] PROTOCAL_HEAD = {126, -106, 105};
    private static final byte[] PROTOCAL_TAIL = {-127};
    public static final int DEFAULT_START_TONE = SystemProperties.getInt("ro.agold_intercom_tone", 0);
    public static final int DEFAULT_END_TONE = SystemProperties.getInt("ro.agold_intercom_tone", 0);
    public static final int DEFAULT_LIMIT_SEND_TIME = SystemProperties.getInt("ro.default.limit_send_time", 30);
    public static final int DEFAULT_PTT_EXIT_TIME = SystemProperties.getInt("ro.default.ptt_exit_time", 0);
    public static final String[] CTCSS_LIST = {"62.5Hz", "67.0Hz", "69.3Hz", "71.9Hz", "74.4Hz", "77.0Hz", "79.7Hz", "82.5Hz", "85.4Hz", "88.5Hz", "91.5Hz", "94.8Hz", "97.4Hz", "100.0Hz", "103.5Hz", "107.2Hz", "110.9Hz", "114.8Hz", "118.8Hz", "123.0Hz", "127.3Hz", "131.8Hz", "136.5Hz", "141.3Hz", "146.2Hz", "151.4Hz", "156.7Hz", "159.8Hz", "162.2Hz", "165.5Hz", "167.9Hz", "171.3Hz", "173.8Hz", "177.3Hz", "179.9Hz", "183.5Hz", "186.2Hz", "189.9Hz", "192.8Hz", "196.6Hz", "199.5Hz", "203.5Hz", "206.5Hz", "210.7Hz", "218.1Hz", "225.7Hz", "229.1Hz", "233.6Hz", "241.8Hz", "250.3Hz", "254.1Hz"};
    public static final String[] CDCSS_LIST = {"023N", "025N", "026N", "031N", "032N", "043N", "047N", "051N", "054N", "065N", "071N", "072N", "073N", "074N", "114N", "115N", "116N", "125N", "131N", "132N", "134N", "143N", "152N", "155N", "156N", "162N", "165N", "172N", "174N", "205N", "223N", "226N", "243N", "244N", "245N", "251N", "261N", "263N", "265N", "271N", "306N", "311N", "315N", "331N", "343N", "346N", "351N", "364N", "365N", "371N", "411N", "412N", "413N", "423N", "431N", "432N", "445N", "464N", "465N", "466N", "503N", "506N", "516N", "532N", "546N", "565N", "606N", "612N", "624N", "627N", "631N", "632N", "654N", "662N", "664N", "703N", "712N", "723N", "731N", "732N", "734N", "743N", "754N"};
    public static final String[] RCDCSS_LIST = {"023I", "025I", "026I", "031I", "032I", "043I", "047I", "051I", "054I", "065I", "071I", "072I", "073I", "074I", "114I", "115I", "116I", "125I", "131I", "132I", "134I", "143I", "152I", "155I", "156I", "162I", "165I", "172I", "174I", "205I", "223I", "226I", "243I", "244I", "245I", "251I", "261I", "263I", "265I", "271I", "306I", "311I", "315I", "331I", "343I", "346I", "351I", "364I", "365I", "371I", "411I", "412I", "413I", "423I", "431I", "432I", "445I", "464I", "465I", "466I", "503I", "506I", "516I", "532I", "546I", "565I", "606I", "612I", "624I", "627I", "631I", "632I", "654I", "662I", "664I", "703I", "712I", "723I", "731I", "732I", "734I", "743I", "754I"};

    public static String byte2HexStr(byte[] bArr) {
        StringBuilder sb = new StringBuilder("");
        for (byte b : bArr) {
            String hexString = Integer.toHexString(b & 255);
            if (hexString.length() == 1) {
                hexString = "0" + hexString;
            }
            sb.append(hexString);
            sb.append(" ");
        }
        return sb.toString().toUpperCase().trim();
    }

    public static String byte2HexStrNoBlank(byte[] bArr) {
        StringBuilder sb = new StringBuilder("");
        for (byte b : bArr) {
            String hexString = Integer.toHexString(b & 255);
            if (hexString.length() == 1) {
                hexString = "0" + hexString;
            }
            sb.append(hexString);
        }
        return sb.toString().toUpperCase().trim();
    }

    public static byte[] hexStr2Bytes(String str) {
        if (str == null || str.equals("")) {
            return null;
        }
        String upperCase = str.toUpperCase();
        int length = upperCase.length() / 2;
        char[] charArray = upperCase.toCharArray();
        byte[] bArr = new byte[length];
        for (int i = 0; i < length; i++) {
            int i2 = i * 2;
            bArr[i] = (byte) (charToByte(charArray[i2 + 1]) | (charToByte(charArray[i2]) << 4));
        }
        return bArr;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    public static String gbEncoding2(String str) {
        String str2 = "";
        for (char c : str.toCharArray()) {
            String hexString = Integer.toHexString(c);
            if (hexString.length() >= 1) {
                if (hexString.length() == 1) {
                    hexString = "000" + hexString;
                } else if (hexString.length() == 2) {
                    hexString = "00" + hexString;
                }
                str2 = str2 + hexString;
            }
        }
        System.out.println("unicodeBytes is: " + str2);
        return str2;
    }

    public static byte[] gbEncoding2Bytes(String str) {
        byte[] hexStr2Bytes = hexStr2Bytes(gbEncoding2(str));
        if (hexStr2Bytes != null && hexStr2Bytes.length >= 2) {
            for (int i = 0; i < hexStr2Bytes.length; i++) {
                if (i % 2 == 1) {
                    byte b = hexStr2Bytes[i];
                    int i2 = i - 1;
                    hexStr2Bytes[i] = hexStr2Bytes[i2];
                    hexStr2Bytes[i2] = b;
                }
            }
        }
        return hexStr2Bytes;
    }

    public static String decodeUnicode(byte[] bArr) {
        if (bArr == null || bArr.length < 2) {
            return null;
        }
        int length = bArr.length;
        byte[] bArr2 = new byte[length];
        System.arraycopy(bArr, 0, bArr2, 0, bArr.length);
        for (int i = 0; i < length; i++) {
            if (i % 2 == 1) {
                byte b = bArr2[i];
                int i2 = i - 1;
                bArr2[i] = bArr2[i2];
                bArr2[i2] = b;
            }
        }
        return decodeUnicode2(byte2HexStr(bArr2));
    }

    public static String decodeUnicode2(String str) {
        String replace = str.replace(" ", "");
        Log.i("xuelb", "decodeUnicode2 theString:" + replace);
        int length = replace.length();
        StringBuffer stringBuffer = new StringBuffer(length);
        int i = 0;
        while (i < length && i + 4 <= length) {
            int i2 = 0;
            int i3 = 0;
            while (i2 < 4) {
                int i4 = i + 1;
                char charAt = replace.charAt(i);
                switch (charAt) {
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        i3 = ((i3 << 4) + charAt) - 48;
                        break;
                    default:
                        switch (charAt) {
                            case 'A':
                            case 'B':
                            case 'C':
                            case 'D':
                            case 'E':
                            case 'F':
                                i3 = (((i3 << 4) + 10) + charAt) - 65;
                                break;
                            default:
                                switch (charAt) {
                                    case 'a':
                                    case 'b':
                                    case 'c':
                                    case 'd':
                                    case 'e':
                                    case 'f':
                                        i3 = (((i3 << 4) + 10) + charAt) - 97;
                                        break;
                                    default:
                                        throw new IllegalArgumentException("Malformed   \\uxxxx   encoding.");
                                }
                        }
                }
                i2++;
                i = i4;
            }
            stringBuffer.append((char) i3);
        }
        return stringBuffer.toString();
    }

    public static int checkSum(byte[] bArr, int i, int i2) {
        int i3 = 0;
        int i4 = i2;
        while (i > i2 + 1) {
            i3 += (((bArr[i4] & 255) << 8) + (bArr[i4 + 1] & 255)) & 65535;
            i4 += 2;
            i -= 2;
        }
        if (i > i2) {
            i3 += (bArr[i4] & 255) << 8;
        }
        while (true) {
            int i5 = i3 >> 16;
            if (i5 <= 0) {
                return i3 ^ 65535;
            }
            i3 = (i3 & 65535) + i5;
        }
    }

    public static byte[] makePackage(byte[] bArr, byte[] bArr2, byte[] bArr3) {
        byte[] bArr4 = PROTOCAL_TAIL;
        byte[] bArr5 = PROTOCAL_HEAD;
        int length = bArr3.length;
        int length2 = bArr5.length + bArr.length + bArr2.length + 2 + bArr3.length + 2 + bArr4.length;
        byte[] intTo2Bytes = intTo2Bytes(length);
        byte[] bArr6 = new byte[length2];
        for (int i = 0; i < bArr5.length; i++) {
            bArr6[i] = bArr5[i];
        }
        int length3 = bArr5.length + 0;
        for (int i2 = 0; i2 < bArr.length; i2++) {
            bArr6[i2 + length3] = bArr[i2];
        }
        int length4 = length3 + bArr.length;
        for (int i3 = 0; i3 < bArr2.length; i3++) {
            bArr6[i3 + length4] = bArr2[i3];
        }
        int length5 = length4 + bArr2.length;
        for (int i4 = 0; i4 < intTo2Bytes.length; i4++) {
            bArr6[i4 + length5] = intTo2Bytes[i4];
        }
        int length6 = length5 + intTo2Bytes.length;
        for (int i5 = 0; i5 < bArr3.length; i5++) {
            bArr6[i5 + length6] = bArr3[i5];
        }
        int checkSum = checkSum(bArr6, (length2 - 2) - bArr4.length, bArr5.length);
        Log.i("IComUtils", "dataPackage checkSum:" + checkSum);
        byte[] intTo2Bytes2 = intTo2Bytes(checkSum);
        int length7 = length6 + bArr3.length;
        for (int i6 = 0; i6 < intTo2Bytes2.length; i6++) {
            bArr6[i6 + length7] = intTo2Bytes2[i6];
        }
        int length8 = length7 + intTo2Bytes2.length;
        for (int i7 = 0; i7 < bArr4.length; i7++) {
            bArr6[i7 + length8] = bArr4[i7];
        }
        return bArr6;
    }

    public static byte[] intTo2Bytes(int i) {
        int i2 = i & 65535;
        byte[] bArr = new byte[2];
        for (int i3 = 0; i3 < 2; i3++) {
            bArr[i3] = (byte) ((i2 >> (8 - (i3 * 8))) & 255);
        }
        return bArr;
    }

    public static byte[] intTo4Bytes(int i) {
        byte[] bArr = new byte[4];
        for (int i2 = 0; i2 < 4; i2++) {
            bArr[i2] = (byte) ((i >> (24 - (i2 * 8))) & 255);
        }
        return bArr;
    }

    public static byte[] intTo4LittleEndianBytes(int i) {
        byte[] bArr = new byte[4];
        for (int i2 = 0; i2 < 4; i2++) {
            bArr[i2] = (byte) ((i >> (i2 * 8)) & 255);
        }
        return bArr;
    }

    public static int bytesToInt2(byte[] bArr) {
        int length = bArr.length;
        int i = 0;
        for (int i2 = 0; i2 < length; i2++) {
            int i3 = bArr[i2];
            if (i3 < 0) {
                i3 += 256;
            }
            int i4 = i3 << (((length - 1) - i2) * 8);
            Log.i("IComUtils", "bytesToInt2 i:" + i2 + ", tmp:" + i4);
            i += i4;
        }
        return i;
    }

    public static int bytesToInt(byte[] bArr, int i) {
        int i2 = 0;
        for (int i3 = 0; i3 < i; i3++) {
            int i4 = bArr[i3];
            if (i4 < 0) {
                i4 += 256;
            }
            int i5 = i4 << (((i - 1) - i3) * 8);
            Log.i("IComUtils", "bytesToInt i:" + i3 + ", tmp:" + i5);
            i2 += i5;
        }
        return i2;
    }

    public static int bytesToIntInv(byte[] bArr, int i) {
        int i2 = 0;
        for (int i3 = 0; i3 < i; i3++) {
            int i4 = bArr[i3];
            if (i4 < 0) {
                i4 += 256;
            }
            int i5 = i4 << (i3 * 8);
            Log.i("IComUtils", "bytesToIntInv i:" + i3 + ", tmp:" + i5);
            i2 += i5;
        }
        return i2;
    }

    public static boolean isIComCmd(byte[] bArr) {
        if (bArr != null && bArr.length > 11) {
            byte b = bArr[0];
            byte[] bArr2 = PROTOCAL_HEAD;
            if (b == bArr2[0] && bArr[1] == bArr2[1] && bArr[2] == bArr2[2]) {
                return true;
            }
        }
        return false;
    }

    public static boolean isIComCmd(byte[] bArr, int i) {
        if (bArr != null && bArr.length > 11) {
            byte b = bArr[i + 0];
            byte[] bArr2 = PROTOCAL_HEAD;
            if (b == bArr2[0] && bArr[i + 1] == bArr2[1] && bArr[i + 2] == bArr2[2]) {
                return true;
            }
        }
        return false;
    }

    public static String splitData(long j, long j2) {
        double pow = Math.pow(10.0d, j2);
        String str = ("" + ((int) (j / pow))) + ".";
        int i = (int) (j % ((int) pow));
        for (int i2 = ((int) j2) - 1; i2 >= 0; i2--) {
            if (Math.pow(10.0d, i2) <= i) {
                return str + i;
            }
            str = str + "0";
        }
        return str;
    }

    public static boolean isRunningActivity(Context context, String str) {
        for (ActivityManager.RunningTaskInfo runningTaskInfo : ((ActivityManager) context.getSystemService("activity")).getRunningTasks(7)) {
            if (runningTaskInfo.topActivity.getClassName().equals(str)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isMyServiceRunning(Context context, String str) {
        for (ActivityManager.RunningServiceInfo runningServiceInfo : ((ActivityManager) context.getSystemService("activity")).getRunningServices(Integer.MAX_VALUE)) {
            if (str.equals(runningServiceInfo.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
