package com.example.genfitness

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.temporal.ChronoUnit

class HealthViewModel(private val healthConnectClient: HealthConnectClient) : ViewModel() {

    // Guardar os passos e avisa automaticamente a interface quando o número muda
    private val _passos = MutableStateFlow(0L)
    val passos: StateFlow<Long> = _passos

    // Evento para avisar a UI de que o objetivo foi atingido
    private val _objetivoAtingido = MutableSharedFlow<Unit>()
    val objetivoAtingido: SharedFlow<Unit> = _objetivoAtingido

    // Função para ir buscar os passos ao Health Connect
    fun carregarPassos() {
        // Coroutine: faz o trabalho em segundo plano para não travar a app
        viewModelScope.launch {
            try {
                // Data e fuso horário do dispositivo
                val zonaAtual = java.time.ZonedDateTime.now()

                // Momento exato
                val agora = zonaAtual.toInstant()

                 // Inicio do dia atual
                val inicioDoDia = zonaAtual.truncatedTo(ChronoUnit.DAYS).toInstant()

                // Create request for the API using aggregation
                val pedido = AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(inicioDoDia, agora),
                )
                // Information received
                val resposta = healthConnectClient.aggregate(pedido)

                // Get the total steps from the metrics
                val totalPassos = resposta[StepsRecord.COUNT_TOTAL] ?: 0L

                // Guarda o total (isto vai atualizar o ecrã automaticamente)
                val totalAntigo = _passos.value
                _passos.value = totalPassos

                // Verifica objetivo de 10k
                if (totalAntigo < 10000 && totalPassos >= 10000) {
                    _objetivoAtingido.emit(Unit)
                }

                // O TEU "PRINT" PARA TESTAR
                Log.d("Genfitness", "Sucesso! O total de passos de hoje é: $totalPassos")

            } catch (e: Exception) {
                Log.e("Genfitness", "Erro ao carregar passos", e)
                // Se der erro (ex: não tem permissões), mete a zero
                _passos.value = 0L
            }
        }
    }
}