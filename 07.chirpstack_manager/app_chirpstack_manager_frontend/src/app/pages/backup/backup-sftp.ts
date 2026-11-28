import { Component, inject, OnInit, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { BackupService, SftpForm } from '@/app/pages/backup/backup.service';
import { SftpConfig, SftpTeste } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora } from '@/app/core/format/format';
import { travaEdicao } from '@/app/core/edicao/trava-edicao';
import { CopyButton } from '@/app/shared/campos/copy-button';

/**
 * Destino SFTP dos backups, mesmo padrão dos demais sistemas: um computador, NAS ou servidor da rede
 * do cliente. A chave do servidor é conferida antes da senha sair do sistema: o primeiro teste mostra
 * a impressão digital e o ADMIN confirma.
 */
@Component({
    selector: 'app-backup-sftp',
    standalone: true,
    imports: [CommonModule, FormsModule, ButtonModule, InputNumberModule, InputTextModule, MessageModule, PasswordModule, SelectButtonModule, TagModule, TextareaModule, ToggleSwitchModule, CopyButton],
    template: `
        @if (cfg(); as c) {
            <div class="grid grid-cols-1 xl:grid-cols-12 gap-6">
                <div class="xl:col-span-7 flex flex-col gap-6">
                    <!-- Situação -->
                    <div class="border rounded-2xl p-5">
                        <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
                            <div class="flex items-center gap-4 min-w-0">
                                <div class="w-12 h-12 shrink-0 rounded-2xl flex items-center justify-center"
                                     [ngClass]="c.pronto ? 'bg-green-50 text-green-600 dark:bg-green-900/30 dark:text-green-300' : c.preenchido ? 'bg-amber-50 text-amber-600 dark:bg-amber-900/30 dark:text-amber-300' : 'bg-surface-100 text-surface-500 dark:bg-surface-800'">
                                    <i class="pi pi-server text-xl!"></i>
                                </div>
                                <div class="min-w-0">
                                    @if (c.pronto) {
                                        <div class="font-semibold text-lg">Pronto para receber backups</div>
                                        <div class="text-sm text-surface-500 break-all">{{ c.usuario }}&#64;{{ c.host }}:{{ c.porta }}{{ c.pastaRemota ? ' · ' + c.pastaRemota : '' }}</div>
                                    } @else if (c.preenchido) {
                                        <div class="font-semibold text-lg">Falta confirmar a chave do servidor</div>
                                        <div class="text-sm text-surface-500">Use "Testar conexão", confira a impressão digital e confirme.</div>
                                    } @else {
                                        <div class="font-semibold text-lg">SFTP não configurado</div>
                                        <div class="text-sm text-surface-500">Preencha o servidor e a credencial abaixo e salve.</div>
                                    }
                                </div>
                            </div>
                            <div class="flex flex-wrap gap-2">
                                <p-button label="Testar conexão" icon="pi pi-bolt" [outlined]="true" severity="secondary" (onClick)="testar()" [loading]="testando()" [disabled]="!c.preenchido || sujo()" />
                                @if (c.fingerprint) {
                                    <p-button label="Redefinir chave" icon="pi pi-key" [text]="true" severity="warn" (onClick)="redefinirChave()" />
                                }
                                @if (c.preenchido) {
                                    <p-button label="Remover" icon="pi pi-trash" [text]="true" severity="danger" (onClick)="remover()" />
                                }
                            </div>
                        </div>
                        @if (sujo() && c.preenchido) {
                            <p-message severity="info" text="Salve as alterações antes de testar." styleClass="w-full mt-4" />
                        }
                        @if (c.fingerprint) {
                            <div class="flex flex-wrap items-center gap-2 mt-4 text-sm">
                                <i class="pi pi-shield text-green-500"></i>
                                <span class="text-surface-500">Chave confirmada{{ c.tipoChave ? ' (' + c.tipoChave + ')' : '' }}:</span>
                                <code class="font-mono text-xs break-all">{{ c.fingerprint }}</code>
                            </div>
                        }
                        @if (teste(); as t) {
                            <p-message [severity]="t.sucesso ? 'success' : 'error'" [text]="t.mensagem" styleClass="w-full mt-4" />
                            @if (t.sucesso && t.chaveNova && t.fingerprint) {
                                <div class="mt-4 rounded-xl border border-amber-300 dark:border-amber-800 bg-amber-50 dark:bg-amber-900/20 p-4 flex flex-col gap-3">
                                    <div class="font-semibold flex items-center gap-2"><i class="pi pi-key text-amber-600"></i>Confira a chave do servidor</div>
                                    <div class="text-sm">
                                        O servidor apresentou a chave <b>{{ t.tipoChave }}</b> com a impressão digital:
                                    </div>
                                    <div class="flex items-center gap-2 min-w-0">
                                        <code class="font-mono text-sm break-all bg-surface-0 dark:bg-surface-900 rounded-lg px-3 py-2 border flex-1 min-w-0">{{ t.fingerprint }}</code>
                                        <app-copy-button [value]="t.fingerprint" />
                                    </div>
                                    <div class="text-sm text-surface-600 dark:text-surface-300">
                                        No computador de destino, compare com <code>ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub</code> (Linux) ou
                                        <code>ssh-keygen -lf C:\\ProgramData\\ssh\\ssh_host_ed25519_key.pub</code> (Windows). Só confirme se forem iguais.
                                    </div>
                                    <div>
                                        <p-button label="As impressões conferem: confirmar chave" icon="pi pi-check" (onClick)="confirmarChave(t)" [loading]="confirmando()" />
                                    </div>
                                </div>
                            }
                        }
                        @if (c.ultimoEnvioEm) {
                            <div class="flex items-start gap-2 mt-4 text-sm">
                                <i class="pi mt-0.5" [ngClass]="c.ultimoEnvioStatus === 'SUCESSO' ? 'pi-check-circle text-green-500' : 'pi-times-circle text-red-500'"></i>
                                <span class="min-w-0 break-words"><b>Último envio</b> em {{ data(c.ultimoEnvioEm) }}: {{ c.ultimoEnvioMensagem }}</span>
                            </div>
                        }
                    </div>

                    <!-- Servidor e credencial -->
                    <div class="border rounded-2xl p-5">
                        <div class="font-semibold text-lg mb-4">Servidor e credencial</div>
                        <div class="grid grid-cols-1 md:grid-cols-12 gap-5">
                            <div class="md:col-span-8">
                                <label class="block font-medium mb-2" for="sftpHost">Servidor (nome ou IP)</label>
                                <input id="sftpHost" pInputText class="w-full" [(ngModel)]="form.host" (ngModelChange)="tocar()" maxlength="255" placeholder="Ex.: backup.minhaempresa.com.br ou 200.150.10.20" autocomplete="off" />
                            </div>
                            <div class="md:col-span-4">
                                <label class="block font-medium mb-2" for="sftpPorta">Porta</label>
                                <p-inputnumber inputId="sftpPorta" [(ngModel)]="form.porta" (ngModelChange)="tocar()" [min]="1" [max]="65535" [useGrouping]="false" placeholder="22 (padrão do SSH)" styleClass="w-full" inputStyleClass="w-full" />
                            </div>
                            <div class="md:col-span-6">
                                <label class="block font-medium mb-2" for="sftpUsuario">Usuário</label>
                                <input id="sftpUsuario" pInputText class="w-full" [(ngModel)]="form.usuario" (ngModelChange)="tocar()" maxlength="120" placeholder="Ex.: backup (usuário do computador de destino)" autocomplete="off" />
                            </div>
                            <div class="md:col-span-6">
                                <label class="block font-medium mb-2">Autenticação</label>
                                <p-selectbutton [(ngModel)]="form.autenticacao" (ngModelChange)="tocar()" [options]="autenticacoes" optionLabel="label" optionValue="value" [allowEmpty]="false" />
                            </div>
                            @if (form.autenticacao === 'CHAVE') {
                                <div class="md:col-span-12">
                                    <label class="block font-medium mb-2" for="sftpChave">Chave privada</label>
                                    <textarea id="sftpChave" pTextarea class="w-full font-mono text-xs" rows="6" [(ngModel)]="form.chavePrivada" (ngModelChange)="tocar()" autocomplete="off" spellcheck="false"
                                        [placeholder]="c.chaveDefinida ? 'Chave gravada. Cole outra só para trocar (-----BEGIN OPENSSH PRIVATE KEY-----...)' : 'Cole o conteúdo do arquivo de chave privada, ex.: -----BEGIN OPENSSH PRIVATE KEY----- ... (ssh-keygen -t ed25519)'"></textarea>
                                    <small class="text-surface-500">A chave pública correspondente vai em <code>~/.ssh/authorized_keys</code> do usuário no destino.</small>
                                </div>
                                <div class="md:col-span-6">
                                    <label class="block font-medium mb-2" for="sftpFrase">Frase-senha da chave (opcional)</label>
                                    <p-password inputId="sftpFrase" [(ngModel)]="form.fraseChave" (ngModelChange)="tocar()" [feedback]="false" [toggleMask]="true" styleClass="w-full" inputStyleClass="w-full" autocomplete="new-password"
                                        [placeholder]="c.fraseDefinida ? 'Gravada. Em branco mantém' : 'Só se a chave tiver frase-senha'" />
                                </div>
                            } @else {
                                <div class="md:col-span-6">
                                    <label class="block font-medium mb-2" for="sftpSenha">Senha</label>
                                    <p-password inputId="sftpSenha" [(ngModel)]="form.senha" (ngModelChange)="tocar()" [feedback]="false" [toggleMask]="true" styleClass="w-full" inputStyleClass="w-full" autocomplete="new-password"
                                        [placeholder]="c.senhaDefinida ? 'Senha gravada. Em branco mantém' : 'Senha do usuário no destino'" />
                                </div>
                            }
                            <div class="md:col-span-12">
                                <label class="block font-medium mb-2" for="sftpPasta">Pasta remota</label>
                                <input id="sftpPasta" pInputText class="w-full" [(ngModel)]="form.pastaRemota" (ngModelChange)="tocar()" maxlength="255" placeholder="Ex.: /srv/backups/chirpstack-manager ou C:/Backups/ChirpStackManager (em branco = pasta do usuário)" autocomplete="off" />
                                <small class="text-surface-500">O sistema cria as pastas que faltarem.</small>
                            </div>
                        </div>
                    </div>

                    <!-- Envio e retenção -->
                    <div class="border rounded-2xl p-5">
                        <div class="font-semibold text-lg mb-4">Envio e retenção</div>
                        <div class="flex flex-col gap-5">
                            <label class="flex items-center gap-3 cursor-pointer">
                                <p-toggleswitch [(ngModel)]="form.enviarManuais" (ngModelChange)="tocar()" />
                                <span>Enviar também os backups gerados pela tela (sugestão marcada em "Gerar backup agora")</span>
                            </label>
                            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <div>
                                    <label class="block font-medium mb-2 text-sm" for="sftpRet">Apagar do SFTP após</label>
                                    <p-inputnumber inputId="sftpRet" [(ngModel)]="form.retencaoDias" (ngModelChange)="tocar()" [min]="0" [max]="3650" suffix=" dias" placeholder="30 dias (0 = nunca apagar)" [showButtons]="true" styleClass="w-full" inputStyleClass="w-full" />
                                </div>
                                <div>
                                    <label class="block font-medium mb-2 text-sm" for="sftpMin">Manter no mínimo</label>
                                    <p-inputnumber inputId="sftpMin" [(ngModel)]="form.manterMinimo" (ngModelChange)="tocar()" [min]="1" [max]="100" suffix=" arquivos" placeholder="3 arquivos por formato" [showButtons]="true" styleClass="w-full" inputStyleClass="w-full" />
                                </div>
                            </div>
                        </div>
                        <div class="flex justify-end gap-2 mt-6">
                            @if (sujo()) {
                                <p-button label="Descartar" severity="secondary" [text]="true" (onClick)="aplicar(c)" />
                            }
                            <p-button label="Salvar" icon="pi pi-check" (onClick)="salvar()" [loading]="salvando()" [disabled]="!sujo()" />
                        </div>
                    </div>
                </div>

                <div class="xl:col-span-5 flex flex-col gap-6">
                    <div class="border rounded-2xl p-5">
                        <div class="font-semibold text-lg mb-3">Como funciona</div>
                        <ul class="m-0 pl-5 text-sm text-surface-600 dark:text-surface-300 flex flex-col gap-2 list-disc">
                            <li>O backup é gerado no servidor do sistema e enviado por SFTP (SSH) ao destino: um computador, NAS ou servidor da sua rede.</li>
                            <li>O destino precisa ser acessível a partir da nuvem: IP público ou nome, com a porta liberada no roteador/firewall (ou VPN).</li>
                            <li>A chave do servidor é conferida <b>antes</b> de a senha sair do sistema. Se ela mudar, os envios param até você conferir e redefinir.</li>
                            <li>Cada arquivo sobe como <code>.parcial</code> e só é renomeado no fim: uma queda nunca deixa um backup truncado com cara de bom.</li>
                            <li>Senha e chave ficam cifradas no banco e nunca voltam para a tela.</li>
                            <li>Use o SFTP como destino do backup automático na aba Agendamento.</li>
                        </ul>
                        @if (!c.chaveCifragemDedicada) {
                            <p-message severity="warn" icon="pi pi-key" text="APP_CHAVE_CIFRAGEM não está definida: a chave dos segredos é derivada do segredo do JWT. Se ele mudar, será preciso informar a senha ou a chave do SFTP de novo." styleClass="w-full mt-4" />
                        }
                    </div>
                    <div class="border rounded-2xl p-5">
                        <div class="font-semibold text-lg mb-3">Preparar o destino</div>
                        <div class="flex flex-col gap-3 text-sm text-surface-600 dark:text-surface-300">
                            <div><b>Linux</b>: <code>sudo apt install openssh-server</code> e crie um usuário só para os backups.</div>
                            <div><b>Windows 10/11</b>: Configurações → Aplicativos → Recursos opcionais → <i>Servidor OpenSSH</i>; depois inicie o serviço <code>sshd</code>.</div>
                            <div><b>NAS</b> (Synology, QNAP...): ative o serviço SFTP no painel e dê permissão de escrita na pasta.</div>
                        </div>
                    </div>
                </div>
            </div>
        } @else {
            <div class="py-10 text-center text-surface-500"><i class="pi pi-spin pi-spinner mr-2"></i>Carregando...</div>
        }
    `
})
export class BackupSftpPainel implements OnInit {
    private api = inject(BackupService);
    private notify = inject(NotifyService);

