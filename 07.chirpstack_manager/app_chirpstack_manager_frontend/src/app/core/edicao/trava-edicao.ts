import { DestroyRef, Directive, afterEveryRender, contentChildren, inject, input } from '@angular/core';
import { NgModel } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { EdicaoPendenteService } from '@/app/core/edicao/edicao-pendente.service';

/**
 * Como a trava descobre se o formulário está alterado:
 * - {@code modelo}: devolve o modelo editável; a trava guarda um retrato (JSON) quando o
 *   formulário abre/carrega/salva e compara com o atual. Serve para ngModel e formulários
 *   reativos ({@code () => form.getRawValue()}).
 * - {@code sujo}: a tela já controla a própria alteração (ex.: {@code alterado()}).
 * - {@code aberta}: enquanto false, não há edição (diálogo fechado, modo edição desligado,
 *   dados ainda carregando). Ao virar true, o retrato é tirado.
 */
export interface IOpcoesTravaEdicao {
    modelo?: () => unknown;
    sujo?: () => boolean;
    aberta?: () => boolean;
}

/**
 * Retrato comparável do modelo. Vazio, null e undefined contam igual (digitar e apagar não
 * deixa o formulário "alterado"); Set vira lista ordenada; Date vira ISO (toJSON).
 */
export function serializarEdicao(valor: unknown): string {
    return JSON.stringify(valor ?? null, (_chave, v) => {
        if (v === undefined || v === '') return null;
        if (v instanceof Set) return Array.from(v).sort();
        if (v instanceof Map) return Array.from(v.entries());

        return v;
    });
}

/**
 * Trava de edição de UM formulário (o {@code TravaEdicao} do app mobile): registra-se no
 * {@link EdicaoPendenteService} enquanto houver alteração não salva e sai ao salvar, ao
 * descartar ou ao ser destruída. Reavalia após cada renderização (toda digitação passa por
 * uma) e sob demanda, quando alguém vai decidir se pode sair.
 *
 * <p>Crie com {@link travaEdicao} num inicializador de campo do componente.</p>
 */
export class TravaEdicao {
    private readonly _svc = inject(EdicaoPendenteService);
    private readonly _dono = {};
    private _base: string | null = null;
    private _descartada = false;
    private _sujo = false;
    private _geracao: number;

    constructor(private readonly _opcoes: IOpcoesTravaEdicao) {
        this._geracao = this._svc.geracao;
        const sincronizar = () => this.sincronizar();

        this._svc.adicionarSincronizador(sincronizar);
        afterEveryRender(sincronizar);
        inject(DestroyRef).onDestroy(() => {
            this._svc.removerSincronizador(sincronizar);
            this._svc.remover(this._dono);
        });
    }

    /** Há alteração não salva agora? */
    sujo(): boolean {
        this.sincronizar();

        return this._sujo;
    }

    /** Reavalia o formulário e atualiza o registro global. */
    sincronizar(): void {
        const geracaoMudou = this._geracao !== this._svc.geracao;

        this._geracao = this._svc.geracao;
        if (!this._aberta()) {
            this._base = null;
            this._descartada = false;
            this._definir(false);

            return;
        }
        // O usuário confirmou sair sem salvar (ou a sessão acabou): o estado atual é descartado.
        if (geracaoMudou) {
            this.descartar();

            return;
        }
        if (this._opcoes.sujo) {
            const sujo = this._opcoes.sujo();

            if (this._descartada) {
                if (!sujo) this._descartada = false;
                this._definir(false);

                return;
            }
            this._definir(sujo);

            return;
        }
        const atual = serializarEdicao(this._opcoes.modelo?.());

        if (this._base === null) {
            this._base = atual;
            this._definir(false);

            return;
        }
        this._definir(atual !== this._base);
    }

    /** Depois de salvar (ou recarregar do servidor): o estado atual passa a ser o "sem alteração". */
    marcarLimpo(): void {
        this._descartada = false;
        if (!this._opcoes.sujo) this._base = this._aberta() ? serializarEdicao(this._opcoes.modelo?.()) : null;
        this._definir(false);
    }

