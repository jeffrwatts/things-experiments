package com.skiaddict.thingsexperiments;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by jewatts on 8/6/17.
 */

public class NetworkUtil {

    public static List<String> getIPAddressList() throws SocketException {
        List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());

        List<String> addresses = new LinkedList<>();

        for (NetworkInterface interfaceIx : interfaces) {
            List<InetAddress> inetAddresses = Collections.list(interfaceIx.getInetAddresses());

            // Filter loopback.
            for (InetAddress inetAddressIx : inetAddresses) {
                if (!inetAddressIx.isLoopbackAddress()) {
                    addresses.add(inetAddressIx.getHostAddress());
                }
            }
        }

        return addresses;
    }

}