    /** Configuração mudou (a tela principal atualiza a verificação do ambiente). */
    readonly alterado = output<void>();

    cfg = signal<SftpConfig | null>(null);
    teste = signal<SftpTeste | null>(null);
    sujo = signal(false);
    salvando = signal(false);
    testando = signal(false);
    confirmando = signal(false);
    form: SftpForm = {};
    /** Alterações não salvas: sair da tela pergunta antes ("Deseja realmente sair?"). */
    private readonly trava = travaEdicao({ sujo: () => this.sujo() });
    data = dataHora;

    readonly autenticacoes = [
        { label: 'Senha', value: 'SENHA' },
        { label: 'Chave privada', value: 'CHAVE' }
    ];

    ngOnInit(): void {
        this.api.sftp().subscribe({ next: (c) => this.aplicar(c), error: (e) => this.notify.erro(e) });
    }

    tocar(): void {
        this.sujo.set(true);
    }

    aplicar(c: SftpConfig): void {
        this.cfg.set(c);
        this.form = {
            host: c.host ?? '',
            porta: c.porta || 22,
            usuario: c.usuario ?? '',
            autenticacao: c.autenticacao ?? 'SENHA',
            senha: '',
            chavePrivada: '',
            fraseChave: '',
            pastaRemota: c.pastaRemota ?? '',
            enviarManuais: c.enviarManuais,
            retencaoDias: c.retencaoDias,
            manterMinimo: c.manterMinimo
        };
        this.sujo.set(false);
    }

