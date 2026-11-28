import 'dart:async';

import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';

/// Página de resultados: itens + total (Pagina da API ou a lista do ChirpStack).
class PaginaResultado {
  const PaginaResultado(this.itens, this.total);

  final List<Json> itens;
  final int total;

  factory PaginaResultado.deJson(Object? corpo) {
    final j = comoMapa(corpo);
    return PaginaResultado(j.lista('result'), j.inteiro('totalCount') ?? 0);
  }
}

/// Lista paginada com busca opcional, "puxar para atualizar" e "carregar mais".
class ListaPaginada extends StatefulWidget {
  const ListaPaginada({
    super.key,
    required this.buscar,
    required this.item,
    this.dicaBusca,
    this.vazio = 'Nada por aqui.',
    this.iconeVazio = Icons.inbox_outlined,
    this.tamanhoPagina = 30,
    this.cabecalho,
  });

  /// Busca uma página (pagina começa em 0).
  final Future<PaginaResultado> Function(int pagina, int tamanho, String busca) buscar;
  final Widget Function(BuildContext context, Json item) item;
  final String? dicaBusca;
  final String vazio;
  final IconData iconeVazio;
  final int tamanhoPagina;
  final Widget? cabecalho;

  @override
  State<ListaPaginada> createState() => ListaPaginadaState();
}

class ListaPaginadaState extends State<ListaPaginada> {
  final _busca = TextEditingController();
  final List<Json> _itens = [];
  int _total = 0;
  int _pagina = 0;
  bool _carregando = true;
  bool _carregandoMais = false;
  Object? _erro;
  Timer? _atraso;
  int _geracao = 0;

  @override
  void initState() {
    super.initState();
    recarregar();
  }

  @override
  void dispose() {
    _atraso?.cancel();
    _busca.dispose();
    super.dispose();
  }

  Future<void> recarregar() async {
    final geracao = ++_geracao;
    setState(() {
      _carregando = _itens.isEmpty;
      _erro = null;
    });
    try {
      final p = await widget.buscar(0, widget.tamanhoPagina, _busca.text.trim());
      if (!mounted || geracao != _geracao) return;
      setState(() {
        _itens
          ..clear()
          ..addAll(p.itens);
        _total = p.total;
        _pagina = 0;
      });
    } catch (e) {
      if (mounted && geracao == _geracao) setState(() => _erro = e);
    } finally {
      if (mounted && geracao == _geracao) setState(() => _carregando = false);
    }
  }

  Future<void> _mais() async {
    if (_carregandoMais) return;
    setState(() => _carregandoMais = true);
    try {
      final p = await widget.buscar(_pagina + 1, widget.tamanhoPagina, _busca.text.trim());
      if (!mounted) return;
      setState(() {
        _itens.addAll(p.itens);
        _total = p.total;
        _pagina++;
      });
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(mensagemDeErro(e))));
    } finally {
      if (mounted) setState(() => _carregandoMais = false);
    }
  }

  void _aoDigitar(String _) {
    _atraso?.cancel();
    _atraso = Timer(const Duration(milliseconds: 400), recarregar);
  }

  @override
  Widget build(BuildContext context) {
    final temBusca = widget.dicaBusca != null;
    final cs = Theme.of(context).colorScheme;
    Widget lista;
    if (_carregando) {
      lista = const Carregando();
    } else if (_erro != null && _itens.isEmpty) {
      lista = EstadoMensagem(icone: Icons.cloud_off_outlined, titulo: 'Não foi possível carregar', texto: mensagemDeErro(_erro!), acao: recarregar, cabecalho: widget.cabecalho);
    } else if (_itens.isEmpty) {
      lista = EstadoMensagem(icone: widget.iconeVazio, titulo: widget.vazio, cabecalho: widget.cabecalho);
    } else {
      final haMais = _itens.length < _total;
      lista = ListView.separated(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
        itemCount: _itens.length + 1 + (widget.cabecalho == null ? 0 : 1),
        separatorBuilder: (_, _) => const SizedBox(height: 10),
        itemBuilder: (context, i) {
          if (widget.cabecalho != null) {
            if (i == 0) return widget.cabecalho!;
            i--;
          }
          if (i < _itens.length) return widget.item(context, _itens[i]);
          return Padding(
            padding: const EdgeInsets.only(top: 6),
            child: Column(children: [
              Text('${_itens.length} de $_total', style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12.5)),
              if (haMais) ...[
                const SizedBox(height: 8),
                OutlinedButton(
                  onPressed: _carregandoMais ? null : _mais,
                  child: Text(_carregandoMais ? 'Carregando…' : 'Carregar mais'),
                ),
              ],
            ]),
          );
        },
      );
    }
    return Column(
      children: [
        if (temBusca)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: TextField(
              controller: _busca,
              onChanged: _aoDigitar,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                hintText: widget.dicaBusca,
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _busca.text.isEmpty
                    ? null
                    : IconButton(
                        tooltip: 'Limpar busca',
                        icon: const Icon(Icons.close),
                        onPressed: () {
                          _busca.clear();
                          recarregar();
                        },
                      ),
              ),
            ),
          ),
        Expanded(child: RefreshIndicator(onRefresh: recarregar, child: lista)),
      ],
    );
  }
}
