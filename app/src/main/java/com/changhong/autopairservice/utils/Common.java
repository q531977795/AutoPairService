package com.changhong.autopairservice.utils;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.changhong.autopairservice.config.AotuPairConfig;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;


public class Common {
    public static String[] BLUETOOTH_NAMES = null;
    public static final int COMPLETE_NAME_FLAG = 9;
    public static final String CONFIG_PATH = "/system/etc/AutoPairConfig.xml";
    public static final String[] DEFAULT_MATCH_NAMES = new String[]{"IFLY_REMOTE", "电信BLE语音遥控", "BLE语音遥控器", "SW-Remote"};
    private static final String LOG_TAG = "AutoPair";

    //检查蓝牙名称
    public static boolean checkBluetoothName(String name) {
        if (BLUETOOTH_NAMES == null || BLUETOOTH_NAMES.length <= 0 || name == null) {
            return false;
        }
        String tempName = name.trim();
        for (String str : BLUETOOTH_NAMES) {
            if (tempName.contains(str.trim())) {
                return true;
            }
        }
        return false;
    }

    //比对/蓝牙名称相同
    public boolean compare(BluetoothDevice src, BluetoothDevice dst) {
        if (src == null || dst == null) {
            return false;
        }
        if (src == dst) {
            return true;
        }
        return src.getAddress().equals(dst.getAddress());
    }

    //支持BLE蓝牙检测
    public static boolean hasBLEFeature(Context context) {
        if (context.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            return true;
        }
        return false;
    }

    //
    public static boolean isMatchedHogpRecord(byte[] scanRecord) {
        if (scanRecord == null || scanRecord.length <= 0) {
            return false;
        }
        int length = scanRecord.length;
        int i = 0;
        byte[] RcName = new byte[50];
        String decodedName = null;
        while (i < length - 2) {
            int element_len = scanRecord[i];
            if (scanRecord[i + 1] == (byte) 9) {
                try {
                    System.arraycopy(scanRecord, i + 2, RcName, 0, element_len - 1);
                    decodedName = new String(RcName, "UTF-8");
                    Common.log("Common.isMatchedHogpRecord()-decodedName" + decodedName);

                    if (checkBluetoothName(decodedName)) {
                        return true;
                    }
                } catch (Exception e) {
                    Log.i(LOG_TAG, "error = " + e.toString());
                    e.printStackTrace();
                }

            } else {
            }
            i += element_len + 1;
        }
        return false;
    }

    public static AotuPairConfig loadConfig() {
        try {
            Serializer serializer = new Persister();
            File input = new File(CONFIG_PATH);
            if (!input.exists() || input.isDirectory() || input.length() <= 0) {
                return new AotuPairConfig();
            }
            return (AotuPairConfig) serializer.read(AotuPairConfig.class, input);
        } catch (Exception e) {
            e.printStackTrace();
            return new AotuPairConfig();
        }
    }

    public static boolean isEmpty(String content) {
        return content == null || content.trim().length() <= 0;
    }

    public static void log(String msg) {
        Log.d(LOG_TAG, msg);
    }

    public static void log(BluetoothDevice device, String msg) {
        if (device == null) {
            log(msg);
        } else {
            Log.d(LOG_TAG, msg + "  (" + (isEmpty(device.getAliasName()) ? "" : device.getAliasName() + "-") + device.getAddress() + ")");
        }
    }

//    public static void ioClose(Closeable... closeables) {
//        if (closeables != null && closeables.length > 0) {
//            for (Closeable closeable : closeables) {
//                if (closeable != null) {
//                        try {
//                        closeable.close();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
//            }
//        }
//    }
}

