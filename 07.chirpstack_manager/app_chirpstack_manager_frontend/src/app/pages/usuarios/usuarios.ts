import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { TooltipModule } from 'primeng/tooltip';
import { map } from 'rxjs';
import { SistemaService, UsuarioSistemaForm } from '@/app/core/http/sistema.service';
import { DadosCadastro, UsuarioSistema } from '@/app/core/http/models';
import { AuthService } from '@/app/core/auth/auth.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { formatarTelefone } from '@/app/core/format/localidades';
import { PERFIS_ACESSO, rotulo } from '@/app/core/format/options';
import { emailValido, MSG_EMAIL_INVALIDO } from '@/app/core/format/validacao';
import { CadastroUsuario } from '@/app/shared/campos/cadastro-usuario';
import { FotoPerfil } from '@/app/shared/campos/foto-perfil';
import { RegrasSenha, SENHA_FORTE } from '@/app/shared/campos/regras-senha';
import { ConsultaPagina, ListaPaginada } from '@/app/shared/tabela/lista-paginada';
import { TravaEdicaoJanela } from '@/app/core/edicao/trava-edicao';
import { PageHeader } from '@/app/shared/visual/page-header';

/**
 * Usuários do painel (ChirpStack Manager). Somente ADMIN.
 * Mesmo padrão dos demais sistemas: cadastro completo (foto, contato e endereço com CEP) e
 * criação por convite — o usuário recebe o link por e-mail e define a própria senha. Com senha
 * definida pelo administrador, o usuário confirma o e-mail antes do primeiro acesso.
 */
