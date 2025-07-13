package com.longheethz.pdftwinpage

import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.concurrent.thread
import androidx.activity.result.contract.ActivityResultContracts
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var pdfPageView: ImageView
    private lateinit var pageInfo: TextView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var connectionStatus: TextView

    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var totalPages = 0

    // Bluetooth communication
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectedThread: ConnectedThread? = null
    private var isServer = false
    private var isSyncing = false // Flag to prevent infinite loops

    // Add this property
    private var isLeftPage = true // Server shows left pages, client shows right pages
    private lateinit var sharedPreferences: SharedPreferences
    private var readingMode = SettingsActivity.MODE_ODD_EVEN

    // Add this property at the top of the class
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            loadPdfFromUri(selectedUri)
        }
    }

    private fun openFilePicker() {
        try {
            filePickerLauncher.launch("application/pdf")
        } catch (e: Exception) {
            Toast.makeText(this, "No file manager found. Please install a file manager app.", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadPdfFromUri(uri: android.net.Uri) {
        Toast.makeText(this, "Loading PDF...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                // Copy the PDF from URI to internal storage
                val inputStream = contentResolver.openInputStream(uri)
                val pdfFile = File(filesDir, "selected.pdf")
                val outputStream = FileOutputStream(pdfFile)

                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }

                runOnUiThread {
                    openPdf(pdfFile)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadSettings()
        setupBluetooth()
        setupListeners()
        loadSamplePdf()
    }

    override fun onResume() {
        super.onResume()
        // Reload settings when returning from SettingsActivity
        val previousMode = readingMode
        loadSettings()
        
        // If reading mode changed, sync with the other device
        if (previousMode != readingMode && totalPages > 0) {
            sendSyncMessage("MODE_CHANGE:$readingMode")
        }
    }

    private fun initViews() {
        pdfPageView = findViewById(R.id.pdfPageView)
        pdfPageView.setBackgroundColor(android.graphics.Color.WHITE)
    }

    private fun loadSettings() {
        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        readingMode = sharedPreferences.getString(SettingsActivity.READING_MODE_KEY, SettingsActivity.MODE_ODD_EVEN) ?: SettingsActivity.MODE_ODD_EVEN
    }

    private fun setupBluetooth() {
        // Get Bluetooth connection from singleton
        val connectionManager = BluetoothConnectionManager.getInstance()
        bluetoothSocket = connectionManager.bluetoothSocket
        isServer = connectionManager.isServer()

        // Server shows left pages (odd page numbers: 1,3,5...), Client shows right pages (even page numbers: 2,4,6...)
        isLeftPage = isServer

        if (connectionManager.isConnected()) {
            // Start communication thread
            connectedThread = ConnectedThread(bluetoothSocket!!)
            connectedThread!!.start()
        } else {
            finish() // Go back to pairing screen if no connection
        }
    }

    private fun setupListeners() {
        // Touch navigation on PDF page
        pdfPageView.setOnClickListener { view ->
            if (totalPages == 0) return@setOnClickListener

            when (readingMode) {
                SettingsActivity.MODE_ODD_EVEN -> {
                    // Server (left page) - tap to go previous
                    if (isLeftPage) {
                        if (currentPageIndex >= 2) {
                            showPage(currentPageIndex - 2, true)
                        }
                    }
                    // Client (right page) - tap to go next
                    else {
                        if (currentPageIndex + 2 < totalPages) {
                            showPage(currentPageIndex + 2, true)
                        }
                    }
                }
                SettingsActivity.MODE_SEQUENTIAL -> {
                    // Sequential mode - advance by 1 page
                    if (isLeftPage) {
                        // Server goes backward by 1
                        if (currentPageIndex >= 1) {
                            showPage(currentPageIndex - 1, true)
                        }
                    } else {
                        // Client goes forward by 1
                        if (currentPageIndex + 1 < totalPages) {
                            showPage(currentPageIndex + 1, true)
                        }
                    }
                }
            }
        }

        // Long press to open settings
        pdfPageView.setOnLongClickListener { view ->
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            true
        }

        // Auto-open file picker when activity starts
        // openFilePicker()
    }

    private fun loadSamplePdf() {
        openFilePicker()
    }

    private fun openPdf(file: File) {
        try {
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            totalPages = pdfRenderer!!.pageCount

            // Calculate start page based on reading mode
            val startPage = when (readingMode) {
                SettingsActivity.MODE_ODD_EVEN -> {
                    // Server starts on page 0 (left), Client starts on page 1 (right)
                    if (isLeftPage) 0 else 1
                }
                SettingsActivity.MODE_SEQUENTIAL -> {
                    // Server starts on page 0, Client starts on page 1
                    if (isLeftPage) 0 else 1
                }
                else -> if (isLeftPage) 0 else 1
            }
            showPage(startPage, false) // Don't send sync message on initial load

            // Send PDF loaded message to other device
            sendSyncMessage("PDF_LOADED:$totalPages")

        } catch (e: Exception) {
            println("DEBUG: Error opening PDF: ${e.message}")
        }
    }

    private fun showPage(pageIndex: Int, sendSync: Boolean) {
        if (pageIndex < 0 || pageIndex >= totalPages || pdfRenderer == null) {
            return
        }

        currentPage?.close()

        currentPage = pdfRenderer?.openPage(pageIndex)
        currentPageIndex = pageIndex

        // Create bitmap for the page
        val page = currentPage!!
        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)

        // Fill with white background first
        bitmap.eraseColor(android.graphics.Color.WHITE)

        // Render the page
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // Display the page
        pdfPageView.setImageBitmap(bitmap)

        // Send sync message to other device if requested and not already syncing
        if (sendSync && !isSyncing) {
            sendSyncMessage("PAGE_CHANGE:$pageIndex")
        }
    }

    private fun sendSyncMessage(message: String) {
        connectedThread?.write(message.toByteArray())
        println("DEBUG: Sent sync message: $message")
    }

    private fun handleReceivedMessage(message: String) {
        println("DEBUG: Received sync message: $message")

        when {
            message.startsWith("PAGE_CHANGE:") -> {
                println("DEBUG: is PAGE_CHANGE: $message")
                val otherDevicePageIndex = message.substringAfter("PAGE_CHANGE:").toIntOrNull()
                if (otherDevicePageIndex != null) {
                    // Calculate what page this device should show based on reading mode
                    val myPageIndex = when (readingMode) {
                        SettingsActivity.MODE_ODD_EVEN -> {
                            if (isLeftPage) {
                                otherDevicePageIndex - 1
                            } else {
                                otherDevicePageIndex + 1
                            }
                        }
                        SettingsActivity.MODE_SEQUENTIAL -> {
                            if (isLeftPage) {
                                otherDevicePageIndex - 1
                            } else {
                                otherDevicePageIndex + 1
                            }
                        }
                        else -> {
                            if (isLeftPage) {
                                otherDevicePageIndex - 1
                            } else {
                                otherDevicePageIndex + 1
                            }
                        }
                    }

                    if (myPageIndex >= 0 && myPageIndex < totalPages && myPageIndex != currentPageIndex) {
                        isSyncing = true
                        runOnUiThread {
                            showPage(myPageIndex, false) // Don't send sync message back
                        }
                        Handler(Looper.getMainLooper()).postDelayed({
                            isSyncing = false
                        }, 500)
                    }
                }
            }
            message.startsWith("PDF_LOADED:") -> {
                // Just log, no UI feedback
                println("DEBUG: Other device loaded PDF")
            }
            message.startsWith("MODE_CHANGE:") -> {
                val newMode = message.substringAfter("MODE_CHANGE:")
                if (newMode != readingMode) {
                    readingMode = newMode
                    // Update SharedPreferences to keep both devices in sync
                    sharedPreferences.edit()
                        .putString(SettingsActivity.READING_MODE_KEY, newMode)
                        .apply()
                    println("DEBUG: Reading mode changed to: $newMode")
                }
            }
        }
    }

    // Thread for managing Bluetooth communication
    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream?
        private val outputStream: OutputStream?
        private val handler = Handler(Looper.getMainLooper())

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                println("DEBUG: Error creating streams: ${e.message}")
            }

            inputStream = tmpIn
            outputStream = tmpOut
        }

        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int

            println("DEBUG: MainActivity ConnectedThread started, listening for sync messages...")

            while (true) {
                try {
                    bytes = inputStream!!.read(buffer)
                    val receivedMessage = String(buffer, 0, bytes).trim()

                    println("DEBUG: Received message: $receivedMessage")

                    // Handle the received message
                    handleReceivedMessage(receivedMessage)

                } catch (e: IOException) {
                    println("DEBUG: Connection lost: ${e.message}")
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                outputStream?.write(bytes)
            } catch (e: IOException) {
                println("DEBUG: Error writing data: ${e.message}")
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (e: IOException) {
                println("DEBUG: Error closing connected socket: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPage?.close()
        pdfRenderer?.close()
        connectedThread?.cancel()
        bluetoothSocket?.close()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Clean up connections when going back
        connectedThread?.cancel()
        bluetoothSocket?.close()
    }
}