package com.example.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.signOut
import com.example.data.repository.PetOwnerRepository
import com.example.data.repository.SeedTracker
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.RegistrationDetailScreen
import com.example.ui.screens.RegistrationFormScreen
import com.example.ui.screens.RegistryHomeScreen
import com.example.ui.viewmodel.RegistryViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object AppRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_REGISTER = "register"
    const val ROUTE_DETAIL = "detail/{registrationId}"
    const val ROUTE_EDIT = "edit/{registrationId}"

    fun detail(registrationId: String): String = "detail/${Uri.encode(registrationId)}"
    fun edit(registrationId: String): String = "edit/${Uri.encode(registrationId)}"
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { })
    } else {
        key(user.uid) {
            AuthenticatedRegistryNavHost(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedRegistryNavHost(
    currentUserId: String,
    userDisplayName: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: RegistryViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                RegistryViewModel(
                    repository = PetOwnerRepository(db),
                    currentUserId = currentUserId,
                    seedTracker = SeedTracker(app)
                )
            }
        }
    )

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.ROUTE_HOME
    ) {
        composable(AppRoutes.ROUTE_HOME) {
            RegistryHomeScreen(
                viewModel = viewModel,
                userDisplayName = userDisplayName,
                onNavigateToRegister = {
                    navController.navigate(AppRoutes.ROUTE_REGISTER)
                },
                onNavigateToDetail = { registrationId ->
                    navController.navigate(AppRoutes.detail(registrationId))
                },
                onNavigateToEdit = { registrationId ->
                    navController.navigate(AppRoutes.edit(registrationId))
                },
                onSignOutRequested = {
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope
                    )
                }
            )
        }

        composable(AppRoutes.ROUTE_REGISTER) {
            RegistrationFormScreen(
                viewModel = viewModel,
                registrationId = null,
                onBack = { navController.popBackStack() },
                onSavedSuccess = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoutes.ROUTE_DETAIL,
            arguments = listOf(navArgument("registrationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val registrationId = backStackEntry.arguments?.getString("registrationId").orEmpty()
            RegistrationDetailScreen(
                viewModel = viewModel,
                registrationId = registrationId,
                onBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate(AppRoutes.edit(id))
                }
            )
        }

        composable(
            route = AppRoutes.ROUTE_EDIT,
            arguments = listOf(navArgument("registrationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val registrationId = backStackEntry.arguments?.getString("registrationId").orEmpty()
            RegistrationFormScreen(
                viewModel = viewModel,
                registrationId = registrationId,
                onBack = { navController.popBackStack() },
                onSavedSuccess = { navController.popBackStack() }
            )
        }
    }
}
