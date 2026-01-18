package com.mustafafyp.guardianai.services;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.IOException;

public class GuardianVpnService extends VpnService {

    private Thread vpnThread;
    private ParcelFileDescriptor vpnInterface;

    // Use CleanBrowsing DNS (Family Filter) - Blocks Porn automatically
    private static final String DNS_IP = "185.228.168.168";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (vpnThread != null) {
            vpnThread.interrupt();
        }

        vpnThread = new Thread(() -> {
            try {
                configureVpn();
            } catch (Exception e) {
                Log.e("GuardianVPN", "Error starting VPN", e);
            }
        });
        vpnThread.start();
        return START_STICKY;
    }

    private void configureVpn() throws IOException {
        if (vpnInterface != null) {
            vpnInterface.close();
        }

        // Build the VPN interface
        Builder builder = new Builder();
        builder.setSession("GuardianAI Shield");
        builder.addAddress("10.0.0.2", 32);
        builder.addRoute("0.0.0.0", 0);
        builder.addDnsServer(DNS_IP); // Redirect ALL traffic to Safe DNS

        vpnInterface = builder.establish();
        Log.i("GuardianVPN", "VPN Established. Traffic is now filtered.");

        // In a real production app, you would read packets here.
        // For FYP Demo: Just setting the DNS is enough to demonstrate filtering.
        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (vpnThread != null) {
            vpnThread.interrupt();
        }
        try {
            if (vpnInterface != null) vpnInterface.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }
}