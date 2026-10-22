package com.parentalcontrol.parent.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.parentalcontrol.parent.analysis.LoginValidator
import com.parentalcontrol.parent.databinding.ActivityLoginBinding

/**
 * Masuk ke akun keluarga Firebase. Database hanya bisa diakses oleh akun ini (lihat database.rules.json),
 * jadi aplikasi tidak menampilkan apa pun sebelum masuk. Sesi disimpan oleh Firebase Auth.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSignIn.setOnClickListener { signIn() }
    }

    private fun signIn() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        LoginValidator.validate(email, password)?.let {
            showError(it)
            return
        }

        binding.tvError.visibility = View.GONE
        binding.btnSignIn.isEnabled = false
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                binding.btnSignIn.isEnabled = true
                showError(authErrorMessage(e))
            }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
