package com.berry_med.waveform;

import java.util.concurrent.LinkedBlockingQueue;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.berry_med.main.R;


public class DrawRunnable implements Runnable {

    private final String TAG = this.getClass().getName();

    private Paint mPaint;
    private int WAVEFORM_PADDING = 50;
    private int STROKE_WIDTH = 2;
    private LinkedBlockingQueue<Integer> mQueue;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceView mSurfaceView;
    private WaveFormParams mWaveParams;

    private Context mContext;


    public DrawRunnable(Context context, LinkedBlockingQueue<Integer> queue,
                        SurfaceView surfaceView, SurfaceHolder surfaceHolder,
                        WaveFormParams waveParams) {
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStrokeWidth(STROKE_WIDTH);
        mPaint.setAntiAlias(true);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStyle(Paint.Style.STROKE);

        this.mQueue = queue;
        this.mSurfaceHolder = surfaceHolder;
        this.mSurfaceView = surfaceView;
        this.mWaveParams = waveParams;

        this.mContext = context;

    }


    @Override
    public void run() {
        // TODO Auto-generated method stub

        Point oldPoint = new Point(WAVEFORM_PADDING, 0);
        Point newPoint = new Point(WAVEFORM_PADDING, 0);
        Point prevOldPoint = new Point(WAVEFORM_PADDING, 0);
        int[] tempArray = new int[5];
        Path mPath = new Path();
        float xScale;
        float yScale;
        int temp;
        int counter;
        int i;

        while (true) {
            counter = mWaveParams.getBufferCounter();
            for (i = 0; i < counter; i++) {
                temp = 0;
                try {
                    temp = mQueue.take();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                tempArray[i] = temp;
                //Log.i(TAG, String.format("tempArray[%d]=%d", i, tempArray[i]));
                if (temp > 0 && temp < mWaveParams.getValueMin()) {
                    mWaveParams.setValueMin(temp);
                } else if (temp < 409600 && temp > mWaveParams.getValueMax()) {
                    mWaveParams.setValueMax(temp);
                }
                //Log.i(TAG, String.format("temp = %d", temp));
            }

            synchronized (this) {
                Canvas mCanvas = mSurfaceHolder.lockCanvas(new Rect(oldPoint.x, WAVEFORM_PADDING,
                        oldPoint.x + mWaveParams.getxStep() * counter, mSurfaceView.getHeight() - WAVEFORM_PADDING));
                if (mCanvas != null) {
                    mCanvas.drawColor(mContext.getResources().getColor(R.color.app_background_color));
                    mPath.reset();

                    //xScale = (float)mSurfaceView.getWidth() / 600;
                    xScale = 0.5f;
                    yScale = 1.5f;
                    //yScale = 1.0f * (float)(mSurfaceView.getHeight() - WAVEFORM_PADDING * 2) / mWaveParams.getValueRange();
                    //Log.i(TAG, "xScale = " + xScale + ", yScale = " + yScale);
                    for (i = 0; i < counter; i++) {
                        newPoint.x = oldPoint.x + (int) (xScale * mWaveParams.getxStep());
                        //newPoint.x = oldPoint.x + (int)(xScale);
                        if (newPoint.x > mSurfaceView.getWidth() - WAVEFORM_PADDING) {
                            prevOldPoint.x = oldPoint.x = newPoint.x = WAVEFORM_PADDING;
                            mWaveParams.calcValueBase();
                            mWaveParams.setValueRange();
                        }
                        newPoint.y = mSurfaceView.getHeight() / 2 - (int) ((tempArray[i] - mWaveParams.getValueBase()) * yScale);
                        if (newPoint.y < WAVEFORM_PADDING)
                            newPoint.y = WAVEFORM_PADDING;
                        else if (newPoint.y > mSurfaceView.getHeight() - WAVEFORM_PADDING)
                            newPoint.y = mSurfaceView.getHeight() - WAVEFORM_PADDING;
                        if (prevOldPoint.y == 0 && oldPoint.y == 0) {
                            prevOldPoint.y = oldPoint.y = newPoint.y;
                        }
//                        if (tempArray[i] >= 0 && tempArray[i] <= 5)
//                            Log.i(TAG, String.format("prevOldPoint(%d, %d), oldPoint(%d, %d), newPoint(%d, %d)",
//                                    prevOldPoint.x, prevOldPoint.y, oldPoint.x, oldPoint.y, newPoint.x, newPoint.y));
                        mPath.moveTo(prevOldPoint.x, prevOldPoint.y);
                        //mPath.quadTo((newPoint.x+oldPoint.x)/2, (newPoint.y+oldPoint.y)/2, newPoint.x, newPoint.y);
                        mPath.cubicTo(oldPoint.x + (oldPoint.x - prevOldPoint.x) / 2,
                                oldPoint.y + (oldPoint.y - prevOldPoint.y) / 2,

                                oldPoint.x + (newPoint.x - oldPoint.x) / 2,
                                oldPoint.y + (newPoint.y - oldPoint.y) / 2,

                                newPoint.x,
                                newPoint.y);

                        prevOldPoint.x = oldPoint.x;
                        prevOldPoint.y = oldPoint.y;
                        oldPoint.x = newPoint.x;
                        oldPoint.y = newPoint.y;
                    }

                    mCanvas.drawPath(mPath, mPaint);
                    mCanvas.save();

                    mSurfaceHolder.unlockCanvasAndPost(mCanvas);
                }
            }
        }
    }
}