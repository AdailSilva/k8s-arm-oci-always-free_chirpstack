import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';
import { PasswordModule } from 'primeng/password';
import { TagModule } from 'primeng/tag';
import { SistemaService } from '@/app/core/http/sistema.service';
import { ContaEmail } from '@/app/core/http/models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora } from '@/app/core/format/format';
import { travaEdicao } from '@/app/core/edicao/trava-edicao';
import { PageHeader } from '@/app/shared/visual/page-header';

/**
 * Sistema → Conta de e-mail (ADMIN). Troca a senha de app do Google usada para enviar alertas e os
 * links de redefinição de senha. A senha nunca volta para a tela.
 */
@Component({
    selector: 'app-conta-email',
    standalone: true,
    imports: [CommonModule, FormsModule, ButtonModule, CheckboxModule, MessageModule, PasswordModule, TagModule, PageHeader],
    template: `
        <app-page-header title="Conta de e-mail" subtitle="Conta que envia os alertas e os links de redefinição de senha" icon="pi pi-envelope">
            <p-button icon="pi pi-send" label="Enviar e-mail de teste" severity="secondary" [outlined]="true" [loading]="enviando()" (onClick)="enviarTeste()" />
        </app-page-header>

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div class="card mb-0!">
                <div class="font-semibold text-lg mb-4">Situação</div>
                @if (c(); as c) {
                    <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
                        <div class="min-w-0"><span class="text-surface-500">Conta</span><div class="font-medium break-all">{{ c.conta }}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Servidor</span><div class="font-medium break-all">{{ c.servidor }}:{{ c.porta }}</div></div>
                        <div class="min-w-0">
                            <span class="text-surface-500">Senha em uso</span>
                            <div>
                                @switch (c.origem) {
                                    @case ('SISTEMA') { <p-tag value="Cadastrada nesta tela" severity="success" /> }
                                    @case ('SISTEMA_ILEGIVEL') { <p-tag value="Ilegível: cadastre de novo" severity="danger" /> }
                                    @default { <p-tag value="Da instalação" severity="secondary" /> }
                                }
                            </div>
                        </div>
                        <div class="min-w-0"><span class="text-surface-500">Alterada em</span><div class="font-medium">{{ c.alteradoEm ? dh(c.alteradoEm) + (c.alteradoPor ? ' por ' + c.alteradoPor : '') : '—' }}</div></div>
                    </div>
                    @if (!c.chaveDedicada) {
                        <p-message severity="warn" styleClass="w-full mt-4" text="Sem chave de cifragem dedicada (APP_CHAVE_CIFRAGEM): a senha é protegida com uma chave derivada do segredo do JWT." />
                    }
                    @if (c.origem === 'SISTEMA') {
                        <div class="mt-4">
                            <p-button label="Voltar à senha da instalação" icon="pi pi-undo" severity="secondary" [text]="true" (onClick)="remover()" />
                        </div>
                    }
                }
            </div>

            <div class="card mb-0!">
                <div class="font-semibold text-lg mb-1">Nova senha de app</div>
                <p class="text-sm text-surface-500 mt-0 mb-4">Gere em Conta Google → Segurança → Senhas de app. Os espaços são ignorados.</p>
                <div class="flex flex-col gap-4">
                    <div class="min-w-0">
                        <label class="block font-medium mb-2">Senha de app</label>
                        <p-password [(ngModel)]="senha" [feedback]="false" [toggleMask]="true" placeholder="abcd efgh ijkl mnop" styleClass="w-full" inputStyleClass="w-full" autocomplete="new-password" />
                    </div>
                    <div class="min-w-0">
                        <label class="block font-medium mb-2">Confirmação</label>
                        <p-password [(ngModel)]="confirmacao" [feedback]="false" [toggleMask]="true" placeholder="Repita a senha" styleClass="w-full" inputStyleClass="w-full" autocomplete="new-password" />
                    </div>
                    <label class="flex items-center gap-2 text-sm">
                        <p-checkbox [(ngModel)]="testarAntes" [binary]="true" />
                        Testar a conexão antes de salvar
                    </label>
                    <div class="flex flex-wrap gap-2 justify-end">
                        <p-button label="Só testar" icon="pi pi-bolt" severity="secondary" [outlined]="true" [loading]="testando()" (onClick)="testar()" />
                        <p-button label="Salvar" icon="pi pi-check" [loading]="salvando()" (onClick)="salvar()" />
                    </div>
                </div>
            </div>
        </div>
    `
})
export class ContaEmailPage implements OnInit {
    private api = inject(SistemaService);
    private notify = inject(NotifyService);

    c = signal<ContaEmail | null>(null);
    senha = '';
    confirmacao = '';
    testarAntes = true;
    salvando = signal(false);
    testando = signal(false);
    enviando = signal(false);
    dh = dataHora;
    /** Alterações não salvas: sair da tela pergunta antes ("Deseja realmente sair?"). */
    private readonly trava = travaEdicao({ modelo: () => [this.senha, this.confirmacao] });

    ngOnInit(): void {
        this.api.contaEmail().subscribe({ next: (c) => this.c.set(c), error: (e) => this.notify.erro(e) });
    }

    salvar(): void {
        if (!this.senha.trim()) {
            this.notify.aviso('Informe a nova senha de app.');
            return;
        }
        this.salvando.set(true);
        this.api.alterarSenhaEmail(this.senha, this.confirmacao, this.testarAntes).subscribe({
            next: (c) => {
                this.c.set(c);
                this.senha = '';
                this.confirmacao = '';
                this.trava.marcarLimpo();
                this.salvando.set(false);
                this.notify.sucesso('Senha de app salva.');
            },
            error: (e) => {
                this.salvando.set(false);
                this.notify.erro(e);
            }
        });
    }

    testar(): void {
        this.testando.set(true);
        this.api.testarSenhaEmail(this.senha.trim() || null).subscribe({
            next: () => {
                this.testando.set(false);
                this.notify.sucesso(this.senha.trim() ? 'O servidor aceitou a senha informada.' : 'O servidor aceitou a senha em uso.');
            },
            error: (e) => {
                this.testando.set(false);
                this.notify.erro(e);
            }
        });
    }

    enviarTeste(): void {
        this.enviando.set(true);
        this.api.enviarTesteEmail().subscribe({
            next: (r) => {
                this.enviando.set(false);
                this.notify.sucesso(`E-mail de teste enviado para ${r.destinatario}.`);
            },
            error: (e) => {
                this.enviando.set(false);
                this.notify.erro(e);
            }
        });
    }

    remover(): void {
        this.notify.confirmar('Voltar à senha da instalação', 'Apagar a senha cadastrada nesta tela? O envio volta a usar a senha configurada no servidor.', () =>
            this.api.removerSenhaEmail().subscribe({
                next: (c) => {
                    this.c.set(c);
                    this.notify.sucesso('Senha cadastrada removida.');
                },
                error: (e) => this.notify.erro(e)
            })
        );
    }
}
