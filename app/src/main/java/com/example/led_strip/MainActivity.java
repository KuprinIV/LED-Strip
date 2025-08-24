package com.example.led_strip;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static com.example.led_strip.LED_ControlActivity.EXTRAS_DEVICE_ADDRESS;
import static com.example.led_strip.LED_ControlActivity.EXTRAS_HAS_SAVED_DEVICE_ADDRESS;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_CODE_PERMISSION = 1;
    private static final int START_LOCATION_SETTINGS_CODE = 2;
    private static final int REQUEST_CODE_LED_CONTROL= 3;
    private static final long SCAN_PERIOD = 20000;

    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothAdapter bluetoothAdapter;
    private boolean mScanning;
    private Handler handler = new Handler();
    ArrayAdapter<String> leDeviceListAdapter;
    ArrayList<String> bleNamesList = new ArrayList<String>();

    private Button startDiscoveryBtn;
    private ListView bleListView;
    private boolean isAccessFineLocationGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // check BLE support
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getResources().getString(R.string.ble_not_support_message));
            builder.setPositiveButton(getResources().getString(R.string.ok_button), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            builder.setCancelable(true);
            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }

        // enable Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter.isEnabled()){
            String deviceAddress = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).getString(EXTRAS_DEVICE_ADDRESS, "");
            if(!deviceAddress.isEmpty()) {
                // if device address was saved in shared preferences, start led control activity immediately
                final Intent intent = new Intent(getApplicationContext(), LED_ControlActivity.class);
                intent.putExtra(EXTRAS_DEVICE_ADDRESS, deviceAddress);
                startActivityForResult(intent, REQUEST_CODE_LED_CONTROL);
            }
        }else{
            setBluetoothEnabled(true);
        }

        // check ACCESS_FINE_LOCATION permission
        isAccessFineLocationGranted = isPermissionGranted(ACCESS_FINE_LOCATION);

        startDiscoveryBtn = findViewById(R.id.startDiscoveryBtn);
        startDiscoveryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mScanning) {
                    if (isAccessFineLocationGranted) {
                        if (isLocationEnabled()) {
                            scanLeDevice();
                        } else {
                            requestUserLocationSettings();
                        }
                    } else {
                        requestPermissions();
                    }
                }else{
                    stopLeScan();
                }
            }
        });

        bleListView = findViewById(R.id.bleDevicesList);
        leDeviceListAdapter = new ArrayAdapter<>(getApplicationContext(),R.layout.list_item, bleNamesList);
        bleListView.setAdapter(leDeviceListAdapter);

        bleListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                // try to connect
                String info = ((TextView)view).getText().toString();
                String deviceAddress = info.substring(info.length() - 17);
                String deviceName = info.substring(0,info.length() - 18);

                if(deviceName.contains("LED_Strip_BT")) {
                    // stop discovery
                    stopLeScan();
                    // save device address in shared preferences
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString(EXTRAS_DEVICE_ADDRESS, deviceAddress).apply();
                    // start led control activity
                    final Intent intent = new Intent(getApplicationContext(), LED_ControlActivity.class);
                    intent.putExtra(EXTRAS_DEVICE_ADDRESS, deviceAddress);
                    startActivityForResult(intent, REQUEST_CODE_LED_CONTROL);
                }else{
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.wrong_device_selected_message), Toast.LENGTH_LONG).show();
                }

            }
        });
    }

    private void setBluetoothEnabled(boolean isEnable){
        // get Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if(bluetoothAdapter == null) return;
        // Ensures Bluetooth is available on the device and it is enabled. If not,
// displays a dialog requesting user permission to enable Bluetooth.
        if (!bluetoothAdapter.isEnabled()) {
            if(isEnable) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }else{
                bluetoothAdapter.disable();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        String deviceAddress = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).getString(EXTRAS_DEVICE_ADDRESS, "");

        if(requestCode == REQUEST_ENABLE_BT && !deviceAddress.isEmpty()) {
            // if device address was saved in shared preferences, start led control activity immediately
            final Intent intent = new Intent(getApplicationContext(), LED_ControlActivity.class);
            intent.putExtra(EXTRAS_DEVICE_ADDRESS, deviceAddress);
            startActivityForResult(intent, REQUEST_CODE_LED_CONTROL);
        }

        if(requestCode == REQUEST_ENABLE_BT && isAccessFineLocationGranted && deviceAddress.isEmpty()){
            if(isLocationEnabled()) {
                scanLeDevice();
            }else{
                requestUserLocationSettings();
            }
        }
        if(requestCode == START_LOCATION_SETTINGS_CODE) {
            if (isLocationEnabled()) {
                scanLeDevice();
            } else {
                Toast.makeText(this, getResources().getString(R.string.request_location_settings_message), Toast.LENGTH_LONG).show();
            }
        }
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        if(requestCode == REQUEST_CODE_LED_CONTROL){
            if(data != null) {
                if (data.getBooleanExtra(EXTRAS_HAS_SAVED_DEVICE_ADDRESS, false)) {
                    finish();
                }
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    Timer mTimer;
    stopDiscoveryTimerTask stopDiscoveryTask;

    private void scanLeDevice() {
        if(bluetoothAdapter != null) {
            if(!bluetoothAdapter.isEnabled()){ // enable Bluetooth, if it not enabled
                setBluetoothEnabled(true);
                return;
            }
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }else{
            return;
        }

        if (!mScanning) {
            // clear device list
            leDeviceListAdapter.clear();
            leDeviceListAdapter.notifyDataSetChanged();
            startDiscoveryBtn.setText(getResources().getString(R.string.stop_discovery_button));

            mScanning = true;
            bluetoothLeScanner.startScan(leScanCallback);

            // Stops scanning after a pre-defined scan period.
            mTimer = new Timer();
            stopDiscoveryTask = new stopDiscoveryTimerTask();
            mTimer.schedule(stopDiscoveryTask, SCAN_PERIOD);
        }
    }

    private void stopLeScan(){
        if(mScanning){
            mScanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    startDiscoveryBtn.setText(getResources().getString(R.string.start_discovery_button));
                    Toast.makeText(getApplicationContext(), getResources().getString(R.string.discovery_end_message), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private class stopDiscoveryTimerTask extends TimerTask {
        @Override
        public void run() {
            stopLeScan();
            stopDiscoveryTask.cancel();
            mTimer.cancel();
            mTimer.purge();
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if(!bleNamesList.contains(result.getDevice().getName() + " " + result.getDevice().getAddress())) {
                        bleNamesList.add(result.getDevice().getName() + " " + result.getDevice().getAddress());
                        leDeviceListAdapter.notifyDataSetChanged();
                    }
                }
            };

    private void requestPermissions(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{ACCESS_FINE_LOCATION}, REQUEST_CODE_PERMISSION);
        }
    }

    public boolean isPermissionGranted(String permission) {
        // For Android < Android M, self permissions are always granted.
        boolean result = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // targetSdkVersion >= Android M, we can
            // use Context#checkSelfPermission
            result = (this.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
        }
        return result;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission granted
                    isAccessFineLocationGranted = true;
                    startDiscoveryBtn.setText(getResources().getString(R.string.stop_discovery_button));
                    if(isLocationEnabled()) {
                        scanLeDevice();
                    }else{
                        requestUserLocationSettings();
                    }
                }else{
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(getResources().getString(R.string.check_permission_warning));
                    builder.setPositiveButton(getResources().getString(R.string.retry_button), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions();
                        }
                    });
                    builder.setCancelable(true);
                    builder.setNegativeButton(getResources().getString(R.string.exit_button), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            finish();
                        }
                    });
                    final AlertDialog dialog = builder.create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                }
                break;
            default:
                return;
        }
    }

    private boolean isLocationEnabled(){
        boolean locationEnabled = true;

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
            locationEnabled = lm.isLocationEnabled();
        }
        return locationEnabled;
    }

    private void requestUserLocationSettings(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        // Setting Dialog Title
        alertDialog.setTitle(getResources().getString(R.string.request_location_settings_dialog_name));
        // Setting Dialog Message
        alertDialog.setMessage(getResources().getString(R.string.request_location_settings_message));
        // On pressing Settings button
        alertDialog.setPositiveButton(
                getResources().getString(R.string.ok_button),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivityForResult(intent, START_LOCATION_SETTINGS_CODE);
                    }
                });
        alertDialog.setNegativeButton(getResources().getString(R.string.cancel_button),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        alertDialog.show();
    }
}
