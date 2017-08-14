package com.berry_med.dataparser;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;

import com.berry_med.main.BluetoothLeService;

import java.util.Arrays;

/**
 * Created by ZXX on 2015/8/31.
 * <p>
 * Add all data from oximeter into a Queue, and then Parsing the data as the protocol manual.
 * If you want more details about the protocol, click the link below.
 * <p>
 * https://github.com/zh2x/BCI_Protocol_Demo/tree/master/protocol_manual
 */
public class PackageParser {
    private final String TAG = this.getClass().getName();

    private OxiParams mOxiParams;
    private OnDataChangeListener mOnDataChangeListener;
    private int pkgIndexOld;
    private int verIndex;
    private String strVer = new String();
    public String mSWVer = new String();
    public String mHWVer = new String();
    public String mBLEVer = new String();

    public PackageParser(OnDataChangeListener onDataChangeListener) {
        this.mOnDataChangeListener = onDataChangeListener;
        mOxiParams = new OxiParams();
    }

    private boolean parseVerInfo(int[] pkgData) {
        boolean isPkgVerInfo = false;

        if (pkgData[0] == 0xff) {
        	isPkgVerInfo = true;
            Log.i(TAG, "SWVer pkgData: " + Arrays.toString(pkgData));
            for (int i = 1; i < 5; i++) {
                if (pkgData[i] != 0) {
                    strVer += (char) pkgData[i];
                }
            }
            if (++verIndex == 3) {
                mSWVer = "SW: " + strVer;
                strVer = "";
                verIndex = 0;
                Log.i(TAG, "mSWVer: " + mSWVer);
                mOnDataChangeListener.onGetVerInfo();
            }
        } else if (pkgData[0] == 0xfe) {
        	isPkgVerInfo = true;
            Log.i(TAG, "HWVer pkgData: " + Arrays.toString(pkgData));
            for (int i = 1; i < 5; i++) {
                if (pkgData[i] != 0) {
                    strVer += (char) pkgData[i];
                }
            }
            if (++verIndex == 1) {
                mHWVer = "HW: " + strVer;
                strVer = "";
                verIndex = 0;
                Log.i(TAG, "mHWVer: " + mHWVer);
                mOnDataChangeListener.onGetVerInfo();
            }

        } else if (pkgData[0] == 0xfd) {
        	isPkgVerInfo = true;
            Log.i(TAG, "BLEVer pkgData: " + Arrays.toString(pkgData));
            for (int i = 1; i < 5; i++) {
                if (pkgData[i] != 0) {
                    strVer += (char) pkgData[i];
                }
            }
            if (++verIndex == 3) {
                mBLEVer = "BLE: " + strVer;
                strVer = "";
                verIndex = 0;
                Log.i(TAG, "mBLEVer: " + mBLEVer);
                mOnDataChangeListener.onGetVerInfo();
            }
        }
        return isPkgVerInfo;
    }

    public void parse(int[] pkgData) {

        int spo2, pulseRate, pi, wave;
        int pkgIndex = 0;
        int checkSum = 0;

        if (parseVerInfo(pkgData)) {
            return;
        }

        //Log.i(TAG, "pkgData: " + Arrays.toString(pkgData));
        spo2 = pkgData[4];
        pulseRate = pkgData[3] | ((pkgData[2] & 0x40) << 1);
        pi = pkgData[0] & 0x0f;
        wave = pkgData[1];

        // validation of pkgIndex
        pkgIndex = pkgData[1];
        if (pkgIndex - pkgIndexOld != 1 && pkgIndexOld != 100) {
            //Log.i(TAG, String.format("pkgIndex error, pkgIndex = %d, pkgIndexOld = %d", pkgIndex, pkgIndexOld));
        }
        pkgIndexOld = pkgIndex;

        //Log.i(TAG, String.format("spo2 = %d, pr = %d, wave = %d", spo2, pulseRate, wave));

        if (spo2 != mOxiParams.spo2 || pulseRate != mOxiParams.pulseRate || pi != mOxiParams.pi) {
            mOxiParams.update(spo2, pulseRate, pi);
            mOnDataChangeListener.onSpO2ParamsChanged();
        }
        mOnDataChangeListener.onSpO2WaveChanged(wave);
    }

