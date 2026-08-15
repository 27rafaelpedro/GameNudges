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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    
    private val _user = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val user: StateFlow<FirebaseUser?> = _user

    private val _minecraftUsername = MutableStateFlow<String?>(null)
    val minecraftUsername: StateFlow<String?> = _minecraftUsername

    private val _isLoadingUsername = MutableStateFlow(false)
    val isLoadingUsername: StateFlow<Boolean> = _isLoadingUsername

    fun initUsername(context: Context) {
        val email = auth.currentUser?.email ?: return
        val prefs = context.getSharedPreferences("genfitness_prefs", Context.MODE_PRIVATE)
        val localUsername = prefs.getString("minecraft_username", null)
        
        if (localUsername != null) {
            _minecraftUsername.value = localUsername
            return
        }

        // Se não houver localmente, verificar na Cloud
        _isLoadingUsername.value = true
        viewModelScope.launch {
            try {
                val doc = db.collection("users").document(email).get().await()
                val cloudUsername = doc.getString("minecraft_username")
                if (cloudUsername != null) {
                    prefs.edit().putString("minecraft_username", cloudUsername).apply()
                    _minecraftUsername.value = cloudUsername
                }
            } catch (e: Exception) {
                Log.e("Auth", "Erro ao sincronizar username da cloud: ${e.message}")
            } finally {
                _isLoadingUsername.value = false
            }
        }
    }

    fun saveMinecraftUsername(context: Context, username: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val email = auth.currentUser?.email ?: return
        val prefs = context.getSharedPreferences("genfitness_prefs", Context.MODE_PRIVATE)

        viewModelScope.launch {
            try {
                _isLoadingUsername.value = true
                
                // 1. Verificar se o username já está em uso por outro email
                val query = db.collection("users")
                    .whereEqualTo("minecraft_username", username)
                    .get()
                    .await()
                
                if (!query.isEmpty) {
                    val existingUser = query.documents[0]
                    if (existingUser.id != email) {
                        onError("Este username já está a ser utilizado por outro jogador.")
                        _isLoadingUsername.value = false
                        return@launch
                    }
                }

                // 2. Guardar na Cloud (users/email)
                db.collection("users").document(email)
                    .set(mapOf("minecraft_username" to username), SetOptions.merge())
                    .await()
                
                // 3. Guardar Localmente
                prefs.edit().putString("minecraft_username", username).apply()
                
                _minecraftUsername.value = username
                _isLoadingUsername.value = false
                onSuccess()
            } catch (e: Exception) {
                _isLoadingUsername.value = false
                onError(e.message ?: "Erro ao guardar username")
            }
        }
    }

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
                            val user = auth.currentUser
                            _user.value = user
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
        _minecraftUsername.value = null
        val credentialManager = CredentialManager.create(context)
        viewModelScope.launch {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }
        _user.value = null
    }
}
