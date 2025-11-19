package com.example.flare_capstone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.util.Patterns
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flare_capstone.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.*
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance().reference
    private val verifyHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setupPasswordToggle(binding.password)

        binding.loginButton.setOnClickListener { onLoginClicked() }
        binding.registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
        binding.forgotPassword.setOnClickListener { onForgotPassword() }

        handleResetIntent(intent)
    }

    // ======================================================
    // 🔐 LOGIN FLOW
    // ======================================================
    private fun onLoginClicked() {
        val email = binding.email.text.toString().trim().lowercase()
        val password = binding.password.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show()
            return
        }

        setLoginEnabled(false)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    val message = when (val e = task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Incorrect password."
                        is FirebaseAuthInvalidUserException -> "Account not found."
                        else -> e?.message ?: "Login failed."
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    setLoginEnabled(true)
                    return@addOnCompleteListener
                }

                val user = auth.currentUser
                if (user == null) {
                    Toast.makeText(this, "Login error: user null", Toast.LENGTH_SHORT).show()
                    setLoginEnabled(true)
                    return@addOnCompleteListener
                }

                user.reload().addOnSuccessListener {
                    if (user.isEmailVerified) {
                        moveUserIfStillUnverified(user)
                    } else {
                        // If email not verified, check if user info exists to pass to dialog
                        fetchUserInfoAndShowVerifyDialog(user)
                    }
                }
            }
    }

    // ======================================================
    // 🔄 MOVE USER AFTER VERIFY
    // ======================================================
    private fun moveUserIfStillUnverified(user: FirebaseUser) {
        val email = user.email!!.lowercase()
        val unverifiedRef = db.child("UnverifiedUsers").orderByChild("email").equalTo(email)

        unverifiedRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val key = snapshot.children.first().key
                val data = snapshot.children.first().value

                if (key != null) {
                    db.child("Users").child(key).setValue(data).addOnSuccessListener {
                        db.child("UnverifiedUsers").child(key).removeValue()
                        Toast.makeText(this, "Email verified! You can now login.", Toast.LENGTH_SHORT).show()
                        routeToDashboard(email)
                    }
                }
            } else {
                routeToDashboard(email)
            }
        }.addOnFailureListener {
            Log.e("LoginFlow", "Error checking UnverifiedUsers: ${it.message}")
            routeToDashboard(email)
        }
    }

    // ======================================================
    // 📩 FETCH INFO & SHOW VERIFY DIALOG
    // ======================================================
    private fun fetchUserInfoAndShowVerifyDialog(user: FirebaseUser) {
        val email = user.email!!.lowercase()
        val unverifiedRef = db.child("UnverifiedUsers").orderByChild("email").equalTo(email)

        unverifiedRef.get().addOnSuccessListener { snapshot ->
            var name = ""
            var contact = ""

            if (snapshot.exists()) {
                val child = snapshot.children.first()
                name = child.child("name").getValue(String::class.java) ?: ""
                contact = child.child("contact").getValue(String::class.java) ?: ""
            }

            showVerifyDialog(email, name, contact)
            setLoginEnabled(true)
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to fetch user info: ${it.message}", Toast.LENGTH_SHORT).show()
            showVerifyDialog(email, "", "")
            setLoginEnabled(true)
        }
    }

    private fun showVerifyDialog(email: String, name: String, contact: String) {
        val frag = VerifyEmailDialogFragment().apply {
            arguments = Bundle().apply {
                putString("email", email)
                putString("name", name)
                putString("contact", contact)
            }
        }
        frag.show(supportFragmentManager, "VerifyEmailDialog")
    }

    // ======================================================
    // 🧭 NAVIGATION
    // ======================================================
    private fun routeToDashboard(email: String) {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("email", email)
        startActivity(intent)
        finish()
    }

    private fun setLoginEnabled(enabled: Boolean) {
        binding.loginButton.isEnabled = enabled
        binding.loginButton.alpha = if (enabled) 1f else 0.6f
    }

    // ======================================================
    // 🔑 PASSWORD RESET
    // ======================================================
    private fun onForgotPassword() {
        val email = binding.email.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Enter valid email.", Toast.LENGTH_SHORT).show()
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "Reset link sent to $email", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Reset failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleResetIntent(intent: Intent) {
        val data: Uri? = intent.data ?: return
        val mode = data?.getQueryParameter("mode")
        val oobCode = data?.getQueryParameter("oobCode")
        if (mode != "resetPassword" || oobCode.isNullOrBlank()) return

        auth.verifyPasswordResetCode(oobCode)
            .addOnSuccessListener { }
            .addOnFailureListener {
                Toast.makeText(this, "Invalid or expired reset link.", Toast.LENGTH_LONG).show()
            }
    }

    // ======================================================
    // 👁 PASSWORD TOGGLE
    // ======================================================
    private fun setupPasswordToggle(editText: EditText) {
        val visibleIcon = R.drawable.ic_visibility
        val hiddenIcon = R.drawable.ic_visibility_off

        editText.transformationMethod = PasswordTransformationMethod.getInstance()
        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, hiddenIcon, 0)

        editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = editText.compoundDrawables[2]
                if (drawableEnd != null && event.rawX >= (editText.right - drawableEnd.bounds.width())) {
                    if (editText.transformationMethod is PasswordTransformationMethod) {
                        editText.transformationMethod = null
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, visibleIcon, 0)
                    } else {
                        editText.transformationMethod = PasswordTransformationMethod.getInstance()
                        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, hiddenIcon, 0)
                    }
                    editText.setSelection(editText.text.length)
                    return@setOnTouchListener true
                }
            }
            false
        }
    }
}
