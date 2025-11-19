package com.example.flare_capstone

import android.content.Intent
import android.os.Bundle
import android.text.*
import android.text.method.*
import android.util.Log
import android.util.Patterns
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.flare_capstone.databinding.ActivityRegisterBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    private val CONTACT_REGEX = Regex("^09\\d{9}$")
    private val PASSWORD_REGEX =
        Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])(?=\\S+$).{8,}$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()

        binding.contact.keyListener = DigitsKeyListener.getInstance("0123456789")
        binding.contact.filters = arrayOf(InputFilter.LengthFilter(11))
        setupPasswordToggle(binding.password)
        setupPasswordToggle(binding.confirmPassword)

        binding.loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.logo.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        binding.register.setOnClickListener { onRegisterClicked() }
    }

    private fun onRegisterClicked() {
        val name = binding.name.text.toString().trim()
        val email = binding.email.text.toString().trim().lowercase()
        val contact = binding.contact.text.toString().trim()
        val password = binding.password.text.toString()
        val confirmPassword = binding.confirmPassword.text.toString()

        if (name.isEmpty() || email.isEmpty() || contact.isEmpty() ||
            password.isEmpty() || confirmPassword.isEmpty()
        ) {
            toast("Please fill all fields")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.email.error = "Invalid email"
            toast("Invalid email format")
            return
        }

        if (!CONTACT_REGEX.matches(contact)) {
            binding.contact.error = "Invalid contact format"
            toast("Contact must start with 09 and be 11 digits")
            return
        }

        if (!PASSWORD_REGEX.matches(password)) {
            binding.password.error = "Weak password"
            toast("Password must have upper, lower, number, special")
            return
        }

        if (password != confirmPassword) {
            binding.confirmPassword.error = "Passwords don’t match"
            toast("Passwords don’t match")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    toast("Registration failed: ${task.exception?.message}")
                    return@addOnCompleteListener
                }

                val user = auth.currentUser ?: return@addOnCompleteListener
                user.sendEmailVerification()

                val now = Date()
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

                val userMap = mapOf(
                    "name" to name,
                    "email" to email,
                    "contact" to contact,
                    "status" to "Unverified",
                    "date" to date,
                    "time" to time
                )

                FirebaseDatabase.getInstance().getReference("UnverifiedUsers")
                    .child(user.uid)
                    .setValue(userMap)
                    .addOnSuccessListener {
                        toast("Account created. Please verify your email.")
                        val dialog = VerifyEmailDialogFragment().apply {
                            arguments = Bundle().apply {
                                putString("name", name)
                                putString("email", email)
                                putString("contact", contact)
                            }
                        }
                        dialog.show(supportFragmentManager, "VerifyEmailDialog")
                    }
                    .addOnFailureListener { e ->
                        toast("Failed to save user: ${e.message}")
                    }
            }
    }

    private fun setupPasswordToggle(editText: EditText) {
        val visibleIcon = android.R.drawable.ic_menu_view
        val hiddenIcon = android.R.drawable.ic_secure
        setEndIcon(editText, hiddenIcon)
        editText.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawable = editText.compoundDrawables[2]
                drawable?.let {
                    val iconStart = editText.width - editText.paddingRight - it.intrinsicWidth
                    if (event.x >= iconStart) {
                        togglePasswordVisibility(editText, visibleIcon, hiddenIcon)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun togglePasswordVisibility(editText: EditText, visibleIcon: Int, hiddenIcon: Int) {
        val sel = editText.selectionEnd
        if (editText.transformationMethod is PasswordTransformationMethod) {
            editText.transformationMethod = null
            setEndIcon(editText, visibleIcon)
        } else {
            editText.transformationMethod = PasswordTransformationMethod.getInstance()
            setEndIcon(editText, hiddenIcon)
        }
        editText.setSelection(sel)
    }

    private fun setEndIcon(editText: EditText, iconRes: Int) {
        editText.setCompoundDrawablesWithIntrinsicBounds(0, 0, iconRes, 0)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
