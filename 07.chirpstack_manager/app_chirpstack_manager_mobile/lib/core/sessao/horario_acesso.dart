import 'dart:async';

import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';

/// HORÁRIO DE ACESSO — o mesmo do painel web (Sistema › Horário de acesso).
///
/// Para quem não é ADMIN: consulta a situação (GET /api/v1/horario-acesso/situacao) a cada minuto
/// com o app aberto; 5 minutos antes do fim do horário do dia mostra o aviso com a contagem e, no
/// fim, encerra a sessão (motivo FORA_HORARIO) e leva ao login explicando. O relógio é o do
/// servidor. O servidor também encerra a sessão por conta própria, com o app fechado.
class HorarioAcesso with WidgetsBindingObserver {
  HorarioAcesso._();

  static final HorarioAcesso instancia = HorarioAcesso._();

  static const Duration _aviso = Duration(minutes: 5);
  static const Duration _consulta = Duration(seconds: 60);

  Timer? _relogioConsulta;
  Timer? _relogioTick;
  bool _primeiroPlano = true;
  Duration _desvio = Duration.zero;
  DateTime? _fim;
  String _horaFim = '';
  bool _avisoAberto = false;
  bool _dispensado = false;
  bool _encerrando = false;

  /// Tempo restante (para o diálogo do aviso).
  final ValueNotifier<Duration?> restante = ValueNotifier<Duration?>(null);

  void instalar() {
    WidgetsBinding.instance.addObserver(this);
    Sessao.instancia.addListener(_reavaliar);
    _reavaliar();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _primeiroPlano = state == AppLifecycleState.resumed;
    _reavaliar();
  }

  void _reavaliar() {
    final s = Sessao.instancia;
    final ativo = _primeiroPlano && s.autenticado && !s.trocarSenha && !(s.usuario?.admin ?? false);
    if (ativo && _relogioConsulta == null) {
      _encerrando = false;
      unawaited(_consultar());
      _relogioConsulta = Timer.periodic(_consulta, (_) => unawaited(_consultar()));
      _relogioTick = Timer.periodic(const Duration(seconds: 1), (_) => _avaliar());
    } else if (!ativo) {
      _relogioConsulta?.cancel();
      _relogioTick?.cancel();
      _relogioConsulta = _relogioTick = null;
      if (!s.autenticado) {
        _fim = null;
        _dispensado = false;
        restante.value = null;
      }
    }
  }

  Future<void> _consultar() async {
    try {
      final j = comoMapa(await ApiClient.instancia.get('/api/v1/horario-acesso/situacao'));
      final agora = DateTime.tryParse(j.texto('agora') ?? '');
      if (agora != null) _desvio = agora.difference(DateTime.now());
      final novoFim = j.logico('restrito') ? DateTime.tryParse(j.texto('fimDoHorario') ?? '') : null;
      if (novoFim != _fim) _dispensado = false;
      _fim = novoFim;
      _horaFim = j.texto('horaFim') ?? '';
      if (j.logico('restrito') && !j.logico('permitidoAgora', true)) {
        await _encerrar(j.texto('mensagem'));
        return;
      }
      _avaliar();
    } catch (_) {
      // Sem rede: o servidor garante o encerramento no fim do horário.
    }
  }

  void _avaliar() {
    final fim = _fim;
    if (fim == null) {
      restante.value = null;
      return;
    }
    final r = fim.difference(DateTime.now().add(_desvio));
    restante.value = r;
    if (r <= Duration.zero) {
      unawaited(_encerrar(_horaFim.isEmpty
          ? 'O horário de acesso ao sistema terminou. Você foi desconectado.'
          : 'O horário de acesso de hoje terminou às $_horaFim. Você foi desconectado.'));
      return;
    }
    if (r <= _aviso && !_dispensado && !_avisoAberto) _mostrarAviso();
  }

  Future<void> _encerrar(String? mensagem) async {
    if (_encerrando) return;
    _encerrando = true;
    _fecharAviso();
    await AuthService.instancia.sair(motivo: 'FORA_HORARIO', aviso: mensagem ?? 'O horário de acesso ao sistema terminou. Você foi desconectado.');
  }

  /// O login limpa a pilha de telas (inclusive o diálogo): basta esquecer que ele estava aberto.
  void _fecharAviso() => _avisoAberto = false;

  void _mostrarAviso() {
    final ctx = Navegacao.chave.currentContext;
    if (ctx == null) return;
    _avisoAberto = true;
    showDialog<void>(
      context: ctx,
      barrierDismissible: false,
      builder: (c) => _AvisoHorario(horaFim: _horaFim, restante: restante),
    ).whenComplete(() {
      _avisoAberto = false;
      _dispensado = true;
    });
  }
}

class _AvisoHorario extends StatelessWidget {
  const _AvisoHorario({required this.horaFim, required this.restante});

  final String horaFim;
  final ValueNotifier<Duration?> restante;

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return AlertDialog(
      icon: const Icon(Icons.event_busy_outlined, color: CmCores.azul500),
      title: const Text('O horário de acesso está terminando', textAlign: TextAlign.center),
      content: Column(mainAxisSize: MainAxisSize.min, children: [
        Text(
          'O acesso de hoje vai até as $horaFim, conforme o horário definido pelo administrador. Nesse momento você será desconectado.',
          textAlign: TextAlign.center,
          style: const TextStyle(height: 1.45),
        ),
        const SizedBox(height: 18),
        Text('Tempo restante', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13)),
        ValueListenableBuilder<Duration?>(
          valueListenable: restante,
          builder: (_, r, _) {
            final s = (r ?? Duration.zero).inSeconds.clamp(0, 359999);
            final texto = '${(s ~/ 60).toString().padLeft(2, '0')}:${(s % 60).toString().padLeft(2, '0')}';
            return Text(texto,
                style: TextStyle(
                    fontSize: 40,
                    fontWeight: FontWeight.w700,
                    fontFeatures: const [FontFeature.tabularFigures()],
                    color: s <= 60 ? cs.error : CmCores.azul500));
          },
        ),
        const SizedBox(height: 12),
        Text('Salve agora o que estiver aberto: o que não for gravado até lá será perdido.',
            textAlign: TextAlign.center, style: TextStyle(color: cs.onSurfaceVariant, fontSize: 13, height: 1.4)),
      ]),
      actions: [FilledButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Entendi'))],
    );
  }
}
