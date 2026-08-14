package com.example.genfitness

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class StepSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val email = user.email ?: return Result.success()

        // Tornar o worker um Foreground Service para garantir execução e acesso ao Health Connect
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.e("StepSyncWorker", "Erro ao definir foreground", e)
        }

        val prefs = applicationContext.getSharedPreferences("genfitness_prefs", Context.MODE_PRIVATE)
        val minecraftUsername = prefs.getString("minecraft_username", null)

        if (minecraftUsername == null) {
            Log.d("StepSyncWorker", "Username do Minecraft não configurado localmente. Sincronização ignorada.")
            return Result.success()
        }

        val healthConnectClient = HealthConnectClient.getOrCreate(applicationContext)
        val sdkStatus = HealthConnectClient.getSdkStatus(applicationContext)

        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            Log.w("StepSyncWorker", "Health Connect não disponível.")
            return Result.failure()
        }

        return try {
            val hoje = LocalDate.now()
            val installDateString = prefs.getString("install_date", hoje.toString())
            val installDate = LocalDate.parse(installDateString)
            
            // 1. Sincronizar a última semana (7 dias), mas apenas se >= data de instalação
            for (i in 0..6) {
                val dataParaSincronizar = hoje.minusDays(i.toLong())
                if (!dataParaSincronizar.isBefore(installDate)) {
                    syncStepsForDate(email, minecraftUsername, healthConnectClient, dataParaSincronizar)
                }
            }

            // 2. Limpeza: Apagar o registo de 8 dias atrás (para manter apenas os últimos 7)
            val dataParaApagar = hoje.minusDays(7)
            if (!dataParaApagar.isBefore(installDate)) {
                deleteOldEntry(minecraftUsername, dataParaApagar)
            }

            // 3. Reagendar despertar de meia-noite
            scheduleMidnightWakeup(applicationContext)

            Log.d("StepSyncWorker", "Sincronização da última semana concluída para $minecraftUsername.")
            Result.success()
        } catch (e: Exception) {
            Log.e("StepSyncWorker", "Erro na sincronização", e)
            Result.retry()
        }
    }

    private suspend fun syncStepsForDate(email: String, mcUsername: String, client: HealthConnectClient, date: LocalDate) {
        try {
            val zona = ZonedDateTime.now().zone
            val inicio = date.atStartOfDay(zona).toInstant()
            val fim = date.plusDays(1).atStartOfDay(zona).toInstant()

            val pedido = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(inicio, fim),
            )
            
            val resposta = client.aggregate(pedido)
            val passos = resposta[StepsRecord.COUNT_TOTAL] ?: 0L

            val db = FirebaseFirestore.getInstance()
            val docId = "${mcUsername}_$date"

            val dataMap = hashMapOf(
                "email" to email,
                "minecraft_username" to mcUsername,
                "steps" to passos,
                "timestamp" to FieldValue.serverTimestamp(),
                "date" to date.toString()
            )

            db.collection("user_visits").document(docId).set(dataMap).await()
            Log.d("StepSyncWorker", "Atualizado: $docId -> $passos passos")
        } catch (e: Exception) {
            Log.e("StepSyncWorker", "Erro ao sincronizar data $date", e)
        }
    }

    private fun deleteOldEntry(mcUsername: String, date: LocalDate) {
        val db = FirebaseFirestore.getInstance()
        val docId = "${mcUsername}_$date"
        
        db.collection("user_visits").document(docId).delete()
            .addOnSuccessListener {
                Log.d("StepSyncWorker", "Registo antigo removido: $docId")
            }
            .addOnFailureListener { e ->
                Log.e("StepSyncWorker", "Erro ao remover registo antigo $docId", e)
            }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val channelId = "sync_channel"
        val notificationId = 2

        val channel = NotificationChannel(
            channelId,
            "Sincronização de Passos",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Usado para sincronização de passos em segundo plano"
        }
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("StepTrack")
            .setContentText("Sincronizando passos...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
    
    companion object {
        fun scheduleMidnightWakeup(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, TimeChangeReceiver::class.java).apply {
                action = "com.example.genfitness.ACTION_MIDNIGHT_SYNC"
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val agora = LocalDateTime.now()
            // Agendamos para as 00:00:05 para garantir que o dia já mudou no sistema
            var proximoDespertar = agora.withHour(0).withMinute(0).withSecond(5).withNano(0)
            
            if (agora.isAfter(proximoDespertar) || agora.isEqual(proximoDespertar)) {
                proximoDespertar = proximoDespertar.plusDays(1)
            }

            val zoneId = ZoneId.systemDefault()
            val epochMillis = proximoDespertar.atZone(zoneId).toInstant().toEpochMilli()

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    epochMillis,
                    pendingIntent
                )
                Log.d("StepSyncWorker", "Despertar de meia-noite agendado para: $proximoDespertar")
            } catch (e: SecurityException) {
                Log.w("StepSyncWorker", "Sem permissão para alarme exato: ${e.message}")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    epochMillis,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.e("StepSyncWorker", "Erro ao agendar alarme de meia-noite", e)
            }
        }

        fun runOnce(context: Context) {
            val syncRequest = OneTimeWorkRequestBuilder<StepSyncWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "ManualStepSync",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
    }
}
