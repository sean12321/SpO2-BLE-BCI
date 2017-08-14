package com.berry_med.main;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.berry_med.dataparser.DataParser;
import com.berry_med.dataparser.PackageParser;
import com.berry_med.waveform.WaveForm;
import com.berry_med.waveform.WaveFormParams;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PackageParser.OnDataChangeListener, BluetoothLeService.RecvListener {

    private final String TAG = this.getClass().getSimpleName();

    private final String CYPRESS_BLE_NAME_PREFIX = "00:A0:50";
    private static final long SCAN_PERIOD = 5000;

    private LinearLayout rlInfoBtns;
    private LinearLayout llModifyBtName;
    private Button btnBluetoothToggle;
    private Button btnSearchOximeters;
    private Button btnNotify;
    private Button btnUpdate;
    private Button btnVerInfo;
    private TextView tvStatusBar;
    private TextView tvParamsBar;
    private EditText edBluetoothName;
    private EditText edNewBtName;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mTargetDevice;
    private BluetoothLeService mBLEService;

    private BluetoothGattCharacteristic chSend;
    private BluetoothGattCharacteristic chReceive;
    private BluetoothGattCharacteristic chChangeBtName;
    private BluetoothGattCharacteristic chUpdate;


    private Handler mScanHandler;
    private boolean mIsScanning;
    private boolean mIsDeviceFound;
    private boolean mIsConnected;
    private boolean mIsNotified;

    private DataParser mDataParser;
    private PackageParser mPackageParser;
    private WaveForm mSpO2WaveDraw;

    private String strBluetoothName = "BerryMed"; // "Dual-SPP", "iFeel Labs", "iHeart", "BerryMed";
    private int oldVal;
    private List<String> list;
    private ArrayAdapter<String> adapter;

    private String LoadBluetoothName() {
        String str;
        Context ctx = MainActivity.this;
        SharedPreferences sp = ctx.getSharedPreferences("BT", MODE_PRIVATE);
        str = sp.getString("BluetoothName", strBluetoothName);
        return str;
    }

    private void SaveBluetoothName(String newName) {
        Context ctx = MainActivity.this;
        SharedPreferences sp = ctx.getSharedPreferences("BT", MODE_PRIVATE);
        Editor editor = sp.edit();
        editor.putString("BluetoothName", newName);
        editor.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        edBluetoothName = (EditText) findViewById(R.id.edBluetoothName);
        strBluetoothName = LoadBluetoothName();
        edBluetoothName.setText(strBluetoothName);
        edBluetoothName.setSelection(strBluetoothName.length());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        btnBluetoothToggle = (Button) findViewById(R.id.btnBluetoothToggle);
        btnSearchOximeters = (Button) findViewById(R.id.btnSearchOximeters);
        btnNotify = (Button) findViewById(R.id.btnNotify);
        btnUpdate = (Button) findViewById(R.id.btnUpdate);
        btnVerInfo = (Button) findViewById(R.id.btnVerInfo);
        tvStatusBar = (TextView) findViewById(R.id.tvStatusBar);
        tvParamsBar = (TextView) findViewById(R.id.tvParamsBar);
        rlInfoBtns = (LinearLayout) findViewById(R.id.rlInfoBtns);
        llModifyBtName = (LinearLayout) findViewById(R.id.llModifyBtName);
        edNewBtName = (EditText) findViewById(R.id.etNewBtName);

        rlInfoBtns.setVisibility(View.GONE);
        btnUpdate.setEnabled(false);
        btnVerInfo.setEnabled(false);

        //init bluetooth adapter
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (mBluetoothAdapter.isEnabled()) {
            btnBluetoothToggle.setText(getString(R.string.turn_off_bluetooth));
            btnSearchOximeters.setEnabled(true);
        } else {
            btnBluetoothToggle.setText(getString(R.string.turn_on_bluetooth));
            btnSearchOximeters.setEnabled(false);
        }

        SurfaceView sfvSpO2 = (SurfaceView) findViewById(R.id.sfvSpO2);
        TextView tvGetSource = (TextView) findViewById(R.id.tvGetSource);
        tvGetSource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Const.GITHUB_SITE)));
            }
        });

        //******************************** package parse******************************
        WaveFormParams mSpO2WaveParas;
        mSpO2WaveParas = new WaveFormParams(2, 3, new int[]{0, 255});
        mSpO2WaveDraw = new WaveForm(this, sfvSpO2, mSpO2WaveParas);
        mDataParser = new DataParser(new DataParser.onPackageReceivedListener() {
            @Override
            public void onPackageReceived(int[] pkgData) {
                if (mPackageParser == null) {
                    mPackageParser = new PackageParser(MainActivity.this);
                }
                mPackageParser.parse(pkgData);
            }
        });
        mDataParser.start();
        //*******************************************************************************

        mScanHandler = new Handler();
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case Const.MESSAGE_OXIMETER_PARAMS:
                    tvParamsBar.setText("SpO2: " + msg.arg1 + "   Pulse Rate:" + msg.arg2);
                    break;
                case Const.MESSAGE_OXIMETER_WAVE:
                    mSpO2WaveDraw.add(msg.arg1);
                    break;
                case Const.MESSAGE_OXIMETER_VERINFO:
                    Toast.makeText(MainActivity.this,
                            mPackageParser.mSWVer + "\n" + mPackageParser.mHWVer + "\n" + mPackageParser.mBLEVer,
                            Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBLEService != null) {
            final boolean result = mBLEService.connect(mTargetDevice.getAddress());
            Log.i(TAG, "Connect request result=" + result);
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDataParser.stop();
        if (mBLEService != null) unbindService(mServiceConnection);
        mBLEService = null;
    }


    @SuppressWarnings("deprecation")
    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mScanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.i(TAG, "BLE scan is finished.");
                    if (mIsScanning) {
                        Log.i(TAG, "stop ble scan.");
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        mIsScanning = false;
                    }
                    if (mIsConnected) {
                        btnSearchOximeters.setText(R.string.Disconnect);
                        Log.i(TAG, "Device connect ok.");
                    } else {
                        btnSearchOximeters.setText(R.string.search_oximeters);
                    }
                    if (!mIsDeviceFound) {
                        tvStatusBar.setText("No devices found.");
                        Log.i(TAG, "No devices found.");
                    }
                    btnSearchOximeters.setEnabled(true);
                }
            }, SCAN_PERIOD);

            mBluetoothAdapter.startLeScan(mLeScanCallback);
            mIsScanning = true;
            btnSearchOximeters.setEnabled(false);
            tvStatusBar.setText("Searching...");
            Log.i(TAG, "BLE scan is starting...");
        } else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            mIsScanning = false;
            Log.i(TAG, "BLE scan is stopped.");
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, final byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "run onLeSan...");
                            if (device.getName() != null) {
                                if (!mIsDeviceFound && device.getName().equals(strBluetoothName) &&
                                        device.getAddress().startsWith(CYPRESS_BLE_NAME_PREFIX)) {
                                    mTargetDevice = device;
                                    mIsDeviceFound = true;
                                    tvStatusBar.setText("Name:" + device.getName() + "     " + "Mac:" + device.getAddress());
                                    //tvStatusBar.setText("Name:"+device.getName()+"     "+"Mac:"+ Arrays.toString(scanRecord)); //wdl, just for debug

                                    //start BluetoothLeService
                                    Intent gattServiceIntent = new Intent(MainActivity.this, BluetoothLeService.class);
                                    bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                                    scanLeDevice(false);
                                }
                            }
                        }
                    });
                }
            };


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBLEService = ((BluetoothLeService.LocalBinder) service).getService();
            mBLEService.setRecvListener(MainActivity.this);

            if (!mBLEService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                //finish();
            } else {
                // Automatically connects to the device upon successful start-up initialization.
                mBLEService.connect(mTargetDevice.getAddress());
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.i(TAG, "onServiceDisconnected: service disconnected.");
            mBLEService = null;
        }
    };


    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.i(TAG, "onReceive: action = " + action);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Device connect ok.");
                if (mIsScanning) {
                    scanLeDevice(false);
                }
                btnSearchOximeters.setText(R.string.Disconnect);
                btnSearchOximeters.setEnabled(true);
                mIsConnected = true;
                rlInfoBtns.setVisibility(View.VISIBLE);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(MainActivity.this, "Disconnected", Toast.LENGTH_SHORT).show();
                btnSearchOximeters.setText(R.string.search_oximeters);
                mIsConnected = false;
                rlInfoBtns.setVisibility(View.GONE);
                tvStatusBar.setText("____");
                if (mBLEService != null) {
                    unbindService(mServiceConnection);
                    mBLEService = null;
                }
                chReceive = null;
                chSend = null;
                chChangeBtName = null;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                initCharacteristic();

            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                Toast.makeText(MainActivity.this,
                        intent.getStringExtra(BluetoothLeService.EXTRA_DATA),
                        Toast.LENGTH_SHORT).show();
            } else if (BluetoothLeService.ACTION_SPO2_DATA_AVAILABLE.equals(action)) {
                mDataParser.add(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_SPO2_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btnBluetoothToggle:
                if (btnBluetoothToggle.getText().toString().equals(getString(R.string.turn_on_bluetooth))) {
                    btnBluetoothToggle.setText(R.string.turn_off_bluetooth);
                    //turn on bluetooth
                    if (!mBluetoothAdapter.isEnabled()) {
                        mBluetoothAdapter.enable();
                    }
                    btnSearchOximeters.setEnabled(true);
                    edBluetoothName.setFocusable(false);
                    edBluetoothName.setFocusableInTouchMode(false);
                } else {
                    btnBluetoothToggle.setText(R.string.turn_on_bluetooth);
                    //turn off bluetooth
                    if (mBluetoothAdapter.isEnabled()) {
                        mBluetoothAdapter.disable();
                    }
                    btnSearchOximeters.setEnabled(false);
                    edBluetoothName.setFocusableInTouchMode(true);
                    edBluetoothName.setFocusable(true);
                    edBluetoothName.requestFocus();
                }
                break;

            case R.id.btnSearchOximeters:
                if (mIsConnected) {
                    mIsConnected = false;
                    mIsNotified = false;
                    if (mBLEService != null) {
                        Log.i(TAG, "onClick: unbind service.");
                        unbindService(mServiceConnection);
                        mBLEService = null;
                    }
                    btnSearchOximeters.setText(R.string.search_oximeters);
                    rlInfoBtns.setVisibility(View.GONE);
                    btnVerInfo.setEnabled(false);
                    tvStatusBar.setText("____");
                    Log.i(TAG, String.format("onClick: device is disconnected."));
                } else {
                    Log.i(TAG, "onClick: start searching...");
                    if (mIsScanning) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        mIsScanning = false;
                    }
                    mIsDeviceFound = false;
                    scanLeDevice(true);
                }
                strBluetoothName = edBluetoothName.getText().toString();
                SaveBluetoothName(strBluetoothName);
                break;
            case R.id.btnNotify:
                if (chReceive != null) {
                    if (!mIsNotified) {
                        mBLEService.setCharacteristicNotification(chReceive, true);
                        mIsNotified = true;
                        btnNotify.setText(R.string.turn_off_notify);
                        btnVerInfo.setEnabled(true);
                        Log.i(TAG, ">>>>>>>>>>>>>>>>>>>>START<<<<<<<<<<<<<<<<<<<");
                    } else {
                        mBLEService.setCharacteristicNotification(chReceive, false);
                        mIsNotified = false;
                        btnNotify.setText(R.string.turn_on_notify);
                        btnVerInfo.setEnabled(false);
                        Log.i(TAG, ">>>>>>>>>>>>>>>>>>>>STOP<<<<<<<<<<<<<<<<<<<");
                    }
                }
                break;
            case R.id.btnModifyBtName:
                String btName = edNewBtName.getText().toString();
                if (btName.length() == 0) {
                    Toast.makeText(MainActivity.this, "new name is empty!", Toast.LENGTH_SHORT).show();
                } else if (btName.length() > 26) {
                    Toast.makeText(MainActivity.this, "new name length is too long!", Toast.LENGTH_SHORT).show();
                } else {
                    PackageParser.modifyBluetoothName(mBLEService, chChangeBtName, btName);
                    Log.i(TAG, "btName = " + btName);
                    edBluetoothName.setText(btName);
                }
                break;
            case R.id.btnUpdate:
                PackageParser.UpdateBluetoothFirmware(mBLEService, chUpdate);
                Log.i(TAG, "Update bluetooth firmware.");
                break;
            case R.id.btnVerInfo:
                PackageParser.GetVersionInfo(mBLEService, chSend);
                break;
        }
    }

    public void initCharacteristic() {
        List<BluetoothGattService> services = mBLEService.getSupportedGattServices();
        BluetoothGattService mInfoService = null;
        BluetoothGattService mDataService = null;
        for (BluetoothGattService service : services) {
            if (service.getUuid().equals(Const.UUID_SERVICE_DATA)) {
                mDataService = service;
            }
        }
        if (mDataService != null) {
            List<BluetoothGattCharacteristic> characteristics =
                    mDataService.getCharacteristics();
            for (BluetoothGattCharacteristic ch : characteristics) {
                if (ch.getUuid().equals(Const.UUID_CHARACTER_RECEIVE)) {
                    chReceive = ch;
                } else if (ch.getUuid().equals(Const.UUID_CHARACTER_SEND)) {
                    chSend = ch;
                } else if (ch.getUuid().equals(Const.UUID_MODIFY_BT_NAME)) {
                    chChangeBtName = ch;
                    llModifyBtName.setVisibility(View.VISIBLE);
                } else if (ch.getUuid().equals(Const.UUID_UPDATE)) {
                    chUpdate = ch;
                    btnUpdate.setEnabled(true);
                }
            }
        }
    }

    @Override
    public void onSpO2ParamsChanged() {
        PackageParser.OxiParams params = mPackageParser.getOxiParams();
        mHandler.obtainMessage(Const.MESSAGE_OXIMETER_PARAMS, params.getSpo2(), params.getPulseRate()).sendToTarget();
    }

    @Override
    public void onSpO2WaveChanged(int wave) {
        mHandler.obtainMessage(Const.MESSAGE_OXIMETER_WAVE, wave, 0).sendToTarget();
    }

    @Override
    public void onDataReceived(byte[] dat) {
        mDataParser.add(dat);
    }

    @Override
    public void onGetVerInfo() {
        mHandler.obtainMessage(Const.MESSAGE_OXIMETER_VERINFO, 0, 0).sendToTarget();
    }
}
