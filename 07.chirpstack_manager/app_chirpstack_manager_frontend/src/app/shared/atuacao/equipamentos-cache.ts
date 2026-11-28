import { inject, Injectable, signal } from '@angular/core';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { ComandoSuportado, EquipamentoResponse, ModeloResponse } from '@/app/core/http/atuacao.models';
import { PerifericoOpcao } from './comando-form';

/**
 * Equipamentos (comandos e periféricos) dos dispositivos escolhidos nos formulários de cenas,
 * agendamentos e automações. Carrega sob demanda e guarda enquanto a tela estiver aberta.
 */
@Injectable()
export class EquipamentosCache {
    private api = inject(AtuacaoService);
    private mapa = signal<Record<number, EquipamentoResponse | 'carregando' | 'erro'>>({});
    private modelosCarregados = false;
    /** União dos comandos de todos os modelos (para automações "no mesmo dispositivo do alerta"). */
    readonly todosComandos = signal<ComandoSuportado[]>([]);

    garantir(id: number | null | undefined, recarregar = false): void {
        if (!id) {
            return;
        }
        const atual = this.mapa()[id];

        if (atual && atual !== 'erro' && !recarregar) {
            return;
        }
        this.mapa.update((m) => ({ ...m, [id]: 'carregando' }));
        this.api.equipamento(id).subscribe({
            next: (e) => this.mapa.update((m) => ({ ...m, [id]: e })),
            error: () => this.mapa.update((m) => ({ ...m, [id]: 'erro' }))
        });
    }

    equipamento(id: number | null | undefined): EquipamentoResponse | null {
        const e = id ? this.mapa()[id] : null;

        return e && typeof e === 'object' ? e : null;
    }

    carregando(id: number | null | undefined): boolean {
        return !!id && this.mapa()[id] === 'carregando';
    }

    comandos(id: number | null | undefined): ComandoSuportado[] {
        return this.equipamento(id)?.comandos ?? [];
    }

    perifericos(id: number | null | undefined): PerifericoOpcao[] {
        return this.equipamento(id)?.perifericos ?? [];
    }

    /** Mensagem para o seletor de comandos quando não há o que oferecer. */
    semComandos(id: number | null | undefined): string {
        if (!id) {
            return 'Escolha o dispositivo primeiro.';
        }

        if (this.carregando(id)) {
            return 'Carregando comandos...';
        }
        const e = this.equipamento(id);

        return e && !e.configurado ? 'Configure o equipamento deste dispositivo (aba Controle).' : 'Nenhum comando disponível.';
    }

    garantirModelos(): void {
        if (this.modelosCarregados) {
            return;
        }
        this.modelosCarregados = true;
        this.api.modelos().subscribe({
            next: (ms: ModeloResponse[]) => {
                const vistos = new Map<string, ComandoSuportado>();

                ms.forEach((m) => m.comandos.forEach((c) => vistos.has(c.tipo) || vistos.set(c.tipo, c)));
                this.todosComandos.set([...vistos.values()]);
            },
            error: () => (this.modelosCarregados = false)
        });
    }
}
