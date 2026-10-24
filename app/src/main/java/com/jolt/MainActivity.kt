package com.jolt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jolt.ui.AlarmListScreen
import com.jolt.ui.EditAlarmScreen
import com.jolt.ui.ObjectsScreen
import com.jolt.ui.RegisterObjectScreen
import com.jolt.ui.TestScanScreen
import com.jolt.ui.theme.JoltTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A force stop clears our system alarm; opening the app is the only way back, so re-arm here.
        lifecycleScope.launch { jolt.scheduler.scheduleNext() }
        enableEdgeToEdge()
        setContent {
            JoltTheme {
                val nav = rememberNavController()
                NavHost(
                    navController = nav,
                    startDestination = "alarms",
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                ) {
                    composable("alarms") {
                        AlarmListScreen(
                            onEdit = { nav.navigate("alarm/$it") },
                            onObjects = { nav.navigate("objects") },
                        )
                    }
                    composable("alarm/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val newObjectId by entry.savedStateHandle
                            .getStateFlow<Long?>(NEW_OBJECT_ID, null)
                            .collectAsStateWithLifecycle()
                        EditAlarmScreen(
                            alarmId = entry.arguments?.getLong("id") ?: 0L,
                            newObjectId = newObjectId,
                            onDone = { nav.popBackStack() },
                            onAddObject = { nav.navigate("objects/new") },
                        )
                    }
                    composable("objects") {
                        ObjectsScreen(
                            onBack = { nav.popBackStack() },
                            onAdd = { nav.navigate("objects/new") },
                            onTest = { nav.navigate("objects/$it/test") },
                        )
                    }
                    composable("objects/new") {
                        RegisterObjectScreen(
                            onBack = { nav.popBackStack() },
                            onSaved = { id ->
                                nav.previousBackStackEntry?.savedStateHandle?.set(NEW_OBJECT_ID, id)
                                nav.popBackStack()
                            },
                        )
                    }
                    composable("objects/{id}/test", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        TestScanScreen(objectId = entry.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }

    private companion object {
        const val NEW_OBJECT_ID = "new_object_id"
    }
}
