package com.example.led_strip;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.app.Application;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class LED_ControlActivity extends AppCompatActivity {
    private static final String TAG = "LED_ControlActivity";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String EXTRAS_HAS_SAVED_DEVICE_ADDRESS = "HAS_SAVED_DEVICE_ADDRESS";
    public static final String bleServiceUUIDString = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String bleCharacteristicUUIDString = "0000ffe1-0000-1000-8000-00805f9b34fb";

    private enum ColorScale {RGB, HSV};

    BluetoothLeService mBluetoothLeService;
    BluetoothGattCharacteristic mWriteGattCharacteristic = null;
    BluetoothGattCharacteristic mReadGattCharacteristic = null;
    private String mDeviceAddress;
    private boolean isConnected = false;

    private int currentColorIndex = 0;
    private int currentEffectIndex = 0;

    // define effects parameters
    private final effectsParameters[] effects = {
            new effectsParameters((byte)0, new int[]{Color.BLACK}, (byte)0, (byte)0, (byte)0), // switch off strip
            new effectsParameters((byte)1, new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE}, (byte)0, (byte)0, (byte)0), // four colors blinking effect
            new effectsParameters((byte)2, new int[]{Color.RED, Color.YELLOW}, (byte)0, (byte)0, (byte)1), // two colors blinking effect
            new effectsParameters((byte)3, null, (byte)255, (byte)150, (byte)0), // smooth strip color changing effect
            new effectsParameters((byte)4, new int[]{Color.WHITE}, (byte)0, (byte)0, (byte)0), // constant strip color effect
            new effectsParameters((byte)5, new int[]{Color.WHITE}, (byte)0, (byte)0, (byte)0), // blinking strip color effect
            new effectsParameters((byte)6, new int[]{Color.WHITE}, (byte)0, (byte)0, (byte)15) , // shifting color part of strip effect
            new effectsParameters((byte)7, null, (byte)255, (byte)150, (byte)0) // rainbow
    };

    // effect 1 data
    private final int[] effect1_indicators = new int[]{R.id.shapeCircle11, R.id.shapeCircle12, R.id.shapeCircle13, R.id.shapeCircle14};
    private final int[] effect1_radioButtons = new int[]{R.id.color1RB1, R.id.color2RB1, R.id.color3RB1, R.id.color4RB1};
    // effect 2 data
    private final int[] effect2_indicators = new int[]{R.id.shapeCircle21, R.id.shapeCircle22};
    private final int[] effect2_radioButtons = new int[]{R.id.color1RB2, R.id.color2RB2};
    // effect 3 hasn't data
    // effect 4 data
    private final int[] effect4_indicators = new int[]{R.id.shapeCircle41};
    // effect 5 data
    private final int[] effect5_indicators = new int[]{R.id.shapeCircle51};
    // effect 6 data
    private final int[] effect6_indicators = new int[]{R.id.shapeCircle61};

    private int[] currentIndicators;

    private final byte[] commandData = new byte[19];

    private final Queue<byte[]> mCommandQueue  = new LinkedList<>();

    private ColorScale currentColorScale = ColorScale.RGB;
    private int h, s, v;


    private TextView connectionStateTextView;
    private Button connectCtrlButton;
    private SeekBar redColorBar;
    private SeekBar greenColorBar;
    private SeekBar blueColorBar;
    private Spinner effectsSpinner;
    private LinearLayout settingsLayout;
    private LinearLayout fourColorsBlinkingLayout;
    private LinearLayout twoColorsBlinkingLayout;
    private LinearLayout hsvColorLayout;
    private LinearLayout constantLayout;
    private LinearLayout blinkingLayout;
    private LinearLayout shiftingLayout;
    private LinearLayout rainbowLayout;
    private LinearLayout setColorLayout;
    private RadioGroup colorsRB1Group;
    private RadioGroup colorsRB2Group;
    private Button setColorButton;
    private TextView redColorSeekBarName;
    private TextView greenColorSeekBarName;
    private TextView blueColorSeekBarName;
    private TextView colorCodeTV;

    private void initFourColorsBlinkingLayout(){
        fourColorsBlinkingLayout = findViewById(R.id.four_color_blinking_control_layout);

        for(int i = 0; i < effects[1].colors.length; i++){
            setIndicatorColor(effects[1].colors[i], (ImageView)findViewById(effect1_indicators[i]));
        }

        colorsRB1Group = findViewById(R.id.colorsRB1Group);
        colorsRB1Group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch(i){
                    case R.id.color1RB1:
                    default:
                        currentColorIndex = 0;
                        break;

                    case R.id.color2RB1:
                        currentColorIndex = 1;
                        break;

                    case R.id.color3RB1:
                        currentColorIndex = 2;
                        break;

                    case R.id.color4RB1:
                        currentColorIndex = 3;
                        break;
                }
                setIndicatorColor(effects[1].colors[currentColorIndex], (ImageView)findViewById(effect1_indicators[currentColorIndex]));
                updateColorSeekBars(effects[1].colors[currentColorIndex], currentColorScale);
            }
        });
    }

    private void initTwoColorBlinkingLayout(){
        twoColorsBlinkingLayout = findViewById(R.id.two_color_blinking_control_layout);

        for(int i = 0; i < effects[2].colors.length; i++){
            setIndicatorColor(effects[2].colors[i], (ImageView)findViewById(effect2_indicators[i]));
        }

        colorsRB2Group = findViewById(R.id.colorsRB2Group);
        colorsRB2Group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch(i){
                    case R.id.color1RB2:
                    default:
                        currentColorIndex = 0;
                        break;

                    case R.id.color2RB2:
                        currentColorIndex = 1;
                        break;
                }
                setIndicatorColor(effects[2].colors[currentColorIndex], (ImageView)findViewById(effect2_indicators[currentColorIndex]));
                updateColorSeekBars(effects[2].colors[currentColorIndex], currentColorScale);

            }
        });

        LinearLayout pickerLayout = findViewById(R.id.pickerLayout);
        final EditText pickerValue = pickerLayout.findViewById(R.id.pickerTextValue);
        pickerValue.setText(String.format("%d", effects[2].sectionSize));
        Button plusButton = pickerLayout.findViewById(R.id.plusButton);
        plusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(effects[2].sectionSize < 30){
                    effects[2].sectionSize++;
                }
                pickerValue.setText(String.format("%d", effects[2].sectionSize));
                prepareCommandData();
                sendCommand();
            }
        });

        Button minusButton = pickerLayout.findViewById(R.id.minusButton);
        minusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(effects[2].sectionSize > 1){
                    effects[2].sectionSize--;
                }
                pickerValue.setText(String.format("%d", effects[2].sectionSize));
                prepareCommandData();
                sendCommand();
            }
        });
    }

    private byte[] valuesList = new byte[]{0, 25, 50, 75, 100, 125, (byte)150, (byte)175, (byte)200, (byte)225, (byte)255};
    private int valueIndex = 6;
    private int valueIndexRainbow = 6;

    private void initHSVColorLayout(){
        hsvColorLayout = findViewById(R.id.hsv_color_control);

        LinearLayout pickerLayout = findViewById(R.id.pickerLayoutHSV);
        final EditText pickerValue = pickerLayout.findViewById(R.id.pickerTextValue);
        pickerValue.setText(String.format("%d", valueIndex));
        Button plusButton = pickerLayout.findViewById(R.id.plusButton);
        plusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(valueIndex < 10){
                    valueIndex++;
                    effects[3].value = valuesList[valueIndex];
                }
                pickerValue.setText(String.format("%d", valueIndex));
                prepareCommandData();
                sendCommand();
            }
        });

        Button minusButton = pickerLayout.findViewById(R.id.minusButton);
        minusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(valueIndex > 0){
                    valueIndex--;
                    effects[3].value = valuesList[valueIndex];
                }
                pickerValue.setText(String.format("%d", valueIndex));
                prepareCommandData();
                sendCommand();
            }
        });
    }

    private void initConstantLayout(){
        constantLayout = findViewById(R.id.constant_control_layout);
        for(int i = 0; i < effects[4].colors.length; i++){
            setIndicatorColor(effects[4].colors[i], (ImageView)findViewById(effect4_indicators[i]));
        }
    }

    private void initBlinkingLayout(){
        blinkingLayout = findViewById(R.id.blinking_control_layout);
        for(int i = 0; i < effects[5].colors.length; i++){
            setIndicatorColor(effects[5].colors[i], (ImageView)findViewById(effect5_indicators[i]));
        }
    }

    private void initShiftingLayout(){
        shiftingLayout = findViewById(R.id.shifting_control_layout);
        for(int i = 0; i < effects[6].colors.length; i++){
            setIndicatorColor(effects[6].colors[i], (ImageView)findViewById(effect6_indicators[i]));
        }

        LinearLayout pickerLayout = findViewById(R.id.pickerLayoutShift);
        final EditText pickerValue = pickerLayout.findViewById(R.id.pickerTextValue);
        pickerValue.setText(String.format("%d", effects[6].sectionSize));
        Button plusButton = pickerLayout.findViewById(R.id.plusButton);
        plusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(effects[6].sectionSize < 30){
                    effects[6].sectionSize++;
                }
                pickerValue.setText(String.format("%d", effects[6].sectionSize));
                prepareCommandData();
                sendCommand();
            }
        });

        Button minusButton = pickerLayout.findViewById(R.id.minusButton);
        minusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(effects[6].sectionSize > 1){
                    effects[6].sectionSize--;
                }
                pickerValue.setText(String.format("%d", effects[6].sectionSize));
                prepareCommandData();
                sendCommand();
            }
        });
    }

    private void initRainbowLayout(){
        rainbowLayout = findViewById(R.id.rainbow_control);

        LinearLayout pickerLayout = findViewById(R.id.pickerLayoutRainbow);
        final EditText pickerValue = pickerLayout.findViewById(R.id.pickerTextValue);
        pickerValue.setText(String.format("%d", valueIndexRainbow));
        Button plusButton = pickerLayout.findViewById(R.id.plusButton);
        plusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(valueIndexRainbow < 10){
                    valueIndexRainbow++;
                    effects[7].value = valuesList[valueIndexRainbow];
                }
                pickerValue.setText(String.format("%d", valueIndexRainbow));
                prepareCommandData();
                sendCommand();
            }
        });

        Button minusButton = pickerLayout.findViewById(R.id.minusButton);
        minusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(valueIndexRainbow > 0){
                    valueIndexRainbow--;
                    effects[7].value = valuesList[valueIndexRainbow];
                }
                pickerValue.setText(String.format("%d", valueIndexRainbow));
                prepareCommandData();
                sendCommand();
            }
        });
    }

    private void initColorSetLayout(){
        setColorLayout = findViewById(R.id.setColorLayout);
        RadioGroup colorScaleRBGroup;
        colorScaleRBGroup = findViewById(R.id.color_scaleRBGroup);
        redColorSeekBarName = findViewById(R.id.redColorBarName);
        greenColorSeekBarName = findViewById(R.id.greenColorBarName);
        blueColorSeekBarName = findViewById(R.id.blueColorBarName);

        colorScaleRBGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch(i){
                    case R.id.rgbColorScaleRB:
                    default:
                        currentColorScale = ColorScale.RGB;
                        redColorSeekBarName.setText("R:");
                        greenColorSeekBarName.setText("G:");
                        blueColorSeekBarName.setText("B:");
                        break;

                    case R.id.hsvColorScaleRB:
                        currentColorScale = ColorScale.HSV;
                        redColorSeekBarName.setText("H:");
                        greenColorSeekBarName.setText("S:");
                        blueColorSeekBarName.setText("V:");
                        break;
                }
                updateColorSeekBars(effects[currentEffectIndex].colors[currentColorIndex], currentColorScale);
            }
        });

        redColorBar = findViewById(R.id.redColorBar);
        redColorBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(currentColorScale == ColorScale.RGB) {
                    effects[currentEffectIndex].colors[currentColorIndex] &= 0x00FFFF;
                    effects[currentEffectIndex].colors[currentColorIndex] |= ((i & 0xFF) << 16);
                }else{
                    h = i;
                    effects[currentEffectIndex].colors[currentColorIndex] = HsvToRgb(h, s, v);
                }
                setIndicatorColor(effects[currentEffectIndex].colors[currentColorIndex], (ImageView) findViewById(currentIndicators[currentColorIndex]));
                colorCodeTV.setText(String.format("0x%06X", effects[currentEffectIndex].colors[currentColorIndex] & 0xFFFFFF));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prepareCommandData();
                sendCommand();
            }
        });

        greenColorBar = findViewById(R.id.greenColorBar);
        greenColorBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(currentColorScale == ColorScale.RGB) {
                    effects[currentEffectIndex].colors[currentColorIndex] &= 0xFF00FF;
                    effects[currentEffectIndex].colors[currentColorIndex] |= ((i & 0xFF) << 8);
                }else{
                    s = i;
                    effects[currentEffectIndex].colors[currentColorIndex] = HsvToRgb(h, s, v);
                }
                setIndicatorColor(effects[currentEffectIndex].colors[currentColorIndex], (ImageView) findViewById(currentIndicators[currentColorIndex]));
                colorCodeTV.setText(String.format("0x%06X", effects[currentEffectIndex].colors[currentColorIndex] & 0xFFFFFF));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prepareCommandData();
                sendCommand();
            }
        });

        blueColorBar = findViewById(R.id.blueColorBar);
        blueColorBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if(currentColorScale == ColorScale.RGB) {
                    effects[currentEffectIndex].colors[currentColorIndex] &= 0xFFFF00;
                    effects[currentEffectIndex].colors[currentColorIndex] |= (i & 0xFF);
                }else{
                    v = i;
                    effects[currentEffectIndex].colors[currentColorIndex] = HsvToRgb(h, s, v);
                }
                setIndicatorColor(effects[currentEffectIndex].colors[currentColorIndex], (ImageView) findViewById(currentIndicators[currentColorIndex]));
                colorCodeTV.setText(String.format("0x%06X", effects[currentEffectIndex].colors[currentColorIndex] & 0xFFFFFF));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prepareCommandData();
                sendCommand();
            }
        });
    }

    private void setIndicatorColor(int color, ImageView src){
        src.setColorFilter(Color.rgb((color&0xFF0000)>>16, (color&0xFF00)>>8, color&0xFF));
    }

    private void updateColorSeekBars(int color, ColorScale scale){
        if(scale == ColorScale.RGB) {
            redColorBar.setProgress((color >> 16) & 0xFF);
            greenColorBar.setProgress((color >> 8) & 0xFF);
            blueColorBar.setProgress(color & 0xFF);
        }else{
            // define hsv
            int hsv = RgbToHsv(color);
            h = (hsv>>16)&0xFF;
            s = (hsv>>8)&0xFF;
            v = hsv&0xFF;
            // update seek bars values
            redColorBar.setProgress(h);
            greenColorBar.setProgress(s);
            blueColorBar.setProgress(v);
        }
        colorCodeTV.setText(String.format("0x%06X", color & 0xFFFFFF));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_led_control);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        final Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        connectionStateTextView = findViewById(R.id.connectionStateTextView);

        connectCtrlButton = findViewById(R.id.connectCtrlButton);
        connectCtrlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isConnected){
                    connectionStateTextView.setText(getResources().getString(R.string.disconnecting_state));
                    mBluetoothLeService.disconnect();
                    // close activity
                    Intent data = new Intent (LED_ControlActivity.this, LED_ControlActivity.class);
                    data.putExtra(EXTRAS_HAS_SAVED_DEVICE_ADDRESS, hasSavedDeviceAddress());
                    setResult(RESULT_OK, data);
                    finish();
                }else{
                    if(mBluetoothLeService != null) {
                        connectionStateTextView.setText(getResources().getString(R.string.connecting_state));
                        if(!mBluetoothLeService.connect(mDeviceAddress)){
                            Toast.makeText(getApplicationContext(), getResources().getString(R.string.device_connection_fail_message), Toast.LENGTH_LONG).show();
                        }
                    }else{
                        Toast.makeText(getApplicationContext(), getResources().getString(R.string.device_connection_fail_message), Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        effectsSpinner = findViewById(R.id.effectsSpinner);
        effectsSpinner.setSelection(1);
        effectsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                currentColorIndex = 0;
                currentEffectIndex = i;

                switch(i){
                    case 0:
                        fourColorsBlinkingLayout.setVisibility(View.GONE);
                        twoColorsBlinkingLayout.setVisibility(View.GONE);
                        hsvColorLayout.setVisibility(View.GONE);
                        constantLayout.setVisibility(View.GONE);
                        blinkingLayout.setVisibility(View.GONE);
                        shiftingLayout.setVisibility(View.GONE);
                        rainbowLayout.setVisibility(View.GONE);
                        setColorLayout.setVisibility(View.GONE);
                        break;

                    case 1:
                        currentIndicators = effect1_indicators;
                        colorsRB1Group.check(effect1_radioButtons[0]);

                        fourColorsBlinkingLayout.setVisibility(View.VISIBLE);
                        twoColorsBlinkingLayout.setVisibility(View.GONE);
                        hsvColorLayout.setVisibility(View.GONE);
                        constantLayout.setVisibility(View.GONE);
                        blinkingLayout.setVisibility(View.GONE);
                        shiftingLayout.setVisibility(View.GONE);
                        rainbowLayout.setVisibility(View.GONE);
                        setColorLayout.setVisibility(View.VISIBLE);
                        break;

                    case 2:
                        currentIndicators = effect2_indicators;
                        colorsRB2Group.check(effect2_radioButtons[0]);

                        fourColorsBlinkingLayout.setVisibility(View.GONE);
                        twoColorsBlinkingLayout.setVisibility(View.VISIBLE);
                        hsvColorLayout.setVisibility(View.GONE);
                        constantLayout.setVisibility(View.GONE);
                        blinkingLayout.setVisibility(View.GONE);
                        shiftingLayout.setVisibility(View.GONE);
                        rainbowLayout.setVisibility(View.GONE);
                        setColorLayout.setVisibility(View.VISIBLE);

                        break;

                    case 3:
                        fourColorsBlinkingLayout.setVisibility(View.GONE);
                        twoColorsBlinkingLayout.setVisibility(View.GONE);
                        hsvColorLayout.setVisibility(View.VISIBLE);
                        constantLayout.setVisibility(View.GONE);
                        blinkingLayout.setVisibility(View.GONE);
                        shiftingLayout.setVisibility(View.GONE);
                        rainbowLayout.setVisibility(View.GONE);
                        setColorLayout.setVisibility(View.GONE);
                        break;

                    case 4:
                        currentIndicators = effect4_indicators;

                        fourColorsBlinkingLayout.setVisibility(View.GONE);
                        twoColorsBlinkingLayout.setVisibility(View.GONE);
                        hsvColorLayout.setVisibility(View.GONE);
                        constantLayout.setVisibility(View.VISIBLE);
                        blinkingLayout.setVisibility(View.GONE);
                        shiftingLayout.setVisibility(View.GONE);
                        rainbowLayout.setVisibility(View.GONE);
                        setColorLayout.setVisibility(View.VISIBLE);
                        break;

                    case 5:
                        currentIndicators = effect5_indicators;

                        fourColorsBlinkingLayout.setVisibility(View.GONE);
                        twoColorsBlinkingLayout.setVisibility(View.GONE);
                        hsvColorLayout.setVisibility(View.GONE);
                        constantLayout.setVisibility(View.GONE);
                        blinkingLayout.setVisibility(View.VISIBLE);
                        shiftingLayout.setVisibility(View.GONE);
                        rainbowLayout.setVisibility(View.GONE);
                        setColorLayout.setVisibility(View.VISIBLE);
                        break;

                    case 6:
                        currentIndicators = effect6_indicators;

                        fourColorsBlinkingLayout.setVisibility(View.GONE);
                        twoColorsBlinkingLayout.setVisibility(View.GONE);
                        hsvColorLayout.setVisibility(View.GONE);
                        constantLayout.setVisibility(View.GONE);
                        blinkingLayout.setVisibility(View.GONE);
                        shiftingLayout.setVisibility(View.VISIBLE);
                        rainbowLayout.setVisibility(View.GONE);
                        setColorLayout.setVisibility(View.VISIBLE);
                        break;

                    case 7:
                        fourColorsBlinkingLayout.setVisibility(View.GONE);
                        twoColorsBlinkingLayout.setVisibility(View.GONE);
                        hsvColorLayout.setVisibility(View.GONE);
                        constantLayout.setVisibility(View.GONE);
                        blinkingLayout.setVisibility(View.GONE);
                        shiftingLayout.setVisibility(View.GONE);
                        rainbowLayout.setVisibility(View.VISIBLE);
                        setColorLayout.setVisibility(View.GONE);
                        break;
                }

                if(effects[currentEffectIndex].colors != null) {
                    setIndicatorColor(effects[currentEffectIndex].colors[currentColorIndex], (ImageView) findViewById(currentIndicators[currentColorIndex]));
                    updateColorSeekBars(effects[currentEffectIndex].colors[currentColorIndex], currentColorScale);
                }
                prepareCommandData();
                sendCommand();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        // init effects control layouts
        settingsLayout = findViewById(R.id.settingLayout);
        initFourColorsBlinkingLayout();
        initTwoColorBlinkingLayout();
        initHSVColorLayout();
        initConstantLayout();
        initBlinkingLayout();
        initShiftingLayout();
        initRainbowLayout();

        // init color setting layout
        initColorSetLayout();

        setColorButton = findViewById(R.id.sendCommandButton);
        setColorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prepareCommandData();
                sendCommand();
            }
        });

        ImageButton resetDeviceButton = findViewById(R.id.resetDeviceButton);
        resetDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(LED_ControlActivity.this);
                builder.setMessage(getResources().getString(R.string.reset_device_message));
                builder.setPositiveButton(getResources().getString(R.string.ok_button), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // remove device address from shared preferences
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().remove(EXTRAS_DEVICE_ADDRESS).apply();
                        if(isConnected) {
                            mBluetoothLeService.disconnect();
                        }
                        // close activity
                        Intent data = new Intent (LED_ControlActivity.this, LED_ControlActivity.class);
                        data.putExtra(EXTRAS_HAS_SAVED_DEVICE_ADDRESS, hasSavedDeviceAddress());
                        setResult(RESULT_OK, data);
                        finish();
                    }
                });
                builder.setNegativeButton(getResources().getString(R.string.cancel_button),  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.setCancelable(true);
                final AlertDialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(false);
                dialog.show();
            }
        });

        colorCodeTV = findViewById(R.id.colorCodeTV);

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        boolean res = bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        Log.d(TAG, "bindService result = " + res);
        // update interface
        connectionStateTextView.setText(getResources().getString(R.string.connecting_state));
        connectCtrlButton.setText(getResources().getString(R.string.connect_button));
        setSettingsLayoutEnabled(false);
    }


    void setSettingsLayoutEnabled(boolean isEnable){
        effectsSpinner.setEnabled(isEnable);
        settingsLayout.setVisibility(isEnable ? View.VISIBLE : View.GONE);
        setColorButton.setEnabled(isEnable);
    }

    void updateInterface(){
        if(isConnected){
            connectionStateTextView.setText(getResources().getString(R.string.device_connected_message));
            connectCtrlButton.setText(getResources().getString(R.string.disconnect_button));
            setSettingsLayoutEnabled(true);
        }else {
            connectionStateTextView.setText(getResources().getString(R.string.device_disconnected_message));
            connectCtrlButton.setText(getResources().getString(R.string.connect_button));
            setSettingsLayoutEnabled(false);
        }
    }

    void prepareCommandData(){
        commandData[0] = (byte) 0xAA;

        commandData[1] = effects[currentEffectIndex].effectNum;

        if(effects[currentEffectIndex].colors != null) {
            if (effects[currentEffectIndex].colors.length >= 1) {
                commandData[2] = (byte) ((effects[currentEffectIndex].colors[0] >> 8) & 0xFF); // color 1 G component
                commandData[3] = (byte) ((effects[currentEffectIndex].colors[0] >> 16) & 0xFF); // color 1 R component
                commandData[4] = (byte) ((effects[currentEffectIndex].colors[0]) & 0xFF); // color 1 B component
            } else {
                commandData[2] = (byte) (0); // color 1 G component
                commandData[3] = (byte) (0); // color 1 R component
                commandData[4] = (byte) (0); // color 1 B component
            }

            if (effects[currentEffectIndex].colors.length >= 2) {
                commandData[5] = (byte) ((effects[currentEffectIndex].colors[1] >> 8) & 0xFF); // color 2 G component
                commandData[6] = (byte) ((effects[currentEffectIndex].colors[1] >> 16) & 0xFF); // color 2 R component
                commandData[7] = (byte) ((effects[currentEffectIndex].colors[1]) & 0xFF); // color 2 B component
            } else {
                commandData[5] = (byte) (0); // color 2 G component
                commandData[6] = (byte) (0); // color 2 R component
                commandData[7] = (byte) (0); // color 2 B component
            }

            if (effects[currentEffectIndex].colors.length >= 3) {
                commandData[8] = (byte) ((effects[currentEffectIndex].colors[2] >> 8) & 0xFF); // color 3 G component
                commandData[9] = (byte) ((effects[currentEffectIndex].colors[2] >> 16) & 0xFF); // color 3 R component
                commandData[10] = (byte) ((effects[currentEffectIndex].colors[2]) & 0xFF); // color 3 B component
            } else {
                commandData[8] = (byte) (0); // color 3 G component
                commandData[9] = (byte) (0); // color 3 R component
                commandData[10] = (byte) (0); // color 3 B component
            }

            if (effects[currentEffectIndex].colors.length == 4) {
                commandData[11] = (byte) ((effects[currentEffectIndex].colors[3] >> 8) & 0xFF); // color 4 G component
                commandData[12] = (byte) ((effects[currentEffectIndex].colors[3] >> 16) & 0xFF); // color 4 R component
                commandData[13] = (byte) ((effects[currentEffectIndex].colors[3]) & 0xFF); // color 4 B component
            } else {
                commandData[11] = (byte)(0); // color 4 G component
                commandData[12] = (byte)(0); // color 4 R component
                commandData[13] = (byte)(0); // color 4 B component
            }
        }else{
            commandData[2] = (byte)0; // color 1 G component
            commandData[3] = (byte)0; // color 1 R component
            commandData[4] = (byte)0; // color 1 B component

            commandData[5] = (byte)0; // color 2 G component
            commandData[6] = (byte)0; // color 2 R component
            commandData[7] = (byte)0; // color 2 B component

            commandData[8] = (byte)0; // color 3 G component
            commandData[9] = (byte)0; // color 3 R component
            commandData[10] = (byte)0; // color 3 B component

            commandData[11] = (byte)(0); // color 4 G component
            commandData[12] = (byte)(0); // color 4 R component
            commandData[13] = (byte)(0); // color 4 B component
        }

        commandData[14] = effects[currentEffectIndex].saturation;
        commandData[15] = effects[currentEffectIndex].value;
        commandData[16] = effects[currentEffectIndex].sectionSize;
        commandData[17] = (byte)0x0D; // CR
        commandData[18] = (byte)0x0A; // LF
    }

    void sendCommand(){
        if(!isConnected) return;

        if(mWriteGattCharacteristic != null) {
            // send actual command or from queue
            byte[] cmdFromQueue = mCommandQueue.poll();
            if(cmdFromQueue != null){ // queue isn't empty
                // add current command in queue
                byte[] temp = new byte[commandData.length];
                System.arraycopy(commandData, 0, temp, 0, commandData.length);
                mCommandQueue.offer(temp);
                // write command from queue
                mWriteGattCharacteristic.setValue(cmdFromQueue);
                mBluetoothLeService.writeCharacteristic(mWriteGattCharacteristic);
            }else{
                // queue is empty, send actual command
                mWriteGattCharacteristic.setValue(commandData);
                mBluetoothLeService.writeCharacteristic(mWriteGattCharacteristic);
            }
        }else{
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.send_error_message), Toast.LENGTH_LONG).show();
        }
    }

    private int HsvToRgb(int h, int s, int v)
    {
        int rgb;
        int region, remainder, p, q, t;

        if (s == 0)
        {
            rgb = (v<<16)|(v<<8)|v;
            return rgb;
        }

        region = h/43;
        remainder = (h - (region * 43)) * 6;

        p = (v * (255 - s)) >> 8;
        q = (v * (255 - ((s * remainder) >> 8))) >> 8;
        t = (v * (255 - ((s * (255 - remainder)) >> 8))) >> 8;

        switch (region)
        {
            case 0:
                rgb = (t<<8)|(v<<16)|p;
                break;
            case 1:
                rgb = (v<<8)|(q<<16)|p;
                break;
            case 2:
                rgb = (v<<8)|(p<<16)|t;
                break;
            case 3:
                rgb = (q<<8)|(p<<16)|v;
                break;
            case 4:
                rgb = (p<<8)|(t<<16)|v;
                break;
            default:
                rgb = (p<<8)|(v<<16)|q;
                break;
        }

        return rgb;
    }

    private int RgbToHsv(int rgb)
    {
        int grbMin, grbMax;
        int r = (rgb>>16)&0xFF, g = (rgb>>8)&0xFF, b = (rgb & 0xFF);
        int h = 0, s = 0, v = 0;

        grbMin = r < g ? (r < b ? r : b) : (g < b ? g : b);
        grbMax = r > g ? (r > b ? r : b) : (g > b ? g : b);

        v = grbMax;
        if (v == 0)
        {
            return 0;
        }

        s = (int)(255 * (long)(grbMax - grbMin) / v);
        if (s == 0)
        {
            return v;
        }

        if (grbMax == r)
            h = 43 * (g - b) / (grbMax - grbMin);
        else if (grbMax == g)
            h = 85 + 43 * (b - r) / (grbMax - grbMin);
        else
            h = 171 + 43 * (r - g) / (grbMax - grbMin);

        return (h<<16)|(s<<8)|v;
    }

    @Override
    protected void onStart(){
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
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
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                // close activity
                Intent data = new Intent (LED_ControlActivity.this, LED_ControlActivity.class);
                data.putExtra(EXTRAS_HAS_SAVED_DEVICE_ADDRESS, hasSavedDeviceAddress());
                setResult(RESULT_OK, data);
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
// ACTION_GATT_CONNECTED: connected to a GATT server.
// ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
// ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
// ACTION_DATA_AVAILABLE: received data from the device. This can be a
// result of read or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                isConnected = true;
                updateInterface();
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                isConnected = false;
                updateInterface();
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Get read and write characteristics of BLE device
                getGattCharacteristics(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                // handle data receive action
                byte[] deviceReply = mReadGattCharacteristic.getValue();
                String str = new String(deviceReply, StandardCharsets.UTF_8);
                if(str.contains("Effect set")){ // device can receive command
                    if(mCommandQueue.peek() != null){ // if queue isn't empty send command
                        sendCommand();
                    }
                }
            }
        }
    };

    private void getGattCharacteristics(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid;

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            uuid = gattService.getUuid().toString();
            if (uuid.equals(bleServiceUUIDString)) {
                List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                // Loops through available Characteristics.
                for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                    if (((gattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) |
                            (gattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) {
                        // writing characteristic functions
                        mWriteGattCharacteristic = gattCharacteristic;
                        mBluetoothLeService.setCharacteristicNotification(mWriteGattCharacteristic,true);
                    }
                    if((gattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                        // writing characteristic functions
                        mReadGattCharacteristic = gattCharacteristic;
                        mBluetoothLeService.readCharacteristic(mReadGattCharacteristic);
                        mBluetoothLeService.setCharacteristicNotification(mReadGattCharacteristic,true);
                    }
                }
            }
        }
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private boolean hasSavedDeviceAddress(){
        return !PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext()).getString(EXTRAS_DEVICE_ADDRESS, "").isEmpty();
    }
}

class effectsParameters{
    public byte effectNum;
    public int[] colors;
    public byte saturation;
    public byte value;
    public byte sectionSize;

    public effectsParameters(byte effectNum, int[] colors, byte saturation, byte value, byte sectionSize){
        this.effectNum = effectNum;
        this.colors = colors;
        this.saturation = saturation;
        this.value = value;
        this.sectionSize = sectionSize;
    }
}
