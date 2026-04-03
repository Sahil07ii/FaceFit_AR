package com.example.facefitar.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        // If user is already logged in, we could trigger success.
        // For simplicity, we'll let the UI decide based on auth.currentUser != null.
        if (auth.currentUser != null) {
            _authState.value = AuthState.Success
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authState.value = AuthState.Error("Email and Password cannot be empty.")
            return
        }

        if (!isValidEmail(email)) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }

        if (pass.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters.")
            return
        }

        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState.Success
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Login failed")
                }
            }
    }

    fun signUp(email: String, pass: String, confirmPass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _authState.value = AuthState.Error("Fields cannot be empty.")
            return
        }

        if (!isValidEmail(email)) {
            _authState.value = AuthState.Error("Please enter a valid email address.")
            return
        }

        if (confirmPass.isBlank()) {
            _authState.value = AuthState.Error("Confirm password cannot be empty.")
            return
        }

        if (pass.length < 6) {
            _authState.value = AuthState.Error("Password must be at least 6 characters.")
            return
        }
        if (pass != confirmPass) {
            _authState.value = AuthState.Error("Passwords do not match.")
            return
        }
        
        _authState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    saveUserToFirestore(email)
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Signup failed")
                }
            }
    }

    private fun saveUserToFirestore(email: String) {
        val user = auth.currentUser
        if (user != null) {
            val userData = hashMapOf(
                "uid" to user.uid,
                "email" to email,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
            
            // Allow user to proceed immediately while Firestore updates in the background
            _authState.value = AuthState.Success
            
            firestore.collection("users").document(user.uid)
                .set(userData, com.google.firebase.firestore.SetOptions.merge())
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
        } else {
            _authState.value = AuthState.Success
        }
    }

    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        saveUserToFirestore(user.email ?: "")
                    } else {
                        _authState.value = AuthState.Success
                    }
                } else {
                    _authState.value = AuthState.Error(task.exception?.message ?: "Google Sign-In failed")
                }
            }
    }

    fun signOut() {
        auth.signOut()
        _authState.value = AuthState.Idle
    }
    
    fun resetState() {
        if (auth.currentUser == null) {
            _authState.value = AuthState.Idle
        }
    }

    fun setLoading() {
        _authState.value = AuthState.Loading
    }
}
