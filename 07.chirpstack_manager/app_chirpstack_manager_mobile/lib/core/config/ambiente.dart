// Ambiente do app — o mesmo esquema de perfis do frontend Angular (development / staging /
// production), escolhido no build:
//
//   flutter run                                    -> development (API local do notebook)
//   flutter run --dart-define=AMBIENTE=staging     -> staging
//   flutter build apk --dart-define=AMBIENTE=production   (pipeline mobile.yml)
//
// API_URL sobrepõe a URL do perfil, para testar contra outra máquina sem mexer no código:
//   flutter run --dart-define=API_URL=http://192.168.0.10:8081/chirpstack-manager
//
// As rotas da API são sempre /api/v1/... somadas a esta base:
//   - em desenvolvimento a base é o backend direto, com o context-path (/chirpstack-manager);
//   - publicado, é o host do painel: o NGINX do frontend encaminha /api para o backend.

class Ambiente {
  Ambiente._();

  static const String nome = String.fromEnvironment('AMBIENTE', defaultValue: 'development');

  static const String _apiUrlForcada = String.fromEnvironment('API_URL');

  /// Backend em desenvolvimento (porta 8081). No emulador Android, 10.0.2.2 é o localhost do computador.
  static const String _apiDesenvolvimento = 'http://10.0.2.2:8081/chirpstack-manager';
  static const String _urlPublica = 'https://app-chirpstack-manager.adailsilva.com.br';

  static bool get producao => nome == 'production';
  static bool get homologacao => nome == 'staging';
  static bool get desenvolvimento => !producao && !homologacao;

  static String get apiUrl {
    if (_apiUrlForcada.isNotEmpty) return _apiUrlForcada;
    if (producao || homologacao) return _urlPublica;
    return _apiDesenvolvimento;
  }

  /// Endereço do painel web (Termos de Uso e telas que ficam só no navegador).
  static String get urlWeb => desenvolvimento ? 'http://localhost:4201' : _urlPublica;

  static const String nomeApp = 'ChirpStack Manager';

  /// Rótulo curto do ambiente para o rodapé da tela Conta (vazio em produção).
  static String get rotulo {
    if (producao) return '';
    if (homologacao) return 'Homologação';
    return 'Desenvolvimento';
  }
}
