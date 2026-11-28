import { Component, inject, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputMaskModule } from 'primeng/inputmask';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { DadosCadastro } from '@/app/core/http/models';
import { SistemaService } from '@/app/core/http/sistema.service';
import { separarTipoLogradouro, TIPOS_LOGRADOURO, UFS } from '@/app/core/format/localidades';

/**
 * Contato e endereço do usuário (mesmo conjunto dos demais sistemas), usado no cadastro de
 * Usuários e em Meu perfil. O CEP completo preenche logradouro, bairro, cidade e UF.
 */
@Component({
    selector: 'cadastro-usuario',
    standalone: true,
    imports: [FormsModule, ButtonModule, InputMaskModule, InputTextModule, SelectModule, TextareaModule],
    template: `
        <div class="grid grid-cols-12 gap-4">
            <div class="col-span-12 md:col-span-6">
                <label class="block font-medium mb-2" for="cad-telefone">Telefone</label>
                <p-inputmask inputId="cad-telefone" mask="(99) 9999-9999?9" [unmask]="true" [autoClear]="false" placeholder="(88) 99999-0000" styleClass="w-full" [(ngModel)]="dados().telefone" (ngModelChange)="alterar()" />
            </div>
            <div class="col-span-12 md:col-span-6">
                <label class="block font-medium mb-2" for="cad-cargo">Cargo</label>
                <input pInputText id="cad-cargo" class="w-full" maxlength="80" placeholder="Ex.: Técnico de campo" [(ngModel)]="dados().cargo" (ngModelChange)="alterar()" />
            </div>

            <div class="col-span-12 md:col-span-4">
                <label class="block font-medium mb-2" for="cad-cep">CEP</label>
                <div class="flex gap-2">
                    <p-inputmask inputId="cad-cep" mask="99999-999" [unmask]="true" [autoClear]="false" placeholder="62320-000" styleClass="w-full" class="flex-1 min-w-0" [(ngModel)]="dados().cep" (ngModelChange)="cepAlterado($event)" />
                    <p-button icon="pi pi-search" [outlined]="true" [loading]="buscando()" (onClick)="buscarCep()" ariaLabel="Buscar CEP" />
                </div>
                @if (avisoCep()) {
                    <small class="block mt-1 text-amber-600 dark:text-amber-400">{{ avisoCep() }}</small>
                }
            </div>
            <div class="col-span-12 md:col-span-3">
                <label class="block font-medium mb-2" for="cad-tipo">Tipo</label>
                <p-select inputId="cad-tipo" [options]="tipos" optionLabel="label" optionValue="value" placeholder="Rua, Avenida..." [showClear]="true" styleClass="w-full" appendTo="body" [(ngModel)]="dados().enderecoTipo" (ngModelChange)="alterar()" />
            </div>
            <div class="col-span-12 md:col-span-5">
                <label class="block font-medium mb-2" for="cad-endereco">Logradouro</label>
                <input pInputText id="cad-endereco" class="w-full" maxlength="255" placeholder="Nome da rua, avenida..." [(ngModel)]="dados().endereco" (ngModelChange)="alterar()" />
            </div>

            <div class="col-span-12 md:col-span-3">
                <label class="block font-medium mb-2" for="cad-numero">Número</label>
                <input pInputText id="cad-numero" class="w-full" maxlength="20" placeholder="Ex.: 775 ou S/N" [(ngModel)]="dados().numero" (ngModelChange)="alterar()" />
            </div>
            <div class="col-span-12 md:col-span-4">
                <label class="block font-medium mb-2" for="cad-complemento">Complemento</label>
                <input pInputText id="cad-complemento" class="w-full" maxlength="255" placeholder="Sala, bloco, apto..." [(ngModel)]="dados().complemento" (ngModelChange)="alterar()" />
            </div>
            <div class="col-span-12 md:col-span-5">
                <label class="block font-medium mb-2" for="cad-bairro">Bairro</label>
                <input pInputText id="cad-bairro" class="w-full" maxlength="150" placeholder="Ex.: Centro" [(ngModel)]="dados().bairro" (ngModelChange)="alterar()" />
            </div>

            <div class="col-span-12 md:col-span-7">
                <label class="block font-medium mb-2" for="cad-cidade">Cidade</label>
                <input pInputText id="cad-cidade" class="w-full" maxlength="100" placeholder="Ex.: Tianguá" [(ngModel)]="dados().cidade" (ngModelChange)="alterar()" />
            </div>
            <div class="col-span-12 md:col-span-5">
                <label class="block font-medium mb-2" for="cad-uf">UF</label>
                <p-select inputId="cad-uf" [options]="ufs" optionLabel="label" optionValue="value" placeholder="Selecione o estado" [filter]="true" filterBy="label" [showClear]="true" styleClass="w-full" appendTo="body" [(ngModel)]="dados().uf" (ngModelChange)="alterar()" />
            </div>

            @if (comObservacoes()) {
                <div class="col-span-12">
                    <label class="block font-medium mb-2" for="cad-obs">Observações</label>
                    <textarea pTextarea id="cad-obs" rows="3" class="w-full" maxlength="4000" placeholder="Anotações internas sobre este usuário (opcional)" [(ngModel)]="dados().observacoes" (ngModelChange)="alterar()"></textarea>
                </div>
            }
        </div>
    `
})
export class CadastroUsuario {
    private api = inject(SistemaService);

    dados = model.required<DadosCadastro>();
    comObservacoes = input<boolean>(false);

    readonly tipos = TIPOS_LOGRADOURO;
    readonly ufs = UFS;
    buscando = signal(false);
    avisoCep = signal('');
    private ultimoCepBuscado = '';

    /** Reemite o objeto para quem usa [(dados)] perceber a mudança. */
    alterar(): void {
        this.dados.set({ ...this.dados() });
    }

    cepAlterado(valor: string | null): void {
        this.alterar();
        const d = (valor ?? '').replace(/\D/g, '');
        if (d.length === 8 && d !== this.ultimoCepBuscado) {
            this.buscarCep();
        }
    }

    buscarCep(): void {
        const d = (this.dados().cep ?? '').replace(/\D/g, '');
        this.avisoCep.set('');
        if (d.length !== 8) {
            this.avisoCep.set('Informe os 8 dígitos do CEP.');
            return;
        }
        this.ultimoCepBuscado = d;
        this.buscando.set(true);
        this.api.buscarCep(d).subscribe({
            next: (r) => {
                this.buscando.set(false);
                if (!r.encontrado) {
                    this.avisoCep.set('CEP não encontrado. Preencha o endereço manualmente.');
                    return;
                }
                const { tipo, nome } = separarTipoLogradouro(r.logradouro);
                const atual = this.dados();
                this.dados.set({
                    ...atual,
                    enderecoTipo: tipo ?? atual.enderecoTipo,
                    endereco: nome || atual.endereco,
                    complemento: atual.complemento || r.complemento || atual.complemento,
                    bairro: r.bairro || atual.bairro,
                    cidade: r.cidade || atual.cidade,
                    uf: r.uf || atual.uf
                });
            },
            error: () => {
                this.buscando.set(false);
                this.avisoCep.set('Consulta de CEP indisponível. Preencha o endereço manualmente.');
            }
        });
    }
}
