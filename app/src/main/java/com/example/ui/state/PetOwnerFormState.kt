package com.example.ui.state

import android.util.Patterns
import com.example.data.model.PetOwnerFormInput
import com.example.data.model.PetOwnerRegistration
import com.example.data.model.PetTypeOption

enum class AgeUnit(val label: String, val singular: String) {
    YEARS("years", "year"),
    MONTHS("months", "month")
}

data class PetOwnerFormState(
    val fullName: String = "",
    val email: String = "",
    val mobileNumber: String = "",
    val residentialAddress: String = "",
    val petName: String = "",
    val selectedPetType: PetTypeOption = PetTypeOption.DOG,
    val customPetType: String = "",
    val petBreed: String = "",
    val petAgeValue: String = "",
    val petAgeUnit: AgeUnit = AgeUnit.YEARS,
    val fullNameError: String? = null,
    val emailError: String? = null,
    val mobileNumberError: String? = null,
    val residentialAddressError: String? = null,
    val petNameError: String? = null,
    val customPetTypeError: String? = null,
    val petBreedError: String? = null,
    val petAgeError: String? = null,
    val isSubmitting: Boolean = false,
    val submitError: String? = null
) {
    val hasErrors: Boolean
        get() = listOfNotNull(
            fullNameError,
            emailError,
            mobileNumberError,
            residentialAddressError,
            petNameError,
            customPetTypeError,
            petBreedError,
            petAgeError
        ).isNotEmpty()

    fun resolvedPetType(): String =
        if (selectedPetType == PetTypeOption.OTHER) {
            customPetType.trim().ifBlank { PetTypeOption.OTHER.label }
        } else {
            selectedPetType.label
        }

    fun resolvedPetAge(): String {
        val raw = petAgeValue.trim()
        val numeric = raw.toIntOrNull()
        return if (numeric != null) {
            val unitWord = if (numeric == 1) petAgeUnit.singular else petAgeUnit.label
            "$numeric $unitWord"
        } else {
            raw
        }
    }

    fun toFormInput(): PetOwnerFormInput = PetOwnerFormInput(
        fullName = fullName.trim(),
        email = email.trim(),
        mobileNumber = mobileNumber.trim(),
        residentialAddress = residentialAddress.trim(),
        petName = petName.trim(),
        petType = resolvedPetType(),
        petBreed = petBreed.trim(),
        petAge = resolvedPetAge()
    )

    fun validated(): PetOwnerFormState {
        val nameTrim = fullName.trim()
        val emailTrim = email.trim()
        val phoneTrim = mobileNumber.trim()
        val addressTrim = residentialAddress.trim()
        val petNameTrim = petName.trim()
        val breedTrim = petBreed.trim()
        val ageTrim = petAgeValue.trim()

        val nameErr = when {
            nameTrim.length < 2 -> "Enter owner's full name (min 2 characters)"
            nameTrim.length > 100 -> "Full name must be 100 characters or fewer"
            else -> null
        }
        val emailErr = when {
            emailTrim.isEmpty() -> "Email address is required"
            emailTrim.length !in 3..120 -> "Email must be 3–120 characters"
            !Patterns.EMAIL_ADDRESS.matcher(emailTrim).matches() -> "Enter a valid email address"
            else -> null
        }
        val digitsCount = phoneTrim.count { it.isDigit() }
        val phoneErr = when {
            phoneTrim.length < 5 || digitsCount < 5 -> "Enter a valid mobile number (min 5 digits)"
            phoneTrim.length > 30 -> "Mobile number must be 30 characters or fewer"
            else -> null
        }
        val addressErr = when {
            addressTrim.length < 3 -> "Enter residential address (min 3 characters)"
            addressTrim.length > 250 -> "Address must be 250 characters or fewer"
            else -> null
        }
        val petNameErr = when {
            petNameTrim.isEmpty() -> "Pet name is required"
            petNameTrim.length > 80 -> "Pet name must be 80 characters or fewer"
            else -> null
        }
        val customTypeErr = if (selectedPetType == PetTypeOption.OTHER) {
            val customTrim = customPetType.trim()
            when {
                customTrim.isEmpty() -> "Specify the pet type"
                customTrim.length > 50 -> "Pet type must be 50 characters or fewer"
                else -> null
            }
        } else {
            null
        }
        val breedErr = when {
            breedTrim.isEmpty() -> "Pet breed is required"
            breedTrim.length > 80 -> "Pet breed must be 80 characters or fewer"
            else -> null
        }
        val ageNumeric = ageTrim.toIntOrNull()
        val ageErr = when {
            ageTrim.isEmpty() -> "Pet age is required"
            ageNumeric != null && (ageNumeric < 0 || ageNumeric > 150) -> "Enter a realistic age (0–150)"
            resolvedPetAge().length > 40 -> "Pet age must be 40 characters or fewer"
            else -> null
        }

        return copy(
            fullNameError = nameErr,
            emailError = emailErr,
            mobileNumberError = phoneErr,
            residentialAddressError = addressErr,
            petNameError = petNameErr,
            customPetTypeError = customTypeErr,
            petBreedError = breedErr,
            petAgeError = ageErr
        )
    }

    companion object {
        fun fromRegistration(reg: PetOwnerRegistration): PetOwnerFormState {
            val matchedType = PetTypeOption.fromLabel(reg.petType)
            val isStandardNonOther = matchedType != PetTypeOption.OTHER &&
                reg.petType.trim().equals(matchedType.label, ignoreCase = true)

            val parts = reg.petAge.trim().split(Regex("\\s+"), limit = 2)
            val parsedNum = parts.firstOrNull()?.toIntOrNull()
            val parsedUnit = when {
                parts.getOrNull(1)?.startsWith("month", ignoreCase = true) == true -> AgeUnit.MONTHS
                else -> AgeUnit.YEARS
            }

            return PetOwnerFormState(
                fullName = reg.fullName,
                email = reg.email,
                mobileNumber = reg.mobileNumber,
                residentialAddress = reg.residentialAddress,
                petName = reg.petName,
                selectedPetType = if (isStandardNonOther) matchedType else PetTypeOption.OTHER,
                customPetType = if (isStandardNonOther) "" else reg.petType,
                petBreed = reg.petBreed,
                petAgeValue = parsedNum?.toString() ?: reg.petAge,
                petAgeUnit = parsedUnit
            )
        }
    }
}
