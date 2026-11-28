import { Component, inject } from '@angular/core';
import { Location } from '@angular/common';
import { RouterModule } from '@angular/router';

interface SecaoTermo {
    numero: number;
    id: string;
    titulo: string;
    paragrafos: string[];
    itens?: string[];
}

/**
 * Termos de Uso do ChirpStack Manager.
 *
 * Página cheia e autossuficiente (não usa o layout do painel), aberta pelo link da tela de
 * login — antes da autenticação — e também por quem já está logado. Rota pública /termos-de-uso.
 */
@Component({
    selector: 'app-termos-de-uso',
    standalone: true,
    imports: [RouterModule],
    template: `
        <div class="min-h-screen bg-surface-50 dark:bg-surface-950 text-surface-700 dark:text-surface-300">
            <!-- Barra superior -->
            <header class="sticky top-0 z-20 border-b border-surface-200 dark:border-surface-800 bg-surface-0/80 dark:bg-surface-900/80 backdrop-blur">
                <div class="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
                    <div class="flex items-center gap-3 min-w-0">
                        <img src="brand/chirpstack-manager-logo-horizontal.svg" alt="ChirpStack Manager" width="190" height="30" class="block dark:hidden" />
                        <img src="brand/chirpstack-manager-logo-horizontal-negativo.svg" alt="ChirpStack Manager" width="190" height="30" class="hidden dark:block" />
                    </div>
                    <button type="button" (click)="voltar()"
                            class="inline-flex shrink-0 items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-surface-700 dark:text-surface-200 border border-surface-200 dark:border-surface-700 hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors">
                        <i class="pi pi-arrow-left text-xs"></i> Voltar
                    </button>
                </div>
            </header>

            <!-- Cabeçalho -->
            <section class="border-b border-surface-200 dark:border-surface-800">
                <div class="max-w-6xl mx-auto px-4 sm:px-6 py-12">
                    <span class="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-medium text-primary bg-surface-100 dark:bg-surface-800 mb-4">
                        <i class="pi pi-file-edit text-xs"></i> Documento legal
                    </span>
                    <h1 class="text-3xl sm:text-4xl font-bold text-surface-900 dark:text-surface-0">Termos de Uso</h1>
                    <p class="mt-3 max-w-2xl leading-relaxed text-surface-600 dark:text-surface-300">
                        Condições que regem o acesso e a utilização do <strong>ChirpStack Manager</strong> — administração e monitoramento da
                        rede LoRaWAN ChirpStack: tenants, aplicações, dispositivos, gateways, usuários e os eventos da rede em tempo real.
                    </p>
                    <p class="mt-4 text-sm text-surface-500">Última atualização: {{ atualizadoEm }} · Versão {{ versao }}</p>
                </div>
            </section>

            <!-- Corpo -->
            <div class="max-w-6xl mx-auto px-4 sm:px-6 py-10 grid lg:grid-cols-[230px_1fr] gap-10">
                <aside class="hidden lg:block">
                    <nav class="sticky top-24 flex flex-col gap-1 text-sm">
                        <span class="px-3 pb-2 text-xs uppercase tracking-wider text-surface-400">Nesta página</span>
                        @for (s of secoes; track s.id) {
                            <a [href]="'#' + s.id" (click)="irPara(s.id, $event)"
                               class="px-3 py-2 rounded-lg text-surface-600 dark:text-surface-300 hover:text-primary hover:bg-surface-100 dark:hover:bg-surface-800 transition-colors">
                                {{ s.numero }}. {{ s.titulo }}
                            </a>
                        }
                    </nav>
                </aside>

                <main class="flex flex-col gap-5 max-w-3xl min-w-0">
                    @for (s of secoes; track s.id) {
                        <section [id]="s.id" class="scroll-mt-24 bg-surface-0 dark:bg-surface-900 rounded-2xl border border-surface-200 dark:border-surface-800 p-6">
                            <h2 class="flex items-center gap-3 text-lg font-semibold text-surface-900 dark:text-surface-0">
                                <span class="inline-flex shrink-0 items-center justify-center w-7 h-7 rounded-lg text-xs font-bold text-primary bg-surface-100 dark:bg-surface-800">{{ s.numero }}</span>
                                {{ s.titulo }}
                            </h2>
                            <div class="mt-3 flex flex-col gap-3 text-sm leading-relaxed text-surface-600 dark:text-surface-300">
                                @for (p of s.paragrafos; track $index) {
                                    <p [innerHTML]="p"></p>
                                }
                                @if (s.itens && s.itens.length) {
                                    <ul class="list-disc pl-5 flex flex-col gap-1.5">
                                        @for (it of s.itens; track $index) {
                                            <li [innerHTML]="it"></li>
                                        }
                                    </ul>
                                }
                            </div>
                        </section>
                    }

                    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 pt-2 text-sm text-surface-500">
                        <span>Dúvidas sobre estes termos? Fale com o administrador da rede.</span>
                        <a routerLink="/auth/login" class="text-primary font-medium hover:underline">Ir para o login</a>
                    </div>
                </main>
            </div>

            <footer class="border-t border-surface-200 dark:border-surface-800 mt-6">
                <div class="max-w-6xl mx-auto px-4 sm:px-6 py-6 text-center text-xs text-surface-500">&copy; {{ ano }} ChirpStack · Manager. Todos os direitos reservados.</div>
            </footer>
        </div>
    `
})
export class TermosDeUso {
    private readonly location = inject(Location);

