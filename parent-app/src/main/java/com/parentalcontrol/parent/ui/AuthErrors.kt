package com.parentalcontrol.parent.ui

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

fun authErrorMessage(e: Exception): String = when (e) {
    is FirebaseAuthInvalidUserException -> "Akun tidak ditemukan atau dinonaktifkan."
    is FirebaseAuthInvalidCredentialsException -> "Email atau password salah."
    is FirebaseAuthUserCollisionException -> "Email ini sudah terdaftar. Gunakan Masuk."
    is FirebaseAuthWeakPasswordException -> "Password terlalu lemah. Gunakan minimal 6 karakter."
    is FirebaseTooManyRequestsException -> "Terlalu banyak percobaan. Coba lagi beberapa saat lagi."
    is FirebaseNetworkException -> "Tidak ada koneksi internet."
    else -> "Gagal: ${e.message}"
}
