package com.linktomac.companion;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LinktoMac";
    private static final int DISCOVERY_PORT = 27191;
    private static final int PAIRING_PORT = 27192;
    private static final int REQUEST_MEDIA_PROJECTION = 1001;

    private String currentPin;
    private boolean isRunning = true;
    private boolean isPaired = false;
    private TextView pinDisplay;
    private TextView statusText;
    private Button btnStartMirroring;
    private Button btnDisconnect;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pinDisplay = findViewById(R.id.pin_display);
        statusText = findViewById(R.id.status_text);
        btnStartMirroring = findViewById(R.id.btn_start_mirroring);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        resetToPairingState();

        btnStartMirroring.setOnClickListener(v -> {
            requestScreenCapture();
        });

        btnDisconnect.setOnClickListener(v -> {
            stopMirroring();
            resetToPairingState();
        });

        startNsdRegistration();
        startPairingServer();
    }

    private void resetToPairingState() {
        currentPin = String.format("%04d", new Random().nextInt(10000));
        pinDisplay.setText(currentPin);
        pinDisplay.setTextSize(48);
        statusText.setText("Enter this code on your Mac to connect.");
        btnStartMirroring.setVisibility(View.GONE);
        btnDisconnect.setVisibility(View.GONE);
        isPaired = false;
    }

    private void setPairedState() {
        isPaired = true;
        statusText.setText("Paired! Tap \"Start Mirroring\" to begin screen capture.");
        btnStartMirroring.setVisibility(View.VISIBLE);
    }

    private void setMirroringState() {
        statusText.setText("Mirroring active - screen is being captured");
        btnStartMirroring.setVisibility(View.GONE);
        btnDisconnect.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        // Unregister NSD service
        NsdManager nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        if (nsdManager != null && registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (Exception e) {
                Log.e(TAG, "NSD unregistration failed", e);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.i(TAG, "Screen capture permission granted");
                MirrorService.startService(this, resultCode, data);
                setMirroringState();
            } else {
                Log.w(TAG, "Screen capture permission denied");
                statusText.setText("Permission denied. Tap to retry.");
            }
        }
    }

    private void stopMirroring() {
        Intent serviceIntent = new Intent(this, MirrorService.class);
        stopService(serviceIntent);
        Log.i(TAG, "Mirroring stopped");
    }

    private void requestScreenCapture() {
        Intent captureIntent = projectionManager.createScreenCaptureIntent();
        startActivityForResult(captureIntent, REQUEST_MEDIA_PROJECTION);
    }

    private NsdManager.RegistrationListener registrationListener;

    private void startNsdRegistration() {
        String deviceName = Build.MODEL;
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(deviceName);
        serviceInfo.setServiceType("_linktomac._tcp.");
        serviceInfo.setPort(PAIRING_PORT);
        
        // Use TXT records to pass deviceId
        serviceInfo.setAttribute("device_id", deviceId);

        NsdManager nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
                Log.i(TAG, "mDNS Service registered: " + NsdServiceInfo.getServiceName());
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "mDNS Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo arg0) {
                Log.i(TAG, "mDNS Service unregistered");
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "mDNS Unregistration failed: " + errorCode);
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
    }

    private void startPairingServer() {
        new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new java.net.InetSocketAddress(PAIRING_PORT));
                try {
                while (isRunning) {
                    try (Socket client = serverSocket.accept()) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                        OutputStream out = client.getOutputStream();

                        String line = reader.readLine();
                        if (line != null) {
                            String[] parts = line.split(":");
                            if (parts.length >= 3 && "PAIR".equals(parts[0]) && currentPin.equals(parts[1])) {
                                out.write("SUCCESS\n".getBytes());
                                out.flush();
                                
                                runOnUiThread(() -> {
                                    pinDisplay.setText("Paired with " + parts[2]);
                                    pinDisplay.setTextSize(24);
                                    setPairedState();
                                });
                            } else {
                                out.write("INVALID_PIN\n".getBytes());
                                out.flush();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Client error", e);
                    }
                }
                serverSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Server loop exited", e);
            }
            } catch (Exception e) {
                Log.e(TAG, "Server setup failed", e);
            }
        }).start();
    }
}