import 'dart:convert';

import 'package:flutter/material.dart';

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
import 'package:chirpstack_manager/features/atuacao/parametros.dart';
import 'package:chirpstack_manager/features/atuacao/selecao.dart';

/// Cenas: vários comandos (em um ou vários dispositivos) em um toque.
class CenasPage extends StatefulWidget {
  const CenasPage({super.key});

  @override
  State<CenasPage> createState() => _CenasPageState();
}

class _CenasPageState extends State<CenasPage> {
  List<Json> _itens = const [];
  Object? _erro;
  bool _carregando = true;
  int? _executando;

  bool get _podeAlterar => Sessao.instancia.usuario?.podeAlterar ?? false;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    try {
      final l = await AtuacaoApi.cenas();
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

  Future<void> _abrir([Json? c]) async {
    final salvo = await Navigator.of(context).push<bool>(MaterialPageRoute(builder: (_) => CenaFormPage(atual: c)));
    if (salvo == true) await _carregar();
  }

  Future<void> _executar(Json c) async {
    final id = c.inteiro('id');
    if (id == null) return;
    final ok = await confirmar(context, titulo: 'Executar cena', texto: 'Executar "${c.texto('nome') ?? ''}" (${c.lista('itens').length} comando(s))?', rotuloConfirmar: 'Executar', icone: Icons.play_arrow);
    if (!ok || !mounted) return;
    setState(() => _executando = id);
    try {
      final r = await executarComCritico(context, (cc) => AtuacaoApi.executarCena(id, confirmarCritico: cc));
      if (r != null) {
        final n = r['comandos'] is List ? (r['comandos'] as List).length : 0;
        Navegacao.avisar('$n comando(s) na fila.');
      }
    } catch (e) {
      Navegacao.avisar(mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _executando = null);
    }
  }

  Future<void> _excluir(Json c) async {
    final id = c.inteiro('id');
    if (id == null) return;
    final ok = await confirmar(
      context,
      titulo: 'Excluir cena',
      texto: 'Excluir "${c.texto('nome') ?? ''}"? Agendamentos e automações que usam esta cena deixam de funcionar.',
      rotuloConfirmar: 'Excluir',
      perigo: true,
      icone: Icons.delete_outline,
    );
    if (!ok) return;
    try {
      await AtuacaoApi.excluirCena(id);
      Navegacao.avisar('Cena excluída.');
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
      corpo = const EstadoMensagem(icone: Icons.collections_outlined, titulo: 'Nenhuma cena', texto: 'Ex.: "Sair de casa" desliga a iluminação e liga o alarme.');
    } else {
      corpo = ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 88),
        children: [
          for (final c in _itens)
            Card(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(14, 12, 8, 8),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(c.texto('nome') ?? '—', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                        if (c.texto('descricao') != null)
                          Text(c.texto('descricao')!, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
                      ]),
                    ),
                    if (c.logico('temCritico')) const ChipEstado(texto: 'Crítica', cor: CmCores.offline),
                  ]),
                  const SizedBox(height: 8),
                  for (final (i, it) in c.lista('itens').indexed)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 4, right: 6),
                      child: Text(
                        '${i + 1}. ${resumoDe(it)} · ${it.texto('dispositivoNome') ?? '#${it.inteiro('dispositivoId')}'}',
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(fontSize: 13),
                      ),
                    ),
                  Text('Atualizada ${Formatos.relativo(c.data('atualizadoEm'))}', style: TextStyle(fontSize: 12, color: cs.onSurfaceVariant)),
                  if (_podeAlterar)
                    Wrap(spacing: 4, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
                      FilledButton.icon(
                        style: FilledButton.styleFrom(minimumSize: const Size(0, 44)),
                        onPressed: _executando != null ? null : () => _executar(c),
                        icon: _executando == c.inteiro('id') ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.play_arrow),
                        label: const Text('Executar'),
                      ),
                      IconButton(tooltip: 'Editar', icon: const Icon(Icons.edit_outlined), onPressed: () => _abrir(c)),
                      IconButton(tooltip: 'Excluir', icon: const Icon(Icons.delete_outline), onPressed: () => _excluir(c)),
                    ]),
                ]),
              ),
            ),
        ],
      );
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Cenas')),
      floatingActionButton: _podeAlterar ? FloatingActionButton.extended(onPressed: () => _abrir(), icon: const Icon(Icons.add), label: const Text('Nova cena')) : null,
      body: RefreshIndicator(onRefresh: _carregar, child: corpo),
    );
  }
}