@Component({
    selector: 'app-usuarios',
    standalone: true,
    imports: [TravaEdicaoJanela, CommonModule, FormsModule, ButtonModule, DialogModule, IconFieldModule, InputIconModule, InputTextModule, PasswordModule, SelectModule, TableModule, TabsModule, TagModule, ToggleSwitchModule, TooltipModule, PageHeader, CadastroUsuario, FotoPerfil, RegrasSenha],
    template: `
        <app-page-header title="Usuários do painel" subtitle="Quem acessa o ChirpStack Manager e com qual perfil" icon="pi pi-user-edit">
            <p-button label="Novo usuário" icon="pi pi-plus" (onClick)="novo()" />
        </app-page-header>

        <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
            <div class="card mb-0! flex gap-3 items-start"><i class="pi pi-shield text-red-500 text-xl! mt-1"></i><div><b>Administrador</b><div class="text-sm text-surface-500">Tudo, inclusive tenants, usuários do ChirpStack, usuários do painel e auditoria.</div></div></div>
            <div class="card mb-0! flex gap-3 items-start"><i class="pi pi-wrench text-amber-500 text-xl! mt-1"></i><div><b>Operador</b><div class="text-sm text-surface-500">Cria, altera e exclui aplicações, dispositivos, gateways, perfis, multicast e integrações; edita dispositivos monitorados e regras de alerta e reconhece alertas.</div></div></div>
            <div class="card mb-0! flex gap-3 items-start"><i class="pi pi-eye text-sky-500 text-xl! mt-1"></i><div><b>Visualizador</b><div class="text-sm text-surface-500">Somente leitura (inclui monitoramento, leituras e alertas), sem acesso a chaves, sessões e segredos.</div></div></div>
        </div>

        <div class="card">
            <p-table [value]="itens()" [lazy]="true" (onLazyLoad)="onLazy($event)" [paginator]="true" [rows]="rows()" [first]="first()" [totalRecords]="total()" [loading]="carregando()" [tableStyle]="{ 'min-width': '68rem' }" [rowHover]="true">
                <ng-template #caption>
                    <div class="flex justify-end">
                        <p-iconfield>
                            <p-inputicon class="pi pi-search" />
                            <input pInputText type="text" placeholder="Nome ou e-mail" [ngModel]="busca()" (ngModelChange)="pesquisar($event)" />
                        </p-iconfield>
                    </div>
                </ng-template>
                <ng-template #header>
                    <tr>
                        <th>Nome</th>
                        <th>E-mail</th>
                        <th>Telefone</th>
                        <th>Perfil</th>
                        <th>Situação</th>
                        <th>Último acesso</th>
                        <th class="w-44"></th>
                    </tr>
                </ng-template>
                <ng-template #body let-u>
                    <tr>
                        <td>
                            <div class="flex items-center gap-3 whitespace-nowrap">
                                <span class="w-9 h-9 shrink-0 rounded-full overflow-hidden bg-primary text-primary-contrast flex items-center justify-center text-sm font-semibold">
                                    @if (u.cadastro?.fotoPerfil) {
                                        <img [src]="u.cadastro.fotoPerfil" alt="" class="w-full h-full object-cover" />
                                    } @else {
                                        {{ iniciais(u.nome) }}
                                    }
                                </span>
                                <div class="leading-tight">
                                    <div class="font-medium">
                                        {{ u.nome }}
                                        @if (u.email === auth.usuario()?.email) {
                                            <p-tag value="você" severity="info" class="ml-1" />
                                        }
                                    </div>
                                    @if (u.cadastro?.cargo) {
                                        <div class="text-xs text-surface-500">{{ u.cadastro.cargo }}</div>
                                    }
                                </div>
                            </div>
                        </td>
                        <td>{{ u.email }}</td>
                        <td class="whitespace-nowrap">{{ telefone(u.cadastro?.telefone) || '—' }}</td>
                        <td><p-tag [value]="perfil(u.perfil)" [severity]="u.perfil === 'ADMIN' ? 'danger' : u.perfil === 'OPERADOR' ? 'warn' : 'info'" /></td>
                        <td>
                            <div class="flex flex-wrap gap-1">
                                @if (u.bloqueado) {
                                    <p-tag value="Bloqueado" severity="danger" icon="pi pi-lock" />
                                } @else {
                                    <p-tag [value]="u.ativo ? 'Ativo' : 'Inativo'" [severity]="u.ativo ? 'success' : 'secondary'" />
                                }
                                @if (u.convitePendente) {
                                    <p-tag value="Convite pendente" severity="warn" icon="pi pi-envelope" />
                                } @else if (u.emailVerificado === false) {
                                    <p-tag value="E-mail não confirmado" severity="warn" icon="pi pi-envelope" />
                                }
                            </div>
                        </td>
                        <td class="whitespace-nowrap" [title]="data(u.ultimoLogin)">{{ relativo(u.ultimoLogin) }}</td>
                        <td class="text-right whitespace-nowrap">
                            @if (u.ativo && (u.convitePendente || u.emailVerificado === false)) {
                                <p-button icon="pi pi-send" [text]="true" [rounded]="true" [pTooltip]="u.convitePendente ? 'Reenviar convite' : 'Reenviar confirmação de e-mail'" (onClick)="reenviar(u)" />
                            }
                            @if (u.bloqueado) {
                                <p-button icon="pi pi-lock-open" [text]="true" [rounded]="true" pTooltip="Desbloquear" (onClick)="desbloquear(u)" />
                            }
                            <p-button icon="pi pi-pencil" [text]="true" [rounded]="true" pTooltip="Editar" (onClick)="editar(u)" />
                            <p-button icon="pi pi-trash" severity="danger" [text]="true" [rounded]="true" pTooltip="Excluir" (onClick)="excluir(u)" [disabled]="u.email === auth.usuario()?.email" />
                        </td>
                    </tr>
                </ng-template>
                <ng-template #emptymessage>
                    <tr><td colspan="7" class="text-center py-8 text-surface-500">Nenhum usuário.</td></tr>
                </ng-template>
            </p-table>
        </div>

        <p-dialog [csmTravaEdicao]="[form, cadastro]" #trava="travaEdicao" [(visible)]="dialogo" [header]="editando() ? 'Editar usuário' : 'Novo usuário'" [modal]="true" [style]="{ width: '52rem' }" [breakpoints]="{ '900px': '95vw' }" [contentStyle]="{ 'min-height': '28rem' }">
            <p-tabs [value]="aba()" (valueChange)="aba.set($any($event))">
                <p-tablist>
                    <p-tab value="dados"><i class="pi pi-user mr-2"></i>Dados</p-tab>
                    <p-tab value="endereco"><i class="pi pi-map-marker mr-2"></i>Contato e endereço</p-tab>
                    <p-tab value="acesso"><i class="pi pi-key mr-2"></i>Acesso</p-tab>
                </p-tablist>
                <p-tabpanels>
                    <p-tabpanel value="dados">
                        <div class="flex flex-col gap-5 pt-2">
                            <foto-perfil [(foto)]="cadastro.fotoPerfil" [nome]="form.nome" />
                            <div class="grid grid-cols-12 gap-4">
                                <div class="col-span-12 md:col-span-6">
                                    <label class="block font-medium mb-2" for="usu-nome">Nome <span class="text-red-500">*</span></label>
                                    <input pInputText id="usu-nome" class="w-full" [(ngModel)]="form.nome" maxlength="120" placeholder="Nome completo" />
                                </div>
                                <div class="col-span-12 md:col-span-6">
                                    <label class="block font-medium mb-2" for="usu-email">E-mail <span class="text-red-500">*</span></label>
                                    <input pInputText id="usu-email" type="email" class="w-full" [(ngModel)]="form.email" maxlength="160" autocomplete="off" placeholder="nome@empresa.com.br" [invalid]="!emailOk()" />
                                    @if (!emailOk()) {
                                        <small class="block mt-1 text-red-500">E-mail inválido.</small>
                                    } @else if (editando() && emailMudou()) {
                                        <small class="block mt-1 text-amber-600 dark:text-amber-400">O novo e-mail precisará ser confirmado pelo link enviado a ele.</small>
                                    }
                                </div>
                                <div class="col-span-12 md:col-span-6">
                                    <label class="block font-medium mb-2" for="usu-perfil">Perfil</label>
                                    <p-select inputId="usu-perfil" [options]="perfis" optionLabel="label" optionValue="value" [(ngModel)]="form.perfil" placeholder="Selecione o perfil" styleClass="w-full" appendTo="body" />
                                </div>
                                <div class="col-span-12 md:col-span-6 flex items-end pb-2">
                                    <label class="flex items-center gap-3"><p-toggleswitch [(ngModel)]="form.ativo" /> Ativo</label>
                                </div>
                            </div>
                        </div>
                    </p-tabpanel>
                    <p-tabpanel value="endereco">
                        <div class="pt-2">
                            <cadastro-usuario [(dados)]="cadastro" [comObservacoes]="true" />
                        </div>
                    </p-tabpanel>
                    <p-tabpanel value="acesso">
                        <div class="flex flex-col gap-5 pt-2">
                            @if (!editando()) {
                                <label class="flex items-start gap-3">
                                    <p-toggleswitch [(ngModel)]="form.enviarConvite" />
                                    <span>
                                        <b>Enviar convite por e-mail</b>
                                        <span class="block text-sm text-surface-500">O usuário recebe um link (válido por 72 h) e define a própria senha. Recomendado.</span>
                                    </span>
                                </label>
                            } @else if (editando()?.convitePendente) {
                                <div class="text-sm text-surface-500 flex items-center gap-2"><i class="pi pi-envelope"></i>Convite pendente: o usuário ainda não definiu a senha. Use "Reenviar convite" na lista, se preciso.</div>
                            }
                            @if (editando() || !form.enviarConvite) {
                                <div>
                                    <label class="block font-medium mb-2" for="usu-senha">{{ editando() ? 'Redefinir senha (opcional)' : 'Senha' }} @if (!editando()) {<span class="text-red-500">*</span>}</label>
                                    <p-password inputId="usu-senha" [(ngModel)]="senha" [toggleMask]="true" [feedback]="false" styleClass="w-full" inputStyleClass="w-full" appendTo="body" autocomplete="new-password" placeholder="De 8 a 72 caracteres, com letras e números" />
                                    <div class="mt-3"><regras-senha [senha]="senha" /></div>
                                    @if (!editando()) {
                                        <small class="block mt-3 text-surface-500">Com a senha definida aqui, o usuário recebe um link para confirmar o e-mail e só entra depois de confirmar.</small>
                                    }
                                </div>
                            }
                        </div>
                    </p-tabpanel>
                </p-tabpanels>
            </p-tabs>
            <ng-template #footer>
                <p-button label="Cancelar" severity="secondary" [outlined]="true" (onClick)="trava.fechar()" />
                <p-button [label]="!editando() && form.enviarConvite ? 'Salvar e enviar convite' : 'Salvar'" icon="pi pi-check" (onClick)="salvar()" [loading]="salvando()" [disabled]="!emailOk()" />
            </ng-template>
        </p-dialog>
    `
})
export class Usuarios extends ListaPaginada<UsuarioSistema> {
    private api = inject(SistemaService);
    private notify = inject(NotifyService);
    auth = inject(AuthService);

