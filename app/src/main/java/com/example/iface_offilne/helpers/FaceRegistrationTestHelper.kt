package com.example.iface_offilne.helpers

import android.content.Context
import android.util.Log

/**
 * 🧪 HELPER DE TESTE PARA VERIFICAR CORREÇÕES
 * 
 * Este helper verifica se as correções implementadas estão funcionando corretamente.
 */
class FaceRegistrationTestHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "FaceRegistrationTest"
    }
    
    /**
     * 🧪 EXECUTAR TESTES DE COMPATIBILIDADE
     */
    fun runCompatibilityTests(): TestResult {
        val results = mutableListOf<TestResult>()
        
        try {
            Log.d(TAG, "🧪 === INICIANDO TESTES DE COMPATIBILIDADE ===")
            
            // ✅ Teste 1: Verificar TensorFlow Lite
            results.add(testTensorFlowLite())
            
            // ✅ Teste 2: Verificar MobileFaceNet Helper
            results.add(testMobileFaceNetHelper())
            
            // ✅ Teste 3: Verificar Configurações
            results.add(testConfigurations())
            
            // ✅ Teste 4: Verificar Permissões
            results.add(testPermissions())
            
            val allPassed = results.all { it.isSuccess }
            val summary = if (allPassed) {
                "✅ Todos os testes passaram"
            } else {
                "❌ Alguns testes falharam: ${results.filter { !it.isSuccess }.joinToString(", ") { it.message }}"
            }
            
            Log.d(TAG, "🧪 === RESULTADO DOS TESTES ===")
            Log.d(TAG, summary)
            
            return TestResult(
                isSuccess = allPassed,
                message = summary,
                details = results
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro durante os testes", e)
            return TestResult(
                isSuccess = false,
                message = "Erro durante os testes: ${e.message}",
                details = emptyList()
            )
        }
    }
    
    /**
     * 🧪 TESTE 1: VERIFICAR TENSORFLOW LITE
     */
    private fun testTensorFlowLite(): TestResult {
        return try {
            // ✅ Verificar se a classe Interpreter está disponível
            Class.forName("org.tensorflow.lite.Interpreter")
            
            // ✅ Verificar se não há GPU Delegate (que causa crash)
            try {
                Class.forName("org.tensorflow.lite.gpu.GpuDelegateFactory\$Options")
                TestResult(
                    isSuccess = false,
                    message = "GPU Delegate ainda disponível (pode causar crash)"
                )
            } catch (e: ClassNotFoundException) {
                TestResult(
                    isSuccess = true,
                    message = "TensorFlow Lite OK - GPU Delegate não disponível (bom)"
                )
            }
            
        } catch (e: ClassNotFoundException) {
            TestResult(
                isSuccess = false,
                message = "TensorFlow Lite não disponível: ${e.message}"
            )
        }
    }
    
    /**
     * 🧪 TESTE 2: VERIFICAR MOBILEFACENET HELPER
     */
    private fun testMobileFaceNetHelper(): TestResult {
        return try {
            val helper = MobileFaceNetHelper(context)
            TestResult(
                isSuccess = true,
                message = "MobileFaceNet Helper inicializado com sucesso"
            )
        } catch (e: Exception) {
            TestResult(
                isSuccess = false,
                message = "Erro ao inicializar MobileFaceNet Helper: ${e.message}"
            )
        }
    }
    
    /**
     * 🧪 TESTE 3: VERIFICAR CONFIGURAÇÕES
     */
    private fun testConfigurations(): TestResult {
        return try {
            // ✅ Verificar se GPU Delegate está desabilitado
            if (FaceRecognitionConfig.USE_GPU_DELEGATE) {
                TestResult(
                    isSuccess = false,
                    message = "GPU Delegate ainda habilitado nas configurações"
                )
            } else {
                TestResult(
                    isSuccess = true,
                    message = "Configurações OK - GPU Delegate desabilitado"
                )
            }
        } catch (e: Exception) {
            TestResult(
                isSuccess = false,
                message = "Erro ao verificar configurações: ${e.message}"
            )
        }
    }
    
    /**
     * 🧪 TESTE 4: VERIFICAR PERMISSÕES
     */
    private fun testPermissions(): TestResult {
        return try {
            // ✅ Verificar permissão de câmera
            val cameraPermission = android.Manifest.permission.CAMERA
            val hasPermission = context.checkSelfPermission(cameraPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                TestResult(
                    isSuccess = true,
                    message = "Permissão de câmera concedida"
                )
            } else {
                TestResult(
                    isSuccess = false,
                    message = "Permissão de câmera não concedida"
                )
            }
        } catch (e: Exception) {
            TestResult(
                isSuccess = false,
                message = "Erro ao verificar permissões: ${e.message}"
            )
        }
    }
}

/**
 * 📊 RESULTADO DE TESTE
 */
data class TestResult(
    val isSuccess: Boolean,
    val message: String,
    val details: List<TestResult> = emptyList()
) 