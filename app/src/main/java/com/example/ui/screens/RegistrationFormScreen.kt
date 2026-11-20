package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.firebase.friendlyFirestoreMessage
import com.example.data.model.PetTypeOption
import com.example.ui.state.AgeUnit
import com.example.ui.state.PetOwnerFormState
import com.example.ui.state.UiState
import com.example.ui.viewmodel.RegistryViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegistrationFormScreen(
    viewModel: RegistryViewModel,
    registrationId: String?,
    onBack: () -> Unit,
    onSavedSuccess: () -> Unit
) {
    val isEditMode = !registrationId.isNullOrBlank()
    var formState by remember { mutableStateOf(PetOwnerFormState()) }
    var hasInitializedEdit by remember { mutableStateOf(!isEditMode) }

    BackHandler(enabled = !formState.isSubmitting) {
        onBack()
    }

    if (isEditMode && registrationId != null) {
        val detailFlow = remember(registrationId) {
            viewModel.observeRegistrationDetail(registrationId)
        }
        val detailState by detailFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)

        LaunchedEffect(detailState) {
            if (!hasInitializedEdit && detailState is UiState.Success) {
                val existing = (detailState as UiState.Success).data
                if (existing != null) {
                    formState = PetOwnerFormState.fromRegistration(existing)
                    hasInitializedEdit = true
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = !formState.isSubmitting,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("form_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back"
                        )
                    }
                },
                title = {
                    Text(
                        text = if (isEditMode) {
                            stringResource(R.string.edit_registration_title)
                        } else {
                            stringResource(R.string.new_registration_title)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { innerPadding ->
        if (!hasInitializedEdit) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                AnimatedVisibility(visible = formState.submitError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formState.submitError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // Section 1: Owner Information
                FormSectionCard(
                    icon = Icons.Rounded.Person,
                    title = stringResource(R.string.owner_details_section),
                    accentContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    accentContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    OutlinedTextField(
                        value = formState.fullName,
                        onValueChange = {
                            formState = formState.copy(fullName = it, fullNameError = null, submitError = null)
                        },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g., Elena Vance") },
                        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        isError = formState.fullNameError != null,
                        supportingText = formState.fullNameError?.let { err -> { Text(err) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("full_name_input")
                    )

                    OutlinedTextField(
                        value = formState.email,
                        onValueChange = {
                            formState = formState.copy(email = it, emailError = null, submitError = null)
                        },
                        label = { Text("Email Address") },
                        placeholder = { Text("e.g., elena.vance@example.com") },
                        leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                        isError = formState.emailError != null,
                        supportingText = formState.emailError?.let { err -> { Text(err) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_input")
                    )

                    OutlinedTextField(
                        value = formState.mobileNumber,
                        onValueChange = {
                            formState = formState.copy(mobileNumber = it, mobileNumberError = null, submitError = null)
                        },
                        label = { Text("Mobile Number") },
                        placeholder = { Text("e.g., +1 555 234 5678") },
                        leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                        isError = formState.mobileNumberError != null,
                        supportingText = formState.mobileNumberError?.let { err -> { Text(err) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mobile_number_input")
                    )

                    OutlinedTextField(
                        value = formState.residentialAddress,
                        onValueChange = {
                            formState = formState.copy(
                                residentialAddress = it,
                                residentialAddressError = null,
                                submitError = null
                            )
                        },
                        label = { Text("Residential Address") },
                        placeholder = { Text("Street, city, state / postal code") },
                        leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        isError = formState.residentialAddressError != null,
                        supportingText = formState.residentialAddressError?.let { err -> { Text(err) } },
                        minLines = 2,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("residential_address_input")
                    )
                }

                // Section 2: Pet Information
                FormSectionCard(
                    icon = Icons.Rounded.Pets,
                    title = stringResource(R.string.pet_details_section),
                    accentContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    accentContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    OutlinedTextField(
                        value = formState.petName,
                        onValueChange = {
                            formState = formState.copy(petName = it, petNameError = null, submitError = null)
                        },
                        label = { Text("Pet Name") },
                        placeholder = { Text("e.g., Barnaby") },
                        leadingIcon = { Icon(Icons.Rounded.Pets, contentDescription = null) },
                        isError = formState.petNameError != null,
                        supportingText = formState.petNameError?.let { err -> { Text(err) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pet_name_input")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Pet Type",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            PetTypeOption.entries.forEach { option ->
                                val selected = formState.selectedPetType == option
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        formState = formState.copy(
                                            selectedPetType = option,
                                            customPetTypeError = null,
                                            submitError = null
                                        )
                                    },
                                    label = { Text(option.label) },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        {
                                            Icon(
                                                imageVector = option.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    modifier = Modifier.testTag("pet_type_chip_${option.name.lowercase()}")
                                )
                            }
                        }

                        AnimatedVisibility(visible = formState.selectedPetType == PetTypeOption.OTHER) {
                            OutlinedTextField(
                                value = formState.customPetType,
                                onValueChange = {
                                    formState = formState.copy(
                                        customPetType = it,
                                        customPetTypeError = null,
                                        submitError = null
                                    )
                                },
                                label = { Text("Specify Pet Type") },
                                placeholder = { Text("e.g., Hamster, Ferret, Parrot") },
                                isError = formState.customPetTypeError != null,
                                supportingText = formState.customPetTypeError?.let { err -> { Text(err) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .testTag("custom_pet_type_input")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = formState.petBreed,
                        onValueChange = {
                            formState = formState.copy(petBreed = it, petBreedError = null, submitError = null)
                        },
                        label = { Text("Pet Breed") },
                        placeholder = { Text("e.g., Golden Retriever, Siamese") },
                        leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                        isError = formState.petBreedError != null,
                        supportingText = formState.petBreedError?.let { err -> { Text(err) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pet_breed_input")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = formState.petAgeValue,
                            onValueChange = { newVal ->
                                if (newVal.length <= 12) {
                                    formState = formState.copy(
                                        petAgeValue = newVal,
                                        petAgeError = null,
                                        submitError = null
                                    )
                                }
                            },
                            label = { Text("Pet Age") },
                            placeholder = { Text("e.g., 3") },
                            leadingIcon = { Icon(Icons.Rounded.Cake, contentDescription = null) },
                            isError = formState.petAgeError != null,
                            supportingText = formState.petAgeError?.let { err -> { Text(err) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pet_age_input")
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Age unit:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilterChip(
                                selected = formState.petAgeUnit == AgeUnit.YEARS,
                                onClick = { formState = formState.copy(petAgeUnit = AgeUnit.YEARS) },
                                label = { Text("Years") },
                                modifier = Modifier.testTag("age_unit_years")
                            )
                            FilterChip(
                                selected = formState.petAgeUnit == AgeUnit.MONTHS,
                                onClick = { formState = formState.copy(petAgeUnit = AgeUnit.MONTHS) },
                                label = { Text("Months") },
                                modifier = Modifier.testTag("age_unit_months")
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val validated = formState.validated()
                        if (validated.hasErrors) {
                            formState = validated
                            return@Button
                        }
                        formState = validated.copy(isSubmitting = true, submitError = null)
                        val input = validated.toFormInput()
                        if (isEditMode && registrationId != null) {
                            viewModel.updatePetOwner(registrationId, input) { result ->
                                result.onSuccess {
                                    formState = formState.copy(isSubmitting = false)
                                    onSavedSuccess()
                                }.onFailure { error ->
                                    formState = formState.copy(
                                        isSubmitting = false,
                                        submitError = friendlyFirestoreMessage(error)
                                    )
                                }
                            }
                        } else {
                            viewModel.registerPetOwner(input) { result ->
                                result.onSuccess {
                                    formState = formState.copy(isSubmitting = false)
                                    onSavedSuccess()
                                }.onFailure { error ->
                                    formState = formState.copy(
                                        isSubmitting = false,
                                        submitError = friendlyFirestoreMessage(error)
                                    )
                                }
                            }
                        }
                    },
                    enabled = !formState.isSubmitting,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("save_registration_button")
                ) {
                    if (formState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(if (isEditMode) "Updating..." else "Saving...")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isEditMode) {
                                stringResource(R.string.update_registration)
                            } else {
                                stringResource(R.string.save_registration)
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun FormSectionCard(
    icon: ImageVector,
    title: String,
    accentContainerColor: androidx.compose.ui.graphics.Color,
    accentContentColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentContentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}
