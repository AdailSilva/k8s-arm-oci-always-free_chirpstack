import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';

import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/ui/tema_app.dart';
import 'package:chirpstack_manager/features/auth/login_page.dart';
import 'package:chirpstack_manager/features/auth/recuperar_senha_page.dart';
import 'package:chirpstack_manager/features/auth/tela_bloqueada_page.dart';
import 'package:chirpstack_manager/features/auth/trocar_senha_page.dart';
import 'package:chirpstack_manager/features/atuacao/agendamentos_page.dart';
import 'package:chirpstack_manager/features/atuacao/automacoes_page.dart';
import 'package:chirpstack_manager/features/atuacao/cenas_page.dart';
import 'package:chirpstack_manager/features/atuacao/comandos_page.dart';
import 'package:chirpstack_manager/features/atuacao/controle_page.dart';
import 'package:chirpstack_manager/features/conta/meu_perfil_page.dart';
import 'package:chirpstack_manager/features/conta/suporte_page.dart';
import 'package:chirpstack_manager/features/dispositivos/dispositivo_detalhe_page.dart';
import 'package:chirpstack_manager/features/shell/shell_page.dart';

import 'dados_exemplo.dart';

/// Todas as telas em celulares pequenos e grandes, nos dois temas e com o texto ampliado ao
/// máximo permitido pelo app (115%): qualquer campo cortado, colado ou fora da tela vira um erro
/// de overflow e o teste falha.
void main() {
  const tamanhos = [Size(320, 568), Size(360, 740), Size(412, 915)];

  final telas = <String, Widget Function()>{
    'login': () => const LoginPage(),
    'login com aviso': () => const LoginPage(argumentos: ArgumentosLogin(aviso: 'Sua sessão foi encerrada por inatividade. Entre novamente para continuar.')),
    'recuperar senha': () => const RecuperarSenhaPage(emailInicial: 'maria.aparecida.albuquerque@empresa-exemplo.com.br'),
    'trocar senha': () => const TrocarSenhaPage(),
    'tela bloqueada': () => const TelaBloqueadaPage(),
    'meu perfil': () => const MeuPerfilPage(),
    'suporte': () => const SuportePage(),
    'painel': () => const ShellPage(abaInicial: 0),
    'dispositivos': () => const ShellPage(abaInicial: 1),
    'gateways': () => const ShellPage(abaInicial: 2),
    'alertas': () => const ShellPage(abaInicial: 3),
    'conta': () => const ShellPage(abaInicial: 4),
    'detalhe do dispositivo': () => const DispositivoDetalhePage(id: 1, nome: 'Sensor de temperatura da câmara fria número 12 do galpão norte'),
    'controle do dispositivo': () => const DispositivoDetalhePage(id: 1, nome: 'Sensor de temperatura da câmara fria número 12 do galpão norte', abaInicial: 1),
    'energia do dispositivo': () => const DispositivoDetalhePage(id: 1, nome: 'Sensor de temperatura da câmara fria número 12 do galpão norte', abaInicial: 2),
    'comandos do dispositivo': () => const DispositivoDetalhePage(id: 1, nome: 'Sensor de temperatura da câmara fria número 12 do galpão norte', abaInicial: 3),
    'agendamentos do dispositivo': () => const DispositivoDetalhePage(id: 1, nome: 'Sensor de temperatura da câmara fria número 12 do galpão norte', abaInicial: 4),
    'equipamento': () => const EquipamentoPage(dispositivoId: 1, dispositivoNome: 'Sensor de temperatura da câmara fria número 12 do galpão norte'),
    'fila de comandos': () => const FilaComandosPage(),
    'cenas': () => const CenasPage(),
    'nova cena': () => const CenaFormPage(),
    'agendamentos': () => const AgendamentosPage(),
    'novo agendamento': () => const AgendamentoFormPage(),
    'automações': () => const AutomacoesPage(),
    'nova automação': () => const AutomacaoFormPage(),
  };

  setUpAll(() {
    PackageInfo.setMockInitialValues(appName: 'ChirpStack Manager', packageName: 'br.com.chirpstack.manager', version: '1.0', buildNumber: '1', buildSignature: '');
    instalarApiSimulada();
  });

  setUp(() async => iniciarSessaoExemplo());

  for (final brilho in Brightness.values) {
    for (final tamanho in tamanhos) {
      for (final e in telas.entries) {
        testWidgets('${e.key} · ${tamanho.width.toInt()}x${tamanho.height.toInt()} · ${brilho.name}', (tester) async {
          tester.view.physicalSize = tamanho;
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.reset);

          await tester.pumpWidget(MaterialApp(
            theme: construirTema(brilho),
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(textScaler: const TextScaler.linear(1.15)),
              child: child!,
            ),
            home: e.value(),
          ));
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);

          // No login, também com as mensagens de validação dos campos na tela.
          if (e.key.startsWith('login')) {
            await tester.ensureVisible(find.text('Entrar'));
            await tester.pumpAndSettle();
            await tester.tap(find.text('Entrar'));
            await tester.pumpAndSettle();
            expect(find.text('Informe o e-mail.'), findsOneWidget);
            expect(tester.takeException(), isNull);
          }
        });
      }
    }
  }
}
