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
import 'package:chirpstack_manager/features/atuacao/agendamentos_page.dart';
import 'package:chirpstack_manager/features/atuacao/atuacao_api.dart';
import 'package:chirpstack_manager/features/atuacao/formularios.dart';
import 'package:chirpstack_manager/features/atuacao/modelo_atuacao.dart';
import 'package:chirpstack_manager/features/atuacao/parametros.dart';
import 'package:chirpstack_manager/features/atuacao/selecao.dart';

/// Campos de AutomacaoRequest (para ativar/pausar sem abrir o formulário).
const _camposAutomacao = [
  'nome', 'regraId', 'gatilho', 'alvo', 'mesmoDispositivo', 'dispositivoId', 'tipo', 'parametros', 'cenaId', 'intervaloMinimoS', 'ativo',
];

const _operadores = {'GT': '>', 'GTE': '≥', 'LT': '<', 'LTE': '≤', 'EQ': '=', 'NE': '≠'};

/// Descrição curta de uma regra de alerta.
String descreverRegra(Json r) {
  final alvo = r.texto('dispositivoNome') != null ? 'em ${r.texto('dispositivoNome')}' : 'em qualquer dispositivo';
  final limite = r.decimal('limite');
  if (r.texto('tipo') == 'SEM_COMUNICACAO') return 'Abre após ${Formatos.decimal(limite, casas: 0)} min sem mensagem, $alvo.';
  final op = _operadores[r.texto('operador')] ?? r.texto('operador') ?? '';
  return 'Abre quando ${r.texto('campo') ?? 'o valor'} $op ${Formatos.decimal(limite, casas: 2)}, $alvo.';
}

/// Automações: quando o alerta de uma regra abre (ou é resolvido), executa um comando ou uma cena.
class AutomacoesPage extends StatefulWidget {
  const AutomacoesPage({super.key});

  @override
  State<AutomacoesPage> createState() => _AutomacoesPageState();
}

