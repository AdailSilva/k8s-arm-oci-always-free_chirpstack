import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/format/formatos.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/core/ui/dialogos.dart';
import 'package:chirpstack_manager/features/atuacao/atuacao_api.dart';
import 'package:chirpstack_manager/features/atuacao/formularios.dart';
import 'package:chirpstack_manager/features/atuacao/modelo_atuacao.dart';
import 'package:chirpstack_manager/features/atuacao/parametros.dart';
import 'package:chirpstack_manager/features/atuacao/selecao.dart';

/// Campos de AgendamentoRequest (para ativar/pausar sem abrir o formulário).
const _camposAgendamento = [
  'nome', 'alvo', 'dispositivoId', 'tipo', 'parametros', 'cenaId', 'recorrencia', 'executarEm', 'horario', 'diasSemana', 'validadeMin', 'maxTentativas', 'ativo',
];

/// Agendamentos: lista (todos, ou só os de um dispositivo), criar, editar, excluir e ativar/pausar.
class AgendamentosLista extends StatefulWidget {
  const AgendamentosLista({super.key, this.dispositivoId, this.dispositivoNome});

  /// Só os agendamentos deste dispositivo (e os novos já apontam para ele).
  final int? dispositivoId;
  final String? dispositivoNome;

  @override
  State<AgendamentosLista> createState() => _AgendamentosListaState();
}

class _AgendamentosListaState extends State<AgendamentosLista> {
  List<Json> _itens = const [];
  Object? _erro;
  bool _carregando = true;

  bool get _podeAlterar => Sessao.instancia.usuario?.podeAlterar ?? false;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    try {
      final l = await AtuacaoApi.agendamentos(dispositivoId: widget.dispositivoId);
      if (mounted) {
        setState(() {
          _itens = l;
          _erro = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _erro = e);
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  Future<void> _abrir([Json? a]) async {
    final salvo = await Navigator.of(context).push<bool>(MaterialPageRoute(
      builder: (_) => AgendamentoFormPage(atual: a, dispositivoId: widget.dispositivoId),
    ));
    if (salvo == true) await _carregar();
  }

  Future<void> _alternar(Json a, bool ativo) async {
    final id = a.inteiro('id');
    if (id == null) return;
    try {
      final corpo = AtuacaoApi.corpoDe(a, campos: _camposAgendamento, mudar: {'ativo': ativo, 'horario': Atuacao.horaCurta(a.texto('horario'))});
      final novo = await executarComCritico(context, (c) => AtuacaoApi.salvarAgendamento(id, {...corpo, 'confirmarCritico': c}));
      if (novo == null || !mounted) return;
      setState(() => _itens = [for (final x in _itens) x.inteiro('id') == id ? novo : x]);
      Navegacao.avisar(ativo ? 'Agendamento ativado.' : 'Agendamento pausado.');
    } catch (e) {
      Navegacao.avisar(mensagemDeErro(e));
    }
  }

  Future<void> _excluir(Json a) async {
    final id = a.inteiro('id');
    if (id == null) return;
    final ok = await confirmar(context, titulo: 'Excluir agendamento', texto: 'Excluir "${a.texto('nome') ?? ''}"?', rotuloConfirmar: 'Excluir', perigo: true, icone: Icons.delete_outline);
    if (!ok) return;
    try {
      await AtuacaoApi.excluirAgendamento(id);
      Navegacao.avisar('Agendamento excluído.');
      await _carregar();
    } catch (e) {
      Navegacao.avisar(mensagemDeErro(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final botao = !_podeAlterar
        ? null
        : Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: FilledButton.icon(onPressed: () => _abrir(), icon: const Icon(Icons.add), label: const Text('Novo agendamento')),
          );
    Widget corpo;
    if (_carregando) {
      corpo = const Carregando();
    } else if (_erro != null && _itens.isEmpty) {
      corpo = EstadoMensagem(icone: Icons.cloud_off_outlined, titulo: 'Não foi possível carregar', texto: mensagemDeErro(_erro!), acao: _carregar);
    } else if (_itens.isEmpty) {
      corpo = EstadoMensagem(icone: Icons.event_outlined, titulo: 'Nenhum agendamento', texto: 'Execute um comando ou uma cena em data e hora única ou toda semana.', cabecalho: botao);
    } else {
      corpo = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          ?botao,
          for (final a in _itens)
            Card(
              child: InkWell(
                onTap: _podeAlterar ? () => _abrir(a) : null,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(14, 10, 6, 10),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Icon(a.texto('alvo') == 'CENA' ? Icons.collections_outlined : Icons.send_outlined, color: CmCores.azul500, size: 20),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(a.texto('nome') ?? '—', maxLines: 2, overflow: TextOverflow.ellipsis,
                            style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: a.logico('ativo') ? null : cs.onSurfaceVariant)),
                        const SizedBox(height: 2),
                        Text(
                          a.texto('alvo') == 'CENA'
                              ? 'Cena ${a.texto('cenaNome') ?? '#${a.inteiro('cenaId')}'}'
                              : [a.texto('resumo') ?? a.texto('tipoRotulo') ?? Atuacao.rotuloComando(a.texto('tipo')), if (widget.dispositivoId == null) ?a.texto('dispositivoNome')].join(' · '),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant),
                        ),
                        Text(Atuacao.recorrencia(a), maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
                        if (a.data('proximaExecucaoEm') != null)
                          Text('Próxima: ${Formatos.dataHora(a.data('proximaExecucaoEm'))}', style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
                      ]),
                    ),
                    Column(children: [
                      Switch(value: a.logico('ativo'), onChanged: _podeAlterar ? (b) => _alternar(a, b) : null),
                      if (_podeAlterar) IconButton(tooltip: 'Excluir', icon: const Icon(Icons.delete_outline), onPressed: () => _excluir(a)),
                    ]),
                  ]),
                ),
              ),
            ),
        ],
      );
    }
    return RefreshIndicator(onRefresh: _carregar, child: corpo);
  }
}

