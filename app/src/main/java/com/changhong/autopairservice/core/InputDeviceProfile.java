package com.changhong.autopairservice.core;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothInputDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;

@SuppressLint({"NewApi"})
public class InputDeviceProfile implements BluetoothProfile.ServiceListener {
    private boolean isReady;
    private BluetoothInputDevice mInputDevice;

    public InputDeviceProfile(Context context) {
        BluetoothAdapter.getDefaultAdapter().getProfileProxy(context, this, 4);
    }

    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        this.mInputDevice = (BluetoothInputDevice) proxy;
        this.isReady = true;
    }

    public void onServiceDisconnected(int profile) {
        this.mInputDevice = null;
        this.isReady = false;
    }

    public boolean isReady() {
        return this.isReady;
    }

    public boolean connect(BluetoothDevice device) {
        if (this.mInputDevice == null) {
            return false;
        }
        ParcelUuid[] uuids = device.getUuids();
        if (uuids == null || device.getType() == 1 || !BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.Hogp)) {
            return false;
        }
        return this.mInputDevice.connect(device);
    }

    public boolean disconnect(BluetoothDevice device) {
        if (this.mInputDevice == null) {
            return false;
        }
        return this.mInputDevice.disconnect(device);
    }

    public int getConnectionStatus(BluetoothDevice device) {
        if (this.mInputDevice == null) {
            return 0;
        }
        List<BluetoothDevice> deviceList;
        int i;
        try {
            deviceList = this.mInputDevice.getConnectedDevices();
        } catch (Exception e) {
            deviceList = new ArrayList();
        }
        if (deviceList.isEmpty() || !((BluetoothDevice) deviceList.get(0)).equals(device)) {
            i = 0;
        } else {
            i = this.mInputDevice.getConnectionState(device);
        }
        return i;
    }

    protected void finalize() throws Throwable {
        super.finalize();
        release();
    }

    public void release() {
        if (this.mInputDevice != null) {
            BluetoothAdapter.getDefaultAdapter().closeProfileProxy(4, this.mInputDevice);
        }
        this.mInputDevice = null;
        this.isReady = false;
    }
}