class _AutomacoesPageState extends State<AutomacoesPage> {
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
      final l = await AtuacaoApi.automacoes();
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
    final salvo = await Navigator.of(context).push<bool>(MaterialPageRoute(builder: (_) => AutomacaoFormPage(atual: a)));
    if (salvo == true) await _carregar();
  }

  Future<void> _alternar(Json a, bool ativo) async {
    final id = a.inteiro('id');
    if (id == null) return;
    try {
      final corpo = AtuacaoApi.corpoDe(a, campos: _camposAutomacao, mudar: {'ativo': ativo});
      final novo = await executarComCritico(context, (c) => AtuacaoApi.salvarAutomacao(id, {...corpo, 'confirmarCritico': c}));
      if (novo == null || !mounted) return;
      setState(() => _itens = [for (final x in _itens) x.inteiro('id') == id ? novo : x]);
      Navegacao.avisar(ativo ? 'Automação ativada.' : 'Automação pausada.');
    } catch (e) {
      Navegacao.avisar(mensagemDeErro(e));
    }
  }

  Future<void> _excluir(Json a) async {
    final id = a.inteiro('id');
    if (id == null) return;
    final ok = await confirmar(context, titulo: 'Excluir automação', texto: 'Excluir "${a.texto('nome') ?? ''}"?', rotuloConfirmar: 'Excluir', perigo: true, icone: Icons.delete_outline);
    if (!ok) return;
    try {
      await AtuacaoApi.excluirAutomacao(id);
      Navegacao.avisar('Automação excluída.');
      await _carregar();
    } catch (e) {
      Navegacao.avisar(mensagemDeErro(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    Widget corpo;
    if (_carregando) {
      corpo = const Carregando();
    } else if (_erro != null && _itens.isEmpty) {
      corpo = EstadoMensagem(icone: Icons.cloud_off_outlined, titulo: 'Não foi possível carregar', texto: mensagemDeErro(_erro!), acao: _carregar);
    } else if (_itens.isEmpty) {
      corpo = const EstadoMensagem(
        icone: Icons.account_tree_outlined,
        titulo: 'Nenhuma automação',
        texto: 'Ex.: potência acima de 2000 W desliga o relé do chuveiro. Crie a regra em Alertas e ligue-a a um comando aqui.',
      );
    } else {
      corpo = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 88),
        children: [
          for (final a in _itens)
            Card(
              child: InkWell(
                onTap: _podeAlterar ? () => _abrir(a) : null,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(14, 10, 6, 10),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Padding(padding: EdgeInsets.only(top: 2), child: Icon(Icons.account_tree_outlined, color: CmCores.azul500, size: 20)),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(a.texto('nome') ?? '—', maxLines: 2, overflow: TextOverflow.ellipsis,
                            style: TextStyle(fontWeight: FontWeight.w700, fontSize: 15, color: a.logico('ativo') ? null : cs.onSurfaceVariant)),
                        const SizedBox(height: 2),
                        Text('${Atuacao.rotuloGatilho(a.texto('gatilho'))}: ${a.texto('regraNome') ?? 'regra #${a.inteiro('regraId')}'}',
                            maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
                        Text(
                          a.texto('alvo') == 'CENA'
                              ? 'Cena ${a.texto('cenaNome') ?? '#${a.inteiro('cenaId')}'}'
                              : '${resumoDe(a)} · ${a.logico('mesmoDispositivo') ? 'no dispositivo do alerta' : a.texto('dispositivoNome') ?? '#${a.inteiro('dispositivoId')}'}',
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant),
                        ),
                        if (a.data('ultimaExecucaoEm') != null)
                          Text('Última: ${Formatos.relativo(a.data('ultimaExecucaoEm'))}', style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
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
    return Scaffold(
      appBar: AppBar(title: const Text('Automações')),
      floatingActionButton: _podeAlterar ? FloatingActionButton.extended(onPressed: () => _abrir(), icon: const Icon(Icons.add), label: const Text('Nova automação')) : null,
      body: RefreshIndicator(onRefresh: _carregar, child: corpo),
    );
  }
}

/// Criar ou editar uma automação.
class AutomacaoFormPage extends StatefulWidget {
  const AutomacaoFormPage({super.key, this.atual});

  final Json? atual;

  @override
  State<AutomacaoFormPage> createState() => _AutomacaoFormPageState();
}

class _AutomacaoFormPageState extends State<AutomacaoFormPage> {
  final _cache = CacheEquipamentos();
  final _nome = TextEditingController();
  final _intervalo = TextEditingController();
  List<Json> _regras = const [];
  List<Json> _dispositivos = const [];
  List<Json> _cenas = const [];

  int? _regraId;
  String _gatilho = 'ALERTA_ABERTO';
  String _alvo = 'COMANDO';
  bool _mesmo = false;
  int? _dispositivoId;
  late ValoresComando _cmd;
  int _versaoCmd = 0;
  int? _cenaId;
  bool _ativo = true;

  Map<String, String> _erros = const {};
  Map<String, String> _errosCmd = const {};
  String? _erro;
  bool _salvando = false;

  @override
  void initState() {
    super.initState();
    _cmd = ValoresComando();
    final a = widget.atual;
    if (a != null) {
      _nome.text = a.texto('nome') ?? '';
      _regraId = a.inteiro('regraId');
      _gatilho = a.texto('gatilho') ?? 'ALERTA_ABERTO';
      _alvo = a.texto('alvo') ?? 'COMANDO';
      _mesmo = a.logico('mesmoDispositivo');
      _dispositivoId = a.inteiro('dispositivoId');
      _cmd = ValoresComando.deJson(a.texto('tipo'), a.mapa('parametros'));
      _cenaId = a.inteiro('cenaId');
      if (a.inteiro('intervaloMinimoS') != null) _intervalo.text = '${a.inteiro('intervaloMinimoS')}';
      _ativo = a.logico('ativo', true);
    }
    _cache.garantir(_dispositivoId, _mudou);
    _cache.garantirModelos(_mudou);
    _carregarListas();
  }

  @override
  void dispose() {
    _nome.dispose();
    _intervalo.dispose();
    super.dispose();
  }

  void _mudou() {
    if (mounted) setState(() {});
  }

  Future<void> _carregarListas() async {
    try {
      final r = await Future.wait([AtuacaoApi.regrasAlerta(), carregarDispositivos(), AtuacaoApi.cenas()]);
      if (!mounted) return;
      setState(() {
        _regras = r[0];
        _dispositivos = r[1];
        _cenas = r[2];
      });
      _cache.garantir(_regra?.inteiro('dispositivoId'), _mudou);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    }
  }

  Json? get _regra {
    for (final r in _regras) {
      if (r.inteiro('id') == _regraId) return r;
    }
    return null;
  }

  /// Dispositivo cujos comandos e periféricos o formulário oferece.
  int? get _dispositivoDoForm => _mesmo ? _regra?.inteiro('dispositivoId') : _dispositivoId;

  List<Json> get _comandos {
    if (!_mesmo) return _cache.comandos(_dispositivoId);
    final d = _regra?.inteiro('dispositivoId');
    return d != null && _cache.equipamento(d) != null ? _cache.comandos(d) : _cache.todosComandos;
  }

  void _mudarRegra(int? id) {
    setState(() => _regraId = id);
    final d = _regra?.inteiro('dispositivoId');
    _cache.garantir(d, _mudou);
    if (d != null && !_mesmo && _dispositivoId == null) _mudarDispositivo(d);
  }

  void _mudarDispositivo(int? id) {
    setState(() {
      _dispositivoId = id;
      _cmd = ValoresComando();
      _versaoCmd++;
    });
    _cache.garantir(id, _mudou);
  }

  Future<void> _salvar() async {
    FocusScope.of(context).unfocus();
    final erros = <String, String>{};
    var errosCmd = <String, String>{};
    if (_nome.text.trim().isEmpty) erros['nome'] = 'Informe o nome.';
    if (_regraId == null) erros['regra'] = 'Escolha a regra de alerta.';
    if (_alvo == 'COMANDO') {
      if (!_mesmo && _dispositivoId == null) erros['dispositivo'] = 'Escolha o dispositivo.';
      errosCmd = _cmd.validar();
    } else if (_cenaId == null) {
      erros['cena'] = 'Escolha a cena.';
    }
    final intervalo = int.tryParse(_intervalo.text.trim());
    if (_intervalo.text.trim().isNotEmpty && (intervalo == null || intervalo > 604800)) erros['intervalo'] = 'De 0 a 604.800 s (7 dias).';
    setState(() {
      _erros = erros;
      _errosCmd = errosCmd;
      _erro = erros.isEmpty && errosCmd.isEmpty ? null : 'Corrija os campos destacados.';
    });
    if (erros.isNotEmpty || errosCmd.isNotEmpty) return;
    final comando = _alvo == 'COMANDO';
    final corpo = <String, Object?>{
      'nome': _nome.text.trim(),
      'regraId': _regraId,
      'gatilho': _gatilho,
      'alvo': _alvo,
      'mesmoDispositivo': comando && _mesmo,
      'dispositivoId': comando && !_mesmo ? _dispositivoId : null,
      'tipo': comando ? _cmd.tipo : null,
      'parametros': comando ? _cmd.paraJson() : null,
      'cenaId': comando ? null : _cenaId,
      'intervaloMinimoS': intervalo,
      'ativo': _ativo,
    };
    setState(() => _salvando = true);
    try {
      final r = await executarComCritico(context, (c) => AtuacaoApi.salvarAutomacao(widget.atual?.inteiro('id'), {...corpo, 'confirmarCritico': c}));
      if (r == null || !mounted) return;
      Navegacao.avisar('Automação salva.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _salvando = false);
    }
  }

  /// Retrato do formulário para a trava "Deseja realmente sair?".
  String _retrato() {
    return jsonEncode([_nome.text, _intervalo.text, _regraId, _gatilho, _alvo, _mesmo, _dispositivoId, _cmd.tipo, _cmd.paraJson(), _cenaId, _ativo]);
  }

  @override
  Widget build(BuildContext context) => TravaEdicao(retrato: _retrato, child: Builder(builder: _construir));

  Widget _construir(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    final regra = _regra;
    final ids = _regras.map((r) => r.inteiro('id')).whereType<int>().toSet();
    final regraValida = ids.contains(_regraId) ? _regraId : null;
    final comandos = _comandos;
    return Scaffold(
      appBar: AppBar(title: Text(widget.atual == null ? 'Nova automação' : 'Editar automação')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
        children: [
          if (_erro != null) ...[CaixaMensagem(texto: _erro!), const SizedBox(height: 16)],
          CampoRotulado(
            rotulo: 'Nome',
            child: TextField(controller: _nome, maxLength: 120, decoration: InputDecoration(hintText: 'Ex.: Desligar o chuveiro acima de 2 kW', counterText: '', errorText: _erros['nome'])),
          ),
          CampoRotulado(
            rotulo: 'Regra de alerta',
            ajuda: regra == null ? (_regras.isEmpty ? 'Nenhuma regra cadastrada (Alertas).' : null) : descreverRegra(regra),
            child: DropdownButtonFormField<int>(
              key: ValueKey('regra-$regraValida-${_regras.length}'),
              initialValue: regraValida,
              isExpanded: true,
              hint: const Text('Escolha a regra', maxLines: 1, overflow: TextOverflow.ellipsis),
              decoration: InputDecoration(errorText: _erros['regra'], errorMaxLines: 3),
              items: [
                for (final r in _regras)
                  if (r.inteiro('id') != null) itemMenu(r.inteiro('id')!, '${r.texto('nome') ?? '#${r.inteiro('id')}'}${r.logico('ativo', true) ? '' : ' (inativa)'}'),
              ],
              onChanged: _mudarRegra,
            ),
          ),
          CampoRotulado(
            rotulo: 'Gatilho',
            child: SegmentedButton<String>(
              showSelectedIcon: false,
              segments: const [
                ButtonSegment(value: 'ALERTA_ABERTO', label: RotuloEncolhe('Alerta abriu')),
                ButtonSegment(value: 'ALERTA_RESOLVIDO', label: RotuloEncolhe('Alerta resolveu')),
              ],
              selected: {_gatilho},
              onSelectionChanged: (s) => setState(() => _gatilho = s.first),
            ),
          ),
          SeletorAlvo(valor: _alvo, aoMudar: (v) => setState(() => _alvo = v)),
          if (_alvo == 'COMANDO') ...[
            InterruptorRotulado(
              titulo: 'No dispositivo que disparou o alerta',
              subtitulo: 'Útil quando a regra vale para todos os dispositivos.',
              valor: _mesmo,
              aoMudar: (b) => setState(() {
                _mesmo = b;
                _cmd = ValoresComando();
                _versaoCmd++;
              }),
            ),
            if (!_mesmo) SeletorDispositivo(dispositivos: _dispositivos, valor: _dispositivoId, aoMudar: _mudarDispositivo, erro: _erros['dispositivo']),
            if (_mesmo && regra != null && regra.inteiro('dispositivoId') == null)
              const Padding(
                padding: EdgeInsets.only(bottom: 12),
                child: CaixaMensagem(texto: 'A regra vale para todos os dispositivos: o comando só é enviado se o dispositivo do alerta suportar.', cor: CmCores.azul500, icone: Icons.info_outline),
              ),
            Card(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                child: comandos.isEmpty
                    ? Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: Text(_mesmo ? 'Carregando comandos…' : _cache.semComandos(_dispositivoId), style: TextStyle(color: cs.onSurfaceVariant)),
                      )
                    : FormComando(
                        key: ValueKey('au-$_mesmo-$_dispositivoDoForm-$_versaoCmd-${comandos.length}'),
                        valores: _cmd,
                        comandos: comandos,
                        perifericos: _cache.perifericos(_dispositivoDoForm),
                        erros: _errosCmd,
                        aoMudar: _mudou,
                      ),
              ),
            ),
            const SizedBox(height: 16),
          ] else
            SeletorCena(cenas: _cenas, valor: _cenaId, aoMudar: (v) => setState(() => _cenaId = v), erro: _erros['cena']),
          CampoRotulado(
            rotulo: 'Intervalo mínimo entre disparos (s)',
            ajuda: 'Evita repetir a ação se o alerta abrir e fechar várias vezes. Em branco, 300 s.',
            child: TextField(
              controller: _intervalo,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: InputDecoration(hintText: '300', errorText: _erros['intervalo']),
            ),
          ),
          InterruptorRotulado(titulo: 'Ativa', valor: _ativo, aoMudar: (b) => setState(() => _ativo = b)),
          const SizedBox(height: 8),
          FilledButton.icon(onPressed: _salvando ? null : _salvar, icon: const Icon(Icons.check), label: Text(_salvando ? 'Salvando…' : 'Salvar')),
        ],
      ),
    );
  }
}
