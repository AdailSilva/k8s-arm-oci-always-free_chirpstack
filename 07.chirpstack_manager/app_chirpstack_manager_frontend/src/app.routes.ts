import { Routes } from '@angular/router';
import { AppLayout } from '@/app/layout/components/app.layout';
import { AuthLayout } from '@/app/layout/components/app.authlayout';
import { anonimoGuard, authGuard, perfilGuard, telaBloqueadaGuard, telaBloqueadaPendenteGuard, trocaSenhaGuard, trocaSenhaPendenteGuard } from '@/app/core/auth/auth.guards';
import { comTravaEdicao } from '@/app/core/edicao/edicao-pendente.guard';
import { perfisRoutes, templatesRoutes } from '@/app/pages/perfis-dispositivo/device-profiles.routes';

const ADMIN = { perfis: ['ADMIN'] };

export const appRoutes: Routes = [
    // Login em tela cheia (identidade do sistema) e Termos de Uso: fora dos layouts e públicos.
    { path: 'auth/login', canActivate: [anonimoGuard], loadComponent: () => import('@/app/pages/auth/login').then((c) => c.Login) },
    { path: 'termos-de-uso', loadComponent: () => import('@/app/pages/termos-de-uso/termos-de-uso').then((c) => c.TermosDeUso) },
    {
        path: '',
        component: AppLayout,
        canActivate: [authGuard, trocaSenhaGuard, telaBloqueadaGuard],
        canActivateChild: [trocaSenhaGuard, telaBloqueadaGuard],
        // Sair de uma tela com formulário alterado pergunta antes ("Deseja realmente sair?").
        children: comTravaEdicao([
            { path: '', redirectTo: 'painel', pathMatch: 'full' },
            {
                path: 'painel',
                loadComponent: () => import('@/app/pages/dashboard/dashboard').then((c) => c.Dashboard),
                data: { title: 'Painel', breadcrumb: ['Visão geral', 'Painel'] }
            },
            {
                path: 'monitoramento',
                children: [
                    { path: '', redirectTo: 'painel', pathMatch: 'full' },
                    {
                        path: 'painel',
                        loadComponent: () => import('@/app/pages/monitoramento/painel').then((c) => c.Painel),
                        data: { title: 'Painel de monitoramento', breadcrumb: ['Monitoramento', 'Painel'] }
                    },
                    {
                        path: 'dispositivos',
                        loadComponent: () => import('@/app/pages/monitoramento/dispositivos').then((c) => c.Dispositivos),
                        data: { title: 'Dispositivos monitorados', breadcrumb: ['Monitoramento', 'Dispositivos'] }
                    },
                    {
                        path: 'dispositivos/:id',
                        loadComponent: () => import('@/app/pages/monitoramento/dispositivo-detalhe').then((c) => c.DispositivoDetalhe),
                        data: { title: 'Dispositivo', breadcrumb: ['Monitoramento', 'Dispositivos', 'Detalhe'] }
                    },
                    {
                        path: 'alertas',
                        loadComponent: () => import('@/app/pages/monitoramento/alertas').then((c) => c.Alertas),
                        data: { title: 'Alertas', breadcrumb: ['Monitoramento', 'Alertas'] }
                    }
                ]
            },
            {
                path: 'atuacao',
                children: [
                    { path: '', redirectTo: 'comandos', pathMatch: 'full' },
                    {
                        path: 'comandos',
                        loadComponent: () => import('@/app/pages/atuacao/fila-comandos').then((c) => c.FilaComandos),
                        data: { title: 'Fila de comandos', breadcrumb: ['Atuação', 'Fila de comandos'] }
                    },
                    {
                        path: 'cenas',
                        loadComponent: () => import('@/app/pages/atuacao/cenas').then((c) => c.Cenas),
                        data: { title: 'Cenas', breadcrumb: ['Atuação', 'Cenas'] }
                    },
                    {
                        path: 'agendamentos',
                        loadComponent: () => import('@/app/pages/atuacao/agendamentos').then((c) => c.Agendamentos),
                        data: { title: 'Agendamentos', breadcrumb: ['Atuação', 'Agendamentos'] }
                    },
                    {
                        path: 'automacoes',
                        loadComponent: () => import('@/app/pages/atuacao/automacoes').then((c) => c.Automacoes),
                        data: { title: 'Automações', breadcrumb: ['Atuação', 'Automações'] }
                    }
                ]
            },
            { path: 'aplicacoes', loadChildren: () => import('@/app/pages/aplicacoes/applications.routes') },
            { path: 'gateways', loadChildren: () => import('@/app/pages/gateways/gateways.routes') },
            {
                path: 'relay-gateways',
                loadComponent: () => import('@/app/pages/relay-gateways/relay-gateway-list').then((c) => c.RelayGatewayList),
                data: { title: 'Relay gateways', breadcrumb: 'Relay gateways' }
            },
            {
                path: 'multicast',
                loadComponent: () => import('@/app/pages/multicast/multicast-list').then((c) => c.MulticastList),
                data: { title: 'Grupos multicast', breadcrumb: 'Grupos multicast' }
            },
            { path: 'perfis-dispositivo', children: perfisRoutes },
            { path: 'templates-perfil', children: templatesRoutes },
            {
                path: 'catalogo-dispositivos',
                loadComponent: () => import('@/app/pages/catalogo/catalogo-dispositivos').then((c) => c.CatalogoDispositivos),
                data: { title: 'Catálogo de dispositivos', breadcrumb: 'Catálogo de dispositivos' }
            },
            {
                path: 'tenants',
                canActivate: [perfilGuard],
                data: ADMIN,
                loadChildren: () => import('@/app/pages/tenants/tenants.routes')
            },
            {
                path: 'usuarios-chirpstack',
                canActivate: [perfilGuard],
                loadComponent: () => import('@/app/pages/usuarios-chirpstack/cs-user-list').then((c) => c.CsUserList),
                data: { ...ADMIN, title: 'Usuários do ChirpStack', breadcrumb: ['ChirpStack', 'Usuários'] }
            },
            {
                path: 'sistema/usuarios',
                canActivate: [perfilGuard],
                loadComponent: () => import('@/app/pages/usuarios/usuarios').then((c) => c.Usuarios),
                data: { ...ADMIN, title: 'Usuários do painel', breadcrumb: ['Sistema', 'Usuários'] }
            },
            {
                path: 'sistema/visitantes',
                canActivate: [perfilGuard],
                loadComponent: () => import('@/app/pages/visitantes/visitantes').then((c) => c.Visitantes),
                data: { ...ADMIN, title: 'Visitantes', breadcrumb: ['Sistema', 'Visitantes'] }
            },
            {
                path: 'sistema/controle-sessao',
                canActivate: [perfilGuard],
                loadComponent: () => import('@/app/pages/controle-sessao/controle-sessao').then((c) => c.ControleSessao),
                data: { ...ADMIN, title: 'Controle de sessão', breadcrumb: ['Sistema', 'Controle de sessão'] }
            },
            {
                path: 'sistema/horario-acesso',
                canActivate: [perfilGuard],
                loadComponent: () => import('@/app/pages/horario-acesso/horario-acesso').then((c) => c.HorarioAcesso),
                data: { ...ADMIN, title: 'Horário de acesso', breadcrumb: ['Sistema', 'Horário de acesso'] }
            },
            {
                path: 'sistema/backup',
                canActivate: [perfilGuard],
                loadComponent: () => import('@/app/pages/backup/backup').then((c) => c.Backup),
                data: { ...ADMIN, title: 'Backup & Restauração', breadcrumb: ['Sistema', 'Backup & Restauração'] }
            },
            {
                path: 'sistema/conta-email',
                canActivate: [perfilGuard],
                loadComponent: () => import('@/app/pages/conta-email/conta-email').then((c) => c.ContaEmailPage),
                data: { ...ADMIN, title: 'Conta de e-mail', breadcrumb: ['Sistema', 'Conta de e-mail'] }
            },
            {
                path: 'sistema/auditoria',
                canActivate: [perfilGuard],
                loadComponent: () => import('@/app/pages/auditoria/auditoria').then((c) => c.Auditoria),
                data: { ...ADMIN, title: 'Auditoria', breadcrumb: ['Sistema', 'Auditoria'] }
            },
            {
                path: 'meu-perfil',
                loadComponent: () => import('@/app/pages/minha-conta/meu-perfil').then((c) => c.MeuPerfil),
                data: { title: 'Meu perfil', breadcrumb: ['Conta', 'Meu perfil'] }
            },
            {
                path: 'suporte',
                loadComponent: () => import('@/app/pages/suporte/suporte').then((c) => c.Suporte),
                data: { title: 'Suporte', breadcrumb: ['Conta', 'Suporte'] }
            }
        ])
    },
    {
        path: 'auth',
        component: AuthLayout,
        children: [
            { path: '', redirectTo: 'login', pathMatch: 'full' },
            { path: 'esqueci-senha', canActivate: [anonimoGuard], loadComponent: () => import('@/app/pages/auth/esqueci-senha').then((c) => c.EsqueciSenha) },
            { path: 'nova-senha', loadComponent: () => import('@/app/pages/auth/nova-senha').then((c) => c.NovaSenha) },
            // Confirmação do e-mail pelo link (usuário criado com senha ou com o e-mail alterado).
            { path: 'confirmar-email', loadComponent: () => import('@/app/pages/auth/confirmar-email').then((c) => c.ConfirmarEmail) },
            // Troca obrigatória no primeiro acesso (senha inicial): tela própria, como nos demais sistemas.
            { path: 'trocar-senha', canActivate: [trocaSenhaPendenteGuard], loadComponent: () => import('@/app/pages/auth/trocar-senha').then((c) => c.TrocarSenha) },
            // Tela bloqueada ("Bloquear tela" no menu do usuário): pede a senha para continuar.
            { path: 'tela-bloqueada', canActivate: [telaBloqueadaPendenteGuard], loadComponent: () => import('@/app/pages/auth/tela-bloqueada').then((c) => c.TelaBloqueada) },
            // Erro inesperado (ex.: nova versão publicada com a página aberta).
            { path: 'oops', loadComponent: () => import('@/app/pages/auth/oops').then((c) => c.Oops) },
            { path: 'access-denied', loadComponent: () => import('@/app/pages/auth/acesso-negado').then((c) => c.AccessDenied) },
            { path: 'notfound', loadComponent: () => import('@/app/pages/auth/nao-encontrado').then((c) => c.Notfound) }
        ]
    },
    { path: '**', redirectTo: 'auth/notfound' }
];
