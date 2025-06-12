package com.example.pdfreader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var pdfPageView: ImageView
    private lateinit var pageInfo: TextView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button

    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var currentPageIndex = 0
    private var totalPages = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        pdfPageView = findViewById(R.id.pdfPageView)
        pageInfo = findViewById(R.id.pageInfo)
        prevButton = findViewById(R.id.prevButton)
        nextButton = findViewById(R.id.nextButton)
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.loadPdfButton).setOnClickListener {
            loadSamplePdf()
        }

        prevButton.setOnClickListener {
            if (currentPageIndex > 0) {
                showPage(currentPageIndex - 1)
            }
        }

        nextButton.setOnClickListener {
            if (currentPageIndex < totalPages - 1) {
                showPage(currentPageIndex + 1)
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

            showPage(0)

            Toast.makeText(this, "PDF loaded! Use Previous/Next buttons to navigate", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPage(pageIndex: Int) {
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
    }

    override fun onDestroy() {
        super.onDestroy()
        currentPage?.close()
        pdfRenderer?.close()
    }
}