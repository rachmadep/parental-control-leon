package com.parentalcontrol.parent.ui

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

fun authErrorMessage(e: Exception): String = when (e) {
    is FirebaseAuthInvalidUserException -> "Akun tidak ditemukan atau dinonaktifkan."
    is FirebaseAuthInvalidCredentialsException -> "Email atau password salah."
    is FirebaseNetworkException -> "Tidak ada koneksi internet."
    else -> "Gagal masuk: ${e.message}"
}
