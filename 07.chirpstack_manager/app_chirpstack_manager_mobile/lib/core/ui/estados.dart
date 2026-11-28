import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';

/// Rótulos e cores de estado — os mesmos do painel web (pages/monitoramento/situacao.ts).
class Estados {
  Estados._();

  /// Situação do dispositivo: ONLINE, SEM_COMUNICACAO ou NUNCA.
  static ChipEstado situacao(String? s) => switch (s) {
        'ONLINE' => const ChipEstado(texto: 'On-line', cor: CmCores.online),
        'SEM_COMUNICACAO' => const ChipEstado(texto: 'Sem comunicação', cor: CmCores.offline),
        _ => const ChipEstado(texto: 'Sem mensagens', cor: CmCores.neutro),
      };

  /// Estado do gateway no ChirpStack: ONLINE, OFFLINE ou NEVER_SEEN.
  static ChipEstado gateway(String? s) => switch (s) {
        'ONLINE' => const ChipEstado(texto: 'Online', cor: CmCores.online),
        'OFFLINE' => const ChipEstado(texto: 'Offline', cor: CmCores.offline),
        _ => const ChipEstado(texto: 'Nunca visto', cor: CmCores.neutro),
      };

  /// Status do alerta: ABERTO, RECONHECIDO ou RESOLVIDO.
  static ChipEstado alerta(String? s) => switch (s) {
        'ABERTO' => const ChipEstado(texto: 'Aberto', cor: CmCores.offline),
        'RECONHECIDO' => const ChipEstado(texto: 'Reconhecido', cor: CmCores.aviso),
        _ => const ChipEstado(texto: 'Resolvido', cor: CmCores.online),
      };

  /// Severidade do alerta: INFO, AVISO ou CRITICO.
  static Color severidade(String? s) => switch (s) {
        'CRITICO' => CmCores.offline,
        'AVISO' => CmCores.aviso,
        _ => CmCores.azul500,
      };

  static String severidadeRotulo(String? s) => switch (s) {
        'CRITICO' => 'Crítico',
        'AVISO' => 'Aviso',
        'INFO' => 'Informativo',
        _ => s ?? '—',
      };

  static Color bateria(int? b) {
    if (b == null) return CmCores.neutro;
    if (b < 20) return CmCores.offline;
    if (b < 50) return CmCores.aviso;
    return CmCores.online;
  }
}
