import { Component, computed, input, model, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { reduzirImagem } from '@/app/shared/imagem/reduzir-imagem';

/** Maior arquivo aceito antes da redução (a miniatura gravada fica em dezenas de KB). */
const MAX_ORIGINAL_MB = 15;

/**
 * Foto de perfil: mostra a foto (ou as iniciais), escolhe uma imagem — reduzida no navegador a
 * 512 px em JPEG antes de ir para a API — e permite remover. Mesmo comportamento dos demais sistemas.
 */
@Component({
    selector: 'foto-perfil',
    standalone: true,
    imports: [ButtonModule],
    template: `
        <div class="flex items-center gap-4">
            <div class="shrink-0 rounded-full overflow-hidden flex items-center justify-center bg-primary text-primary-contrast font-semibold" [style.width.px]="tamanho()" [style.height.px]="tamanho()" [style.fontSize.px]="tamanho() / 2.8">
                @if (foto()) {
                    <img [src]="foto()" alt="Foto de perfil" class="w-full h-full object-cover" />
                } @else {
                    <span>{{ iniciais() }}</span>
                }
            </div>
            <div class="flex flex-col gap-2 min-w-0">
                <div class="flex flex-wrap gap-2">
                    <p-button [label]="foto() ? 'Trocar foto' : 'Escolher foto'" icon="pi pi-image" size="small" [outlined]="true" [loading]="processando()" (onClick)="arquivo.click()" [disabled]="desabilitado()" />
                    @if (foto()) {
                        <p-button label="Remover" icon="pi pi-trash" size="small" severity="danger" [text]="true" (onClick)="foto.set(null)" [disabled]="desabilitado()" />
                    }
                </div>
                <small class="text-surface-500 whitespace-nowrap">JPG, PNG ou WEBP; reduzida automaticamente.</small>
                @if (erro()) {
                    <small class="text-red-500">{{ erro() }}</small>
                }
            </div>
            <input #arquivo type="file" accept="image/jpeg,image/png,image/webp" class="hidden" (change)="escolher($event)" />
        </div>
    `
})
export class FotoPerfil {
    /** Data URI da foto (null = sem foto). */
    foto = model<string | null | undefined>(null);
    nome = input<string>('');
    tamanho = input<number>(72);
    desabilitado = input<boolean>(false);

    processando = signal(false);
    erro = signal('');

    readonly iniciais = computed(() => {
        const partes = (this.nome() ?? '').trim().split(/\s+/).filter(Boolean);
        if (partes.length === 0) return '?';
        const primeira = partes[0][0] ?? '';
        const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
        return (primeira + ultima).toUpperCase();
    });

    async escolher(evento: Event): Promise<void> {
        const campo = evento.target as HTMLInputElement;
        const arquivo = campo.files?.[0];
        campo.value = '';
        this.erro.set('');
        if (!arquivo) return;
        if (!/^image\/(jpeg|png|webp)$/.test(arquivo.type)) {
            this.erro.set('Escolha uma imagem JPG, PNG ou WEBP.');
            return;
        }
        if (arquivo.size > MAX_ORIGINAL_MB * 1024 * 1024) {
            this.erro.set(`Imagem muito grande (máximo ${MAX_ORIGINAL_MB} MB).`);
            return;
        }
        this.processando.set(true);
        try {
            this.foto.set(await reduzirImagem(arquivo));
        } catch (e) {
            this.erro.set(e instanceof Error ? e.message : 'Não foi possível abrir a imagem.');
        } finally {
            this.processando.set(false);
        }
    }
}
