import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/config/ambiente.dart';
import 'package:chirpstack_manager/core/http/api_client.dart';
import 'package:chirpstack_manager/core/navegacao.dart';
import 'package:chirpstack_manager/core/storage/armazenamento.dart';
import 'package:chirpstack_manager/core/ui/cores.dart';
import 'package:chirpstack_manager/core/ui/marca.dart';

/// Login do ChirpStack Manager — o mesmo conteúdo do login web (brand/BRAND.md): marca sobre o
/// azul-noite e o cartão "Entrar no Manager".
class LoginPage extends StatefulWidget {
  const LoginPage({super.key, this.argumentos = const ArgumentosLogin()});

  final ArgumentosLogin argumentos;

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _email = TextEditingController();
  final _senha = TextEditingController();
  final _form = GlobalKey<FormState>();
  bool _lembrar = false;
  bool _mostrarSenha = false;
  bool _carregando = false;
  String? _erro;
  /// O login foi recusado porque o e-mail ainda não foi confirmado: oferece reenviar o link.
  bool _emailNaoConfirmado = false;
  bool _reenviando = false;

  @override
  void initState() {
    super.initState();
    Armazenamento.ler(ChavesArmazenamento.emailLembrado).then((e) {
      if (!mounted || e == null) return;
      setState(() {
        _email.text = e;
        _lembrar = true;
      });
    });
  }

  @override
  void dispose() {
    _email.dispose();
    _senha.dispose();
    super.dispose();
  }