    salvar(): void {
        const f = this.form;
        if (!f.host?.trim() || !f.usuario?.trim()) {
            this.notify.aviso('Informe o servidor e o usuário.');
            return;
        }
        const c = this.cfg();
        if (f.autenticacao === 'SENHA' && !f.senha && !c?.senhaDefinida) {
            this.notify.aviso('Informe a senha do usuário SFTP.');
            return;
        }
        if (f.autenticacao === 'CHAVE' && !f.chavePrivada?.trim() && !c?.chaveDefinida) {
            this.notify.aviso('Cole a chave privada.');
            return;
        }
        this.salvando.set(true);
        this.api.salvarSftp({ ...f, host: f.host.trim(), usuario: f.usuario.trim(), pastaRemota: f.pastaRemota?.trim() ?? '' }).subscribe({
            next: (r) => {
                this.salvando.set(false);
                const trocouServidor = c?.fingerprint && !r.fingerprint;
                this.aplicar(r);
                this.teste.set(null);
                this.alterado.emit();
                this.notify.sucesso(trocouServidor ? 'Configuração salva. O servidor mudou: teste a conexão para confirmar a nova chave.' : 'Configuração do SFTP salva.');
            },
            error: (e) => {
                this.salvando.set(false);
                this.notify.erro(e);
            }
        });
    }

