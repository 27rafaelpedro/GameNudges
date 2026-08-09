package com.example.genfitness

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    
    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    // Web Client ID configurado a partir do google-services.json
    private val WEB_CLIENT_ID = "896777760682-78v13q77e8u2iseqboejq1g7nro70n0e.apps.googleusercontent.com"

    fun signInWithGoogle(context: Context, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val credentialManager = CredentialManager.create(context)
        
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                    
                    auth.signInWithCredential(firebaseCredential)
                        .addOnSuccessListener {
                            _user.value = auth.currentUser
                            onSuccess()
                        }
                        .addOnFailureListener {
                            onError(it.message ?: "Erro ao autenticar com Firebase")
                        }
                }
            } catch (e: GetCredentialException) {
                Log.e("Auth", "Erro CredentialManager: ${e.message}")
                onError(e.message ?: "Erro ao obter credenciais")
            } catch (e: Exception) {
                Log.e("Auth", "Erro inesperado: ${e.message}")
                onError(e.message ?: "Erro desconhecido")
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                _user.value = auth.currentUser
                onSuccess()
            }
            .addOnFailureListener {
                onError(it.message ?: "Erro ao fazer login")
            }
    }

    fun signUp(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                _user.value = auth.currentUser
                onSuccess()
            }
            .addOnFailureListener {
                onError(it.message ?: "Erro ao criar conta")
            }
    }

    fun logout(context: Context) {
        auth.signOut()
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
        _user.value = null
    }
}