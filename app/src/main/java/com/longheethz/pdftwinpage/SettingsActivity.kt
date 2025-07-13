package com.longheethz.pdftwinpage

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var readingModeGroup: RadioGroup
    private lateinit var modeOddEven: RadioButton
    private lateinit var modeSequential: RadioButton
    private lateinit var saveButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "PDFTwinPagePrefs"
        const val READING_MODE_KEY = "reading_mode"
        const val MODE_ODD_EVEN = "odd_even"
        const val MODE_SEQUENTIAL = "sequential"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupPreferences()
        loadCurrentSettings()
        setupListeners()
    }

    private fun initViews() {
        readingModeGroup = findViewById(R.id.readingModeGroup)
        modeOddEven = findViewById(R.id.modeOddEven)
        modeSequential = findViewById(R.id.modeSequential)
        saveButton = findViewById(R.id.saveButton)
    }

    private fun setupPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private fun loadCurrentSettings() {
        val currentMode = sharedPreferences.getString(READING_MODE_KEY, MODE_ODD_EVEN)
        when (currentMode) {
            MODE_ODD_EVEN -> modeOddEven.isChecked = true
            MODE_SEQUENTIAL -> modeSequential.isChecked = true
        }
    }

    private fun setupListeners() {
        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val selectedMode = when (readingModeGroup.checkedRadioButtonId) {
            R.id.modeOddEven -> MODE_ODD_EVEN
            R.id.modeSequential -> MODE_SEQUENTIAL
            else -> MODE_ODD_EVEN
        }

        sharedPreferences.edit()
            .putString(READING_MODE_KEY, selectedMode)
            .apply()

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
        finish()
    }
}