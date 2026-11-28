import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/lista_paginada.dart';

/// Chamadas da API de equipamentos, fila de comandos, cenas, agendamentos e automações.
class AtuacaoApi {
  AtuacaoApi._();

  static ApiClient get _api => ApiClient.instancia;

  // ── equipamento ──
  static Future<List<Json>> modelos() async => comoLista(await _api.get('/api/v1/equipamentos/modelos'));

  static Future<Json> equipamento(int dispositivoId) async => comoMapa(await _api.get('/api/v1/dispositivos/$dispositivoId/equipamento'));

  static Future<Json> salvarEquipamento(int dispositivoId, Json corpo) async =>
      comoMapa(await _api.put('/api/v1/dispositivos/$dispositivoId/equipamento', corpo: corpo));

  static Future<void> removerEquipamento(int dispositivoId) => _api.delete('/api/v1/dispositivos/$dispositivoId/equipamento');

  static Future<Json> energia(int dispositivoId, {required int canal, required DateTime de, required DateTime ate}) async =>
      comoMapa(await _api.get('/api/v1/dispositivos/$dispositivoId/energia', query: {
        'canal': canal,
        'de': de.toUtc().toIso8601String(),
        'ate': ate.toUtc().toIso8601String(),
      }));

  // ── comandos ──
  static Future<Json> enviarComando(int dispositivoId, String tipo, Json parametros, {Json? politica, bool confirmarCritico = false}) async =>
      comoMapa(await _api.post('/api/v1/dispositivos/$dispositivoId/comandos', corpo: {
        'tipo': tipo,
        'parametros': parametros,
        'politica': ?politica,
        'confirmarCritico': confirmarCritico,
      }));

  static Future<PaginaResultado> comandosDoDispositivo(int dispositivoId, int pagina, int tamanho) async =>
      PaginaResultado.deJson(await _api.get('/api/v1/dispositivos/$dispositivoId/comandos', query: {'pagina': pagina, 'tamanho': tamanho}));

  static Future<PaginaResultado> comandos({String? status, int? dispositivoId, required int pagina, required int tamanho}) async =>
      PaginaResultado.deJson(await _api.get('/api/v1/comandos', query: {
        'status': status,
        'dispositivoId': dispositivoId,
        'pagina': pagina,
        'tamanho': tamanho,
      }));

  static Future<Json> resumoComandos() async => comoMapa(await _api.get('/api/v1/comandos/resumo'));

  static Future<Json> comando(int id) async => comoMapa(await _api.get('/api/v1/comandos/$id'));

  static Future<Json> cancelarComando(int id) async => comoMapa(await _api.post('/api/v1/comandos/$id/cancelar'));

  static Future<Json> reenviarComando(int id, {bool confirmarCritico = false}) async =>
      comoMapa(await _api.post('/api/v1/comandos/$id/reenviar', corpo: {'confirmarCritico': confirmarCritico}));

  // ── cenas ──
  static Future<List<Json>> cenas() async => comoLista(await _api.get('/api/v1/cenas'));

  static Future<Json> salvarCena(int? id, Json corpo) async =>
      comoMapa(id == null ? await _api.post('/api/v1/cenas', corpo: corpo) : await _api.put('/api/v1/cenas/$id', corpo: corpo));

  static Future<void> excluirCena(int id) => _api.delete('/api/v1/cenas/$id');

  static Future<Json> executarCena(int id, {bool confirmarCritico = false}) async =>
      comoMapa(await _api.post('/api/v1/cenas/$id/executar', corpo: {'confirmarCritico': confirmarCritico}));

  // ── agendamentos ──
  static Future<List<Json>> agendamentos({int? dispositivoId}) async =>
      comoLista(await _api.get('/api/v1/agendamentos', query: {'dispositivoId': dispositivoId}));

  static Future<Json> salvarAgendamento(int? id, Json corpo) async => comoMapa(
      id == null ? await _api.post('/api/v1/agendamentos', corpo: corpo) : await _api.put('/api/v1/agendamentos/$id', corpo: corpo));

  static Future<void> excluirAgendamento(int id) => _api.delete('/api/v1/agendamentos/$id');

  // ── automações ──
  static Future<List<Json>> automacoes() async => comoLista(await _api.get('/api/v1/automacoes'));

  static Future<Json> salvarAutomacao(int? id, Json corpo) async =>
      comoMapa(id == null ? await _api.post('/api/v1/automacoes', corpo: corpo) : await _api.put('/api/v1/automacoes/$id', corpo: corpo));

  static Future<void> excluirAutomacao(int id) => _api.delete('/api/v1/automacoes/$id');

  static Future<List<Json>> regrasAlerta() async => comoLista(await _api.get('/api/v1/regras-alerta'));

  /// Corpo de agendamento/automação a partir de um existente, mudando só alguns campos (ativar/pausar).
  static Json corpoDe(Json a, {required List<String> campos, Map<String, Object?> mudar = const {}}) {
    final c = <String, Object?>{for (final k in campos) k: a[k]};
    c.addAll(mudar);
    return c;
  }
}
