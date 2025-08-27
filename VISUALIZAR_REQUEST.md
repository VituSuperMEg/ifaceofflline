# 🔍 Como Ver Exatamente Qual Request Está Sendo Enviada

## 📱 **Logs Detalhados Implementados**

Agora quando você tentar sincronizar, vai aparecer **TUDO** nos logs:

### 1. **JSON Completo da Request**
### 2. **Detalhes de Cada Ponto**  
### 3. **Formato Para API**
### 4. **URL e Headers**
### 5. **Resposta Completa**

## 🔍 **Como Visualizar**

### **Método 1: Android Studio Logcat**
```bash
1. Abra Android Studio
2. Conecte o device
3. Vá em View > Tool Windows > Logcat
4. Filtre por: SYNC_DEBUG
```

### **Método 2: Terminal/CMD**
```bash
# Filtrar apenas logs de sincronização
adb logcat | grep "SYNC_DEBUG"

# Ver tudo (inclui OkHttp)
adb logcat | grep -E "(SYNC_DEBUG|OkHttp)"

# Salvar logs em arquivo
adb logcat | grep "SYNC_DEBUG" > logs_sincronizacao.txt
```

## 📋 **O Que Você Vai Ver**

### **1. Dados Preparados:**
```
SYNC_DEBUG: 📦 === PAYLOAD COMPLETO ===
SYNC_DEBUG:   📍 Localização ID: '15'
SYNC_DEBUG:   🔑 Código: '16'
SYNC_DEBUG:   📊 Quantidade de pontos: 1
SYNC_DEBUG:   📅 Data sincronização: '2025-01-16 14:30:45'
```

### **2. JSON Completo (formatado):**
```
SYNC_DEBUG: 📋 === JSON COMPLETO DA REQUEST ===
SYNC_DEBUG: {
SYNC_DEBUG:   "localizacao_id": "15",
SYNC_DEBUG:   "codigo_sincronizacao": "16",
SYNC_DEBUG:   "data_sincronizacao": "2025-01-16 14:30:45",
SYNC_DEBUG:   "pontos": [
SYNC_DEBUG:     {
SYNC_DEBUG:       "id": "1",
SYNC_DEBUG:       "funcionario_id": "00905076303",
SYNC_DEBUG:       "funcionario_nome": "ADAMS ANTONIO GIRAO MENESES",
SYNC_DEBUG:       "funcionario_matricula": "100001",
SYNC_DEBUG:       "funcionario_cpf": "00905076303",
SYNC_DEBUG:       "funcionario_cargo": "Analista",
SYNC_DEBUG:       "funcionario_secretaria": "SEAD",
SYNC_DEBUG:       "funcionario_lotacao": "Diretoria",
SYNC_DEBUG:       "data_hora": "2025-01-16 13:43:22"
SYNC_DEBUG:     }
SYNC_DEBUG:   ]
SYNC_DEBUG: }
```

### **3. Detalhes de Cada Ponto:**
```
SYNC_DEBUG: 🔍 === DETALHES DE CADA PONTO ===
SYNC_DEBUG: Ponto #1:
SYNC_DEBUG:   id: '1'
SYNC_DEBUG:   funcionario_id: '00905076303'
SYNC_DEBUG:   funcionario_nome: 'ADAMS ANTONIO GIRAO MENESES'
SYNC_DEBUG:   funcionario_matricula: '100001'
SYNC_DEBUG:   funcionario_cpf: '00905076303'
SYNC_DEBUG:   funcionario_cargo: 'Analista'
SYNC_DEBUG:   funcionario_secretaria: 'SEAD'
SYNC_DEBUG:   funcionario_lotacao: 'Diretoria'
SYNC_DEBUG:   data_hora: '2025-01-16 13:43:22'
SYNC_DEBUG:   ---
```

### **4. Formato Para API:**
```
SYNC_DEBUG: 📋 === FORMATO PARA API (PontoSyncRequest) ===
SYNC_DEBUG: Ponto API #1:
SYNC_DEBUG:   funcionarioId: '00905076303'
SYNC_DEBUG:   funcionarioNome: 'ADAMS ANTONIO GIRAO MENESES'
SYNC_DEBUG:   dataHora: '2025-01-16 13:43:22'
SYNC_DEBUG:   tipoPonto: 'ENTRADA'
SYNC_DEBUG:   latitude: null
SYNC_DEBUG:   longitude: null
SYNC_DEBUG:   observacao: 'null'
SYNC_DEBUG:   ---
```

### **5. Chamada HTTP:**
```
SYNC_DEBUG: 🌐 === FAZENDO CHAMADA HTTP ===
SYNC_DEBUG: 🔗 URL da API: /{entidade}/services/util/sincronizar-ponto-table
SYNC_DEBUG: 🔗 Entidade: '15'
SYNC_DEBUG: 🔄 Executando chamada HTTP...
```

### **6. Request HTTP Real (OkHttp):**
```
OkHttp: --> POST https://api.rh247.com.br/15/services/util/sincronizar-ponto-table
OkHttp: Content-Type: application/json; charset=UTF-8
OkHttp: Content-Length: 245
OkHttp: 
OkHttp: [{"funcionarioId":"00905076303","funcionarioNome":"ADAMS ANTONIO GIRAO MENESES","dataHora":"2025-01-16 13:43:22","tipoPonto":"ENTRADA","latitude":null,"longitude":null,"observacao":null}]
OkHttp: --> END POST
```

### **7. Resposta da API:**
```
SYNC_DEBUG: 📡 === RESPOSTA DA API ===
SYNC_DEBUG:   📈 Status Code: 200
SYNC_DEBUG:   ✅ Sucesso: true
SYNC_DEBUG:   📝 Response Body: PontoSyncResponse(success=true, message=Pontos sincronizados, pontosSincronizados=1)
SYNC_DEBUG:   🎯 Success: true
SYNC_DEBUG:   💬 Message: 'Pontos sincronizados'
SYNC_DEBUG:   📊 Pontos Sincronizados: 1
```

### **8. Se Houver Erro:**
```
SYNC_DEBUG: ❌ === ERRO DE REDE ===
SYNC_DEBUG:   🔴 Tipo: ConnectException
SYNC_DEBUG:   💬 Mensagem: Failed to connect to api.rh247.com.br
SYNC_DEBUG:   📍 Stack Trace:
```

## 🧪 **Como Testar**

1. **Faça um ponto** pelo reconhecimento facial
2. **Abra o Logcat** (filtre por `SYNC_DEBUG`)
3. **Vá para "Visualizar Pontos"**
4. **Clique no botão de sincronização**
5. **Veja TODOS os detalhes** nos logs

## 📋 **Comandos Úteis**

### **Ver apenas JSON:**
```bash
adb logcat | grep -A 20 "JSON COMPLETO DA REQUEST"
```

### **Ver apenas erros:**
```bash
adb logcat | grep -E "(❌|ERROR)"
```

### **Ver request HTTP:**
```bash
adb logcat | grep "OkHttp"
```

### **Salvar em arquivo:**
```bash
adb logcat | grep "SYNC_DEBUG" > request_completa.txt
```

## 🎯 **O Que Procurar**

### ✅ **Request OK:**
- JSON bem formatado
- Todos os campos preenchidos
- Status 200 na resposta
- `"success": true` na resposta

### ❌ **Problemas Comuns:**
- Campos vazios ou null
- Status 404/500
- `"success": false`
- Erros de conexão

---

**Agora você pode ver EXATAMENTE qual request está sendo enviada para sua API! 🔍**

Teste e me mostre os logs que aparecem! 