package com.musab.niqdah.data.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.musab.niqdah.core.firebase.FirebaseProvider
import com.musab.niqdah.domain.auth.AuthRepository
import com.musab.niqdah.domain.auth.AuthState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseAuthRepository(context: Context) : AuthRepository {
    private val appContext = context.applicationContext
    private val auth: FirebaseAuth? by lazy { FirebaseProvider.auth(appContext) }

    override fun observeAuthState(): Flow<AuthState> = callbackFlow {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            trySend(
                AuthState.ConfigurationNeeded(
                    "Firebase is not configured yet. Add app/google-services.json and sync the project."
                )
            )
            close()
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { currentAuth ->
            val user = currentAuth.currentUser
            val state = if (user == null) {
                AuthState.SignedOut
            } else {
                AuthState.SignedIn(uid = user.uid, email = user.email)
            }
            trySend(state)
        }

        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        withAuth { signInWithEmailAndPassword(email.trim(), password) }

    override suspend fun register(email: String, password: String): Result<Unit> =
        withAuth { createUserWithEmailAndPassword(email.trim(), password) }

    override suspend fun signOut() {
        auth?.signOut()
    }

    private suspend fun withAuth(block: FirebaseAuth.() -> Task<AuthResult>): Result<Unit> {
        val firebaseAuth = auth
            ?: return Result.failure(
                IllegalStateException("Firebase is not configured yet. Add app/google-services.json and sync the project.")
            )
        return firebaseAuth.block().awaitResult()
    }

    private suspend fun Task<AuthResult>.awaitResult(): Result<Unit> =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (!continuation.isActive) return@addOnCompleteListener
                if (task.isSuccessful) {
                    continuation.resume(Result.success(Unit))
                } else {
                    continuation.resume(
                        Result.failure(
                            task.exception ?: IllegalStateException("Firebase request failed.")
                        )
                    )
                }
            }
        }
}
