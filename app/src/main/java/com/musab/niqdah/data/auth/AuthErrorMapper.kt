package com.musab.niqdah.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

object AuthErrorMapper {
    fun toFriendlyMessage(error: Throwable): String = when (error) {
        is FirebaseAuthWeakPasswordException ->
            "Use a stronger password with at least 6 characters."
        is FirebaseAuthUserCollisionException ->
            "An account with this email already exists. Try logging in instead."
        is FirebaseAuthInvalidUserException ->
            "No Niqdah account was found for that email."
        is FirebaseAuthInvalidCredentialsException ->
            "Check the email and password, then try again."
        is FirebaseNetworkException ->
            "Niqdah could not reach Firebase. Check your connection and try again."
        is IllegalStateException ->
            error.message ?: "Firebase is not configured yet."
        else ->
            "Something went wrong. Please try again."
    }
}