    perfis = PERFIS_ACESSO;
    dialogo = signal(false);
    editando = signal<UsuarioSistema | null>(null);
    salvando = signal(false);
    aba = signal('dados');
    form: UsuarioSistemaForm = this.formVazio();
    cadastro: DadosCadastro = {};
    senha = '';
    data = dataHora;
    relativo = tempoRelativo;
    telefone = formatarTelefone;

    protected consultar(q: ConsultaPagina) {
        return this.api.listarUsuarios(q.search, Math.floor(q.offset / q.limit), q.limit).pipe(map((r) => r));
    }

    protected override aoFalhar(e: unknown): void {
        this.notify.erro(e);
    }

    emailOk(): boolean {
        return emailValido(this.form.email);
    }

    emailMudou(): boolean {
        const u = this.editando();
        return !!u && this.form.email.trim().toLowerCase() !== u.email.toLowerCase();
    }

    perfil(p: string): string {
        return rotulo(PERFIS_ACESSO, p);
    }

    iniciais(nome: string): string {
        const partes = (nome ?? '').trim().split(/\s+/).filter(Boolean);
        if (partes.length === 0) return '?';
        return ((partes[0][0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
    }

    novo(): void {
        this.form = this.formVazio();
        this.cadastro = {};
        this.senha = '';
        this.aba.set('dados');
        this.editando.set(null);
        this.dialogo.set(true);
    }

    editar(u: UsuarioSistema): void {
        this.form = { nome: u.nome, email: u.email, perfil: u.perfil, ativo: u.ativo };
        this.cadastro = { ...(u.cadastro ?? {}) };
        this.senha = '';
        this.aba.set('dados');
        this.editando.set(u);
        this.dialogo.set(true);
    }

    salvar(): void {
        const u = this.editando();
        if (!this.form.nome.trim() || !this.form.email.trim()) {
            this.aba.set('dados');
            this.notify.aviso('Informe o nome e o e-mail.');
            return;
        }
        if (!emailValido(this.form.email)) {
            this.aba.set('dados');
            this.notify.aviso(MSG_EMAIL_INVALIDO);
            return;
        }
        const exigeSenha = !u && !this.form.enviarConvite;
        if ((exigeSenha || this.senha) && !SENHA_FORTE.test(this.senha)) {
            this.aba.set('acesso');
            this.notify.aviso('A senha deve ter de 8 a 72 caracteres, com letras e números.');
            return;
        }
        this.salvando.set(true);
        const cadastro = this.cadastro;
        const op = u
            ? this.api.atualizarUsuario(u.id, { ...this.form, novaSenha: this.senha || undefined, cadastro })
            : this.api.criarUsuario({ ...this.form, senha: this.form.enviarConvite ? undefined : this.senha, cadastro });
        op.subscribe({
            next: (salvo) => {
                this.salvando.set(false);
                this.dialogo.set(false);
                if (!u) {
                    this.notify.sucesso(salvo.convitePendente ? `Usuário criado. Convite enviado para ${salvo.email}.` : `Usuário criado. Link de confirmação enviado para ${salvo.email}.`);
                } else {
                    this.notify.sucesso(this.emailMudouPara(u, salvo) ? `Usuário atualizado. O novo e-mail (${salvo.email}) precisa ser confirmado.` : 'Usuário atualizado.');
                }
                this.carregar();
            },
            error: (e) => {
                this.salvando.set(false);
                this.notify.erro(e);
            }
        });
    }

    reenviar(u: UsuarioSistema): void {
        this.api.reenviarLinkUsuario(u.id).subscribe({
            next: (r) => {
                this.notify.sucesso(u.convitePendente ? `Convite reenviado para ${r.para}.` : `Link de confirmação reenviado para ${r.para}.`);
                this.carregar();
            },
            error: (e) => this.notify.erro(e)
        });
    }

    desbloquear(u: UsuarioSistema): void {
        this.api.desbloquearUsuario(u.id).subscribe({
            next: () => {
                this.notify.sucesso('Usuário desbloqueado.');
                this.carregar();
            },
            error: (e) => this.notify.erro(e)
        });
    }

    excluir(u: UsuarioSistema): void {
        this.notify.confirmarExclusao(`o usuário ${u.email}`, () =>
            this.api.excluirUsuario(u.id).subscribe({
                next: () => {
                    this.notify.sucesso('Usuário excluído.');
                    this.carregar();
                },
                error: (e) => this.notify.erro(e)
            })
        );
    }

    private emailMudouPara(antes: UsuarioSistema, depois: UsuarioSistema): boolean {
        return antes.email.toLowerCase() !== depois.email.toLowerCase() && depois.emailVerificado === false;
    }

    private formVazio(): UsuarioSistemaForm {
        return { nome: '', email: '', perfil: 'VISUALIZADOR', ativo: true, enviarConvite: true };
    }
}
