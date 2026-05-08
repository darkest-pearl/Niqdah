package com.musab.niqdah.core.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

object FirebaseProvider {
    private const val FUNCTIONS_REGION = "us-central1"

    fun auth(context: Context): FirebaseAuth? = runCatching {
        if (ensureFirebaseApp(context)) FirebaseAuth.getInstance() else null
    }.getOrNull()

    fun firestore(context: Context): FirebaseFirestore? = runCatching {
        if (ensureFirebaseApp(context)) FirebaseFirestore.getInstance() else null
    }.getOrNull()

    fun functions(context: Context): FirebaseFunctions? = runCatching {
        if (ensureFirebaseApp(context)) FirebaseFunctions.getInstance(FUNCTIONS_REGION) else null
    }.getOrNull()

    private fun ensureFirebaseApp(context: Context): Boolean {
        val appContext = context.applicationContext
        return FirebaseApp.getApps(appContext).isNotEmpty() ||
            FirebaseApp.initializeApp(appContext) != null
    }
}
