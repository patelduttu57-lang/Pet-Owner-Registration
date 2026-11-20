package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.PetOwnerFormInput
import com.example.data.model.PetOwnerRegistration
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class PetOwnerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in user changed; refusing cross-user operation." }
        }
        return uid
    }

    private fun collectionRef(userId: String) =
        db.collection("users").document(userId).collection("pet_owners")

    fun observeRegistrations(userId: String): Flow<List<PetOwnerRegistration>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank." }
        val path = "users/$userId/pet_owners"
        emitAll(
            collectionRef(userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PetOwnerRegistration::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRegistration(userId: String, registrationId: String): Flow<PetOwnerRegistration?> = flow {
        require(userId.isNotBlank() && registrationId.isNotBlank()) { "IDs must not be blank." }
        val path = "users/$userId/pet_owners/$registrationId"
        emitAll(
            collectionRef(userId)
                .document(registrationId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            PetOwnerRegistration::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun registerPetOwner(userId: String, input: PetOwnerFormInput): Result<String> {
        var docPath = "users/$userId/pet_owners"
        return try {
            val uid = requireUserId(userId)
            validateInputBounds(input)
            val docRef = collectionRef(uid).document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "fullName" to input.fullName.trim(),
                "email" to input.email.trim(),
                "mobileNumber" to input.mobileNumber.trim(),
                "residentialAddress" to input.residentialAddress.trim(),
                "petName" to input.petName.trim(),
                "petType" to input.petType.trim(),
                "petBreed" to input.petBreed.trim(),
                "petAge" to input.petAge.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePetOwner(
        userId: String,
        registrationId: String,
        input: PetOwnerFormInput
    ): Result<Unit> {
        var docPath = "users/$userId/pet_owners/$registrationId"
        return try {
            val uid = requireUserId(userId)
            require(registrationId.isNotBlank()) { "Registration ID must not be blank." }
            validateInputBounds(input)
            val docRef = collectionRef(uid).document(registrationId)
            docPath = docRef.path
            val updates = mapOf(
                "fullName" to input.fullName.trim(),
                "email" to input.email.trim(),
                "mobileNumber" to input.mobileNumber.trim(),
                "residentialAddress" to input.residentialAddress.trim(),
                "petName" to input.petName.trim(),
                "petType" to input.petType.trim(),
                "petBreed" to input.petBreed.trim(),
                "petAge" to input.petAge.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePetOwner(userId: String, registrationId: String): Result<Unit> {
        var docPath = "users/$userId/pet_owners/$registrationId"
        return try {
            val uid = requireUserId(userId)
            require(registrationId.isNotBlank()) { "Registration ID must not be blank." }
            val docRef = collectionRef(uid).document(registrationId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedSampleRegistrations(
        userId: String,
        onlyIfEmpty: Boolean
    ): Result<Int> {
        val colPath = "users/$userId/pet_owners"
        return try {
            val uid = requireUserId(userId)
            val colRef = collectionRef(uid)
            if (onlyIfEmpty) {
                val existing = colRef.limit(1).get().await()
                if (!existing.isEmpty) {
                    return Result.success(0)
                }
            }

            val samples = listOf(
                PetOwnerFormInput(
                    fullName = "Sarah Jenkins",
                    email = "sarah.jenkins@example.com",
                    mobileNumber = "+1 555-234-8910",
                    residentialAddress = "742 Evergreen Terrace, Austin, TX",
                    petName = "Max",
                    petType = "Dog",
                    petBreed = "Golden Retriever",
                    petAge = "3 years"
                ),
                PetOwnerFormInput(
                    fullName = "Marcus Chen",
                    email = "marcus.chen@example.com",
                    mobileNumber = "+1 555-391-4420",
                    residentialAddress = "118 Harbor View Blvd, Seattle, WA",
                    petName = "Luna",
                    petType = "Cat",
                    petBreed = "Siamese",
                    petAge = "2 years"
                ),
                PetOwnerFormInput(
                    fullName = "Priya Patel",
                    email = "priya.patel@example.com",
                    mobileNumber = "+1 555-642-7731",
                    residentialAddress = "509 Oakridge Lane, Chicago, IL",
                    petName = "Kiwi",
                    petType = "Bird",
                    petBreed = "Cockatiel",
                    petAge = "1 year"
                ),
                PetOwnerFormInput(
                    fullName = "David Morales",
                    email = "david.morales@example.com",
                    mobileNumber = "+1 555-817-3095",
                    residentialAddress = "2840 Sunset Ridge Dr, Denver, CO",
                    petName = "Clover",
                    petType = "Rabbit",
                    petBreed = "Holland Lop",
                    petAge = "8 months"
                ),
                PetOwnerFormInput(
                    fullName = "Hannah Brooks",
                    email = "hannah.brooks@example.com",
                    mobileNumber = "+1 555-904-1266",
                    residentialAddress = "93 Maplewood Court, Boston, MA",
                    petName = "Buster",
                    petType = "Dog",
                    petBreed = "French Bulldog",
                    petAge = "4 years"
                )
            )

            val batch = db.batch()
            samples.forEach { input ->
                val docRef = colRef.document()
                val payload = mapOf(
                    "userId" to uid,
                    "fullName" to input.fullName,
                    "email" to input.email,
                    "mobileNumber" to input.mobileNumber,
                    "residentialAddress" to input.residentialAddress,
                    "petName" to input.petName,
                    "petType" to input.petType,
                    "petBreed" to input.petBreed,
                    "petAge" to input.petAge,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(samples.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colPath)
            Result.failure(e)
        }
    }

    private fun validateInputBounds(input: PetOwnerFormInput) {
        require(input.fullName.trim().length in 2..100) { "Full name must be 2–100 characters." }
        require(input.email.trim().length in 3..120) { "Email must be 3–120 characters." }
        require(input.mobileNumber.trim().length in 5..30) { "Mobile number must be 5–30 characters." }
        require(input.residentialAddress.trim().length in 3..250) { "Residential address must be 3–250 characters." }
        require(input.petName.trim().length in 1..80) { "Pet name must be 1–80 characters." }
        require(input.petType.trim().length in 1..50) { "Pet type must be 1–50 characters." }
        require(input.petBreed.trim().length in 1..80) { "Pet breed must be 1–80 characters." }
        require(input.petAge.trim().length in 1..40) { "Pet age must be 1–40 characters." }
    }
}
