import 'package:flutter/material.dart';

import 'package:chirpstack_manager/app.dart';
import 'package:chirpstack_manager/core/auth/auth_service.dart';
import 'package:chirpstack_manager/core/auth/bloqueio_tela.dart';
import 'package:chirpstack_manager/core/auth/sessao.dart';
import 'package:chirpstack_manager/core/conta/avisos.dart';
import 'package:chirpstack_manager/core/conta/preferencias.dart';
import 'package:chirpstack_manager/core/sessao/horario_acesso.dart';
import 'package:chirpstack_manager/core/sessao/sessao_monitor.dart';
import 'package:chirpstack_manager/core/storage/armazenamento.dart';
import 'package:chirpstack_manager/core/tema/tema_controller.dart';
import 'package:chirpstack_manager/core/ui/erro_tela.dart';

// ChirpStack Manager (mobile) — administração e monitoramento da rede LoRaWAN ChirpStack.
// Mesma API, mesmas regras e mesma identidade visual do painel web.

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Uma ida só ao cofre cifrado: sessão lembrada ("Lembre de mim") e tema.
  await Armazenamento.precarregar();
  await TemaController.instancia.carregar();
  final sessao = Sessao.instancia;
  await sessao.carregar();

  // Sessão lembrada com o token de acesso vencido: renova pelo refresh antes de abrir.
  String? aviso;
  if (sessao.autenticado) {
    final expira = sessao.expiraEm;
    if (expira == null || !expira.isAfter(DateTime.now().add(const Duration(seconds: 30)))) {
      try {
        await AuthService.instancia.renovar();
      } catch (_) {
        await sessao.encerrar();
        aviso = 'Sua sessão expirou. Entre novamente.';
      }
    }
  }

  // Tela bloqueada antes de fechar o app: volta bloqueada (se a sessão ainda é do mesmo usuário).
  await BloqueioTela.instancia.instalar();

  // Erro inesperado ao montar uma tela: mensagem amigável em vez da tela vermelha/cinza.
  instalarTelaDeErro();

  SessaoMonitor.instancia.instalar();
  HorarioAcesso.instancia.instalar();
  Avisos.instancia.instalar();
  Preferencias.instancia.instalar();
  runApp(ChirpStackManagerApp(avisoInicial: aviso));
}
