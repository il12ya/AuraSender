package com.example.aurasender

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var targetPackageEditText: EditText
    private lateinit var dailyLimitEditText: EditText
    private lateinit var minDelayEditText: EditText
    private lateinit var maxDelayEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val enableButton = findViewById<Button>(R.id.enableButton)
        targetPackageEditText = findViewById(R.id.targetPackageEditText)
        dailyLimitEditText = findViewById(R.id.dailyLimitEditText)
        minDelayEditText = findViewById(R.id.minDelayEditText)
        maxDelayEditText = findViewById(R.id.maxDelayEditText)
        val saveButton = findViewById<Button>(R.id.saveButton)

        loadSettings()
        updateServiceStatus(statusText, enableButton)

        enableButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        saveButton.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "✅ تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val statusText = findViewById<TextView>(R.id.statusText)
        val enableButton = findViewById<Button>(R.id.enableButton)
        updateServiceStatus(statusText, enableButton)
    }

    private fun loadSettings() {
        val sharedPref = getSharedPreferences("AuraLimits", MODE_PRIVATE)
        targetPackageEditText.setText(sharedPref.getString("target_package", ""))
        dailyLimitEditText.setText(sharedPref.getInt("daily_limit", 20).toString())
        minDelayEditText.setText(sharedPref.getInt("min_delay", 100).toString())
        maxDelayEditText.setText(sharedPref.getInt("max_delay", 700).toString())
    }

    private fun saveSettings() {
        val sharedPref = getSharedPreferences("AuraLimits", MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("target_package", targetPackageEditText.text.toString())
            putInt("daily_limit", dailyLimitEditText.text.toString().toIntOrNull() ?: 20)
            putInt("min_delay", minDelayEditText.text.toString().toIntOrNull() ?: 100)
            putInt("max_delay", maxDelayEditText.text.toString().toIntOrNull() ?: 700)
            apply()
        }
    }

    private fun updateServiceStatus(statusText: TextView, enableButton: Button) {
        val serviceEnabled = isAccessibilityServiceEnabled()
        if (serviceEnabled) {
            statusText.text = "✅ سرویس فعال است"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            enableButton.text = "باز کردن تنظیمات سرویس"
        } else {
            statusText.text = "❌ سرویس غیرفعال است"
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            enableButton.text = "فعال کردن سرویس دسترسی"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("com.example.aurasender/.AuraService")
    }
}
