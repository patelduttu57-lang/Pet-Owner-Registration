package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.friendlyFirestoreMessage
import com.example.data.model.PetOwnerFormInput
import com.example.data.model.PetOwnerRegistration
import com.example.data.repository.PetOwnerRepository
import com.example.data.repository.SeedTracker
import com.example.ui.state.FeedbackMessage
import com.example.ui.state.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RegistryDashboardState(
    val allRegistrations: List<PetOwnerRegistration> = emptyList(),
    val filteredRegistrations: List<PetOwnerRegistration> = emptyList(),
    val searchQuery: String = "",
    val selectedPetTypeFilter: String? = null
)

class RegistryViewModel(
    private val repository: PetOwnerRepository,
    private val currentUserId: String,
    private val seedTracker: SeedTracker? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPetTypeFilter = MutableStateFlow<String?>(null)
    val selectedPetTypeFilter: StateFlow<String?> = _selectedPetTypeFilter.asStateFlow()

    private val _isSeeding = MutableStateFlow(false)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    private val _feedbackMessages = MutableSharedFlow<FeedbackMessage>(extraBufferCapacity = 4)
    val feedbackMessages: SharedFlow<FeedbackMessage> = _feedbackMessages.asSharedFlow()

    init {
        autoSeedIfFirstVisit()
    }

    private fun autoSeedIfFirstVisit() {
        val tracker = seedTracker ?: return
        if (tracker.hasAutoSeeded(currentUserId)) return
        viewModelScope.launch {
            _isSeeding.value = true
            val result = repository.seedSampleRegistrations(currentUserId, onlyIfEmpty = true)
            _isSeeding.value = false
            result.onSuccess { count ->
                tracker.markAutoSeeded(currentUserId)
                if (count > 0) {
                    _feedbackMessages.tryEmit(
                        FeedbackMessage("Added $count sample pet owner records to Firebase.")
                    )
                }
            }.onFailure { error ->
                Log.w(TAG, "Auto-seed skipped due to error: ${error.message}")
            }
        }
    }

    val registrationsState: StateFlow<UiState<RegistryDashboardState>> = combine(
        repository.observeRegistrations(currentUserId),
        _searchQuery,
        _selectedPetTypeFilter
    ) { registrations, query, typeFilter ->
        val trimmedQuery = query.trim().lowercase()
        val filtered = registrations.filter { reg ->
            val matchesType = typeFilter == null || reg.petType.equals(typeFilter, ignoreCase = true)
            val matchesQuery = trimmedQuery.isEmpty() ||
                reg.fullName.lowercase().contains(trimmedQuery) ||
                reg.petName.lowercase().contains(trimmedQuery) ||
                reg.petBreed.lowercase().contains(trimmedQuery) ||
                reg.email.lowercase().contains(trimmedQuery) ||
                reg.mobileNumber.lowercase().contains(trimmedQuery)
            matchesType && matchesQuery
        }
        RegistryDashboardState(
            allRegistrations = registrations,
            filteredRegistrations = filtered,
            searchQuery = query,
            selectedPetTypeFilter = typeFilter
        )
    }
        .map<RegistryDashboardState, UiState<RegistryDashboardState>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing registrations", rootError)
            emit(UiState.Error(friendlyFirestoreMessage(rootError)))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun observeRegistrationDetail(registrationId: String): Flow<UiState<PetOwnerRegistration?>> =
        repository.observeRegistration(currentUserId, registrationId)
            .map<PetOwnerRegistration?, UiState<PetOwnerRegistration?>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing registration detail", rootError)
                emit(UiState.Error(friendlyFirestoreMessage(rootError)))
            }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectPetTypeFilter(petType: String?) {
        _selectedPetTypeFilter.value = if (_selectedPetTypeFilter.value == petType) null else petType
    }

    fun loadSampleData() {
        if (_isSeeding.value) return
        viewModelScope.launch {
            _isSeeding.value = true
            val result = repository.seedSampleRegistrations(currentUserId, onlyIfEmpty = false)
            _isSeeding.value = false
            result.onSuccess { count ->
                seedTracker?.markAutoSeeded(currentUserId)
                _feedbackMessages.tryEmit(
                    FeedbackMessage("Added $count sample registrations to Firebase!")
                )
            }.onFailure { error ->
                _feedbackMessages.tryEmit(
                    FeedbackMessage(friendlyFirestoreMessage(error), isError = true)
                )
            }
        }
    }

    fun registerPetOwner(
        input: PetOwnerFormInput,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerPetOwner(currentUserId, input)
            result.onSuccess {
                _feedbackMessages.tryEmit(
                    FeedbackMessage("${input.fullName} & ${input.petName} registered successfully!")
                )
            }.onFailure { error ->
                _feedbackMessages.tryEmit(
                    FeedbackMessage(friendlyFirestoreMessage(error), isError = true)
                )
            }
            onResult(result)
        }
    }

    fun updatePetOwner(
        registrationId: String,
        input: PetOwnerFormInput,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.updatePetOwner(currentUserId, registrationId, input)
            result.onSuccess {
                _feedbackMessages.tryEmit(
                    FeedbackMessage("Updated ${input.fullName} & ${input.petName}'s registration.")
                )
            }.onFailure { error ->
                _feedbackMessages.tryEmit(
                    FeedbackMessage(friendlyFirestoreMessage(error), isError = true)
                )
            }
            onResult(result)
        }
    }

    fun deletePetOwner(
        registrationId: String,
        ownerName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.deletePetOwner(currentUserId, registrationId)
            result.onSuccess {
                _feedbackMessages.tryEmit(
                    FeedbackMessage("Removed $ownerName's registration.")
                )
            }.onFailure { error ->
                _feedbackMessages.tryEmit(
                    FeedbackMessage(friendlyFirestoreMessage(error), isError = true)
                )
            }
            onResult(result)
        }
    }

    private companion object {
        const val TAG = "RegistryVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
