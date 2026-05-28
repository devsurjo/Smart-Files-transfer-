package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // First pass: look specifically for Wi-Fi or Ethernet interfaces
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (name.contains("wlan") || name.contains("ap") || name.contains("eth") || name.contains("p2p")) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            // Second pass fallback: any valid IPv4 address
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        return null
    }

    fun getWifiSsid(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val ssid = info.ssid
            if (ssid == null || ssid == "<unknown ssid>" || ssid == "0x" || ssid == "") {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                    "Wi-Fi LAN"
                } else {
                    "Local Network"
                }
            } else {
                ssid.replace("\"", "")
            }
        } catch (e: Exception) {
            "Wi-Fi LAN"
        }
    }

    fun getGatewayAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.dhcpInfo
            val gateway = dhcp.gateway
            if (gateway == 0) {
                val ip = getLocalIpAddress()
                if (ip != null && ip.contains(".")) {
                    ip.substring(0, ip.lastIndexOf(".")) + ".1"
                } else {
                    "192.168.1.1"
                }
            } else {
                ipIntToString(gateway)
            }
        } catch (e: Exception) {
            "192.168.1.1"
        }
    }

    fun getSubnetMask(context: Context): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val dhcp = wifiManager.dhcpInfo
            val netmask = dhcp.netmask
            if (netmask == 0) {
                "255.255.255.0"
            } else {
                ipIntToString(netmask)
            }
        } catch (e: Exception) {
            "255.255.255.0"
        }
    }

    private fun ipIntToString(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }
}
