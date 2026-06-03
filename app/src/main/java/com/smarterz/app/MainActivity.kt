package com.smarterz.app

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.smarterz.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var smarterzApp: SmarterzApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make status bar transparent and content full screen
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        smarterzApp = SmarterzApp(this, binding)
        smarterzApp.initialize()

        setupSearch()
        setupNavigation()
    }

    private fun setupSearch() {
        binding.searchButton.setOnClickListener {
            val query = binding.searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                hideKeyboard()
                smarterzApp.performSearch(query)
            } else {
                Toast.makeText(this, "Enter a search term", Toast.LENGTH_SHORT).show()
            }
        }

        binding.searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val query = binding.searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard()
                    smarterzApp.performSearch(query)
                }
                true
            } else false
        }
    }

    private fun setupNavigation() {
        binding.homeLogo.setOnClickListener {
            binding.searchInput.setText("")
            smarterzApp.showHome()
        }

        binding.prevPageBtn.setOnClickListener { smarterzApp.onPrevPage() }
        binding.nextPageBtn.setOnClickListener { smarterzApp.onNextPage() }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.searchInput.windowToken, 0)
    }

    override fun onBackPressed() {
        when {
            smarterzApp.isPlayerOpen() -> smarterzApp.closePlayer()
            smarterzApp.isDetailOpen() -> smarterzApp.showHome()
            smarterzApp.isSearchOpen() -> {
                binding.searchInput.setText("")
                smarterzApp.showHome()
            }
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smarterzApp.destroy()
    }
}
