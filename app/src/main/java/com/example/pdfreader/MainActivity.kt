package com.example.pdfreader

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rajat.pdfviewer.PdfViewerActivity
import com.rajat.pdfviewer.util.saveTo

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the online PDF button
        val onlinePdfButton = findViewById<Button>(R.id.onlinePdf)
        onlinePdfButton.setOnClickListener {
            openOnlinePdf()
        }
    }

    private fun openOnlinePdf() {
        val pdfUrl = "https://css4.pub/2015/usenix/example.pdf"

        Toast.makeText(this, "Opening PDF...", Toast.LENGTH_SHORT).show()

        startActivity(
            PdfViewerActivity.launchPdfFromUrl(
                context = this,
                pdfUrl = pdfUrl,
                pdfTitle = "Sample PDF",
                saveTo = saveTo.DOWNLOADS,
                enableDownload = true
            )
        )
    }
}