package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CrueltyFree
import androidx.compose.material.icons.rounded.FlutterDash
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.ui.graphics.vector.ImageVector

enum class PetTypeOption(
    val label: String,
    val icon: ImageVector
) {
    DOG("Dog", Icons.Rounded.Pets),
    CAT("Cat", Icons.Rounded.Pets),
    BIRD("Bird", Icons.Rounded.FlutterDash),
    RABBIT("Rabbit", Icons.Rounded.CrueltyFree),
    FISH("Fish", Icons.Rounded.SetMeal),
    REPTILE("Reptile", Icons.Rounded.BugReport),
    OTHER("Other", Icons.Rounded.Pets);

    companion object {
        fun fromLabel(raw: String): PetTypeOption {
            val normalized = raw.trim()
            return entries.firstOrNull { it.label.equals(normalized, ignoreCase = true) } ?: OTHER
        }

        val standardLabels: Set<String> = entries.filter { it != OTHER }.map { it.label.lowercase() }.toSet()
    }
}