/// Tela "Agendamentos" (todos os dispositivos).
class AgendamentosPage extends StatelessWidget {
  const AgendamentosPage({super.key});

  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Agendamentos')), body: const AgendamentosLista());
}

/// Criar ou editar um agendamento.
class AgendamentoFormPage extends StatefulWidget {
  const AgendamentoFormPage({super.key, this.atual, this.dispositivoId});

  /// AgendamentoResponse em edição (nulo = novo).
  final Json? atual;

  /// Dispositivo fixo (aba do dispositivo).
  final int? dispositivoId;

  @override
  State<AgendamentoFormPage> createState() => _AgendamentoFormPageState();
}

class _AgendamentoFormPageState extends State<AgendamentoFormPage> {
  final _cache = CacheEquipamentos();
  final _nome = TextEditingController();
  final _hora = TextEditingController(text: '08:00');
  final _validade = TextEditingController();
  final _tentativas = TextEditingController();
  List<Json> _dispositivos = const [];
  List<Json> _cenas = const [];

  String _alvo = 'COMANDO';
  int? _dispositivoId;
  late ValoresComando _cmd;
  int _versaoCmd = 0;
  int? _cenaId;
  String _recorrencia = 'SEMANAL';
  DateTime? _executarEm;
  Set<int> _dias = {};
  bool _ativo = true;

  Map<String, String> _erros = const {};
  Map<String, String> _errosCmd = const {};
  String? _erro;
  bool _salvando = false;

  @override
  void initState() {
    super.initState();
    final a = widget.atual;
    _dispositivoId = widget.dispositivoId;
    _cmd = ValoresComando();
    if (a != null) {
      _nome.text = a.texto('nome') ?? '';
      _alvo = a.texto('alvo') ?? 'COMANDO';
      _dispositivoId = a.inteiro('dispositivoId') ?? widget.dispositivoId;
      _cmd = ValoresComando.deJson(a.texto('tipo'), a.mapa('parametros'));
      _cenaId = a.inteiro('cenaId');
      _recorrencia = a.texto('recorrencia') ?? 'SEMANAL';
      _executarEm = a.data('executarEm');
      _hora.text = Atuacao.horaCurta(a.texto('horario')) ?? '08:00';
      _dias = (a['diasSemana'] is List ? (a['diasSemana'] as List).whereType<num>().map((e) => e.toInt()).toSet() : <int>{});
      if (a.inteiro('validadeMin') != null) _validade.text = '${a.inteiro('validadeMin')}';
      if (a.inteiro('maxTentativas') != null) _tentativas.text = '${a.inteiro('maxTentativas')}';
      _ativo = a.logico('ativo', true);
    }
    _carregarListas();
  }

