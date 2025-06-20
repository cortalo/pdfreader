package com.example.pdfreader;

import android.bluetooth.BluetoothSocket;

public class BluetoothConnectionManager {
    private static BluetoothConnectionManager instance;
    private BluetoothSocket bluetoothSocket;
    private boolean isServer;

    private BluetoothConnectionManager() {}

    public static synchronized BluetoothConnectionManager getInstance() {
        if (instance == null) {
            instance = new BluetoothConnectionManager();
        }
        return instance;
    }

    public void setBluetoothConnection(BluetoothSocket socket, boolean isServer) {
        this.bluetoothSocket = socket;
        this.isServer = isServer;
    }

    public BluetoothSocket getBluetoothSocket() {
        return bluetoothSocket;
    }

    public boolean isServer() {
        return isServer;
    }

    public boolean isConnected() {
        return bluetoothSocket != null && bluetoothSocket.isConnected();
    }
}