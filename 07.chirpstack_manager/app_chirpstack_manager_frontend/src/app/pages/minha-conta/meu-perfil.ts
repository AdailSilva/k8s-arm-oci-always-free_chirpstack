import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { TagModule } from 'primeng/tag';
import { AuthService } from '@/app/core/auth/auth.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { DadosCadastro, UsuarioSistema } from '@/app/core/http/models';
import { dataHora } from '@/app/core/format/format';
import { PERFIS_ACESSO, rotulo } from '@/app/core/format/options';
import { CadastroUsuario } from '@/app/shared/campos/cadastro-usuario';
import { FotoPerfil } from '@/app/shared/campos/foto-perfil';
import { RegrasSenha, SENHA_FORTE } from '@/app/shared/campos/regras-senha';
import { travaEdicao } from '@/app/core/edicao/trava-edicao';
import { PageHeader } from '@/app/shared/visual/page-header';

/**
 * Minha conta: foto, nome, contato e endereço do próprio usuário e troca de senha (mesmo padrão dos
 * demais sistemas). E-mail e perfil só o administrador altera. A troca obrigatória do primeiro
 * acesso tem tela própria (/auth/trocar-senha).
 */
@Component({
    selector: 'app-meu-perfil',
    standalone: true,
    imports: [CommonModule, FormsModule, ButtonModule, InputTextModule, PasswordModule, TagModule, PageHeader, CadastroUsuario, FotoPerfil, RegrasSenha],
    template: `
        <app-page-header title="Meu perfil" subtitle="Seus dados e o acesso ao ChirpStack Manager" icon="pi pi-user" />

        <div class="grid grid-cols-12 gap-6">
            <div class="col-span-12 xl:col-span-8 flex flex-col gap-6">
                <div class="card mb-0! flex flex-col gap-5">
                    <div class="font-semibold text-lg">Dados pessoais</div>
                    <foto-perfil [(foto)]="cadastro.fotoPerfil" [nome]="nome" [tamanho]="88" />
                    <div class="grid grid-cols-12 gap-4">
                        <div class="col-span-12 md:col-span-6">
                            <label class="block font-medium mb-2" for="mp-nome">Nome</label>
                            <input pInputText id="mp-nome" class="w-full" [(ngModel)]="nome" maxlength="120" placeholder="Seu nome completo" />
                        </div>
                        <div class="col-span-12 md:col-span-6">
                            <label class="block font-medium mb-2" for="mp-email">E-mail</label>
                            <input pInputText id="mp-email" class="w-full" [value]="auth.usuario()?.email ?? ''" disabled placeholder="Alterado só pelo administrador" />
                        </div>
                    </div>
                    <div class="flex flex-wrap gap-x-6 gap-y-2 text-sm">
                        <div class="whitespace-nowrap"><span class="text-surface-500">Perfil:</span> <p-tag [value]="perfil()" class="ml-1" /></div>
                        <div class="whitespace-nowrap"><span class="text-surface-500">Último acesso:</span> {{ data(auth.usuario()?.ultimoLogin) }}</div>
                    </div>
                </div>

                <div class="card mb-0! flex flex-col gap-5">
                    <div class="font-semibold text-lg">Contato e endereço</div>
                    <cadastro-usuario [(dados)]="cadastro" />
                    <div><p-button label="Salvar meus dados" icon="pi pi-check" (onClick)="salvarDados()" [loading]="salvandoDados()" [disabled]="!nome.trim()" /></div>
                </div>
            </div>

            <div class="col-span-12 xl:col-span-4">
                <div class="card mb-0! flex flex-col gap-5">
                    <div class="font-semibold text-lg">Alterar senha</div>
                    <div>
                        <label class="block font-medium mb-2" for="mp-atual">Senha atual</label>
                        <p-password inputId="mp-atual" [(ngModel)]="senhaAtual" [feedback]="false" [toggleMask]="true" styleClass="w-full" inputStyleClass="w-full" autocomplete="current-password" placeholder="Sua senha de hoje" />
                    </div>
                    <div>
                        <label class="block font-medium mb-2" for="mp-nova">Nova senha</label>
                        <p-password inputId="mp-nova" [(ngModel)]="novaSenha" [feedback]="false" [toggleMask]="true" styleClass="w-full" inputStyleClass="w-full" autocomplete="new-password" placeholder="De 8 a 72, letras e números" />
                    </div>
                    <div>
                        <label class="block font-medium mb-2" for="mp-confirma">Confirmar nova senha</label>
                        <p-password inputId="mp-confirma" [(ngModel)]="confirmacao" [feedback]="false" [toggleMask]="true" styleClass="w-full" inputStyleClass="w-full" autocomplete="new-password" placeholder="Repita a nova senha" />
                    </div>
                    <regras-senha [senha]="novaSenha" [confirmacao]="confirmacao" />
                    <div><p-button label="Alterar senha" icon="pi pi-key" (onClick)="trocarSenha()" [loading]="salvando()" /></div>
                    <small class="text-surface-500">Ao trocar a senha, as sessões abertas são encerradas e você entra de novo com a nova senha.</small>
                </div>
            </div>
        </div>
    `
})
export class MeuPerfil implements OnInit {
    auth = inject(AuthService);
    private notify = inject(NotifyService);

    nome = '';
    cadastro: DadosCadastro = {};
    senhaAtual = '';
    novaSenha = '';
    confirmacao = '';
    salvando = signal(false);
    salvandoDados = signal(false);
    data = dataHora;
    /** Alterações não salvas: sair da tela pergunta antes ("Deseja realmente sair?"). */
    private readonly trava = travaEdicao({ modelo: () => [this.nome, this.cadastro, this.senhaAtual, this.novaSenha, this.confirmacao] });

    ngOnInit(): void {
        const atual = this.auth.usuario();
        if (atual) this.preencher(atual);
        this.auth.me().subscribe({ next: (u) => this.preencher(u) });
    }

    perfil(): string {
        return rotulo(PERFIS_ACESSO, this.auth.perfil());
    }

    salvarDados(): void {
        this.salvandoDados.set(true);
        this.auth.atualizarMe(this.nome.trim(), this.cadastro).subscribe({
            next: (u) => {
                this.salvandoDados.set(false);
                this.preencher(u);
                this.notify.sucesso('Seus dados foram atualizados.');
            },
            error: (e) => {
                this.salvandoDados.set(false);
                this.notify.erro(e);
            }
        });
    }

    trocarSenha(): void {
        if (!SENHA_FORTE.test(this.novaSenha)) {
            this.notify.aviso('A nova senha deve ter de 8 a 72 caracteres, com letras e números.');
            return;
        }
        if (this.novaSenha !== this.confirmacao) {
            this.notify.aviso('A confirmação não confere.');
            return;
        }
        this.salvando.set(true);
        this.auth.trocarSenha(this.senhaAtual, this.novaSenha).subscribe({
            next: () => {
                this.salvando.set(false);
                this.notify.sucesso('Senha alterada. Entre novamente.');
                this.auth.sessaoEncerrada('SENHA_ALTERADA');
            },
            error: (e) => {
                this.salvando.set(false);
                this.notify.erro(e);
            }
        });
    }

    private preencher(u: UsuarioSistema): void {
        this.nome = u.nome;
        this.cadastro = { ...(u.cadastro ?? {}) };
        this.trava.marcarLimpo();
    }
}
