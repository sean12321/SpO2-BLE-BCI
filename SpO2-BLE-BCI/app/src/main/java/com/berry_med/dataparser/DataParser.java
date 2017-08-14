package com.berry_med.dataparser;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by ZXX on 2016/1/8.
 */
public class DataParser {

    //Const
    public String TAG = this.getClass().getSimpleName();

    //Buffer queue
    private LinkedBlockingQueue<Integer> bufferQueue = new LinkedBlockingQueue<Integer>(256);

    //Parse Runnable
    private ParseRunnable mParseRunnable;
    private boolean isStop = false;

    private onPackageReceivedListener mListener;


    /**
     * interface for parameters changed.
     */
    public interface onPackageReceivedListener {
        void onPackageReceived(int[] dat);
    }

    //Constructor
    public DataParser(onPackageReceivedListener listener) {
        this.mListener = listener;
    }

    public void start() {
        mParseRunnable = new ParseRunnable();
        new Thread(mParseRunnable).start();
    }

    public void stop() {
        isStop = true;
    }

    /**
     * ParseRunnable
     */
    class ParseRunnable implements Runnable {
        int dat;
        int[] pkgData;

        @Override
        public void run() {
            while (!isStop) {
                dat = getData();
                pkgData = new int[5];
                if ((dat & 0x80) > 0) {
                    pkgData[0] = dat;
                    for (int i = 1; i < pkgData.length; i++) {
                        dat = getData();
                        if ((dat & 0x80) == 0) {
                            pkgData[i] = dat;
                        } else {
                            continue;
                        }
                    }
                    mListener.onPackageReceived(pkgData);
                }
            }
        }
    }

    /**
     * Add the data received from USB or Bluetooth
     *
     * @param dat
     */
    public void add(byte[] dat) {
        //Log.i(TAG, "add: " + dat.length + "");
        for (byte b : dat) {
            try {
                bufferQueue.put(toUnsignedInt(b));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        //Log.i(TAG, "add: "+ bufferQueue.size());
    }

    /**
     * Get Dat from Queue
     *
     * @return
     */
    private int getData() {
        int dat = 0;
        try {
            dat = bufferQueue.take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return dat;
    }


    private int toUnsignedInt(byte x) {
        return ((int) x) & 0xff;
    }

}
