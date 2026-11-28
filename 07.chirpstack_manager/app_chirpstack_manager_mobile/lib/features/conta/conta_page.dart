import 'package:flutter/material.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/auth/bloqueio_tela.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/config/ambiente.dart';
import 'package:chirpstack_manager/core/http/json.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/tema/tema_controller.dart';
import 'package:chirpstack_manager/core/ui/componentes.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/core/ui/marca.dart';
import 'package:chirpstack_manager/features/conta/meu_perfil_page.dart' show bytesDaFoto;

/// Foto de perfil do usuário logado (carregada de /auth/me; não fica no cofre do aparelho).
final ValueNotifier<String?> fotoPerfil = ValueNotifier<String?>(null);

/// Conta: dados do usuário, tema, meu perfil, bloquear tela, suporte, termos e saída.
class ContaPage extends StatefulWidget {
  const ContaPage({super.key});

  @override
  State<ContaPage> createState() => _ContaPageState();
}

class _ContaPageState extends State<ContaPage> {
  @override
  void initState() {
    super.initState();
    _carregarFoto();
  }

  Future<void> _carregarFoto() async {
    try {
      final u = await AuthService.instancia.me();
      fotoPerfil.value = u.mapa('cadastro').texto('fotoPerfil');
    } catch (_) {
      // sem foto: ficam as iniciais
    }
  }

  Future<void> _meuPerfil(BuildContext context) async {
    final salvo = await Navigator.of(context).pushNamed(Rotas.meuPerfil);
    if (salvo == true) await _carregarFoto();
  }

  Future<void> _bloquear() async {
    await BloqueioTela.instancia.bloquear();
    Navegacao.irParaBloqueio();
  }

  Future<void> _abrir(String caminho) =>
      launchUrl(Uri.parse('${Ambiente.urlWeb}$caminho'), mode: LaunchMode.externalApplication);

  Future<void> _confirmarSaida(BuildContext context) async {
    final sair = await showDialog<bool>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Sair do Manager?'),
        content: const Text('A sessão deste aparelho será encerrada.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('Sair')),
        ],
      ),
    );
    if (sair == true) await AuthService.instancia.sair();
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return ListenableBuilder(
      listenable: Listenable.merge([Sessao.instancia, TemaController.instancia]),
      builder: (context, _) {
        final u = Sessao.instancia.usuario;
        return ListView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(children: [
                  ValueListenableBuilder<String?>(
                    valueListenable: fotoPerfil,
                    builder: (_, foto, _) {
                      final bytes = bytesDaFoto(foto);
                      return CircleAvatar(
                        radius: 26,
                        backgroundColor: CmCores.azul600,
                        backgroundImage: bytes == null ? null : MemoryImage(bytes),
                        child: bytes == null
                            ? Text(u?.iniciais ?? '?', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w700))
                            : null,
                      );
                    },
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(u?.nome ?? '—', maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
                      const SizedBox(height: 2),
                      Text(u?.email ?? '', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: cs.onSurfaceVariant)),
                      const SizedBox(height: 8),
                      if (u != null) ChipEstado(texto: u.perfilRotulo, cor: CmCores.azul500),
                    ]),
                  ),
                ]),
              ),
            ),
            const TituloSecao('Aparência'),
            SegmentedButton<ThemeMode>(
              showSelectedIcon: false,
              segments: const [
                ButtonSegment(value: ThemeMode.system, label: Text('Sistema'), icon: Icon(Icons.brightness_auto_outlined)),
                ButtonSegment(value: ThemeMode.light, label: Text('Claro'), icon: Icon(Icons.light_mode_outlined)),
                ButtonSegment(value: ThemeMode.dark, label: Text('Escuro'), icon: Icon(Icons.dark_mode_outlined)),
              ],
              selected: {TemaController.instancia.modo},
              onSelectionChanged: (s) => TemaController.instancia.definir(s.first),
            ),
            const TituloSecao('Conta'),
            Card(
              clipBehavior: Clip.antiAlias,
              child: Column(children: [
                ListTile(
                  leading: const Icon(Icons.badge_outlined),
                  title: const Text('Meu perfil'),
                  subtitle: const Text('Nome, cargo, telefone e endereço'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => _meuPerfil(context),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.lock_outline),
                  title: const Text('Bloquear tela'),
                  subtitle: const Text('Esconde o app sem sair; volta com a senha'),
                  onTap: _bloquear,
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.password),
                  title: const Text('Trocar a senha'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.of(context).pushNamed(Rotas.trocarSenha),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.open_in_browser),
                  title: const Text('Abrir o painel web'),
                  subtitle: const Text('Cadastros e configurações completos'),
                  trailing: const Icon(Icons.open_in_new, size: 18),
                  onTap: () => _abrir('/'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.support_agent),
                  title: const Text('Suporte'),
                  subtitle: const Text('Fale com a equipe'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.of(context).pushNamed(Rotas.suporte),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.description_outlined),
                  title: const Text('Termos de Uso'),
                  trailing: const Icon(Icons.open_in_new, size: 18),
                  onTap: () => _abrir('/termos-de-uso'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: Icon(Icons.logout, color: cs.error),
                  title: Text('Sair', style: TextStyle(color: cs.error, fontWeight: FontWeight.w600)),
                  onTap: () => _confirmarSaida(context),
                ),
              ]),
            ),
            const SizedBox(height: 28),
            Center(child: MarcaManager(tamanho: 30, apoio: false, negativo: Theme.of(context).brightness == Brightness.dark)),
            const SizedBox(height: 8),
            FutureBuilder<PackageInfo>(
              future: PackageInfo.fromPlatform(),
              builder: (context, s) {
                final versao = s.data == null ? '' : 'Versão ${s.data!.version}';
                final partes = [versao, Ambiente.rotulo].where((p) => p.isNotEmpty).join(' · ');
                return Text(
                  partes.isEmpty ? '© ${DateTime.now().year} ChirpStack · Manager' : '$partes\n© ${DateTime.now().year} ChirpStack · Manager',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: cs.onSurfaceVariant, fontSize: 12, height: 1.5),
                );
              },
            ),
          ],
        );
      },
    );
  }
}