  Future<void> _entrar() async {
    FocusScope.of(context).unfocus();
    if (!(_form.currentState?.validate() ?? false)) return;
    setState(() {
      _carregando = true;
      _erro = null;
      _emailNaoConfirmado = false;
    });
    final email = _email.text.trim();
    try {
      final resp = await AuthService.instancia.login(email, _senha.text, lembrar: _lembrar);
      await Armazenamento.gravar(ChavesArmazenamento.emailLembrado, _lembrar ? email : null);
      if (!mounted) return;
      Navigator.of(context).pushNamedAndRemoveUntil(resp.trocaSenhaObrigatoria ? Rotas.trocarSenha : Rotas.inicio, (_) => false);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _carregando = false;
        _senha.clear();
        _erro = mensagemDeErro(e, 'Não foi possível entrar. Tente novamente.');
        _emailNaoConfirmado = e is ApiException && e.codigo == 'EMAIL_NAO_CONFIRMADO';
      });
    }
  }

  /// Reenvia o link de confirmação (ou o convite) para o e-mail digitado.
  Future<void> _reenviarConfirmacao() async {
    final email = _email.text.trim();
    if (email.isEmpty || _reenviando) return;
    setState(() => _reenviando = true);
    try {
      await AuthService.instancia.reenviarConfirmacao(email);
      if (!mounted) return;
      setState(() {
        _emailNaoConfirmado = false;
        _erro = null;
      });
      Navegacao.avisar('Se o e-mail estiver cadastrado, enviamos um novo link de confirmação.');
    } catch (e) {
      if (mounted) Navegacao.avisar(mensagemDeErro(e));
    } finally {
      if (mounted) setState(() => _reenviando = false);
    }
  }

  Future<void> _abrirTermos() async {
    await launchUrl(Uri.parse('${Ambiente.urlWeb}/termos-de-uso'), mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    final aviso = widget.argumentos.aviso;
    return Scaffold(
      backgroundColor: CmCores.noite950,
      body: Container(
        decoration: const BoxDecoration(
          gradient: RadialGradient(
            center: Alignment(-0.6, -0.8),
            radius: 1.3,
            colors: [CmCores.noite800, CmCores.noite, CmCores.noite950],
            stops: [0, 0.45, 1],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(20, 28, 20, 28),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 440),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Align(alignment: Alignment.centerLeft, child: MarcaManager(negativo: true, tamanho: 40)),
                    const SizedBox(height: 22),
                    _CartaoLogin(
                      form: _form,
                      email: _email,
                      senha: _senha,
                      lembrar: _lembrar,
                      mostrarSenha: _mostrarSenha,
                      carregando: _carregando,
                      aviso: aviso,
                      erro: _erro,
                      aoReenviar: _emailNaoConfirmado && !_reenviando ? _reenviarConfirmacao : null,
                      aoLembrar: (v) => setState(() => _lembrar = v),
                      aoMostrarSenha: () => setState(() => _mostrarSenha = !_mostrarSenha),
                      aoEntrar: _entrar,
                      aoEsqueci: () => Navigator.of(context).pushNamed(Rotas.recuperarSenha, arguments: _email.text.trim()),
                      aoTermos: _abrirTermos,
                    ),
                    const SizedBox(height: 18),
                    Text(
                      '© ${DateTime.now().year} ChirpStack · Manager',
                      textAlign: TextAlign.center,
                      style: const TextStyle(color: Color(0xFF8A9BBD), fontSize: 12),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CartaoLogin extends StatelessWidget {
  const _CartaoLogin({
    required this.form,
    required this.email,
    required this.senha,
    required this.lembrar,
    required this.mostrarSenha,
    required this.carregando,
    required this.aviso,
    required this.erro,
    this.aoReenviar,
    required this.aoLembrar,
    required this.aoMostrarSenha,
    required this.aoEntrar,
    required this.aoEsqueci,
    required this.aoTermos,
  });

  final GlobalKey<FormState> form;
  final TextEditingController email;
  final TextEditingController senha;
  final bool lembrar;
  final bool mostrarSenha;
  final bool carregando;
  final String? aviso;
  final String? erro;
  /// E-mail não confirmado: botão para reenviar o link (null = não mostra).
  final VoidCallback? aoReenviar;
  final ValueChanged<bool> aoLembrar;
  final VoidCallback aoMostrarSenha;
  final VoidCallback aoEntrar;
  final VoidCallback aoEsqueci;
  final VoidCallback aoTermos;

  static final RegExp _emailValido = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');

  @override
  Widget build(BuildContext context) {
    final escuro = Theme.of(context).brightness == Brightness.dark;
    final fundo = escuro ? CmCores.noite800 : Colors.white;
    final texto = escuro ? const Color(0xFFF1F5FB) : const Color(0xFF0B1220);
    final apagado = escuro ? const Color(0xFFA9B8D3) : const Color(0xFF5B6B86);
    final destaque = escuro ? CmCores.azul300 : CmCores.azul600;
    return Container(
      decoration: BoxDecoration(
        color: fundo,
        borderRadius: BorderRadius.circular(24),
        boxShadow: const [BoxShadow(color: Color(0x66000000), blurRadius: 40, offset: Offset(0, 24))],
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Faixa no topo do cartão: azul do sistema terminando no teal da marca
          Container(
            height: 3,
            margin: const EdgeInsets.symmetric(horizontal: 24),
            decoration: const BoxDecoration(
              borderRadius: BorderRadius.vertical(bottom: Radius.circular(3)),
              gradient: LinearGradient(colors: [CmCores.azul700, CmCores.azul500, CmCores.teal]),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(22, 26, 22, 22),
            child: Form(
              key: form,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(children: [
                    Icon(Icons.lock_outline, size: 13, color: destaque),
                    const SizedBox(width: 6),
                    Flexible(
                      child: Text('ACESSO SEGURO', style: TextStyle(color: destaque, fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 1.6)),
                    ),
                  ]),
                  const SizedBox(height: 10),
                  Text('Entrar no Manager', style: TextStyle(color: texto, fontSize: 26, fontWeight: FontWeight.w800, letterSpacing: -0.5)),
                  const SizedBox(height: 6),
                  Text(
                    'Administre a rede LoRaWAN e acompanhe gateways e dispositivos em tempo real.',
                    style: TextStyle(color: apagado, fontSize: 14, height: 1.5),
                  ),
                  const SizedBox(height: 20),
                  if (aviso != null) _Alerta(texto: aviso!, aviso: true),
                  if (erro != null) _Alerta(texto: erro!),
                  if (aoReenviar != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: OutlinedButton.icon(
                        onPressed: aoReenviar,
                        icon: const Icon(Icons.forward_to_inbox_outlined),
                        label: const Text('Reenviar e-mail de confirmação'),
                      ),
                    ),
                  _Rotulo('E-mail', cor: texto),
                  TextFormField(
                    controller: email,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.email],
                    textInputAction: TextInputAction.next,
                    autocorrect: false,
                    decoration: const InputDecoration(hintText: 'voce@empresa.com.br', prefixIcon: Icon(Icons.mail_outline)),
                    validator: (v) {
                      final s = (v ?? '').trim();
                      if (s.isEmpty) return 'Informe o e-mail.';
                      if (!_emailValido.hasMatch(s)) return 'E-mail inválido. Exemplo: nome@empresa.com.br';
                      return null;
                    },
                  ),
                  const SizedBox(height: 16),
                  _Rotulo('Senha', cor: texto),
                  TextFormField(
                    controller: senha,
                    obscureText: !mostrarSenha,
                    autofillHints: const [AutofillHints.password],
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => aoEntrar(),
                    decoration: InputDecoration(
                      hintText: '••••••••',
                      prefixIcon: const Icon(Icons.key_outlined),
                      suffixIcon: IconButton(
                        tooltip: mostrarSenha ? 'Ocultar senha' : 'Mostrar senha',
                        icon: Icon(mostrarSenha ? Icons.visibility_off_outlined : Icons.visibility_outlined),
                        onPressed: aoMostrarSenha,
                      ),
                    ),
                    validator: (v) => (v ?? '').isEmpty ? 'Informe a senha.' : null,
                  ),
                  const SizedBox(height: 10),
                  // Quebra para a linha de baixo quando a tela é estreita (nada fica colado).
                  Wrap(
                    alignment: WrapAlignment.spaceBetween,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    spacing: 12,
                    runSpacing: 4,
                    children: [
                      InkWell(
                        borderRadius: BorderRadius.circular(8),
                        onTap: () => aoLembrar(!lembrar),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 6),
                          child: Row(mainAxisSize: MainAxisSize.min, children: [
                            SizedBox(width: 24, height: 24, child: Checkbox(value: lembrar, onChanged: (v) => aoLembrar(v ?? false))),
                            const SizedBox(width: 8),
                            Flexible(child: Text('Lembre de mim', style: TextStyle(color: apagado, fontSize: 13.5))),
                          ]),
                        ),
                      ),
                      TextButton(
                        onPressed: aoEsqueci,
                        style: TextButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 4), foregroundColor: destaque),
                        child: const Text('Esqueceu a senha?', style: TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600)),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  DecoratedBox(
                    decoration: BoxDecoration(gradient: CmCores.degradeSistema, borderRadius: BorderRadius.circular(14)),
                    child: FilledButton(
                      onPressed: carregando ? null : aoEntrar,
                      style: FilledButton.styleFrom(backgroundColor: Colors.transparent, foregroundColor: Colors.white, disabledBackgroundColor: Colors.transparent),
                      child: carregando
                          ? const Row(mainAxisSize: MainAxisSize.min, children: [
                              SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2.4, color: Colors.white)),
                              SizedBox(width: 10),
                              Text('Entrando…', style: TextStyle(color: Colors.white)),
                            ])
                          : const Row(mainAxisSize: MainAxisSize.min, children: [Text('Entrar'), SizedBox(width: 8), Icon(Icons.arrow_forward, size: 18)]),
                    ),
                  ),
                  const SizedBox(height: 18),
                  Wrap(
                    alignment: WrapAlignment.center,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    spacing: 4,
                    children: [
                      Icon(Icons.shield_outlined, size: 14, color: destaque),
                      Text('Acesso restrito a usuários autorizados ·', style: TextStyle(color: apagado, fontSize: 12)),
                      InkWell(
                        onTap: aoTermos,
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Text('Termos de Uso', style: TextStyle(color: destaque, fontSize: 12, fontWeight: FontWeight.w600)),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _Rotulo extends StatelessWidget {
  const _Rotulo(this.texto, {required this.cor});

  final String texto;
  final Color cor;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 7),
        child: Text(texto, style: TextStyle(color: cor, fontSize: 13, fontWeight: FontWeight.w700)),
      );
}

class _Alerta extends StatelessWidget {
  const _Alerta({required this.texto, this.aviso = false});

  final String texto;
  final bool aviso;

  @override
  Widget build(BuildContext context) {
    final cor = aviso ? CmCores.aviso : CmCores.offline;
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: cor.withValues(alpha: 0.1),
        border: Border.all(color: cor.withValues(alpha: 0.3)),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(aviso ? Icons.info_outline : Icons.warning_amber_rounded, color: cor, size: 18),
          const SizedBox(width: 10),
          Expanded(child: Text(texto, style: TextStyle(color: cor, fontSize: 13.5, height: 1.45))),
        ],
      ),
    );
  }
}