    readonly ano = new Date().getFullYear();
    readonly versao = '1.0';
    readonly atualizadoEm = '25 de novembro de 2026';

    readonly secoes: SecaoTermo[] = [
        {
            numero: 1, id: 'aceitacao', titulo: 'Aceitação dos termos',
            paragrafos: [
                'Ao acessar ou utilizar o <strong>ChirpStack Manager</strong> ("Sistema"), você declara ter lido, compreendido e concordado integralmente com estes Termos de Uso.',
                'Caso não concorde com qualquer condição aqui descrita, não utilize o Sistema.'
            ]
        },
        {
            numero: 2, id: 'definicoes', titulo: 'Definições',
            paragrafos: ['Para os fins destes Termos, considera-se:'],
            itens: [
                '<strong>Usuário</strong>: pessoa física autorizada a acessar o Sistema mediante credenciais individuais;',
                '<strong>Rede</strong>: a rede LoRaWAN ChirpStack administrada pelo Sistema — tenants, aplicações, perfis, dispositivos e gateways;',
                '<strong>Dados da rede</strong>: cadastros, chaves de dispositivos, eventos de uplink, downlink e join, estado dos gateways e demais informações recebidas do ChirpStack;',
                '<strong>LGPD</strong>: Lei nº 13.709/2018 (Lei Geral de Proteção de Dados Pessoais).'
            ]
        },
        {
            numero: 3, id: 'conta', titulo: 'Conta de acesso',
            paragrafos: [
                'Não há cadastro público: as contas são criadas por um administrador, e o primeiro acesso exige a troca da senha provisória. As credenciais são <strong>pessoais e intransferíveis</strong>.',
                'Você é responsável por manter seus dados atualizados e por escolher uma senha forte. O compartilhamento de credenciais é proibido e pode resultar no bloqueio do acesso.'
            ]
        },
        {
            numero: 4, id: 'uso', titulo: 'Uso do sistema e perfis de acesso',
            paragrafos: [
                'O ChirpStack Manager destina-se à administração da Rede e ao acompanhamento do seu funcionamento: cadastros, chaves de API, filas de downlink, multicast, eventos em tempo real e alertas de gateways.',
                'Cada Usuário acessa apenas as telas e ações do seu <strong>perfil</strong>, definido pelo administrador, que também pode encerrar sessões. É vedado tentar contornar esses controles.'
            ]
        },
        {
            numero: 5, id: 'responsabilidades', titulo: 'Responsabilidades do usuário',
            paragrafos: ['Ao utilizar o Sistema, você se compromete a:'],
            itens: [
                'fornecer informações verdadeiras, completas e atualizadas;',
                'preservar a confidencialidade das suas credenciais, das chaves de API e das chaves de sessão dos dispositivos;',
                'não excluir ou alterar dispositivos, gateways ou perfis sem autorização do responsável pela Rede;',
                'não introduzir código malicioso, nem realizar engenharia reversa, sobrecarga ou acesso não autorizado.'
            ]
        },
        {
            numero: 6, id: 'dados-rede', titulo: 'Dados da rede',
            paragrafos: [
                'Os Dados da rede são processados para exibir o estado da Rede, gerar os indicadores do painel e manter o histórico de eventos e a auditoria das alterações.',
                'O que aparece em tempo real depende da conectividade dos gateways, dos dispositivos e do broker MQTT. Falhas de comunicação podem gerar lacunas, que o Sistema sinaliza quando as identifica.'
            ]
        },
        {
            numero: 7, id: 'lgpd', titulo: 'Proteção de dados pessoais (LGPD)',
            paragrafos: [
                'O tratamento de dados pessoais dos Usuários (nome, e-mail, registros de acesso) observa a <strong>LGPD</strong> e se limita à autenticação, à segurança e à auditoria das operações realizadas no Sistema. Esses dados não são comercializados.',
                'Os titulares podem exercer seus direitos — acesso, correção, portabilidade e eliminação — por meio do administrador da Rede.'
            ]
        },
        {
            numero: 8, id: 'seguranca', titulo: 'Segurança da informação',
            paragrafos: [
                'Adotamos medidas técnicas e organizacionais para proteger os dados, incluindo controle de acesso por perfil, transmissão criptografada (HTTPS e MQTT com TLS), autenticação por token de curta duração com renovação de sessão, trilha de auditoria e cópias de segurança.',
                'Nenhum sistema é completamente imune a riscos; por isso, a segurança também depende do uso responsável das credenciais por parte do Usuário.'
            ]
        },
        {
            numero: 9, id: 'propriedade', titulo: 'Propriedade intelectual',
            paragrafos: [
                'A identidade visual, o código-fonte e os demais elementos do ChirpStack Manager são protegidos por direitos de propriedade intelectual. O ChirpStack é um projeto de código aberto, com licença própria.',
                'É vedada a reprodução, distribuição, modificação ou exploração comercial do Sistema sem autorização expressa.'
            ]
        },
        {
            numero: 10, id: 'disponibilidade', titulo: 'Disponibilidade e manutenção',
            paragrafos: [
                'Empenhamo-nos para manter o Sistema disponível e estável, mas não garantimos operação ininterrupta. Manutenções programadas serão comunicadas com antecedência sempre que possível.'
            ]
        },
        {
            numero: 11, id: 'responsabilidade', titulo: 'Limitação de responsabilidade',
            paragrafos: [
                'O ChirpStack Manager apoia a administração e o monitoramento da Rede, mas não substitui as rotinas de operação e segurança do responsável por ela. Na máxima extensão permitida pela lei, não há responsabilidade por danos indiretos decorrentes de indisponibilidades temporárias, falhas de comunicação de gateways ou dispositivos, uso indevido das credenciais ou dados incorretos inseridos pelo Usuário.'
            ]
        },
        {
            numero: 12, id: 'alteracoes', titulo: 'Alterações destes termos',
            paragrafos: [
                'Estes Termos podem ser atualizados periodicamente. Mudanças relevantes serão comunicadas no próprio Sistema, com a nova data de atualização.',
                'O uso contínuo após a publicação das alterações representa concordância com a versão vigente.'
            ]
        },
        {
            numero: 13, id: 'foro', titulo: 'Lei aplicável e foro',
            paragrafos: [
                'Estes Termos são regidos pelas leis da República Federativa do Brasil. Fica eleito o foro do domicílio do responsável pela Rede para dirimir eventuais controvérsias.'
            ]
        }
    ];

    voltar(): void {
        this.location.back();
    }

    irPara(id: string, evento: Event): void {
        evento.preventDefault();
        document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}
