import { Component, inject, output, signal, viewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { TagModule } from 'primeng/tag';
import { AuthService } from '@/app/core/auth/auth.service';
import { AtuacaoService } from '@/app/core/http/atuacao.service';
import { ComandoDetalheResponse, ComandoResponse } from '@/app/core/http/atuacao.models';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { dataHora, tempoRelativo } from '@/app/core/format/format';
import { jsonBonito } from '@/app/pages/monitoramento/objeto';
import { COR_STATUS, corResultado, ehCargaCritica, hora, ICONE_STATUS, ROTULO_ORIGEM, ROTULO_RESULTADO, ROTULO_STATUS, rotuloDias, statusFinal } from './atuacao-util';
import { ConfirmacaoCritica } from './confirmacao-critica';

/**
 * Detalhe de um comando da fila: situação, payload, política, resposta e a linha do tempo das
 * tentativas. Permite cancelar (enquanto não terminou) e reenviar.
 */
@Component({
    selector: 'app-comando-detalhe',
    standalone: true,
    imports: [CommonModule, RouterModule, ButtonModule, DialogModule, TagModule, ConfirmacaoCritica],
    template: `
        <p-dialog [visible]="visivel()" (visibleChange)="visivel.set($event)" [modal]="true" [draggable]="false" [dismissableMask]="true" [style]="{ width: '48rem' }" [breakpoints]="{ '960px': '95vw' }" [header]="titulo()">
            @if (d(); as d) {
                @let c = d.comando;
                <div class="flex flex-col gap-5">
                    <div class="flex flex-wrap items-center gap-2">
                        <p-tag [value]="rotuloStatus[c.status]" [severity]="corStatus[c.status]" [icon]="iconeStatus[c.status]" />
                        <p-tag [value]="rotuloOrigem[c.origem]" severity="secondary" />
                        @if (c.resumo) {
                            <span class="text-sm break-words min-w-0">{{ c.resumo }}</span>
                        }
                    </div>

                    <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-3 text-sm">
                        <div class="min-w-0"><span class="text-surface-500">Dispositivo</span><div class="font-medium break-words"><a [routerLink]="['/monitoramento/dispositivos', c.dispositivoId]" class="text-primary" (click)="visivel.set(false)">{{ c.dispositivoNome || '#' + c.dispositivoId }}</a></div></div>
                        <div class="min-w-0"><span class="text-surface-500">Criado</span><div class="font-medium">{{ dh(c.criadoEm) }}@if (c.criadoPor) {<span class="text-surface-500 font-normal break-all"> · {{ c.criadoPor }}</span>}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Tentativas</span><div class="font-medium">{{ c.tentativas }} de {{ c.maxTentativas }} · intervalo {{ c.intervaloTentativaS }} s × {{ c.fatorRecuo }}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Próxima tentativa</span><div class="font-medium">{{ c.proximaTentativaEm && !final(c) ? dh(c.proximaTentativaEm) : '—' }}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Validade</span><div class="font-medium">{{ dh(c.validadeEm) }}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Janela</span><div class="font-medium">{{ c.janelaInicio ? hh(c.janelaInicio) + ' a ' + hh(c.janelaFim) : 'qualquer hora' }} · {{ dias(c.diasSemana) }}</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Confirmação</span><div class="font-medium break-words">{{ c.estrategia || '—' }} · espera {{ c.aguardarRespostaS }} s</div></div>
                        <div class="min-w-0"><span class="text-surface-500">Concluído</span><div class="font-medium">{{ dh(c.concluidoEm) }}</div></div>
                    </div>

                    <div class="rounded-xl border border-surface-200 dark:border-surface-700 p-4 text-sm">
                        <div class="font-semibold mb-2">Downlink</div>
                        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
                            <div class="min-w-0"><span class="text-surface-500">Porta</span><div class="font-mono">{{ c.fPort }}</div></div>
                            <div class="min-w-0"><span class="text-surface-500">Seq</span><div class="font-mono">{{ c.seq ?? '—' }}</div></div>
                            <div class="min-w-0"><span class="text-surface-500">Confirmado (LoRaWAN)</span><div>{{ c.confirmado ? 'sim' : 'não' }}</div></div>
                            <div class="min-w-0 sm:col-span-3"><span class="text-surface-500">Payload (hex)</span><div class="font-mono break-all">{{ hex(c.dadosHex) }}</div></div>
                        </div>
                    </div>

                    @if (c.resposta || c.detalhe) {
                        <div class="grid grid-cols-1 md:grid-cols-2 gap-4 text-sm">
                            @if (c.resposta) {
                                <div class="min-w-0">
                                    <div class="text-surface-500 mb-1">Resposta do equipamento</div>
                                    <pre class="text-xs m-0 whitespace-pre-wrap break-words max-h-56 overflow-auto rounded-lg bg-surface-50 dark:bg-surface-800 p-3">{{ json(c.resposta) }}</pre>
                                </div>
                            }
                            @if (c.detalhe) {
                                <div class="min-w-0">
                                    <div class="text-surface-500 mb-1">Detalhe</div>
                                    <div class="break-words">{{ c.detalhe }}</div>
                                </div>
                            }
                        </div>
                    }

                    <div>
                        <div class="font-semibold mb-3">Tentativas</div>
                        @if (d.tentativas.length === 0) {
                            <div class="text-sm text-surface-500">Nenhuma tentativa ainda.</div>
                        } @else {
                            <ol class="list-none m-0 p-0 flex flex-col">
                                @for (t of d.tentativas; track t.numero; let ultimo = $last) {
                                    <li class="flex gap-3">
                                        <div class="flex flex-col items-center">
                                            <span class="w-8 h-8 shrink-0 rounded-full flex items-center justify-center text-sm font-semibold bg-primary-50 text-primary dark:bg-primary-900/40">{{ t.numero }}</span>
                                            @if (!ultimo) {
                                                <span class="w-px flex-1 bg-surface-200 dark:bg-surface-700 my-1"></span>
                                            }
                                        </div>
                                        <div class="min-w-0 flex-1 pb-4 text-sm">
                                            <div class="flex flex-wrap items-center gap-2">
                                                <p-tag [value]="t.resultado ? rotuloResultado[t.resultado] : 'Em andamento'" [severity]="corRes(t.resultado)" />
                                                <span class="text-surface-500">{{ dh(t.enfileiradoEm) }}</span>
                                            </div>
                                            <div class="mt-1 text-surface-600 dark:text-surface-300">
                                                Transmitido: {{ dh(t.transmitidoEm) }} · Concluída: {{ dh(t.concluidaEm) }}
                                            </div>
                                            @if (t.detalhe) {
                                                <div class="mt-1 break-words">{{ t.detalhe }}</div>
                                            }
                                            @if (t.chirpstackItemId) {
                                                <div class="mt-1 text-xs text-surface-500 font-mono break-all">fila ChirpStack: {{ t.chirpstackItemId }}</div>
                                            }
                                        </div>
                                    </li>
                                }
                            </ol>
                        }
                    </div>
                </div>
            } @else {
                <div class="text-center text-surface-500 py-8"><i class="pi pi-spin pi-spinner mr-2"></i>Carregando...</div>
            }
            <ng-template #footer>
                <div class="flex flex-wrap justify-end gap-2">
                    @if (d(); as d) {
                        @if (auth.podeEscrever()) {
                            @if (!final(d.comando)) {
                                <p-button label="Cancelar comando" icon="pi pi-ban" severity="danger" [outlined]="true" [loading]="agindo()" (onClick)="cancelar(d.comando)" />
                            }
                            <p-button label="Reenviar" icon="pi pi-replay" [outlined]="true" [loading]="agindo()" (onClick)="reenviar(d.comando)" />
                        }
                    }
                    <p-button label="Fechar" severity="secondary" [outlined]="true" (onClick)="visivel.set(false)" />
                </div>
            </ng-template>
        </p-dialog>
        <app-confirmacao-critica />
    `
})
export class ComandoDetalhe {
    auth = inject(AuthService);
    private api = inject(AtuacaoService);
    private notify = inject(NotifyService);
    private critico = viewChild.required(ConfirmacaoCritica);

    /** Avisa a lista para recarregar depois de cancelar ou reenviar. */
    alterado = output<void>();

    visivel = signal(false);
    d = signal<ComandoDetalheResponse | null>(null);
    titulo = signal('Comando');
    agindo = signal(false);

    rotuloStatus = ROTULO_STATUS;
    corStatus = COR_STATUS;
    iconeStatus = ICONE_STATUS;
    rotuloOrigem = ROTULO_ORIGEM;
    rotuloResultado = ROTULO_RESULTADO;
    corRes = corResultado;
    final = (c: ComandoResponse) => statusFinal(c.status);
    dh = dataHora;
    rel = tempoRelativo;
    json = jsonBonito;
    hh = hora;
    dias = rotuloDias;

    abrir(id: number): void {
        this.d.set(null);
        this.titulo.set(`Comando #${id}`);
        this.visivel.set(true);
        this.carregar(id);
    }

    private carregar(id: number): void {
        this.api.comando(id).subscribe({
            next: (d) => {
                this.d.set(d);
                this.titulo.set(`${d.comando.tipoRotulo} · #${d.comando.id}`);
            },
            error: (e) => {
                this.visivel.set(false);
                this.notify.erro(e);
            }
        });
    }

    hex(h?: string | null): string {
        return h ? (h.match(/.{1,2}/g) ?? []).join(' ').toUpperCase() : '—';
    }

    cancelar(c: ComandoResponse): void {
        this.notify.confirmar('Cancelar comando', `Cancelar "${c.tipoRotulo}" em ${c.dispositivoNome || 'dispositivo #' + c.dispositivoId}? Se já estiver na fila do ChirpStack, sai de lá também.`, () => {
            this.agindo.set(true);
            this.api.cancelarComando(c.id).subscribe({
                next: () => {
                    this.agindo.set(false);
                    this.notify.sucesso('Comando cancelado.');
                    this.carregar(c.id);
                    this.alterado.emit();
                },
                error: (e) => {
                    this.agindo.set(false);
                    this.notify.erro(e);
                }
            });
        }, 'Cancelar comando');
    }

    reenviar(c: ComandoResponse, confirmarCritico = false): void {
        this.agindo.set(true);
        this.api.reenviarComando(c.id, confirmarCritico).subscribe({
            next: (novo) => {
                this.agindo.set(false);
                this.notify.sucesso(`Reenviado como comando #${novo.id}.`);
                this.alterado.emit();
                this.d.set(null);
                this.titulo.set(`Comando #${novo.id}`);
                this.carregar(novo.id);
            },
            error: (e) => {
                this.agindo.set(false);

                if (ehCargaCritica(e)) {
                    this.critico().pedir(NotifyService.mensagem(e), () => this.reenviar(c, true));
                } else {
                    this.notify.erro(e);
                }
            }
        });
    }
}