    /** Aceita o estado atual como descartado (usuário confirmou sair/fechar sem salvar). */
    descartar(): void {
        if (this._opcoes.sujo) this._descartada = this._aberta() && this._opcoes.sujo();
        else this._base = this._aberta() ? serializarEdicao(this._opcoes.modelo?.()) : null;
        this._definir(false);
    }

    /**
     * Executa uma mudança que NÃO é edição do usuário (preenchimento vindo do servidor, valor
     * derivado). Se o formulário estava limpo, continua limpo depois dela.
     */
    absorver<T>(mudanca: () => T): T {
        const limpo = !this.sujo();
        const r = mudanca();

        if (limpo) this.marcarLimpo();

        return r;
    }

    /**
     * Pergunta antes de fechar/cancelar este formulário ("Fechar sem salvar?" por padrão).
     * Devolve true quando pode fechar — nada alterado ou o usuário confirmou.
     */
    async podeFechar(janela = true): Promise<boolean> {
        if (!this.sujo()) return true;
        const ok = await this._svc.confirmar(janela);

        if (ok) this.descartar();

        return ok;
    }

    private _aberta(): boolean {
        return this._opcoes.aberta ? this._opcoes.aberta() : true;
    }

    private _definir(sujo: boolean): void {
        this._sujo = sujo;
        if (sujo) this._svc.registrar(this._dono);
        else this._svc.remover(this._dono);
    }
}

/** Cria a trava de edição do formulário (chamar num inicializador de campo / construtor). */
export function travaEdicao(opcoes: IOpcoesTravaEdicao): TravaEdicao {
    return new TravaEdicao(opcoes);
}

/**
 * Trava para formulários dentro de {@code p-dialog}: basta {@code csmTravaEdicao} no p-dialog.
 *
 * <p>O modelo comparado é, por padrão, o valor de TODOS os {@code ngModel} do conteúdo do diálogo
 * (não é preciso dizer qual é o objeto do formulário); {@code [csmTravaEdicao]="form"} usa o objeto
 * informado. O retrato é tirado quando o diálogo abre. Fechar pelo X, Esc ou clique na máscara com
 * alterações pergunta "Fechar sem salvar?" e mantém o diálogo aberto em "Continuar editando" (o
 * {@code close} do Dialog é interceptado: o {@code (onHide)} não permite cancelar). Botões
 * "Cancelar" do rodapé usam o mesmo caminho: {@code #trava="travaEdicao"} … {@code (onClick)="trava.fechar()"}.</p>
 */
@Directive({
    selector: 'p-dialog[csmTravaEdicao]',
    standalone: true,
    exportAs: 'travaEdicao'
})
export class TravaEdicaoJanela {
    /** Modelo editável do diálogo; vazio = os valores dos ngModel do conteúdo. */
    readonly csmTravaEdicao = input<unknown>();

    private readonly _campos = contentChildren(NgModel, { descendants: true });
    private readonly _dialog = inject(Dialog, { self: true });
    private readonly _fecharOriginal = this._dialog.close.bind(this._dialog);

    readonly trava = travaEdicao({
        modelo: () => {
            const m = this.csmTravaEdicao();
            return m !== undefined && m !== '' ? m : this._campos().map((c) => c.model);
        },
        aberta: () => !!this._dialog.visible
    });

    constructor() {
        this._dialog.close = (evento: Event) => {
            evento?.preventDefault?.();
            this.fechar();
        };
    }

    /** Fecha o diálogo, perguntando antes se houver alterações não salvas. */
    fechar(): void {
        void this.trava.podeFechar(true).then((ok) => {
            if (ok) this._fecharOriginal(new Event('close'));
        });
    }

    /** O estado atual do diálogo passa a ser o "sem alteração" (chamar ao salvar). */
    marcarLimpo(): void {
        this.trava.marcarLimpo();
    }
}
