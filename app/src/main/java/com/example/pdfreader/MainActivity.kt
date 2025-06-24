package com.example.pdfreader

import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupBluetooth()
        setupListeners()
        loadSamplePdf()
    }

    private fun initViews() {
        pdfPageView = findViewById(R.id.pdfPageView)
        pageInfo = findViewById(R.id.pageInfo)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
        connectionStatus = findViewById(R.id.connectionStatus)
    }

    private fun setupBluetooth() {
        // Get Bluetooth connection from singleton
        val connectionManager = BluetoothConnectionManager.getInstance()
        bluetoothSocket = connectionManager.bluetoothSocket
        isServer = connectionManager.isServer()

        if (connectionManager.isConnected()) {
            connectionStatus.text = "📶 Connected - Pages will sync automatically"
            // Start communication thread
            connectedThread = ConnectedThread(bluetoothSocket!!)
            connectedThread!!.start()
        } else {
            connectionStatus.text = "❌ Not connected"
            finish() // Go back to pairing screen if no connection
        }
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.loadPdfButton).setOnClickListener {
            loadSamplePdf()
        }

        prevButton.setOnClickListener {
            if (currentPageIndex > 0) {
                showPage(currentPageIndex - 1, true) // true = send sync message
            }
        }

        nextButton.setOnClickListener {
            if (currentPageIndex < totalPages - 1) {
                showPage(currentPageIndex + 1, true) // true = send sync message
            }
        }
    }

    private fun loadSamplePdf() {
        Toast.makeText(this, "Loading PDF...", Toast.LENGTH_SHORT).show()

        thread {
            try {
                // Download a sample PDF
                val url = URL("https://css4.pub/2015/usenix/example.pdf")
                val connection = url.openConnection() as HttpURLConnection
                val inputStream: InputStream = connection.inputStream

                // Save to internal storage
                val pdfFile = File(filesDir, "sample.pdf")
                val outputStream = FileOutputStream(pdfFile)
                inputStream.copyTo(outputStream)
                outputStream.close()
                inputStream.close()
                connection.disconnect()

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

    private fun openPdf(file: File) {
        try {
            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            totalPages = pdfRenderer!!.pageCount

            showPage(0, false) // Don't send sync message on initial load

            Toast.makeText(this, "PDF loaded! Navigation will sync between devices", Toast.LENGTH_LONG).show()

            // Send PDF loaded message to other device
            sendSyncMessage("PDF_LOADED:$totalPages")

        } catch (e: Exception) {
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
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
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // Display the page
        pdfPageView.setImageBitmap(bitmap)

        // Update UI
        pageInfo.text = "Page ${pageIndex + 1} of $totalPages"
        prevButton.isEnabled = pageIndex > 0
        nextButton.isEnabled = pageIndex < totalPages - 1

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
        println("DEBUG: PAGE_CHANGE flag")

        when {
            message.startsWith("PAGE_CHANGE:") -> {
                println("DEBUG: is PAGE_CHANGE: $message")
                val pageIndex = message.substringAfter("PAGE_CHANGE:").toIntOrNull()
                if (pageIndex != null && pageIndex != currentPageIndex) {
                    // Set syncing flag to prevent infinite loop
                    isSyncing = true
                    runOnUiThread {
                        showPage(pageIndex, false) // Don't send sync message back
                        Toast.makeText(this, "Synced to page ${pageIndex + 1}", Toast.LENGTH_SHORT).show()
                    }
                    // Reset syncing flag after a short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        isSyncing = false
                    }, 500)
                }
            }
            message.startsWith("PDF_LOADED:") -> {
                val pages = message.substringAfter("PDF_LOADED:").toIntOrNull()
                runOnUiThread {
                    Toast.makeText(this, "Other device loaded PDF with $pages pages", Toast.LENGTH_SHORT).show()
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
                    handler.post {
                        connectionStatus.text = "❌ Connection lost"
                        Toast.makeText(this@MainActivity, "Bluetooth connection lost", Toast.LENGTH_SHORT).show()
                    }
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

    override fun onBackPressed() {
        super.onBackPressed()
        // Clean up connections when going back
        connectedThread?.cancel()
        bluetoothSocket?.close()
    }
}