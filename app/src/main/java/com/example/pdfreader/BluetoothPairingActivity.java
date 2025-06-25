package com.example.pdfreader;

import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothServerSocket;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import android.Manifest;
import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.os.Handler;
import android.os.Looper;

public class BluetoothPairingActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private TextView statusText;
    private Button toggleButton;
    private Button serverButton;
    private Button connectButton;
    private Button openPdfButton;

    // Connection management
    private BluetoothDevice pairedDevice;
    private BluetoothSocket bluetoothSocket;
    private BluetoothServerSocket serverSocket;
    private ConnectedThread connectedThread;
    private ServerThread serverThread;
    private boolean isServer = false;

    // Standard UUID for SPP (Serial Port Profile)
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String SERVICE_NAME = "PDFReaderSync";

    // Activity result launchers
    private ActivityResultLauncher<Intent> bluetoothEnableLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_pairing);

        statusText = findViewById(R.id.statusText);
        toggleButton = findViewById(R.id.toggleButton);
        serverButton = findViewById(R.id.serverButton);
        connectButton = findViewById(R.id.connectButton);
        openPdfButton = findViewById(R.id.openPdfButton);

        // Initialize Bluetooth
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Initialize activity result launchers
        bluetoothEnableLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Bluetooth enable request denied", Toast.LENGTH_SHORT).show();
                    }
                    updateStatus();
                    updateButtons();
                }
        );

        // Permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean allGranted = true;
                    for (Boolean granted : permissions.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }

                    if (allGranted) {
                        Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
                        updateStatus();
                        updateButtons();
                    } else {
                        Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_LONG).show();
                        statusText.setText("Bluetooth permissions required");
                        disableAllButtons();
                    }
                }
        );

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            statusText.setText("Bluetooth not supported on this device");
            disableAllButtons();
            return;
        }

        // Set up button click listeners
        // In onCreate() method, modify the toggleButton listener:
        toggleButton.setOnClickListener(v -> {
            if (!hasBluetoothPermissions()) {
                requestBluetoothPermissions();
            } else {
                Toast.makeText(this, "Bluetooth permissions already granted", Toast.LENGTH_SHORT).show();
            }
        });

        serverButton.setOnClickListener(v -> {
            if (isServer) {
                stopServer();
            } else {
                startServer();
            }
        });

        connectButton.setOnClickListener(v -> {
            if (isConnected()) {
                disconnect();
            } else {
                connectToPairedDevice();
            }
        });

        openPdfButton.setOnClickListener(v -> openPdfReader());

        // Initial setup
        if (hasBluetoothPermissions()) {
            updateStatus();
            updateButtons();
        } else {
            statusText.setText("Bluetooth permissions required");
            toggleButton.setText("Grant Permissions");
            disableAllButtons();
        }
    }

    private void openPdfReader() {
        if (isConnected()) {
            // Store connection in singleton
            BluetoothConnectionManager.getInstance().setBluetoothConnection(bluetoothSocket, isServer);

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Please connect to another device first", Toast.LENGTH_LONG).show();
        }
    }

    private void startServer() {
        System.out.println("DEBUG: Starting Bluetooth server...");

        if (!hasBluetoothPermissions()) {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth must be enabled first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Make device discoverable with proper permission handling
        try {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300); // 5 minutes
            startActivity(discoverableIntent);
        } catch (SecurityException e) {
            System.out.println("DEBUG: SecurityException making device discoverable: " + e.getMessage());
            Toast.makeText(this, "Permission denied for making device discoverable", Toast.LENGTH_LONG).show();
            // Don't return here - we can still start the server without making it discoverable
            // The user can manually make the device discoverable through system settings
            statusText.setText("⚠️ Starting server without discoverable mode\nOther devices may need to manually find this device");
        }

        isServer = true;
        statusText.setText("🔵 Starting server...");

        serverThread = new ServerThread();
        serverThread.start();

        updateButtons();
    }

    private void stopServer() {
        System.out.println("DEBUG: Stopping server...");

        isServer = false;

        if (serverThread != null) {
            serverThread.cancel();
            serverThread = null;
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.out.println("DEBUG: Error closing server socket: " + e.getMessage());
            }
            serverSocket = null;
        }

        disconnect(); // Also disconnect any active connection

        statusText.setText("🔴 Server stopped");
        updateButtons();
    }

    // Server thread to listen for incoming connections
    private class ServerThread extends Thread {
        public ServerThread() {
        }

        public void run() {
            try {
                System.out.println("DEBUG: Creating server socket...");
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SPP_UUID);

                runOnUiThread(() -> {
                    statusText.setText("🟢 Server listening...\nWaiting for incoming connections\nOther device can now connect to this phone");
                    Toast.makeText(BluetoothPairingActivity.this, "Server started - waiting for connections", Toast.LENGTH_LONG).show();
                });

                System.out.println("DEBUG: Server socket created, waiting for connection...");

                // This is a blocking call - it will wait until a connection is made
                BluetoothSocket socket = serverSocket.accept();

                System.out.println("DEBUG: Incoming connection accepted!");

                runOnUiThread(() -> {
                    bluetoothSocket = socket;
                    statusText.setText("✅ Device connected!\nReady to open PDF Reader");
                    Toast.makeText(BluetoothPairingActivity.this, "Device connected! You can now open PDF Reader.", Toast.LENGTH_LONG).show();

                    // Create a dummy connected thread just to satisfy isConnected() check
                    // But don't actually listen for messages (MainActivity will do that)
                    connectedThread = new ConnectedThread(bluetoothSocket);
                    // Don't start the thread! Just create it for the null check

                    updateButtons();
                });

            } catch (SecurityException e) {
                System.out.println("DEBUG: SecurityException in server: " + e.getMessage());
                runOnUiThread(() -> {
                    statusText.setText("❌ Server failed: Permission denied");
                    Toast.makeText(BluetoothPairingActivity.this, "Permission denied for server", Toast.LENGTH_SHORT).show();
                    isServer = false;
                    updateButtons();
                });
            } catch (IOException e) {
                System.out.println("DEBUG: IOException in server: " + e.getMessage());
                if (isServer) { // Only show error if we're still supposed to be running
                    runOnUiThread(() -> {
                        statusText.setText("❌ Server error: " + e.getMessage());
                        Toast.makeText(BluetoothPairingActivity.this, "Server error", Toast.LENGTH_SHORT).show();
                        isServer = false;
                        updateButtons();
                    });
                }
            }
        }

        public void cancel() {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (IOException e) {
                System.out.println("DEBUG: Error closing server socket: " + e.getMessage());
            }
        }
    }

    private void connectToPairedDevice() {
        System.out.println("DEBUG: Looking for paired devices...");

        if (!hasBluetoothPermissions()) {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Bluetooth must be enabled first", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            System.out.println("DEBUG: Found " + pairedDevices.size() + " paired devices");

            if (pairedDevices.isEmpty()) {
                statusText.setText("No paired devices found.\nPlease pair a device in Bluetooth settings first.\n\nOr use 'Start Server' mode and let the other device connect to you.");
                Toast.makeText(this, "No paired devices found", Toast.LENGTH_LONG).show();
                return;
            }

            // Get the first (and presumably only) paired device
            pairedDevice = pairedDevices.iterator().next();
            String deviceName = pairedDevice.getName();
            String deviceAddress = pairedDevice.getAddress();

            System.out.println("DEBUG: Connecting to: " + deviceName + " (" + deviceAddress + ")");
            statusText.setText("🔵 Connecting to: " + (deviceName != null ? deviceName : "Unknown Device") + "\n" + deviceAddress + "\n\nMake sure the other device is in server mode!");

            connectButton.setText("Connecting...");
            connectButton.setEnabled(false);

            // Connect in background thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        System.out.println("DEBUG: Creating RFCOMM socket...");
                        bluetoothSocket = pairedDevice.createRfcommSocketToServiceRecord(SPP_UUID);

                        System.out.println("DEBUG: Attempting to connect...");
                        bluetoothSocket.connect();

                        System.out.println("DEBUG: Connection successful!");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    String deviceName = pairedDevice.getName();
                                    statusText.setText("✅ Connected to: " + (deviceName != null ? deviceName : "Unknown Device") + "\nReady to open PDF Reader");
                                    Toast.makeText(BluetoothPairingActivity.this, "Connected! You can now open PDF Reader.", Toast.LENGTH_LONG).show();

                                    // Create a dummy connected thread just to satisfy isConnected() check
                                    connectedThread = new ConnectedThread(bluetoothSocket);
                                    // Don't start the thread! Just create it for the null check

                                    updateButtons();

                                } catch (SecurityException e) {
                                    System.out.println("DEBUG: SecurityException updating UI: " + e.getMessage());
                                }
                            }
                        });

                    } catch (SecurityException e) {
                        System.out.println("DEBUG: SecurityException during connection: " + e.getMessage());
                        runOnUiThread(() -> {
                            statusText.setText("❌ Connection failed: Permission denied");
                            Toast.makeText(BluetoothPairingActivity.this, "Permission denied for connection", Toast.LENGTH_SHORT).show();
                            updateButtons();
                        });
                    } catch (IOException e) {
                        System.out.println("DEBUG: IOException during connection: " + e.getMessage());
                        runOnUiThread(() -> {
                            statusText.setText("❌ Connection failed: " + e.getMessage() + "\n\n💡 Try:\n1. Make sure other device is in server mode\n2. Use 'Start Server' on this device instead");
                            Toast.makeText(BluetoothPairingActivity.this, "Connection failed - try server mode instead", Toast.LENGTH_LONG).show();
                            updateButtons();
                        });
                    }
                }
            }).start();

        } catch (SecurityException e) {
            System.out.println("DEBUG: SecurityException getting paired devices: " + e.getMessage());
            statusText.setText("Permission denied accessing paired devices");
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnect() {
        System.out.println("DEBUG: Disconnecting...");

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                System.out.println("DEBUG: Error closing socket: " + e.getMessage());
            }
            bluetoothSocket = null;
        }

        pairedDevice = null;

        if (isServer) {
            statusText.setText("🟢 Server listening...\nWaiting for incoming connections");
        } else {
            statusText.setText("Disconnected");
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        }

        updateButtons();
    }

    private boolean isConnected() {
        return bluetoothSocket != null && bluetoothSocket.isConnected();
    }

    // Thread for managing Bluetooth connection
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final Handler handler;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            this.handler = new Handler(Looper.getMainLooper());
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                System.out.println("DEBUG: Error creating streams: " + e.getMessage());
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            System.out.println("DEBUG: ConnectedThread in BTPA - not listening (MainActivity will handle messages)");
            // Don't listen for messages here - MainActivity will handle all communication
            // Just keep the thread alive to maintain connection state
            try {
                Thread.sleep(Long.MAX_VALUE); // Keep thread alive but inactive
            } catch (InterruptedException e) {
                System.out.println("DEBUG: BTPA ConnectedThread interrupted");
            }
        }

        public void write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                System.out.println("DEBUG: Error writing data: " + e.getMessage());
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("DEBUG: Error closing connected socket: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bluetoothAdapter != null && hasBluetoothPermissions()) {
            updateStatus();
            updateButtons();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer();
        disconnect();
    }

    private void disableAllButtons() {
        serverButton.setEnabled(false);
        connectButton.setEnabled(false);
        openPdfButton.setEnabled(false);
    }

    /**
     * Check if all required Bluetooth permissions are granted
     */
    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            boolean hasConnect = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            boolean hasAdvertise = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED;

            return hasConnect && hasAdvertise; // Need ADVERTISE for server mode
        } else {
            boolean hasBluetooth = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED;
            boolean hasBluetoothAdmin = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED;

            return hasBluetooth && hasBluetoothAdmin;
        }
    }

    /**
     * Request the required Bluetooth permissions
     */
    private void requestBluetoothPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            permissions = new String[] {
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
            };
        } else {
            permissions = new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
            };
        }

        System.out.println("DEBUG: Requesting permissions: " + java.util.Arrays.toString(permissions));
        permissionLauncher.launch(permissions);
    }


    private void updateStatus() {
        if (bluetoothAdapter == null) {
            statusText.setText("Bluetooth not supported on this device");
            return;
        }

        if (!hasBluetoothPermissions()) {
            statusText.setText("Bluetooth permissions required\nTap 'Grant Permissions' button");
            return;
        }

        try {
            if (bluetoothAdapter.isEnabled()) {
                if (isConnected()) {
                    // Status already set in connection method
                    return;
                } else if (isServer) {
                    statusText.setText("🟢 Server mode active\nWaiting for connections...");
                } else {
                    statusText.setText("Bluetooth is enabled\n\n💡 Choose mode:\n• 'Start Server' - let other device connect to you\n• 'Connect' - connect to paired device");
                }
            } else {
                statusText.setText("Bluetooth is disabled\nPlease enable Bluetooth in system settings to continue");
            }
        } catch (SecurityException e) {
            statusText.setText("Permission denied checking Bluetooth status");
        }
    }

    private void updateButtons() {
        if (bluetoothAdapter == null) {
            toggleButton.setText("Not Available");
            toggleButton.setEnabled(false);
            disableAllButtons();
            return;
        }

        if (!hasBluetoothPermissions()) {
            toggleButton.setText("Grant Permissions");
            toggleButton.setEnabled(true);
            disableAllButtons();
            return;
        }

        // Once permissions are granted, hide or disable the button
        toggleButton.setText("Permissions Granted");
        toggleButton.setEnabled(false);

        try {
            if (bluetoothAdapter.isEnabled()) {
                if (isServer) {
                    serverButton.setText("Stop Server");
                    serverButton.setEnabled(true);
                    connectButton.setEnabled(false);
                } else {
                    serverButton.setText("Start Server");
                    serverButton.setEnabled(true);

                    if (isConnected()) {
                        connectButton.setText("Disconnect");
                        connectButton.setEnabled(true);
                    } else {
                        connectButton.setText("Connect to Device");
                        connectButton.setEnabled(true);
                    }
                }

                openPdfButton.setEnabled(isConnected());

            } else {
                serverButton.setText("Start Server");
                connectButton.setText("Connect to Device");
                disableAllButtons();
            }
        } catch (SecurityException e) {
            toggleButton.setText("Grant Permissions");
            toggleButton.setEnabled(true);
            disableAllButtons();
        }
    }
}