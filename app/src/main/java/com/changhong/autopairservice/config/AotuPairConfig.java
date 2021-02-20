package com.changhong.autopairservice.config;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

@Root(name = "Config", strict = true)
public class AotuPairConfig {
    @Element(name = "AlwaysRunningInNoConnectedCase", required = false)
    public boolean alwaysRunning;
    @Element(name = "AutoRemoveBond", required = false)
    public boolean autoRemoveBond;
    @Element(name = "DeviceNames", required = false)
    public DeviceNames deviceNames;
    @Element(name = "Rssi", required = false)
    public int rssi;
    @Element(name = "TimeOut", required = false)
    public int timeout;

    public String toString() {
        return "AotuPairConfig [rssi=" + this.rssi + ", timeout=" + this.timeout + ", deviceNames=" + this.deviceNames + ", autoRemoveBond=" + this.autoRemoveBond + ", alwaysRunning=" + this.alwaysRunning + "]";
    }
}
