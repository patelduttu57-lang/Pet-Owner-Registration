package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class PetOwnerRegistration(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val email: String = "",
    val mobileNumber: String = "",
    val residentialAddress: String = "",
    val petName: String = "",
    val petType: String = "",
    val petBreed: String = "",
    val petAge: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class PetOwnerFormInput(
    val fullName: String,
    val email: String,
    val mobileNumber: String,
    val residentialAddress: String,
    val petName: String,
    val petType: String,
    val petBreed: String,
    val petAge: String
)
