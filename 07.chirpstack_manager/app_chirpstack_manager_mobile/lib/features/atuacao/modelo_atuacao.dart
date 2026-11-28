import 'dart:convert';

import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';

/// Rótulos, cores e regras dos comandos, cenas, agendamentos e automações (os mesmos enums da API).
class Atuacao {
  Atuacao._();

  /// Rótulos de TipoComando (usados quando a resposta não traz o tipoRotulo).
  static const Map<String, String> rotulosComando = {
    'STATUS': 'Consultar status',
    'INTERVALO': 'Intervalo de envio',
    'ENVIAR_AGORA': 'Enviar leitura agora',
    'CONFIRMADO': 'Uplink confirmado',
    'CLASSE': 'Classe LoRaWAN',
    'REINICIAR': 'Reiniciar',
    'RELE_DEFINIR': 'Ligar/desligar relé',
    'RELE_PULSO': 'Pulso no relé',
    'RELE_MASCARA': 'Vários relés de uma vez',
    'RELE_LER': 'Ler relés',
    'PZEM_LER': 'Ler medidor de energia',
    'PZEM_ZERAR_ENERGIA': 'Zerar energia acumulada',
    'PZEM_LIMITE_POTENCIA': 'Limite de potência do medidor',
    'RESET_REMOTO': 'Reset remoto (porta 101)',
    'BRUTO': 'Downlink livre (porta e bytes)',
  };

  static String rotuloComando(String? tipo) => rotulosComando[tipo] ?? tipo ?? '—';

  /// Status do comando na fila (StatusComando).
  static const List<String> statusComando = ['PENDENTE', 'ENFILEIRADO', 'TRANSMITIDO', 'EXECUTADO', 'FALHOU', 'EXPIRADO', 'CANCELADO'];

  static String rotuloStatus(String? s) => switch (s) {
        'PENDENTE' => 'Pendente',
        'ENFILEIRADO' => 'Enfileirado',
        'TRANSMITIDO' => 'Transmitido',
        'EXECUTADO' => 'Executado',
        'FALHOU' => 'Falhou',
        'EXPIRADO' => 'Expirado',
        'CANCELADO' => 'Cancelado',
        _ => s ?? '—',
      };

  static Color corStatus(String? s) => switch (s) {
        'PENDENTE' => CmCores.azul500,
        'ENFILEIRADO' || 'TRANSMITIDO' => CmCores.tealEscuro,
        'EXECUTADO' => CmCores.online,
        'FALHOU' => CmCores.offline,
        'EXPIRADO' => CmCores.aviso,
        _ => CmCores.neutro,
      };

  static ChipEstado chipStatus(String? s) => ChipEstado(texto: rotuloStatus(s), cor: corStatus(s));

  /// O comando ainda pode ser cancelado (não terminou).
  static bool cancelavel(String? s) => s == 'PENDENTE' || s == 'ENFILEIRADO' || s == 'TRANSMITIDO';

  static String rotuloOrigem(String? o) => switch (o) {
        'MANUAL' => 'Manual',
        'AGENDAMENTO' => 'Agendamento',
        'AUTOMACAO' => 'Automação',
        'CENA' => 'Cena',
        'RECONCILIACAO' => 'Reconciliação',
        'PULSO' => 'Fim do pulso',
        _ => o ?? '—',
      };

  static String rotuloResultado(String? r) => switch (r) {
        'EXECUTADO' => 'Executado',
        'SEM_RESPOSTA' => 'Sem resposta',
        'NAO_CONFIRMADO' => 'Não confirmado',
        'ERRO' => 'Erro',
        'RECUSADO' => 'Recusado',
        'OCUPADO' => 'Ocupado',
        'PERDIDO' => 'Perdido',
        'EXPIRADO' => 'Expirado',
        'CANCELADO' => 'Cancelado',
        null => 'Em andamento',
        _ => r,
      };

  static Color corResultado(String? r) => switch (r) {
        'EXECUTADO' => CmCores.online,
        null => CmCores.azul500,
        'ERRO' || 'RECUSADO' || 'PERDIDO' => CmCores.offline,
        'CANCELADO' => CmCores.neutro,
        _ => CmCores.aviso,
      };

  static String rotuloEstrategia(String? e) => switch (e) {
        'RESPOSTA_SEQ' => 'Resposta com o mesmo seq',
        'RESPOSTA_AT' => 'Resposta AT',
        'RESPOSTA_MODBUS' => 'Resposta Modbus',
        'CONFIRMACAO_LORAWAN' => 'Confirmação LoRaWAN',
        'TRANSMISSAO' => 'Transmissão pelo gateway',
        _ => e ?? '—',
      };

  static String rotuloGatilho(String? g) => switch (g) {
        'ALERTA_ABERTO' => 'Quando o alerta abrir',
        'ALERTA_RESOLVIDO' => 'Quando o alerta resolver',
        _ => g ?? '—',
      };

  /// Dias da semana da API: 1 = segunda … 7 = domingo.
  static const List<String> diasCurtos = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];

  static String dias(List<int> dias) {
    if (dias.isEmpty || dias.length == 7) return 'Todos os dias';
    final ordenados = [...dias]..sort();
    return ordenados.where((d) => d >= 1 && d <= 7).map((d) => diasCurtos[d - 1]).join(', ');
  }

  /// Recorrência de um agendamento em texto.
  static String recorrencia(Json a) {
    if (a.texto('recorrencia') == 'UNICA') return 'Uma vez em ${Formatos.dataHora(a.data('executarEm'))}';
    final dias = (a['diasSemana'] is List ? (a['diasSemana'] as List).whereType<num>().map((e) => e.toInt()).toList() : <int>[]);
    return '${Atuacao.dias(dias)} às ${horaCurta(a.texto('horario')) ?? '—'}';
  }

  /// "08:30:00" → "08:30".
  static String? horaCurta(String? h) {
    if (h == null) return null;
    return h.length >= 5 ? h.substring(0, 5) : h;
  }

  /// "HH:mm" válido (00:00 a 23:59).
  static bool horaValida(String h) => RegExp(r'^([01]\d|2[0-3]):[0-5]\d$').hasMatch(h);

  /// Mensagem de recusa por carga crítica (HTTP 409 da API).
  static bool ehCargaCritica(int status, String? mensagem) =>
      status == 409 && (mensagem ?? '').toLowerCase().contains('carga crítica');

  /// Leitura de um campo JSON guardado como texto (leitura do PZEM, último STATUS).
  static Json? jsonDeTexto(String? texto) {
    if (texto == null || texto.trim().isEmpty) return null;
    try {
      return comoMapa(jsonDecode(texto));
    } catch (_) {
      return null;
    }
  }
}
