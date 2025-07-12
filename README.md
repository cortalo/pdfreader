# Synchronized PDF Reader

This app lets two devices read a PDF book together over Bluetooth. One device shows left pages, the other shows right pages, just like a real book.

Tap the left device to go back two pages, tap the right device to go forward two pages, just like flipping through a real book.

<a href="https://play.google.com/store/apps/details?id=com.longheethz.pdftwinpage">
  <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="165">
</a>

## Quick Start

**Setup (Do this once):**
1. Pair both devices in Android Bluetooth settings first
2. Download the apk from release. Install the app on both devices

**Start Reading Session:**

**Device 1 (Server):**
1. Open the app
2. Grant Bluetooth permissions if asked
3. Tap "Start Server"
4. Wait for "Device connected!" message

**Device 2 (Client):**
1. Open the app
2. Grant Bluetooth permissions if asked
3. Tap "Select Device"
4. Choose the server device from the list
5. Wait for "Connected!" message

**Both Devices:**
1. Tap "Open PDF Reader"
2. Select the same PDF file
3. Start reading together!

**Reading:**
- Left device shows odd pages (1, 3, 5...)
- Right device shows even pages (2, 4, 6...)
- Tap left device to go back, tap right device to go forward


## Features

### 📱 Dual-Device Synchronization
- **Left-Right Page Layout**: One device displays left pages (odd numbers), the other displays right pages (even numbers)
- **Real-time Sync**: Navigation on one device automatically updates the other device
- **Role Assignment**: Server device shows left pages, client device shows right pages

### 🔗 Bluetooth Connectivity
- **Server Mode**: Start a server and wait for incoming connections
- **Client Mode**: Connect to paired devices with device selection dialog
- **Multiple Device Support**: Select from multiple paired devices when connecting
- **Auto-discovery**: Server mode makes device discoverable for easy pairing

### 📄 PDF Viewing
- **Touch Navigation**: Tap to navigate through pages
- **High-quality Rendering**: Uses Android's PdfRenderer for crisp page display
- **File Picker Integration**: Easy PDF selection from device storage
- **Responsive Display**: Pages scale to fit screen size

## How It Works

### Connection Setup
1. **Device A** (Server):
    - Grant Bluetooth permissions
    - Tap "Start Server"
    - Device becomes discoverable and waits for connections

2. **Device B** (Client):
    - Grant Bluetooth permissions
    - Ensure devices are paired via system Bluetooth settings
    - Tap "Select Device" and choose the server device
    - Connection established automatically

### Synchronized Reading
- **Server Device**: Shows left pages (1, 3, 5, 7...)
- **Client Device**: Shows right pages (2, 4, 6, 8...)
- **Navigation**: Tap on either device to move through the document
- **Automatic Sync**: Page changes are instantly reflected on both devices

## Installation

### Prerequisites
- Android 6.0 (API level 23) or higher
- Bluetooth capability on both devices
- Two Android devices for full functionality

### Setup Steps
1. Clone or download the project
2. Open in Android Studio
3. Build and install on both devices
4. Grant required permissions when prompted

### Required Permissions
- `BLUETOOTH` / `BLUETOOTH_CONNECT` (Android 12+)
- `BLUETOOTH_ADMIN` / `BLUETOOTH_ADVERTISE` (Android 12+)
- File access permissions for PDF selection

## Usage Guide

### First Time Setup
1. **Install** the app on both devices
2. **Pair devices** using system Bluetooth settings
3. **Launch** the app on both devices

### Starting a Session
1. **On Device 1 (Server)**:
    - Open the app
    - Tap "Grant Permissions" if needed
    - Tap "Start Server"
    - Wait for "Device connected!" message

2. **On Device 2 (Client)**:
    - Open the app
    - Tap "Grant Permissions" if needed
    - Tap "Select Device"
    - Choose the server device from the list
    - Wait for "Connected!" message

3. **On Either Device**:
    - Tap "Open PDF Reader"
    - Select a PDF file from your device
    - Begin synchronized reading!

### Navigation
- **Tap anywhere** on the PDF to navigate
- **Server device**: Tap to go to previous page pair
- **Client device**: Tap to go to next page pair
- **Automatic sync**: Both devices update simultaneously

## Technical Architecture

### Core Components
- **BluetoothPairingActivity**: Handles device pairing and connection setup
- **MainActivity**: PDF rendering and synchronized navigation
- **BluetoothConnectionManager**: Singleton for connection state management
- **ConnectedThread**: Manages Bluetooth communication between devices

### Communication Protocol
- Uses RFCOMM (Serial Port Profile) for Bluetooth communication
- Simple text-based messages for synchronization:
    - `PAGE_CHANGE:n` - Notifies page navigation
    - `PDF_LOADED:n` - Confirms PDF loading with page count

### PDF Rendering
- Utilizes Android's `PdfRenderer` class
- High-resolution bitmap rendering (2x scale)
- Efficient memory management with page caching

## Troubleshooting

### Connection Issues
- **"No paired devices found"**: Pair devices manually in Android Bluetooth settings
- **"Connection failed"**: Ensure server device is in server mode and discoverable
- **"Permission denied"**: Grant all Bluetooth permissions in app settings

### PDF Issues
- **"No file manager found"**: Install a file manager app from Play Store
- **"Error loading PDF"**: Ensure PDF file is not corrupted and accessible
- **Pages not syncing**: Check Bluetooth connection status

### Performance Tips
- **Large PDFs**: May take longer to load; be patient during initial loading
- **Battery**: Bluetooth communication may impact battery life
- **Range**: Keep devices within Bluetooth range (typically 10 meters)

## Development

### Project Structure
```
src/main/java/com/example/pdfreader/
├── BluetoothPairingActivity.java    # Connection setup UI
├── MainActivity.kt                   # PDF viewer and sync logic
├── BluetoothConnectionManager.java   # Connection state management
└── res/layout/
    ├── activity_bluetooth_pairing.xml
    └── activity_main.xml
```

### Key Technologies
- **Android SDK**: Core app development
- **Bluetooth API**: Device communication
- **PdfRenderer**: PDF display and rendering
- **Activity Result API**: File picker integration

### Building from Source
1. Open project in Android Studio
2. Sync Gradle dependencies
3. Build → Generate Signed Bundle/APK
4. Install on target devices

## Known Limitations
- Requires manual Bluetooth pairing before first use
- Limited to two devices per session
- PDF files must be stored locally on both devices
- Bluetooth range limitations apply

## Future Enhancements
- Support for more than two devices
- WiFi Direct connectivity option
- Cloud PDF synchronization
- Annotation sharing capabilities
- Presentation mode with pointer sync

## License
This project is provided as-is for educational and personal use.

## Support
For issues, questions, or contributions, please refer to the project repository or contact `longheethz@gmail.com`.