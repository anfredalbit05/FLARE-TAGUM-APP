package com.example.flare_capstone

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flare_capstone.databinding.ActivityOnboard1Binding
import com.google.firebase.auth.FirebaseAuth

class Onboard1Activity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboard1Binding
    private lateinit var auth: FirebaseAuth

    // Auto logout after 30 minutes (in milliseconds)
    private val logoutTimeLimit: Long = 30 * 60 * 1000
    private val handler = Handler(Looper.getMainLooper())

    // Runnable that logs out user when time limit is reached
    private val logoutRunnable = Runnable {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            auth.signOut()
            Toast.makeText(this, "You have been logged out due to inactivity", Toast.LENGTH_SHORT).show()
            redirectToLogin()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboard1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        // 🔐 Auto-login check
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val email = currentUser.email
            when (email) {
                "mabiniff001@gmail.com",
                "lafilipinaff001@gmail.com",
                "canocotanff001@gmail.com" -> {
                    Toast.makeText(this, "Welcome back, Firefighter", Toast.LENGTH_SHORT).show()
                    navigateTo(DashboardFireFighterActivity::class.java)
                    return
                }
                else -> {
                    Toast.makeText(this, "Welcome back", Toast.LENGTH_SHORT).show()
                    navigateTo(DashboardActivity::class.java)
                    return
                }
            }
        }

        // 🚀 Button listeners
        binding.getStartedButton.setOnClickListener {
            resetInactivityTimer()
            navigateTo(Onboard2Activity::class.java)
        }

        binding.skipButton.setOnClickListener {
            resetInactivityTimer()
            navigateTo(MainActivity::class.java)
        }

        // Start inactivity timer
        handler.postDelayed(logoutRunnable, logoutTimeLimit)
    }

    // 🕒 Reset inactivity timer
    private fun resetInactivityTimer() {
        handler.removeCallbacks(logoutRunnable)
        handler.postDelayed(logoutRunnable, logoutTimeLimit)
    }

    // 🔁 Triggered on any user touch (not just button click)
    override fun onUserInteraction() {
        super.onUserInteraction()
        resetInactivityTimer()
    }

    // 🔄 Helper to navigate and close current activity
    private fun navigateTo(destination: Class<*>) {
        startActivity(Intent(this, destination))
        finish()
    }

    // 🚪 Redirect user to login screen
    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        resetInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(logoutRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