    testar(): void {
        this.testando.set(true);
        this.teste.set(null);
        this.api.testarSftp().subscribe({
            next: (t) => {
                this.testando.set(false);
                this.teste.set(t);
                this.api.sftp().subscribe({ next: (c) => this.cfg.set(c), error: () => undefined });
            },
            error: (e) => {
                this.testando.set(false);
                this.notify.erro(e);
            }
        });
    }

    confirmarChave(t: SftpTeste): void {
        if (!t.fingerprint) {
            return;
        }
        this.confirmando.set(true);
        this.api.confirmarChaveSftp(t.fingerprint, t.tipoChave).subscribe({
            next: (c) => {
                this.confirmando.set(false);
                this.aplicar(c);
                this.teste.set(null);
                this.alterado.emit();
                this.notify.sucesso('Chave do servidor confirmada. O SFTP já pode receber backups.');
            },
            error: (e) => {
                this.confirmando.set(false);
                this.notify.erro(e);
            }
        });
    }

    redefinirChave(): void {
        this.notify.confirmar(
            'Redefinir a chave do servidor',
            'Os envios ficam suspensos até você testar a conexão e confirmar a nova chave. Faça isso só se o computador de destino foi reinstalado ou trocado.',
            () =>
                this.api.redefinirChaveSftp().subscribe({
                    next: (c) => {
                        this.aplicar(c);
                        this.teste.set(null);
                        this.alterado.emit();
                        this.notify.info('Chave esquecida. Use "Testar conexão" para conferir e confirmar a nova.');
                    },
                    error: (e) => this.notify.erro(e)
                }),
            'Redefinir'
        );
    }

    remover(): void {
        this.notify.confirmarExclusao('a configuração do SFTP (servidor, credencial e chave confirmada)', () =>
            this.api.removerSftp().subscribe({
                next: (c) => {
                    this.aplicar(c);
                    this.teste.set(null);
                    this.alterado.emit();
                    this.notify.sucesso('Configuração do SFTP removida.');
                },
                error: (e) => this.notify.erro(e)
            })
        );
    }
}
