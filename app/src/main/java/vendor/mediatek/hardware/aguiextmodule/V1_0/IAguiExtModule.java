package vendor.mediatek.hardware.aguiextmodule.V1_0;

import android.os.HidlSupport;
import android.os.HwBinder;
import android.os.HwBlob;
import android.os.HwParcel;
import android.os.IHwBinder;
import android.os.IHwInterface;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;
/* loaded from: classes.dex */
public interface IAguiExtModule extends IHwInterface {
    int closePcmIn() throws RemoteException;

    int closePcmOut() throws RemoteException;

    int detectAudioinState() throws RemoteException;

    ArrayList<String> interfaceChain() throws RemoteException;

    int openPcmIn() throws RemoteException;

    int openPcmOut() throws RemoteException;

    void readPcmDevice(IAguiExtModuleReadCallback iAguiExtModuleReadCallback) throws RemoteException;

    void readTTyDevice(IAguiExtModuleReadCallback iAguiExtModuleReadCallback) throws RemoteException;

    int startMcu() throws RemoteException;

    void startPtt() throws RemoteException;

    int stopMcu() throws RemoteException;

    void stopPtt() throws RemoteException;

    int updateDmr(byte[] bArr, int i) throws RemoteException;

    int updateMcu(byte[] bArr, int i) throws RemoteException;

    void writePcmDevice(byte[] bArr, int i) throws RemoteException;

    void writeTTyDevice(byte[] bArr, int i) throws RemoteException;

    static IAguiExtModule asInterface(IHwBinder iHwBinder) {
        if (iHwBinder == null) {
            return null;
        }
        IHwInterface queryLocalInterface = iHwBinder.queryLocalInterface("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
        if (queryLocalInterface != null && (queryLocalInterface instanceof IAguiExtModule)) {
            return (IAguiExtModule) queryLocalInterface;
        }
        Proxy proxy = new Proxy(iHwBinder);
        try {
            Iterator<String> it = proxy.interfaceChain().iterator();
            while (it.hasNext()) {
                if (it.next().equals("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule")) {
                    return proxy;
                }
            }
        } catch (RemoteException unused) {
        }
        return null;
    }

    static IAguiExtModule getService(String str) throws RemoteException {
        return asInterface(HwBinder.getService("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule", str));
    }

    static IAguiExtModule getService() throws RemoteException {
        return getService("default");
    }

    /* loaded from: classes.dex */
    public static final class Proxy implements IAguiExtModule {
        private IHwBinder mRemote;

        public Proxy(IHwBinder iHwBinder) {
            Objects.requireNonNull(iHwBinder);
            this.mRemote = iHwBinder;
        }

        public IHwBinder asBinder() {
            return this.mRemote;
        }

        public String toString() {
            try {
                return interfaceDescriptor() + "@Proxy";
            } catch (RemoteException unused) {
                return "[class or subclass of vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule]@Proxy";
            }
        }

        public final boolean equals(Object obj) {
            return HidlSupport.interfacesEqual(this, obj);
        }

        public final int hashCode() {
            return asBinder().hashCode();
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int startMcu() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(1, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int stopMcu() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(2, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int updateMcu(byte[] bArr, int i) throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwBlob hwBlob = new HwBlob(512);
            if (bArr == null || bArr.length != 512) {
                throw new IllegalArgumentException("Array element is not of the expected length");
            }
            hwBlob.putInt8Array(0L, bArr);
            hwParcel.writeBuffer(hwBlob);
            hwParcel.writeInt32(i);
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(3, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public void startPtt() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(4, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public void stopPtt() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(5, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public void readTTyDevice(IAguiExtModuleReadCallback iAguiExtModuleReadCallback) throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            hwParcel.writeStrongBinder(iAguiExtModuleReadCallback == null ? null : iAguiExtModuleReadCallback.asBinder());
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(6, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public void writeTTyDevice(byte[] bArr, int i) throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwBlob hwBlob = new HwBlob(1920);
            if (bArr == null || bArr.length != 1920) {
                throw new IllegalArgumentException("Array element is not of the expected length");
            }
            hwBlob.putInt8Array(0L, bArr);
            hwParcel.writeBuffer(hwBlob);
            hwParcel.writeInt32(i);
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(7, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int updateDmr(byte[] bArr, int i) throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwBlob hwBlob = new HwBlob(512);
            if (bArr == null || bArr.length != 512) {
                throw new IllegalArgumentException("Array element is not of the expected length");
            }
            hwBlob.putInt8Array(0L, bArr);
            hwParcel.writeBuffer(hwBlob);
            hwParcel.writeInt32(i);
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(8, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int detectAudioinState() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(9, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int openPcmOut() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(10, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int closePcmOut() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(11, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int openPcmIn() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(12, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public int closePcmIn() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(13, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readInt32();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public void readPcmDevice(IAguiExtModuleReadCallback iAguiExtModuleReadCallback) throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            hwParcel.writeStrongBinder(iAguiExtModuleReadCallback == null ? null : iAguiExtModuleReadCallback.asBinder());
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(14, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public void writePcmDevice(byte[] bArr, int i) throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("vendor.mediatek.hardware.aguiextmodule@1.0::IAguiExtModule");
            HwBlob hwBlob = new HwBlob(1920);
            if (bArr == null || bArr.length != 1920) {
                throw new IllegalArgumentException("Array element is not of the expected length");
            }
            hwBlob.putInt8Array(0L, bArr);
            hwParcel.writeBuffer(hwBlob);
            hwParcel.writeInt32(i);
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(15, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
            } finally {
                hwParcel2.release();
            }
        }

        @Override // vendor.mediatek.hardware.aguiextmodule.V1_0.IAguiExtModule
        public ArrayList<String> interfaceChain() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("android.hidl.base@1.0::IBase");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(256067662, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readStringVector();
            } finally {
                hwParcel2.release();
            }
        }

        public String interfaceDescriptor() throws RemoteException {
            HwParcel hwParcel = new HwParcel();
            hwParcel.writeInterfaceToken("android.hidl.base@1.0::IBase");
            HwParcel hwParcel2 = new HwParcel();
            try {
                this.mRemote.transact(256136003, hwParcel, hwParcel2, 0);
                hwParcel2.verifySuccess();
                hwParcel.releaseTemporaryStorage();
                return hwParcel2.readString();
            } finally {
                hwParcel2.release();
            }
        }
    }
}
