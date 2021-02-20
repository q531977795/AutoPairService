package com.changhong.autopairservice.core;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.changhong.autopairservice.utils.Common;

import java.util.Timer;
import java.util.TimerTask;

//by: yazhou.zhu@changhong.com
public class MainService extends Service implements AutoPairManager.AutoPairCallback {
    private AutoPairManager mAutoPairManager;
    private Timer mTimer;
    private TimerTask mTimerTask;

    private class AutoPairTimerTask extends TimerTask {

        public void run() {
            Common.log("MainService.AutoPairTimerTask.run()");
            mAutoPairManager.setKeepAlive(false);
            cancelTimer();
            if (mAutoPairManager.isAlwaysRunning()) {
                if (mAutoPairManager.isConnected()) {
                    Common.log("A. AutoPairTimerTask.run, isAlwaysRunning = " + mAutoPairManager.isAlwaysRunning() + " , isConnected = " + mAutoPairManager.isConnected());
                    stopSelf();
                }
                Common.log("B. AutoPairTimerTask.run, isAlwaysRunning = " + mAutoPairManager.isAlwaysRunning() + " , isConnected = " + mAutoPairManager.isConnected());
                return;
            }
            Common.log("C. AutoPairTimerTask.run, isAlwaysRunning = " + mAutoPairManager.isAlwaysRunning() + " , isConnected = " + mAutoPairManager.isConnected());
            stopSelf();
        }
    }

    public IBinder onBind(Intent intent) {
        Common.log("MainService.onBind()");
        return null;
    }

    public void onCreate() {
        super.onCreate();
        Common.log("MainService.onCreate()");
        init();
    }

    private void init() {

        mAutoPairManager = new AutoPairManager(this, this);
        Common.log("This is init!!!");
        if (mAutoPairManager.start()) {
            mAutoPairManager.setKeepAlive(true);
            cancelTimer();
            Common.log("MainService.startTimer(),KeepAliveDuration = " + mAutoPairManager.getKeepAliveDuration());
            startTimer();
            return;
        }
        cancelTimer();
        stopSelf();
    }

    private void startTimer() {
        Common.log("MainService.startTimer()");
        mTimer = new Timer();
        mTimerTask = new AutoPairTimerTask();
        mTimer.schedule(mTimerTask, (long) ((mAutoPairManager.getKeepAliveDuration() * 1000) * 60));
    }

    private void cancelTimer() {
        Common.log("MainService.cancelTimer()");
        if (mTimer != null) {
            mTimer.cancel();
        }
        if (mTimerTask != null) {
            mTimerTask.cancel();
        }
        mTimer = null;
        mTimerTask = null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Common.log("MainService.onStartCommand()");
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        super.onDestroy();
        Common.log("MainService.onDestroy()");
        cancelTimer();
        if (mAutoPairManager != null) {
            mAutoPairManager.stop();
        }
        System.exit(0);
    }

    public void onStopService() {
        Common.log("MainService.onStopService()");
        cancelTimer();
        stopSelf();
    }
}
