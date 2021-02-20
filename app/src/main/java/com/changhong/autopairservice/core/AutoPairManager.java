package com.changhong.autopairservice.core;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

import com.changhong.autopairservice.R;
import com.changhong.autopairservice.config.AotuPairConfig;
import com.changhong.autopairservice.utils.Common;
import com.changhong.autopairservice.utils.ToastUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressLint({"InlinedApi", "HandlerLeak", "NewApi"})
@TargetApi(18)
//by: yazhou.zhu@changhong.com
public class AutoPairManager implements BluetoothAdapter.LeScanCallback {
    public static final String ACTION_CANCEL_AUTO_PAIR = "com.changhong.action.CANCEL_AUTO_PAIR";
    public static final String ACTION_START_AUTO_PAIR = "com.changhong.action.START_AUTO_PAIR";
    private static final int MSG_CHECK_PROFILE_READY = 1;
    private static final int MSG_CONNECT_NUM_SHOW_DIALOG = 5;
    private static final int MSG_CREATE_BOND = 2;
    private static final int MSG_REMOVE_BOND = 3;
    private static final int MSG_START_LE_SCAN = 4;
    private static final int PENDING_INTENT_REQUEST_CODE = 3008;
    private final int MAX_RETRY_TIMES = 50;
    private volatile boolean isConnecting = false;
    private volatile boolean isKeepAlive;
    private boolean isReceiverRegister;
    private long lastShowTime = 0;
    private AlarmManager mAlarmManager;
    private AotuPairConfig mAotuPairConfig;
    private AutoPairCallback mAutoPairCallback;
    private BluetoothAdapter mBluetoothAdapter;
    private Context mContext;
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CHECK_PROFILE_READY:
                    checkProfilePrepareStatus();
                    return;
                case MSG_CREATE_BOND:
                    handleCreateBondMessage(msg);
                    return;
                case MSG_START_LE_SCAN:
                    if (hasMessages(MSG_START_LE_SCAN)) {
                        removeMessages(MSG_START_LE_SCAN);
                    }
                    startLeScan();
                    return;
                case MSG_CONNECT_NUM_SHOW_DIALOG:
                    showDialog();
                    return;
                default:
                    return;
            }
        }
    };
    private InputDeviceProfile mInputDeviceProfile;
    private Intent mIntent;
    private PairState mPairState = PairState.IDLE;
    private BroadcastReceiver mReceiver;
    private int mRetryTimes = 1;
    private PendingIntent mSender;
    private BluetoothDevice mTargetDevice;

    public interface AutoPairCallback {
        void onStopService();
    }

    private class AutoPairReceiver extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            Common.log("AutoPairReceiver.onReceive = " + action);
            if (isConnected() && !isKeepAlive && isAlwaysRunning()) {
                Common.log("A. AutoPairReceiver.onReceive, isAlwaysRunning = " + isAlwaysRunning() + " , isConnected = " + isConnected() + " , isKeepAlive = " + isKeepAlive);
                if (mAutoPairCallback != null) {
                    mAutoPairCallback.onStopService();
                    Common.log("AutoPairManager-关闭配对服务");
                }
            } else if ("android.bluetooth.device.action.BOND_STATE_CHANGED".equals(action)) {
                onDeviceBondStateChanged(device, intent.getIntExtra("android.bluetooth.device.extra.BOND_STATE", -1));
            } else if ("android.bluetooth.device.action.UUID".equals(action)) {
                onDeviceBonded(device);
            } else if ("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED".equals(action)) {
                onDeviceConnectionStateChanged(device, intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1));
            } else if ("android.bluetooth.adapter.action.DISCOVERY_STARTED".equals(action)) {
                mHandler.removeMessages(MSG_START_LE_SCAN);
                stopLeScan();
                Common.log("ACTION_DISCOVERY_STARTED, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
            } else if ("android.bluetooth.adapter.action.DISCOVERY_FINISHED".equals(action)) {
                mHandler.sendEmptyMessageDelayed(MSG_START_LE_SCAN, 1500);
                Common.log("ACTION_DISCOVERY_FINISHED, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
            } else if ("android.bluetooth.device.action.ACL_DISCONNECTED".equals(action)) {
                onACLDisconneted(device);
            }
        }
    }

    public AutoPairManager(Context context, AutoPairCallback callback) {
        mContext = context;
        mAutoPairCallback = callback;
        mAotuPairConfig = Common.loadConfig();
        initVariable();
        Common.log("AutoPairManager.init,mAotuPairConfig = " + mAotuPairConfig);
    }

    public boolean start() {
        return prepare();
    }

    private void initVariable() {
        mIntent = new Intent(ACTION_CANCEL_AUTO_PAIR);
        mSender = PendingIntent.getBroadcast(mContext, PENDING_INTENT_REQUEST_CODE, mIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        initConfig();
        mBluetoothAdapter = null;
        mInputDeviceProfile = null;
        mTargetDevice = null;
        mPairState = PairState.IDLE;
        mRetryTimes = 1;
        isReceiverRegister = false;
        mReceiver = new AutoPairReceiver();
    }

    private void initConfig() {
        String[] strArr;
        if (mAotuPairConfig.deviceNames == null || mAotuPairConfig.deviceNames.names == null || mAotuPairConfig.deviceNames.names.isEmpty()) {
            strArr = Common.DEFAULT_MATCH_NAMES;
        } else {
            strArr = (String[]) mAotuPairConfig.deviceNames.names.toArray(new String[mAotuPairConfig.deviceNames.names.size()]);
        }
        Common.BLUETOOTH_NAMES = strArr;
        mAotuPairConfig.timeout = mAotuPairConfig.timeout <= 0 ? 10 : mAotuPairConfig.timeout;
        mAotuPairConfig.rssi = Math.abs(mAotuPairConfig.rssi) == 0 ? 70 : Math.abs(mAotuPairConfig.rssi);
    }

    public int getKeepAliveDuration() {
        return mAotuPairConfig.timeout;
    }

    public boolean isAlwaysRunning() {
        return mAotuPairConfig.alwaysRunning;
    }

    private boolean prepare() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null || !Common.hasBLEFeature(mContext) || !mBluetoothAdapter.isEnabled()) {
            Common.log("This is init_AutoPairManager_return_false!!!" + "mBluetoothAdapter" + mBluetoothAdapter + "hasBLEFeatur" + Common.hasBLEFeature(mContext) + "mBluetoothAdapter.isEnabled" + mBluetoothAdapter.isEnabled());
            return false;
        }
        isKeepAlive = true;
        mInputDeviceProfile = new InputDeviceProfile(mContext);
        registerAutoPairReceiver();
        mHandler.sendEmptyMessageDelayed(MSG_CHECK_PROFILE_READY, 200);
        return true;
    }

    public void stop() {
        if (mInputDeviceProfile != null) {
            mInputDeviceProfile.release();
        }
        unregisterAutoPairReceiver();
        stopLeScan();
        if (mHandler.hasMessages(MSG_CREATE_BOND)) {
            mHandler.removeMessages(MSG_CREATE_BOND);
        }
        if (mHandler.hasMessages(MSG_REMOVE_BOND)) {
            mHandler.removeMessages(MSG_REMOVE_BOND);
        }
        if (mHandler != null) {
            mHandler.removeMessages(MSG_CHECK_PROFILE_READY);
        }
        if (mHandler != null) {
            mHandler.removeMessages(MSG_START_LE_SCAN);
        }
    }

    //卸载广播接收
    private void unregisterAutoPairReceiver() {
        if (isReceiverRegister && mReceiver != null) {
            mContext.unregisterReceiver(mReceiver);
            isReceiverRegister = false;
        }
    }

    //广播注册
    private void registerAutoPairReceiver() {
        if (!isReceiverRegister) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
            intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED");
            intentFilter.addAction("android.bluetooth.adapter.action.DISCOVERY_STARTED");
            intentFilter.addAction("android.bluetooth.adapter.action.DISCOVERY_FINISHED");
            intentFilter.addAction("android.bluetooth.device.action.UUID");
            intentFilter.addAction("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED");
            mContext.registerReceiver(mReceiver, intentFilter);
            isReceiverRegister = true;
        }
    }

    private void onACLDisconneted(BluetoothDevice device) {
//         mIntent = new Intent(ACTION_CANCEL_AUTO_PAIR);
//        mContext.sendBroadcast(mIntent);
        if (mPairState == PairState.BOND && device != null && mTargetDevice != null && mTargetDevice.equals(device)) {
            Common.log("onACLDisconneted, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
            showMessage(device, mContext.getString(R.string.connect_failed));
            mTargetDevice = null;
            mPairState = PairState.SCANING;
        }
    }

    private void onDeviceBonded(BluetoothDevice device) {
        if (mTargetDevice != null && device != null && mTargetDevice.equals(device) && isConnectable()) {
            Common.log("ACTION_UUID, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
            showMessage(device, mContext.getString(R.string.connecting));
            mInputDeviceProfile.connect(device);
        }
    }

    //连接状态改变
    private void onDeviceConnectionStateChanged(BluetoothDevice device, int state) {
        if (device != null && mPairState == PairState.BOND && device.equals(mTargetDevice) && state == 2) {
            showMessage(device, mContext.getString(R.string.connected));
            mTargetDevice = null;
            isConnecting = false;
            Common.log("BW=====connected status======AutoPair = " + isConnecting);
            mPairState = PairState.SCANING;
            Common.log("A. onDeviceConnectionStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , connectState =" + state);
        } else if (device != null && mPairState == PairState.BOND && device.equals(mTargetDevice) && state == 0) {
            showMessage(device, mContext.getString(R.string.connect_failed));
            mTargetDevice = null;
            isConnecting = false;
            Common.log("BW=====disconnected status======AutoPair = " + isConnecting);
            mPairState = PairState.SCANING;
            Common.log("B. onDeviceConnectionStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , connectState =" + state);
        } else if (device != null && mPairState == PairState.BOND && device.equals(mTargetDevice) && state == 1) {
            showMessage(device, mContext.getString(R.string.connecting));
            Common.log("C. onDeviceConnectionStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , connectState =" + state);
        } else {
            isConnecting = false;
            Common.log("D. onDeviceConnectionStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , connectState =" + state);
        }
    }

    //绑定状态改变
    private void onDeviceBondStateChanged(BluetoothDevice device, int bondState) {
        if (device != null && mPairState == PairState.REMOVE && device.equals(mTargetDevice) && bondState == 10) {
            mTargetDevice = null;
            isConnecting = false;
            mPairState = PairState.SCANING;
            Common.log("A. onDeviceBondStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , bondState =" + bondState);
        } else if (device != null && mPairState == PairState.BOND && mTargetDevice.equals(device) && bondState == 12) {
            Common.log("B. onDeviceBondStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , bondState =" + bondState);
            showMessage(device, mContext.getString(R.string.bond_successfully));
        } else if (device != null && mPairState == PairState.BOND && mTargetDevice.equals(device) && bondState == 11) {
            Common.log("C. onDeviceBondStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , bondState =" + bondState);
            showMessage(device, mContext.getString(R.string.bonding));
        } else if (device != null && mPairState == PairState.BOND && mTargetDevice.equals(device) && bondState == 10) {
            Common.log("D. onDeviceBondStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , bondState =" + bondState);
            showMessage(device, mContext.getString(R.string.bond_failed));
            isConnecting = false;
            mTargetDevice = null;
            mPairState = PairState.SCANING;
        } else {
            isConnecting = false;
            Common.log("E. onDeviceBondStateChanged, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , bondState =" + bondState);
        }
    }

    //是否已经连接
    private boolean isConnectable() {
        if (mTargetDevice == null || mPairState != PairState.BOND || mTargetDevice.getBondState() != 12 || mInputDeviceProfile.getConnectionStatus(mTargetDevice) == 2 || mInputDeviceProfile.getConnectionStatus(mTargetDevice) == 1) {
            return false;
        }
        return true;
    }

    //弹出提示消息
    private void showMessage(BluetoothDevice device, String msg) {
        ToastUtil.getInstance(mContext).showErrString(msg);
    }

    //检测profile状态
    private void checkProfilePrepareStatus() {
        if (mInputDeviceProfile.isReady()) {
            if (Common.BLUETOOTH_NAMES != null && Common.BLUETOOTH_NAMES.length > 0 && (!isAlwaysRunning() || !isConnected() || isKeepAlive)) {
                mTargetDevice = null;
                mPairState = PairState.SCANING;
                startLeScan();
                Common.log("准备扫描！！！！！");
            } else if (mAutoPairCallback != null) {
                mAutoPairCallback.onStopService();
                Common.log("自动配对服务关闭！！！！！");
            }
        } else if (mRetryTimes < MAX_RETRY_TIMES) {
            mRetryTimes++;
            mHandler.sendEmptyMessageDelayed(MSG_CHECK_PROFILE_READY, 200);
            Common.log("mRetryTimes" + mRetryTimes);
        } else if (mAutoPairCallback != null) {
            mAutoPairCallback.onStopService();
            Common.log("自动配对服务关闭！！！！！");
        }
    }

    //判断是否连接
    public boolean isConnected() {
        List<BluetoothDevice> bondedDevices = getBondedDevices();
        if (!(bondedDevices == null || bondedDevices.isEmpty())) {
            for (BluetoothDevice device : bondedDevices) {
                if (mInputDeviceProfile.getConnectionStatus(device) == 2) {
                    return true;
                }
            }
        }
        return false;
    }

    //设置定时
    private void setPendingAlarm() {
        if (mAotuPairConfig.timeout > 0) {
            mAlarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + ((long) ((mAotuPairConfig.timeout * 1000) * 60)), mSender);
        }
    }

    private void cancelPendingAlarm() {
        mAlarmManager.cancel(mSender);
    }

    //开始扫描
    private synchronized void startLeScan() {
        if (!(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled() || mBluetoothAdapter.isDiscovering())) {
            mBluetoothAdapter.startLeScan(this);
            Common.log("开始扫描！！！！！");
        }
    }

    //停止扫描
    private synchronized void stopLeScan() {
        if (mBluetoothAdapter != null) {
            Common.log("停止扫描！！！！！");
            mBluetoothAdapter.stopLeScan(this);
        }
    }

    //开始连接蓝牙遥控器
    private void handleCreateBondMessage(Message msg) {
        if (mHandler.hasMessages(MSG_CREATE_BOND)) {
            mHandler.removeMessages(MSG_CREATE_BOND);
        }
        BluetoothDevice device = (BluetoothDevice) msg.obj;
        showMessage(device, mContext.getString(R.string.start_bond));
        if (mBluetoothAdapter.isDiscovering()) {
            mPairState = PairState.SCANING;
            mTargetDevice = null;
            return;
        }
        isConnecting = true;
        Common.log("BW==========AutoPair createBond isConnecting = " + isConnecting);
        device.createBond();
        Common.log("H onLeScan,createBond, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
    }

    //展示弹窗
    protected void showDialog() {
        ToastUtil.getInstance(mContext).showErrString(mContext.getResources().getString(R.string.bluetooth_pair_num_surpress));
    }

    //获取已绑定设备/设备必须有name
    private List<BluetoothDevice> getBondedDevices() {
        if (mBluetoothAdapter == null) {
            return null;
        }
        Set<BluetoothDevice> bList = mBluetoothAdapter.getBondedDevices();
        List<BluetoothDevice> bondedList = new ArrayList();
        for (BluetoothDevice device : bList) {
            if (device.getName() != null && Common.checkBluetoothName(device.getName())) {
                bondedList.add(device);
            }
        }
        return bondedList;
    }

    //判断是否已经绑定
    private boolean includedInBonded(BluetoothDevice device) {
        List<BluetoothDevice> bondedList = getBondedDevices();
        if (bondedList == null || device == null) {
            return false;
        }
        for (BluetoothDevice bondedDevice : bondedList) {
            if (bondedDevice.getAddress().equals(device.getAddress())) {
                return true;
            }
        }
        return false;
    }

    //扫描的设备
    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        Common.log("onLeScan-device = " + device.getName());
        if (isAvailableDevice(device, scanRecord)) {
            Common.log("A. onLeScan, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device + " , rssi = " + rssi + " , isDiscovering = " + mBluetoothAdapter.isDiscovering() + " , name = " + device.getName());
            if (mPairState != PairState.SCANING || mBluetoothAdapter.isDiscovering() || !onScannedBondedDevice(device)) {
                return;
            }
            if ((mAotuPairConfig.rssi == 0 || Math.abs(rssi) <= mAotuPairConfig.rssi) && !mBluetoothAdapter.isDiscovering()) {
                Common.log("F onLeScan, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
                startCreateBond(device);
            }
        }
    }

    //开始绑定配对
    private void startCreateBond(BluetoothDevice device) {
        mPairState = PairState.BOND;
        mTargetDevice = device;
        if (mHandler.hasMessages(MSG_CREATE_BOND)) {
            mHandler.removeMessages(MSG_CREATE_BOND);
        }
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_CREATE_BOND, device), 600);
    }

    //判定设备是否可用
    private boolean isAvailableDevice(BluetoothDevice device, byte[] scanRecord) {
        String deviceName = device == null ? null : Common.isEmpty(device.getName()) ? device.getAliasName() : device.getName();
//        Log.i("AutoPair", "-----> deviceName = " + deviceName);
//        Log.i("AutoPair", "-----> mBluetoothAdapter.isDiscovering() = " + mBluetoothAdapter.isDiscovering());
//        Log.i("AutoPair", "-----> Common.isMatchedHogpRecord(scanRecord) = " + Common.isMatchedHogpRecord(scanRecord));
//        Log.i("AutoPair", "-----> Common.isEmpty(deviceName) = " + Common.isEmpty(deviceName));
//        Log.i("AutoPair", "-----> Common.isEmpty(device.getAddress() = " + Common.isEmpty(device.getAddress()));
        return (mBluetoothAdapter.isDiscovering() || !Common.isMatchedHogpRecord(scanRecord) || device == null || Common.isEmpty(deviceName) || Common.isEmpty(device.getAddress()) || isOverLinkNum()) ? false : true;
        //return (mBluetoothAdapter.isDiscovering() || !Common.isMatchedHogpRecord(scanRecord) || device == null || Common.isEmpty(device.getAddress()) || isOverLinkNum()) ? false : true;
    }

    //判断是否连接数过多/是则弹出提示框
    private boolean isOverLinkNum() {
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        if (bondedDevices.size() < 3) {
            return false;
        }
        if (System.currentTimeMillis() - lastShowTime > 16000) {
            lastShowTime = System.currentTimeMillis();
            mHandler.sendEmptyMessage(MSG_CONNECT_NUM_SHOW_DIALOG);
        }
        return true;
    }

    //判定设备连接状态/是否可连接
    private boolean onScannedBondedDevice(BluetoothDevice device) {
        if (includedInBonded(device)) {
            Common.log("B. onLeScan, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
            int bondState = device.getBondState();
            int connectState = mInputDeviceProfile.getConnectionStatus(device);
            Common.log("B. onLeScan, bondState = " + bondState + " connectState = " + connectState);
            if (connectState == 2 || mBluetoothAdapter.isDiscovering()) {
                return false;
            }
            if (bondState != 10) {
                Common.log("D. onLeScan, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
                Common.log("BW=====removeBond======AutoPair = " + isConnecting);
                if (bondState != 12 || isConnecting) {
                    return false;
                }
                device.removeBond();
                return false;
            }
        }
        Common.log("E. onLeScan, isAlwaysRunning = " + isAlwaysRunning() + " , isKeepAlive = " + isKeepAlive + "  ,mTargetDevice = " + mTargetDevice + " , mPairState = " + mPairState + " device = " + device);
        return true;
    }

    public void setKeepAlive(boolean keepAlive) {
        isKeepAlive = keepAlive;
    }
}
