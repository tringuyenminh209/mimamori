package ecccomp.s2240788.mimamori.ui

import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.observe
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import ecccomp.s2240788.mimamori.R
import ecccomp.s2240788.mimamori.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusDot: View
    private lateinit var connectionStatusText: android.widget.TextView
    val viewModel: MainViewModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[MainViewModel::class.java]
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Initialize header views
        statusDot = findViewById(R.id.statusDot)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        
        // Setup Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        
        // Setup Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setupWithNavController(navController)

        val appBarLayout = findViewById<AppBarLayout>(R.id.appBarLayout)
        val navHostContainer = findViewById<View>(R.id.nav_host_fragment)
        
        // Initialize connection status
        updateConnectionStatus(false) // Start with disconnected

        viewModel.isConnected.observe(this) { connected ->
            updateConnectionStatus(connected)
        }
        
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, v.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, systemBars.bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(navHostContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, 0)
            insets
        }
    }
    
    /**
     * Update connection status in header
     * @param connected true if connected, false if disconnected
     */
    fun updateConnectionStatus(connected: Boolean) {
        runOnUiThread {
            if (connected) {
                statusDot.setBackgroundResource(R.drawable.status_dot_connected)
                connectionStatusText.text = getString(R.string.connection_status_connected)
            } else {
                statusDot.setBackgroundResource(R.drawable.status_dot_disconnected)
                connectionStatusText.text = getString(R.string.connection_status_disconnected)
            }
        }
    }
    
    /**
     * Set connection status to connecting
     */
    fun setConnectionStatusConnecting() {
        runOnUiThread {
            statusDot.setBackgroundResource(R.drawable.status_dot_connecting)
            connectionStatusText.text = getString(R.string.connection_status_connecting)
        }
    }
}