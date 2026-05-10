package com.musab.niqdah

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FirebaseRulesTest {
    @Test
    fun firestoreRulesScopeDataToSignedInOwner() {
        val rules = listOf(
            File("firestore.rules"),
            File("../firestore.rules")
        ).firstOrNull { it.exists() }?.readText() ?: error("firestore.rules was not found")

        assertTrue(rules.contains("match /users/{uid}"))
        assertTrue(rules.contains("request.auth.uid == uid"))
        assertTrue(rules.contains("match /{document=**}"))
        assertTrue(rules.contains("allow read, write: if false"))
    }
}
