package com.example.genfitness

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.genfitness.ui.theme.GenfitnessTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GenfitnessTheme {
                val context = LocalContext.current
                val authViewModel: AuthViewModel = viewModel()
                val user by authViewModel.user.collectAsState()

                if (user == null) {
                    LoginScreen(authViewModel)
                } else {
                    // Pedir permissão de notificações no Android 13+
                    val launcherNotificacao = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }

                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            launcherNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    
                    // Check Health Connect availability
                    val sdkStatus = remember { 
                        HealthConnectClient.getSdkStatus(context) 
                    }
                    
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF0F172A)
                    ) {
                        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                            val healthConnectClient = remember { 
                                HealthConnectClient.getOrCreate(context) 
                            }
                            
                            val viewModel: HealthViewModel = viewModel {
                                HealthViewModel(healthConnectClient)
                            }

                            EcraGenfitness(viewModel, authViewModel)
                        } else {
                            HealthConnectUnavailableScreen(sdkStatus)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcraGenfitness(viewModel: HealthViewModel, authViewModel: AuthViewModel) {
    val context = LocalContext.current
    val passos by viewModel.passos.collectAsState()
    val user by authViewModel.user.collectAsState()
    
    // Define qual é a permissão que queremos (Ler Passos)
    val permissoesHealthConnect = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    // Atualização automática ao iniciar
    LaunchedEffect(Unit) {
        viewModel.carregarPassos()
    }

    // Registo automático de visita quando os passos mudam e o utilizador está logado
    LaunchedEffect(passos) {
        if (passos > 0 && user != null) {
            registerUserVisit(user?.email ?: "desconhecido", passos)
        }
    }

    // Escutar o objetivo atingido
    LaunchedEffect(viewModel) {
        viewModel.objetivoAtingido.collectLatest {
            showGoalNotification(context)
        }
    }

    val launcherPermissoes = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { permissoesConcedidas ->
        if (permissoesConcedidas.containsAll(permissoesHealthConnect)) {
            viewModel.carregarPassos()
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1E293B),
            Color(0xFF0F172A)
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "StepTrack",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = Color.White
                    ) 
                },
                actions = {
                    IconButton(onClick = { authViewModel.logout(context) }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Sair",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    launcherPermissoes.launch(permissoesHealthConnect)
                },
                containerColor = Color(0xFF22C55E),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sincronizar")
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(gradientBrush)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                StepGoalCircle(steps = passos)

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = "STEPS TAKEN",
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                Text(
                    text = passos.toString(),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = Color.White
                )
            }
        }
    }
}

private fun registerUserVisit(email: String, steps: Long) {
    val db = FirebaseFirestore.getInstance()
    val visitData = hashMapOf(
        "email" to email,
        "steps" to steps,
        "timestamp" to FieldValue.serverTimestamp()
    )

    db.collection("user_visits")
        .document(email) // Usa o email como ID único para atualizar a mesma entrada
        .set(visitData)
        .addOnSuccessListener {
            Log.d("Genfitness", "Visita registada/atualizada com sucesso!")
        }
        .addOnFailureListener { e ->
            Log.e("Genfitness", "Erro ao registar visita", e)
        }
}

@Composable
fun StepGoalCircle(steps: Long, goal: Int = 10000) {
    val progress = remember(steps) { (steps.toFloat() / goal).coerceIn(0f, 1f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "progressAnimation"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color(0xFF22C55E),
                startAngle = -90f,
                sweepAngle = 360 * animatedProgress,
                useCenter = false,
                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val percentage = (progress * 100).toInt()
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "of goal",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

private fun showGoalNotification(context: Context) {
    val channelId = "goal_reached"
    val notificationId = 1
    
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    val channel = NotificationChannel(
        channelId,
        "Goal Notifications",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Congratulations when daily goal is reached"
    }
    manager.createNotificationChannel(channel)

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Goal Reached!")
        .setContentText("Congratulations! You've reached your 10,000 steps goal!")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)

    try {
        manager.notify(notificationId, builder.build())
    } catch (_: Exception) {
        // Handle error
    }
}

@Composable
fun HealthConnectUnavailableScreen(sdkStatus: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Health Connect não disponível",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Red
        )
        Spacer(modifier = Modifier.height(8.dp))
        val message = when (sdkStatus) {
            HealthConnectClient.SDK_UNAVAILABLE -> 
                "O Health Connect não é suportado neste dispositivo."
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> 
                "O Health Connect precisa de ser atualizado na Play Store."
            else -> "Erro desconhecido ao verificar o Health Connect ($sdkStatus)."
        }
        Text(
            text = message,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}