    /**
     * interface for parameters changed.
     */
    public interface OnDataChangeListener {
        void onSpO2ParamsChanged();

        void onSpO2WaveChanged(int wave);

        void onGetVerInfo();
    }


    /**
     * a small collection of Oximeter parameters.
     * you can add more parameters as the manual.
     * <p>
     * spo2          Pulse Oxygen Saturation
     * pulseRate     pulse rate
     * pi            perfusion index
     */
    public class OxiParams {
        private int spo2;           //spo2 saturation, unit: %
        private int pulseRate;      //pulse rate, uint: bpm
        private int pi;             //perfusion index, unit: %

        private void update(int spo2, int pulseRate, int pi) {
            this.spo2 = spo2;
            this.pulseRate = pulseRate;
            this.pi = pi;
        }

        public int getSpo2() {
            return spo2;
        }

        public int getPulseRate() {
            return pulseRate;
        }

        public int getPi() {
            return pi;
        }
    }

    public OxiParams getOxiParams() {
        return mOxiParams;
    }

    /**
     * Modify the Bluetooth Name On the Air.
     *
     * @param service service of BluetoothLeService
     * @param ch      characteristic of Modify Bluetooth Name
     *                if this characteristic not found, the function
     *                of modify not support.
     * @param btName  length of btName should not more than 26 bytes.
     *                the bytes more then 26 bytes will be ignored.
     */
    public static void modifyBluetoothName(BluetoothLeService service,
                                           BluetoothGattCharacteristic ch,
                                           String btName) {
        if (service == null || ch == null)
            return;

        byte[] b = btName.getBytes();
        byte[] bytes = new byte[b.length + 2];
        bytes[0] = 0x00;
        bytes[1] = (byte) b.length;
        System.arraycopy(b, 0, bytes, 2, b.length);

        service.write(ch, bytes);
    }

    /**
     * Update the Bluetooth firmware On the Air.
     *
     * @param service service of BluetoothLeService
     * @param ch      characteristic of Update Bluetooth firmware
     *                if this characteristic not found, the function
     *                of update not support.
     */
    public static void UpdateBluetoothFirmware(BluetoothLeService service, BluetoothGattCharacteristic ch) {
        if (service == null || ch == null)
            return;

        byte[] bytes = new byte[1];
        bytes[0] = 0x01;

        service.write(ch, bytes);
    }

    /**
     * Get Version Information On the Air.
     *
     * @param service service of BluetoothLeService
     * @param ch      characteristic of send to
     *                if this characteristic not found, the function
     *                of send to is not supported.
     */
    public static void GetVersionInfo(BluetoothLeService service, BluetoothGattCharacteristic ch) {
        if (service == null || ch == null)
            return;

        byte[] bytes = new byte[1];
        bytes[0] = (byte) 0xff;
        service.write(ch, bytes);
        bytes[0] = (byte) 0xfe;
        service.write(ch, bytes);
        bytes[0] = (byte) 0xfd;
        service.write(ch, bytes);
    }

    /**
     * Change the Bluetooth PacketRate On the Air.
     *
     * @param service service of BluetoothLeService
     * @param ch      characteristic of Change Bluetooth PacketRate
     *                if this characteristic not found, the function
     *                of change not support.
     * @param cmd     cmd of the Change Bluetooth PacketRate.
     *                0xf4 = 500sps, 0xf3 = 250sps, 0xf2 = 125sps, 0xf1 = 100sps, 0xf0 = 50sps.
     */
    public static void changeBluetoothPacketRate(BluetoothLeService service,
                                                 BluetoothGattCharacteristic ch,
                                                 byte cmd) {
        if (service == null || ch == null)
            return;

        byte[] bytes = new byte[1];
        bytes[0] = cmd;

        service.write(ch, bytes);
    }
}
