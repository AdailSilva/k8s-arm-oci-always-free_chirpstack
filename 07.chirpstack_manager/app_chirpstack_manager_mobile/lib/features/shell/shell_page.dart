import 'package:flutter/material.dart';

import 'package:chirpstack_manager/core/conta/avisos.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/core/ui/marca.dart';
import 'package:chirpstack_manager/features/alertas/alertas_page.dart';
import 'package:chirpstack_manager/features/conta/conta_page.dart';
import 'package:chirpstack_manager/features/dispositivos/dispositivos_page.dart';
import 'package:chirpstack_manager/features/gateways/gateways_page.dart';
import 'package:chirpstack_manager/features/painel/painel_page.dart';

/// Estrutura do app logado: barra superior com o símbolo e as cinco abas.
class ShellPage extends StatefulWidget {
  const ShellPage({super.key, this.abaInicial = 0});

  final int abaInicial;

  @override
  State<ShellPage> createState() => _ShellPageState();
}

class _ShellPageState extends State<ShellPage> {
  late int _aba = widget.abaInicial;

  static const _titulos = ['Painel', 'Dispositivos', 'Gateways', 'Alertas', 'Conta'];

  @override
  Widget build(BuildContext context) {
    final paginas = <Widget>[
      PainelPage(aoAbrirAba: (i) => setState(() => _aba = i)),
      const DispositivosPage(),
      const GatewaysPage(),
      const AlertasPage(),
      const ContaPage(),
    ];
    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        title: Row(children: [
          const SimboloManager(tamanho: 30),
          const SizedBox(width: 12),
          Flexible(child: Text(_titulos[_aba], maxLines: 1, overflow: TextOverflow.ellipsis)),
        ]),
      ),
      body: IndexedStack(index: _aba, children: paginas),
      bottomNavigationBar: ListenableBuilder(
        listenable: Avisos.instancia,
        builder: (context, _) => NavigationBar(
        selectedIndex: _aba,
        onDestinationSelected: (i) {
          setState(() => _aba = i);
          if (i == 3) Avisos.instancia.atualizar();
        },
        destinations: [
          const NavigationDestination(icon: Icon(Icons.dashboard_outlined), selectedIcon: Icon(Icons.dashboard), label: 'Painel'),
          const NavigationDestination(icon: Icon(Icons.sensors_outlined), selectedIcon: Icon(Icons.sensors), label: 'Dispositivos'),
          const NavigationDestination(icon: Icon(Icons.router_outlined), selectedIcon: Icon(Icons.router), label: 'Gateways'),
          NavigationDestination(
            // Alertas abertos (o sino do painel web): vermelho se houver crítico.
            icon: Badge(
              isLabelVisible: Avisos.instancia.total > 0,
              label: Text(Avisos.instancia.rotulo),
              backgroundColor: Avisos.instancia.criticos > 0 ? CmCores.offline : CmCores.aviso,
              child: const Icon(Icons.notifications_outlined),
            ),
            selectedIcon: Badge(
              isLabelVisible: Avisos.instancia.total > 0,
              label: Text(Avisos.instancia.rotulo),
              backgroundColor: Avisos.instancia.criticos > 0 ? CmCores.offline : CmCores.aviso,
              child: const Icon(Icons.notifications),
            ),
            label: 'Alertas',
          ),
          const NavigationDestination(icon: Icon(Icons.person_outline), selectedIcon: Icon(Icons.person), label: 'Conta'),
        ],
      ),
      ),
    );
  }
}