class _ItemCena {
  _ItemCena({this.dispositivoId, ValoresComando? valores}) : valores = valores ?? ValoresComando();

  int? dispositivoId;
  ValoresComando valores;
  int versao = 0;
  Map<String, String> erros = const {};
  String? erroDispositivo;
  final chave = UniqueKey();
}

/// Criar ou editar uma cena.
class CenaFormPage extends StatefulWidget {
  const CenaFormPage({super.key, this.atual});

  final Json? atual;

  @override
  State<CenaFormPage> createState() => _CenaFormPageState();
}

class _CenaFormPageState extends State<CenaFormPage> {
  final _cache = CacheEquipamentos();
  final _nome = TextEditingController();
  final _descricao = TextEditingController();
  List<Json> _dispositivos = const [];
  final List<_ItemCena> _itens = [];
  String? _erro;
  String? _erroNome;
  bool _salvando = false;

  @override
  void initState() {
    super.initState();
    final c = widget.atual;
    if (c != null) {
      _nome.text = c.texto('nome') ?? '';
      _descricao.text = c.texto('descricao') ?? '';
      final itens = c.lista('itens')..sort((a, b) => (a.inteiro('ordem') ?? 0).compareTo(b.inteiro('ordem') ?? 0));
      for (final i in itens) {
        _itens.add(_ItemCena(dispositivoId: i.inteiro('dispositivoId'), valores: ValoresComando.deJson(i.texto('tipo'), i.mapa('parametros'))));
      }
    } else {
      _itens.add(_ItemCena());
    }
    for (final i in _itens) {
      _cache.garantir(i.dispositivoId, _mudou);
    }
    _carregarDispositivos();
  }

  @override
  void dispose() {
    _nome.dispose();
    _descricao.dispose();
    super.dispose();
  }

  void _mudou() {
    if (mounted) setState(() {});
  }

  Future<void> _carregarDispositivos() async {
    try {
      final d = await carregarDispositivos();
      if (mounted) setState(() => _dispositivos = d);
    } catch (e) {
      if (mounted) setState(() => _erro = mensagemDeErro(e));
    }
  }

  void _mudarDispositivo(_ItemCena it, int? id) {
    setState(() {
      it.dispositivoId = id;
      it.valores = ValoresComando();
      it.versao++;
    });
    _cache.garantir(id, _mudou);
  }

  void _mover(int i, int d) {
    final j = i + d;
    if (j < 0 || j >= _itens.length) return;
    setState(() {
      final x = _itens[i];
      _itens[i] = _itens[j];
      _itens[j] = x;
    });
  }

