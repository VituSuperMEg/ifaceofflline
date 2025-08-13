package com.example.iface_offilne.helpers

import android.util.Log
import com.example.iface_offilne.data.FuncionariosEntity

class FaceMatchTracker {
    companion object {
        private const val TAG = "FaceMatchTracker"
        // ✅ CONFIGURAÇÕES MAIS RIGOROSAS PARA EVITAR CONFUSÕES
        private const val REQUIRED_MATCHES = 3 // Era 2 - mais rigoroso para evitar confusões
        private const val MATCH_TIMEOUT_MS = 4000L // Era 3000L - mais tempo para captura
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.75f // Era 0.65f - mais rigoroso
        private const val MIN_SIMILARITY_FOR_CONFIRMATION = 0.60f // Novo: similaridade mínima para confirmação
        private const val DEBUG_MODE = true // Ativado para debug do problema
    }

    private var lastMatchTime = 0L
    private var consecutiveMatches = 0
    private var lastMatchedId: String? = null
    private var confirmedMatch: FuncionariosEntity? = null
    private var bestSimilarity = 0f // Rastrear a melhor similaridade
    private var totalMatches = 0 // Novo: total de matches para análise

    fun trackMatch(funcionario: FuncionariosEntity?, similarity: Float): FuncionariosEntity? {
        val currentTime = System.currentTimeMillis()
        
        // Reset se passou muito tempo
        if (currentTime - lastMatchTime > MATCH_TIMEOUT_MS) {
            if (DEBUG_MODE) Log.d(TAG, "⏰ Timeout - resetando tracker")
            reset()
        }
        
        // Atualizar último tempo
        lastMatchTime = currentTime

        if (funcionario == null) {
            reset()
            return null
        }

        // ✅ VERIFICAÇÃO MAIS RIGOROSA: Similaridade mínima para considerar
        if (similarity < MIN_SIMILARITY_FOR_CONFIRMATION) {
            if (DEBUG_MODE) Log.d(TAG, "❌ Similaridade muito baixa: $similarity (mínima: $MIN_SIMILARITY_FOR_CONFIRMATION)")
            return confirmedMatch // Retornar match anterior se existir
        }

        // ✅ OTIMIZAÇÃO: Se a similaridade for muito alta, confirmar imediatamente
        if (similarity >= HIGH_CONFIDENCE_THRESHOLD && confirmedMatch == null) {
            Log.d(TAG, "🚀 Match de alta confiança detectado: ${funcionario.nome} (similaridade: $similarity)")
            confirmedMatch = funcionario
            consecutiveMatches = REQUIRED_MATCHES // Marcar como confirmado
            return funcionario
        }

        // Se é o mesmo funcionário do último match
        if (funcionario.codigo == lastMatchedId) {
            consecutiveMatches++
            totalMatches++
            
            // ✅ OTIMIZAÇÃO: Rastrear a melhor similaridade
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
            }
            
            if (DEBUG_MODE) {
                Log.d(TAG, "✅ Match consecutivo #$consecutiveMatches para ${funcionario.nome}")
                Log.d(TAG, "   - Similaridade atual: $similarity")
                Log.d(TAG, "   - Melhor similaridade: $bestSimilarity")
                Log.d(TAG, "   - Total de matches: $totalMatches")
            }
            
            // ✅ CONFIGURAÇÃO MAIS RIGOROSA: Mais matches necessários
            if (consecutiveMatches >= REQUIRED_MATCHES && confirmedMatch == null) {
                // ✅ VERIFICAÇÃO ADICIONAL: Garantir que a similaridade média seja boa
                if (bestSimilarity >= MIN_SIMILARITY_FOR_CONFIRMATION) {
                    Log.d(TAG, "🎯 Match confirmado após $consecutiveMatches matches consecutivos!")
                    Log.d(TAG, "   - Funcionário: ${funcionario.nome}")
                    Log.d(TAG, "   - Melhor similaridade: $bestSimilarity")
                    Log.d(TAG, "   - Total de matches: $totalMatches")
                    confirmedMatch = funcionario
                    return funcionario
                } else {
                    if (DEBUG_MODE) Log.w(TAG, "⚠️ Match rejeitado: similaridade insuficiente ($bestSimilarity < $MIN_SIMILARITY_FOR_CONFIRMATION)")
                }
            }
        } else {
            // Match diferente, resetar contagem
            if (DEBUG_MODE) {
                Log.w(TAG, "⚠️ Match diferente detectado!")
                Log.w(TAG, "   - Anterior: $lastMatchedId")
                Log.w(TAG, "   - Atual: ${funcionario.codigo} (${funcionario.nome})")
                Log.w(TAG, "   - Similaridade: $similarity")
            }
            reset()
            lastMatchedId = funcionario.codigo
            consecutiveMatches = 1
            totalMatches = 1
            bestSimilarity = similarity
        }

        return confirmedMatch
    }

    fun getConfirmedMatch(): FuncionariosEntity? {
        return confirmedMatch
    }

    /**
     * ✅ NOVA FUNÇÃO: Verifica se está próximo de confirmar
     */
    fun isNearConfirmation(): Boolean {
        return consecutiveMatches >= (REQUIRED_MATCHES - 1) && confirmedMatch == null
    }

    /**
     * ✅ NOVA FUNÇÃO: Retorna o progresso atual
     */
    fun getProgress(): Float {
        return if (confirmedMatch != null) 1.0f else consecutiveMatches.toFloat() / REQUIRED_MATCHES.toFloat()
    }

    /**
     * ✅ NOVA FUNÇÃO: Retorna a melhor similaridade atual
     */
    fun getBestSimilarity(): Float {
        return bestSimilarity
    }

    /**
     * ✅ NOVA FUNÇÃO: Retorna estatísticas do tracker
     */
    fun getStats(): String {
        return "Matches: $consecutiveMatches/$REQUIRED_MATCHES, Similaridade: $bestSimilarity, Total: $totalMatches"
    }

    fun reset() {
        if (DEBUG_MODE && consecutiveMatches > 0) {
            Log.d(TAG, "🔄 Resetando tracker")
            Log.d(TAG, "   - Matches consecutivos: $consecutiveMatches")
            Log.d(TAG, "   - Melhor similaridade: $bestSimilarity")
            Log.d(TAG, "   - Total de matches: $totalMatches")
        }
        consecutiveMatches = 0
        lastMatchedId = null
        confirmedMatch = null
        bestSimilarity = 0f
        totalMatches = 0
    }
} 