  @override
  void dispose() {
    for (final c in [_nome, _hora, _validade, _tentativas]) {
      c.dispose();
    }
    super.dispose();
  }

  void _mudou() {
    if (mounted) setState(() {});
  }

  Future<void> _carregarListas() async {
    _cache.garantir(_dispositivoId, _mudou);
    try {
      final r = await Future.wait([carregarDispositivos(), AtuacaoApi.cenas()]);
      if (mounted) {
        setState(() {
          _dispositivos = r[0];
          _cenas = r[1];
        });
      }
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    }
  }

  void _mudarDispositivo(int? id) {
    setState(() {
      _dispositivoId = id;
      _cmd = ValoresComando();
      _versaoCmd++;
    });
    _cache.garantir(id, _mudou);
  }

  Future<void> _escolherData() async {
    final agora = DateTime.now();
    final base = _executarEm ?? agora.add(const Duration(hours: 1));
    final d = await showDatePicker(context: context, initialDate: base.isBefore(agora) ? agora : base, firstDate: DateTime(agora.year, agora.month, agora.day), lastDate: agora.add(const Duration(days: 366)));
    if (d == null || !mounted) return;
    final t = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(base));
    if (t == null) return;
    setState(() => _executarEm = DateTime(d.year, d.month, d.day, t.hour, t.minute));
  }

  Future<void> _salvar() async {
    FocusScope.of(context).unfocus();
    final erros = <String, String>{};
    var errosCmd = <String, String>{};
    if (_nome.text.trim().isEmpty) erros['nome'] = 'Informe o nome.';
    if (_alvo == 'COMANDO') {
      if (_dispositivoId == null) erros['dispositivo'] = 'Escolha o dispositivo.';
      errosCmd = _cmd.validar();
    } else if (_cenaId == null) {
      erros['cena'] = 'Escolha a cena.';
    }
    if (_recorrencia == 'UNICA' && _executarEm == null) erros['quando'] = 'Escolha a data e a hora.';
    if (_recorrencia == 'SEMANAL' && !Atuacao.horaValida(_hora.text.trim())) erros['hora'] = 'Horário no formato HH:mm.';
    final validade = int.tryParse(_validade.text.trim());
    if (_validade.text.trim().isNotEmpty && (validade == null || validade < 1 || validade > 43200)) erros['validade'] = 'De 1 a 43.200 min.';
    final tentativas = int.tryParse(_tentativas.text.trim());
    if (_tentativas.text.trim().isNotEmpty && (tentativas == null || tentativas < 1 || tentativas > 50)) erros['tentativas'] = 'De 1 a 50.';
    setState(() {
      _erros = erros;
      _errosCmd = errosCmd;
      _erro = erros.isEmpty && errosCmd.isEmpty ? null : 'Corrija os campos destacados.';
    });
    if (erros.isNotEmpty || errosCmd.isNotEmpty) return;
    final comando = _alvo == 'COMANDO';
    final corpo = <String, Object?>{
      'nome': _nome.text.trim(),
      'alvo': _alvo,
      'dispositivoId': comando ? _dispositivoId : null,
      'tipo': comando ? _cmd.tipo : null,
      'parametros': comando ? _cmd.paraJson() : null,
      'cenaId': comando ? null : _cenaId,
      'recorrencia': _recorrencia,
      'executarEm': _recorrencia == 'UNICA' ? _executarEm!.toUtc().toIso8601String() : null,
      'horario': _recorrencia == 'SEMANAL' ? _hora.text.trim() : null,
      'diasSemana': _recorrencia == 'SEMANAL' ? (_dias.toList()..sort()) : null,
      'validadeMin': validade,
      'maxTentativas': tentativas,
      'ativo': _ativo,
    };
    setState(() => _salvando = true);
    try {
      final r = await executarComCritico(context, (c) => AtuacaoApi.salvarAgendamento(widget.atual?.inteiro('id'), {...corpo, 'confirmarCritico': c}));
      if (r == null || !mounted) return;
      Navegacao.avisar('Agendamento salvo.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _salvando = false);
    }
  }

  /// Retrato do formulário para a trava "Deseja realmente sair?".
  String _retrato() {
    return jsonEncode([
      _nome.text, _hora.text, _validade.text, _tentativas.text, _alvo, _dispositivoId, _cmd.tipo, _cmd.paraJson(), _cenaId,
      _recorrencia, _executarEm?.toIso8601String(), (_dias.toList()..sort()), _ativo,
    ]);
  }

  @override
  Widget build(BuildContext context) => TravaEdicao(retrato: _retrato, child: Builder(builder: _construir));

  Widget _construir(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: Text(widget.atual == null ? 'Novo agendamento' : 'Editar agendamento')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
        children: [
          if (_erro != null) ...[CaixaMensagem(texto: _erro!), const SizedBox(height: 16)],
          CampoRotulado(
            rotulo: 'Nome',
            child: TextField(controller: _nome, maxLength: 120, decoration: InputDecoration(hintText: 'Ex.: Ligar a bomba de manhã', counterText: '', errorText: _erros['nome'])),
          ),
          SeletorAlvo(valor: _alvo, aoMudar: (v) => setState(() => _alvo = v)),
          if (_alvo == 'COMANDO') ...[
            SeletorDispositivo(dispositivos: _dispositivos, valor: _dispositivoId, aoMudar: _mudarDispositivo, erro: _erros['dispositivo'], habilitado: widget.dispositivoId == null),
            Card(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                child: _cache.comandos(_dispositivoId).isEmpty
                    ? Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(_cache.semComandos(_dispositivoId), style: TextStyle(color: cs.onSurfaceVariant)))
                    : FormComando(
                        key: ValueKey('cmd-$_dispositivoId-$_versaoCmd-${_cache.comandos(_dispositivoId).length}'),
                        valores: _cmd,
                        comandos: _cache.comandos(_dispositivoId),
                        perifericos: _cache.perifericos(_dispositivoId),
                        erros: _errosCmd,
                        aoMudar: _mudou,
                      ),
              ),
            ),
            const SizedBox(height: 16),
          ] else
            SeletorCena(cenas: _cenas, valor: _cenaId, aoMudar: (v) => setState(() => _cenaId = v), erro: _erros['cena']),
          CampoRotulado(
            rotulo: 'Recorrência',
            child: SegmentedButton<String>(
              showSelectedIcon: false,
              segments: const [
                ButtonSegment(value: 'UNICA', label: RotuloEncolhe('Uma vez')),
                ButtonSegment(value: 'SEMANAL', label: RotuloEncolhe('Toda semana')),
              ],
              selected: {_recorrencia},
              onSelectionChanged: (s) => setState(() => _recorrencia = s.first),
            ),
          ),
          if (_recorrencia == 'UNICA')
            CampoRotulado(
              rotulo: 'Data e hora',
              ajuda: _erros['quando'],
              child: OutlinedButton.icon(
                onPressed: _escolherData,
                icon: const Icon(Icons.event),
                label: Text(_executarEm == null ? 'Escolher' : Formatos.dataHora(_executarEm), maxLines: 1, overflow: TextOverflow.ellipsis),
              ),
            )
          else ...[
            CampoRotulado(rotulo: 'Horário', child: CampoHora(controller: _hora, aoMudar: (_) {}, erro: _erros['hora'])),
            CampoRotulado(
              rotulo: 'Dias da semana',
              ajuda: 'Nenhum marcado = todos os dias.',
              child: SeletorDias(dias: _dias, aoMudar: (d) => setState(() => _dias = d)),
            ),
          ],
          CampoRotulado(
            rotulo: 'Validade de cada execução (min)',
            ajuda: 'Se não executar nesse prazo, expira. Em branco, 60 min.',
            child: TextField(
              controller: _validade,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(hintText: '60', errorText: _erros['validade']),
            ),
          ),
          CampoRotulado(
            rotulo: 'Máximo de tentativas',
            ajuda: 'De 1 a 50. Em branco, o padrão do servidor.',
            child: TextField(
              controller: _tentativas,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(hintText: '5', errorText: _erros['tentativas']),
            ),
          ),
          InterruptorRotulado(titulo: 'Ativo', valor: _ativo, aoMudar: (b) => setState(() => _ativo = b)),
          const SizedBox(height: 8),
          FilledButton.icon(onPressed: _salvando ? null : _salvar, icon: const Icon(Icons.check), label: Text(_salvando ? 'Salvando…' : 'Salvar')),
        ],
      ),
    );
  }
}

/// Resumo de um comando de cena/agendamento/automação (o servidor manda "resumo" quando salvo).
String resumoDe(Json j) => j.texto('resumo') ?? resumirComando(j.texto('tipo'), j.mapa('parametros'));