  Future<void> _salvar() async {
    FocusScope.of(context).unfocus();
    var ok = true;
    _erroNome = _nome.text.trim().isEmpty ? 'Informe o nome.' : null;
    if (_erroNome != null) ok = false;
    for (final it in _itens) {
      it.erroDispositivo = it.dispositivoId == null ? 'Escolha o dispositivo.' : null;
      it.erros = it.dispositivoId == null ? const {} : it.valores.validar();
      if (it.erroDispositivo != null || it.erros.isNotEmpty) ok = false;
    }
    if (_itens.isEmpty) ok = false;
    setState(() => _erro = ok ? null : (_itens.isEmpty ? 'Adicione ao menos um comando.' : 'Corrija os campos destacados.'));
    if (!ok) return;
    final corpo = {
      'nome': _nome.text.trim(),
      'descricao': _descricao.text.trim().isEmpty ? null : _descricao.text.trim(),
      'itens': [for (final it in _itens) {'dispositivoId': it.dispositivoId, 'tipo': it.valores.tipo, 'parametros': it.valores.paraJson()}],
    };
    setState(() => _salvando = true);
    try {
      await AtuacaoApi.salvarCena(widget.atual?.inteiro('id'), corpo);
      if (!mounted) return;
      Navegacao.avisar('Cena salva.');
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
      _nome.text,
      _descricao.text,
      [for (final it in _itens) [it.dispositivoId, it.valores.tipo, it.valores.paraJson()]],
    ]);
  }

  @override
  Widget build(BuildContext context) => TravaEdicao(retrato: _retrato, child: Builder(builder: _construir));

  Widget _construir(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: Text(widget.atual == null ? 'Nova cena' : 'Editar cena')),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
        children: [
          if (_erro != null) ...[CaixaMensagem(texto: _erro!), const SizedBox(height: 16)],
          CampoRotulado(
            rotulo: 'Nome',
            child: TextField(controller: _nome, maxLength: 120, decoration: InputDecoration(hintText: 'Ex.: Sair de casa', counterText: '', errorText: _erroNome)),
          ),
          CampoRotulado(
            rotulo: 'Descrição',
            child: TextField(controller: _descricao, maxLength: 300, minLines: 1, maxLines: 3, decoration: const InputDecoration(counterText: '')),
          ),
          const TituloSecao('Comandos'),
          for (final (i, it) in _itens.indexed)
            Card(
              key: it.chave,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 4, 4),
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  Row(children: [
                    Expanded(child: Text('${i + 1}º comando', style: const TextStyle(fontWeight: FontWeight.w700))),
                    IconButton(tooltip: 'Subir', icon: const Icon(Icons.arrow_upward), onPressed: i == 0 ? null : () => _mover(i, -1)),
                    IconButton(tooltip: 'Descer', icon: const Icon(Icons.arrow_downward), onPressed: i == _itens.length - 1 ? null : () => _mover(i, 1)),
                    IconButton(tooltip: 'Remover', icon: const Icon(Icons.delete_outline), onPressed: () => setState(() => _itens.removeAt(i))),
                  ]),
                  Padding(
                    padding: const EdgeInsets.only(right: 12),
                    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                      SeletorDispositivo(dispositivos: _dispositivos, valor: it.dispositivoId, aoMudar: (id) => _mudarDispositivo(it, id), erro: it.erroDispositivo),
                      if (_cache.comandos(it.dispositivoId).isEmpty)
                        Padding(padding: const EdgeInsets.only(bottom: 12), child: Text(_cache.semComandos(it.dispositivoId), style: TextStyle(color: cs.onSurfaceVariant)))
                      else
                        FormComando(
                          key: ValueKey('cena-$i-${it.dispositivoId}-${it.versao}-${_cache.comandos(it.dispositivoId).length}'),
                          valores: it.valores,
                          comandos: _cache.comandos(it.dispositivoId),
                          perifericos: _cache.perifericos(it.dispositivoId),
                          erros: it.erros,
                          aoMudar: _mudou,
                        ),
                    ]),
                  ),
                ]),
              ),
            ),
          OutlinedButton.icon(
            onPressed: () {
              final ultimo = _itens.isEmpty ? null : _itens.last.dispositivoId;
              setState(() => _itens.add(_ItemCena(dispositivoId: ultimo)));
              _cache.garantir(ultimo, _mudou);
            },
            icon: const Icon(Icons.add),
            label: const Text('Adicionar comando'),
          ),
          const SizedBox(height: 6),
          Text('Os comandos entram na fila de cada dispositivo na ordem acima.', style: TextStyle(fontSize: 12.5, color: cs.onSurfaceVariant)),
          const SizedBox(height: 20),
          FilledButton.icon(onPressed: _salvando ? null : _salvar, icon: const Icon(Icons.check), label: Text(_salvando ? 'Salvando…' : 'Salvar')),
        ],
      ),
    );
  }
}
