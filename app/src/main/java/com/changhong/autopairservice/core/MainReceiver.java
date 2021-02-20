package com.changhong.autopairservice.core;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.changhong.autopairservice.utils.Common;

//by: yazhou.zhu@changhong.com
public class MainReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("android.intent.action.BOOT_COMPLETED".equals(action)) {
            context.startService(new Intent(context, MainService.class));
            Common.log("Start automatic  pairing service when booting");
        } else if ("android.bluetooth.adapter.action.STATE_CHANGED".equals(action)) {
            switch (intent.getIntExtra("android.bluetooth.adapter.extra.STATE", -1)) {

                case BluetoothAdapter.STATE_OFF /*10*/:
                case BluetoothAdapter.STATE_TURNING_OFF /*13*/:
                    Common.log("Stop the automatic pairing service when the Bluetooth adapter is turned off");
                    context.stopService(new Intent(context, MainService.class));
                    System.exit(0);
                    return;
                case BluetoothAdapter.STATE_ON /*12*/:
                    context.startService(new Intent(context, MainService.class));
                    Common.log("start  automatic pairing service when the Bluetooth adapter is turned on");
                    return;
                default:
                    return;
            }
        } else if (AutoPairManager.ACTION_CANCEL_AUTO_PAIR.equals(action)) {
            Common.log("Cancel automatic  pairing service!!!!!!");
            context.stopService(new Intent(context, MainService.class));
            System.exit(0);
        } else if (AutoPairManager.ACTION_START_AUTO_PAIR.equals(action)) {
            context.startService(new Intent(context, MainService.class));
            Common.log("Start automatic  pairing service!!!!!!");
        } else {
            Common.log("Stop automatic pairing service when shutting down");
            context.stopService(new Intent(context, MainService.class));
            System.exit(0);
        }
    }
}
