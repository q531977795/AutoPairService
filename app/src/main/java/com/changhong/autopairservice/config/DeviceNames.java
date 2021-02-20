package com.changhong.autopairservice.config;

import java.util.ArrayList;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

@Root(strict = false)
public class DeviceNames {
    @ElementList(entry = "Name", inline = true, required = false)
    public ArrayList<String> names = new ArrayList();

    public String toString() {
        return "DeviceNames [names=" + this.names + "]";
    }
}
