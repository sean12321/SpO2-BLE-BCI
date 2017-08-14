package com.berry_med.waveform;


import android.util.Log;

public class WaveFormParams {
    private final String TAG = this.getClass().getName();

    private int xStep;
    private int bufferCounter;
    private int valueMin, valueMax, valueBase, valueRange;

    public WaveFormParams(int xStep, int bufferCounter, int[] valueRange) {
        this.xStep = xStep;
        this.bufferCounter = bufferCounter;
        this.valueBase = (valueRange[1] + valueRange[0]) >> 1;
        this.valueMin = 819200;
        this.valueMax = 0;
        this.valueRange = valueRange[1] - valueRange[0];
        Log.i(TAG, "valueMin = " + this.valueMin + ", valueMax = " + this.valueMax);
    }

    public int getxStep() {
        return xStep;
    }

    public int getBufferCounter() {
        return bufferCounter;
    }

    public void setValueMin(int value) {
        valueMin = value;
    }

    public int getValueMin() {
        return valueMin;
    }

    public void setValueMax(int value) {
        valueMax = value;
    }

    public int getValueMax() {
        return valueMax;
    }

    public int getValueBase() {
        return valueBase;
    }

    public void calcValueBase() {
        valueBase = (valueMax + valueMin) >> 1;
    }

    public int getValueRange() {
        return valueRange;
    }

    public void setValueRange() {
        valueRange = valueMax - valueMin;
        //Log.i(TAG, String.format("valueRange = %d (%d, %d, %d)", valueRange, valueMin, valueBase, valueMax));
        valueMin = 819200;
        valueMax = 0;
    }
}