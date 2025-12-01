package com.example.flare_capstone

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.flare_capstone.databinding.ActivityVerifyEmailBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class VerifyEmailDialogFragment : DialogFragment() {

    private var _binding: ActivityVerifyEmailBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    private val db = FirebaseDatabase.getInstance()
    private val unverifiedDb = db.getReference("UnverifiedUsers")
    private val verifiedDb = db.getReference("users")

    private val handler = Handler(Looper.getMainLooper())
    private var pollRunnable: Runnable? = null
    private var cooldownMs = 30_000L
    private var timer: CountDownTimer? = null

    private var name = ""
    private var email = ""
    private var contact = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), R.style.RoundedDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityVerifyEmailBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()

        // Read passed arguments (if any)
        arguments?.let {
            name = it.getString("name", "")
            email = it.getString("email", "")
            contact = it.getString("contact", "")
        }

        binding.emailTv.text = if (email.isNotBlank()) email else (auth.currentUser?.email ?: "")

        // Button listeners
        binding.verifiedBtn.setOnClickListener { checkVerifiedAndFinish() }
        binding.resendBtn.setOnClickListener { resendVerification() }
        binding.closeBtn.setOnClickListener { dismissAllowingStateLoss() }

        // Start cooldown and verification poll
        startCooldown()
        startVerificationPolling()

        return binding.root
    }

    // ==============================================================
    // 🔁 Real-time polling for email verification
    // ==============================================================
    private fun startVerificationPolling() {
        val user = auth.currentUser ?: return
        pollRunnable = object : Runnable {
            override fun run() {
                user.reload().addOnSuccessListener {
                    if (user.isEmailVerified) {
                        moveToVerified(user.uid)
                    } else {
                        handler.postDelayed(this, 5000) // recheck every 5s
                    }
                }.addOnFailureListener {
                    toast("Error checking verification: ${it.message}")
                    handler.postDelayed(this, 5000)
                }
            }
        }
        handler.postDelayed(pollRunnable!!, 5000)
    }

    // ==============================================================
    // ✅ Move verified user from UnverifiedUsers → Users
    // ==============================================================
    private fun moveToVerified(uid: String) {
        val user = auth.currentUser ?: return
        val now = Date()
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

        val emailLower = user.email?.lowercase() ?: email.lowercase()
        val verifiedMap = mapOf(
            "name" to name,
            "email" to emailLower,
            "contact" to contact,
            "status" to "Verified",
            "date" to date,
            "time" to time
        )

        // Check if user exists in UnverifiedUsers first
        unverifiedDb.child(uid).get().addOnSuccessListener { snap ->
            if (snap.exists()) {
                verifiedDb.child(uid).setValue(verifiedMap).addOnSuccessListener {
                    unverifiedDb.child(uid).removeValue()
                    toast("Email verified successfully!")
                    redirectToLogin()
                }
            } else {
                // If already moved, just redirect
                redirectToLogin()
            }
        }.addOnFailureListener {
            toast("Error verifying user: ${it.message}")
        }
    }

    // ==============================================================
    // 🔎 Manual "Check Verified" Button
    // ==============================================================
    private fun checkVerifiedAndFinish() {
        val user = auth.currentUser ?: return toast("Session expired.")
        user.reload().addOnSuccessListener {
            if (user.isEmailVerified) {
                moveToVerified(user.uid)
            } else {
                toast("Not verified yet. Check your inbox or spam folder.")
            }
        }
    }

    // ==============================================================
    // ✉️ Resend Verification Email with Cooldown
    // ==============================================================
    private fun resendVerification() {
        val user = auth.currentUser ?: return toast("No session found.")
        user.sendEmailVerification()
            .addOnSuccessListener {
                toast("Verification email resent to ${user.email}.")
                startCooldown()
            }
            .addOnFailureListener { e ->
                toast("Failed to resend: ${e.message}")
            }
    }

    private fun startCooldown() {
        binding.resendBtn.isEnabled = false
        timer?.cancel()
        timer = object : CountDownTimer(cooldownMs, 1000) {
            override fun onTick(ms: Long) {
                binding.timerTv.text = "Resend available in ${ms / 1000}s"
            }

            override fun onFinish() {
                binding.timerTv.text = ""
                binding.resendBtn.isEnabled = true
            }
        }.start()
    }

    // ==============================================================
    // 🔚 Lifecycle cleanup
    // ==============================================================
    override fun onDestroyView() {
        super.onDestroyView()
        timer?.cancel()
        pollRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }

    // ==============================================================
    // 🔄 Helper: Redirect after verification
    // ==============================================================
    private fun redirectToLogin() {
        auth.signOut()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
        dismissAllowingStateLoss()
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }
}
