package com.parentalcontrol.parent.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.parentalcontrol.parent.analysis.LoginValidator
import com.parentalcontrol.parent.databinding.ActivityLoginBinding

/**
 * Masuk atau mendaftar akun keluarga Firebase. Data tiap keluarga ada di `devices/{UID akun}` dan
 * hanya bisa dibaca akun itu (lihat database.rules.json), jadi banyak keluarga bisa berbagi satu proyek
 * Firebase. Aplikasi tidak menampilkan apa pun sebelum masuk. Sesi disimpan oleh Firebase Auth.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private var signUpMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSignIn.setOnClickListener { submit() }
        binding.btnToggleMode.setOnClickListener { setMode(!signUpMode) }
        binding.btnForgot.setOnClickListener { resetPassword() }
        setMode(false)
    }

    private fun setMode(signUp: Boolean) {
        signUpMode = signUp
        binding.tvError.visibility = View.GONE
        binding.tilConfirm.visibility = if (signUp) View.VISIBLE else View.GONE
        binding.btnForgot.visibility = if (signUp) View.GONE else View.VISIBLE
        binding.tvTitle.text = if (signUp) "Buat Akun Keluarga" else "Masuk ke Akun Keluarga"
        binding.tvSubtitle.text = if (signUp) {
            "Buat sekali, lalu masuk dengan akun yang sama di tablet anak."
        } else {
            "Gunakan akun yang sama dengan yang dipakai di tablet anak."
        }
        binding.btnSignIn.text = if (signUp) "Daftar" else "Masuk"
        binding.btnToggleMode.text = if (signUp) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar"
    }

    private fun submit() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        val error = if (signUpMode) {
            LoginValidator.validateSignUp(email, password, binding.etConfirm.text.toString())
        } else {
            LoginValidator.validate(email, password)
        }
        if (error != null) {
            showError(error)
            return
        }

        binding.tvError.visibility = View.GONE
        binding.btnSignIn.isEnabled = false
        val task: Task<AuthResult> = if (signUpMode) {
            auth.createUserWithEmailAndPassword(email, password)
        } else {
            auth.signInWithEmailAndPassword(email, password)
        }
        task.addOnSuccessListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }.addOnFailureListener { e ->
            binding.btnSignIn.isEnabled = true
            showError(authErrorMessage(e))
        }
    }

    private fun resetPassword() {
        val email = binding.etEmail.text.toString().trim()
        LoginValidator.validateEmail(email)?.let {
            showError(it)
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "Email pengaturan ulang password dikirim ke $email (cek juga folder spam).", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e -> showError(authErrorMessage(e)) }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }
}
