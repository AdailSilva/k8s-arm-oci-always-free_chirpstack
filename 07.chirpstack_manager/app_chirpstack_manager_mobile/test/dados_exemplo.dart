import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'package:chirpstack_manager/core/auth/auth_modelos.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';

/// Respostas simuladas da API para os testes de tela. Os nomes são longos de propósito: o
/// layout precisa aguentar o pior caso sem transbordar.
final Map<String, Object> respostas = {
  '/api/v1/auth/me': {
    'id': 1,
    'nome': 'Maria Aparecida dos Santos Albuquerque',
    'email': 'maria.aparecida.albuquerque@empresa-exemplo.com.br',
    'perfil': 'OPERADOR',
    'cadastro': {
      'cargo': 'Técnica de campo responsável pela manutenção preventiva',
      'telefone': '88999998888',
      'cep': '62320000',
      'enderecoTipo': 'AVENIDA',
      'endereco': 'Presidente Juscelino Kubitschek de Oliveira',
      'numero': '1234',
      'complemento': 'Bloco B, sala 1203, ao lado da recepção',
      'bairro': 'Centro Histórico',
      'cidade': 'Tianguá',
      'uf': 'CE',
    },
  },
  '/api/v1/painel': {
    'dispositivos': 1284,
    'online': 1190,
    'semComunicacao': 87,
    'leiturasUltimas24h': 1284093,
    'alertasAbertos': 12,
    'alertasReconhecidos': 3,
    'mqtt': {
      'habilitado': true,
      'conectado': true,
      'ultimaMensagemEm': DateTime.now().subtract(const Duration(seconds: 20)).toUtc().toIso8601String(),
      'mensagens': 958221,
    },
    'ultimasLeituras': [
      {'dispositivoId': 1, 'dispositivoNome': 'Sensor de temperatura da câmara fria número 12 do galpão norte', 'recebidoEm': DateTime.now().toUtc().toIso8601String(), 'rssi': -97, 'snr': 7.5},
      {'dispositivoId': 2, 'dispositivoNome': 'Medidor de nível', 'recebidoEm': DateTime.now().subtract(const Duration(minutes: 7)).toUtc().toIso8601String(), 'rssi': -61, 'snr': 10.25},
    ],
  },
  '/api/v1/dispositivos': {
    'totalCount': 87,
    'result': [
      {'id': 1, 'devEui': 'a84041ffff1f2a01', 'nome': 'Sensor de temperatura da câmara fria número 12 do galpão norte', 'situacao': 'ONLINE', 'ultimoContatoEm': DateTime.now().toUtc().toIso8601String(), 'ultimoRssi': -97, 'ultimaBateria': 15, 'aplicacaoNome': 'Monitoramento de câmaras frias e estoque refrigerado'},
      {'id': 2, 'devEui': 'a84041ffff1f2a07', 'nome': 'Medidor de nível', 'situacao': 'SEM_COMUNICACAO', 'ultimoContatoEm': DateTime.now().subtract(const Duration(days: 3)).toUtc().toIso8601String(), 'ultimoRssi': -120, 'ultimaBateria': 60},
      {'id': 3, 'devEui': 'a84041ffff1f2a0c', 'nome': 'Novo dispositivo', 'situacao': 'NUNCA'},
    ],
  },
  '/api/v1/dispositivos/1': {
    'id': 1, 'devEui': 'a84041ffff1f2a01', 'nome': 'Sensor de temperatura da câmara fria número 12 do galpão norte', 'situacao': 'ONLINE',
    'aplicacaoNome': 'Monitoramento de câmaras frias e estoque refrigerado', 'tenantNome': 'Distribuidora Exemplo Ltda', 'perfilNome': 'AU915 · Classe A · OTAA',
    'ultimoContatoEm': DateTime.now().toUtc().toIso8601String(), 'ultimoEvento': 'up', 'ultimoGateway': 'a84041ffff1f2a99', 'ultimoRssi': -97, 'ultimoSnr': 7.5, 'ultimaBateria': 15, 'totalUplinks': 48211,
  },
  '/api/v1/dispositivos/1/leituras': {
    'totalCount': 2,
    'result': [
      {'id': 10, 'recebidoEm': DateTime.now().toUtc().toIso8601String(), 'fCnt': 48211, 'fPort': 10, 'spreadingFactor': 12, 'rssi': -97, 'snr': 7.5, 'objeto': '{"temperatura":-18.4,"umidade":62,"porta_aberta":false,"bateria_mv":3610}'},
      {'id': 9, 'recebidoEm': DateTime.now().subtract(const Duration(minutes: 10)).toUtc().toIso8601String(), 'fCnt': 48210, 'fPort': 10, 'rssi': -98, 'snr': 6},
    ],
  },
  '/api/v1/chirpstack/gateways': {
    'totalCount': 3,
    'result': [
      {'gatewayId': 'a84041ffff1f2a99', 'name': 'Gateway da torre norte com antena de alto ganho', 'description': 'Instalado no topo da caixa d\'água, cobertura do galpão norte e do pátio de manobras.', 'state': 'ONLINE', 'lastSeenAt': DateTime.now().toUtc().toIso8601String()},
      {'gatewayId': 'a84041ffff1f2a98', 'name': 'Gateway Sede', 'state': 'OFFLINE', 'lastSeenAt': DateTime.now().subtract(const Duration(hours: 5)).toUtc().toIso8601String()},
      {'gatewayId': 'a84041ffff1f2a97', 'name': 'Gateway reserva', 'state': 'NEVER_SEEN'},
    ],
  },
  '/api/v1/alertas': {
    'totalCount': 2,
    'result': [
      {'id': 1, 'regraNome': 'Temperatura acima do limite na câmara fria de congelados', 'severidade': 'CRITICO', 'dispositivoNome': 'Sensor de temperatura da câmara fria número 12 do galpão norte', 'status': 'ABERTO', 'mensagem': 'temperatura = -11,2 °C (limite -15 °C) há mais de 10 minutos.', 'disparadoEm': DateTime.now().toUtc().toIso8601String()},
      {'id': 2, 'regraNome': 'Sem comunicação', 'severidade': 'AVISO', 'dispositivoNome': 'Medidor de nível', 'status': 'RECONHECIDO', 'disparadoEm': DateTime.now().toUtc().toIso8601String(), 'reconhecidoPor': 'operador.de.plantao@empresa-exemplo.com.br'},
    ],
  },
  // ── atuação ──
  '/api/v1/equipamentos/modelos': [
    {
      'modelo': 'ADAILSILVA_V1', 'rotulo': 'Firmware AdailSilva v1 (protocolo próprio)', 'descricao': 'Comando na porta 10, resposta na 11 com o mesmo seq, telemetria TLV na 20.',
      'observacoes': ['O firmware não repete a ação se o mesmo comando chegar de novo.'],
      'perifericos': [{'tipo': 'RELE', 'rotulo': 'Relé', 'atuador': true}, {'tipo': 'PZEM004T', 'rotulo': 'Medidor PZEM-004T', 'atuador': false}],
      'comandos': [
        {'tipo': 'STATUS', 'rotulo': 'Consultar status'},
        {'tipo': 'RELE_DEFINIR', 'rotulo': 'Ligar/desligar relé', 'periferico': 'RELE'},
        {'tipo': 'RELE_PULSO', 'rotulo': 'Pulso no relé', 'periferico': 'RELE'},
        {'tipo': 'PZEM_LER', 'rotulo': 'Ler medidor de energia', 'periferico': 'PZEM004T'},
        {'tipo': 'PZEM_ZERAR_ENERGIA', 'rotulo': 'Zerar energia acumulada', 'periferico': 'PZEM004T'},
        {'tipo': 'PZEM_LIMITE_POTENCIA', 'rotulo': 'Limite de potência do medidor', 'periferico': 'PZEM004T'},
      ],
    },
  ],
  '/api/v1/dispositivos/1/equipamento': {
    'dispositivoId': 1, 'configurado': true, 'modelo': 'ADAILSILVA_V1', 'modeloRotulo': 'Firmware AdailSilva v1 (protocolo próprio)', 'reconciliar': true, 'tarifaKwh': 0.95,
    'status': '{"versao":"1.4.2","uptimeS":86400,"reles":3,"rssiUltimoDownlink":-97}', 'statusEm': DateTime.now().subtract(const Duration(minutes: 3)).toUtc().toIso8601String(),
    'perifericos': [
      {'id': 1, 'tipo': 'RELE', 'canal': 0, 'nome': 'Bomba d\'água da caixa superior do bloco administrativo', 'critico': true, 'estadoDesejado': true, 'estadoReportado': false, 'reportadoEm': DateTime.now().subtract(const Duration(minutes: 3)).toUtc().toIso8601String()},
      {'id': 2, 'tipo': 'RELE', 'canal': 1, 'nome': 'Iluminação', 'critico': false, 'estadoDesejado': false, 'estadoReportado': false},
      {'id': 3, 'tipo': 'PZEM004T', 'canal': 0, 'nome': 'Quadro geral de distribuição do galpão norte', 'enderecoModbus': 1, 'critico': false,
        'leitura': '{"canal":0,"tensao":221.4,"corrente":12.345,"potencia":2733.1,"energia":1284093,"frequencia":60.0,"fp":0.98,"alarme":true,"custoTotal":1219.88}',
        'leituraEm': DateTime.now().subtract(const Duration(minutes: 1)).toUtc().toIso8601String()},
    ],
    'comandos': [
      {'tipo': 'STATUS', 'rotulo': 'Consultar status'},
      {'tipo': 'RELE_DEFINIR', 'rotulo': 'Ligar/desligar relé', 'periferico': 'RELE'},
      {'tipo': 'RELE_PULSO', 'rotulo': 'Pulso no relé', 'periferico': 'RELE'},
      {'tipo': 'PZEM_LER', 'rotulo': 'Ler medidor de energia', 'periferico': 'PZEM004T'},
      {'tipo': 'PZEM_ZERAR_ENERGIA', 'rotulo': 'Zerar energia acumulada', 'periferico': 'PZEM004T'},
      {'tipo': 'PZEM_LIMITE_POTENCIA', 'rotulo': 'Limite de potência do medidor', 'periferico': 'PZEM004T'},
    ],
  },
  '/api/v1/dispositivos/1/energia': {
    'canal': 0, 'nome': 'Quadro geral', 'de': DateTime.now().subtract(const Duration(days: 1)).toUtc().toIso8601String(), 'ate': DateTime.now().toUtc().toIso8601String(),
    'consumoKwh': 1284.093, 'tarifaKwh': 0.95, 'custo': 1219.88, 'potenciaMedia': 2311.4, 'potenciaMaxima': 7104.9, 'leituras': 1440,
  },
  '/api/v1/dispositivos/1/comandos': {
    'totalCount': 1,
    'result': [
      {'id': 41, 'dispositivoId': 1, 'tipo': 'RELE_DEFINIR', 'tipoRotulo': 'Ligar/desligar relé', 'resumo': 'Relé 0 (Bomba d\'água da caixa superior): ligar', 'status': 'TRANSMITIDO', 'origem': 'MANUAL', 'tentativas': 2, 'maxTentativas': 5, 'criadoEm': DateTime.now().toUtc().toIso8601String()},
    ],
  },
  '/api/v1/agendamentos': [
    {
      'id': 1, 'nome': 'Ligar a bomba d\'água todos os dias úteis de manhã cedo', 'alvo': 'COMANDO', 'dispositivoId': 1, 'dispositivoNome': 'Sensor de temperatura da câmara fria número 12 do galpão norte',
      'tipo': 'RELE_DEFINIR', 'tipoRotulo': 'Ligar/desligar relé', 'resumo': 'Relé 0 (Bomba d\'água da caixa superior): ligar', 'parametros': {'canal': 0, 'acao': 'LIGAR'},
      'recorrencia': 'SEMANAL', 'horario': '06:30:00', 'diasSemana': [1, 2, 3, 4, 5], 'validadeMin': 60, 'maxTentativas': 5, 'ativo': true,
      'proximaExecucaoEm': DateTime.now().add(const Duration(hours: 10)).toUtc().toIso8601String(), 'atualizadoEm': DateTime.now().toUtc().toIso8601String(),
    },
    {
      'id': 2, 'nome': 'Sair de casa', 'alvo': 'CENA', 'cenaId': 1, 'cenaNome': 'Sair de casa', 'recorrencia': 'UNICA', 'executarEm': DateTime.now().add(const Duration(days: 2)).toUtc().toIso8601String(),
      'validadeMin': 60, 'maxTentativas': 5, 'ativo': false, 'atualizadoEm': DateTime.now().toUtc().toIso8601String(),
    },
  ],
  '/api/v1/cenas': [
    {
      'id': 1, 'nome': 'Desligar tudo no fim do expediente do galpão norte', 'descricao': 'Desliga a iluminação e a bomba e lê o medidor para o fechamento do dia.', 'temCritico': true,
      'atualizadoEm': DateTime.now().subtract(const Duration(days: 1)).toUtc().toIso8601String(),
      'itens': [
        {'id': 1, 'ordem': 0, 'dispositivoId': 1, 'dispositivoNome': 'Sensor de temperatura da câmara fria número 12 do galpão norte', 'tipo': 'RELE_DEFINIR', 'tipoRotulo': 'Ligar/desligar relé', 'resumo': 'Relé 0: desligar', 'parametros': {'canal': 0, 'acao': 'DESLIGAR'}},
        {'id': 2, 'ordem': 1, 'dispositivoId': 1, 'dispositivoNome': 'Medidor de nível', 'tipo': 'PZEM_LER', 'tipoRotulo': 'Ler medidor de energia', 'parametros': {'canal': 0}},
      ],
    },
  ],
  '/api/v1/regras-alerta': [
    {'id': 1, 'nome': 'Potência acima de 2 kW no quadro geral', 'tipo': 'VALOR', 'dispositivoId': 1, 'dispositivoNome': 'Sensor de temperatura da câmara fria número 12 do galpão norte', 'campo': 'pzem.0.potencia', 'operador': 'GT', 'limite': 2000, 'ativo': true},
    {'id': 2, 'nome': 'Sem comunicação', 'tipo': 'SEM_COMUNICACAO', 'limite': 60, 'ativo': true},
  ],
  '/api/v1/automacoes': [
    {
      'id': 1, 'nome': 'Desligar a bomba quando a potência passar de 2 kW no quadro geral', 'regraId': 1, 'regraNome': 'Potência acima de 2 kW no quadro geral', 'gatilho': 'ALERTA_ABERTO',
      'alvo': 'COMANDO', 'mesmoDispositivo': false, 'dispositivoId': 1, 'dispositivoNome': 'Sensor de temperatura da câmara fria número 12 do galpão norte',
      'tipo': 'RELE_DEFINIR', 'tipoRotulo': 'Ligar/desligar relé', 'resumo': 'Relé 0: desligar', 'parametros': {'canal': 0, 'acao': 'DESLIGAR'},
      'intervaloMinimoS': 300, 'ativo': true, 'ultimaExecucaoEm': DateTime.now().subtract(const Duration(hours: 2)).toUtc().toIso8601String(), 'atualizadoEm': DateTime.now().toUtc().toIso8601String(),
    },
  ],
  '/api/v1/comandos/resumo': {'pendentes': 3, 'emAndamento': 2, 'executados24h': 1284, 'falhas24h': 7},
  '/api/v1/comandos': {
    'totalCount': 1,
    'result': [
      {'id': 41, 'dispositivoId': 1, 'dispositivoNome': 'Sensor de temperatura da câmara fria número 12 do galpão norte', 'tipo': 'RELE_DEFINIR', 'tipoRotulo': 'Ligar/desligar relé', 'resumo': 'Relé 0: ligar', 'status': 'FALHOU', 'origem': 'AUTOMACAO', 'tentativas': 5, 'maxTentativas': 5, 'criadoEm': DateTime.now().toUtc().toIso8601String()},
    ],
  },
};

/// Cliente HTTP simulado: responde pelo caminho (sem a base e a query).
void instalarApiSimulada() {
  ApiClient.instancia.clienteHttp = MockClient((req) async {
    final caminho = req.url.path.replaceFirst(RegExp(r'^/chirpstack-manager'), '');
    final r = respostas[caminho];
    if (r == null) return http.Response('{"status":404,"mensagem":"Não encontrado"}', 404);
    return http.Response.bytes(utf8.encode(jsonEncode(r)), 200, headers: {'content-type': 'application/json; charset=utf-8'});
  });
}

/// Sessão de exemplo (usuário com nome e e-mail longos).
Future<void> iniciarSessaoExemplo({bool trocaSenha = false}) => Sessao.instancia.iniciar(
      RespostaAuth(
        token: 'token',
        refreshToken: 'refresh',
        usuario: const UsuarioSessao(id: 1, nome: 'Maria Aparecida dos Santos Albuquerque', email: 'maria.aparecida.albuquerque@empresa-exemplo.com.br', perfil: 'OPERADOR'),
        trocaSenhaObrigatoria: trocaSenha,
      ),
      lembrar: false,
    );
