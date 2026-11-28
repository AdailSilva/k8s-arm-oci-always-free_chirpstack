import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { InputMaskModule } from 'primeng/inputmask';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { environment } from '@/environments/environment';
import { SuporteEnvio } from '@/app/core/http/models';
import { AuthService } from '@/app/core/auth/auth.service';
import { NotifyService } from '@/app/core/notificacao/notify.service';
import { travaEdicao } from '@/app/core/edicao/trava-edicao';
import { PageHeader } from '@/app/shared/visual/page-header';

interface Faq {
    pergunta: string;
    resposta: string;
}

const FAQS: Faq[] = [
    {
        pergunta: 'Um dispositivo parou de aparecer como on-line. O que conferir?',
        resposta:
            'Em Monitoramento › Dispositivos monitorados veja a hora da última leitura. Depois confira, em Rede LoRaWAN › Gateways, se o gateway que atende a região está on-line. Sem uplinks recentes, verifique a alimentação e a antena do dispositivo.'
    },
    {
        pergunta: 'Por que não consigo criar ou alterar aplicações e gateways?',
        resposta: 'O perfil Visualizador é somente leitura. Peça a um administrador para mudar seu perfil para Operador em Sistema › Usuários do painel.'
    },
    {
        pergunta: 'Fui desconectado sem clicar em Sair. Por quê?',
        resposta:
            'A sessão termina por inatividade, pelo tempo máximo, ao fechar o navegador, no fim do horário de acesso ou quando um administrador a encerra. A tela de login sempre mostra o motivo.'
    },
    {
        pergunta: 'Como recebo os alertas por e-mail?',
        resposta: 'Ao criar a regra em Monitoramento › Alertas, informe os e-mails que devem ser avisados. A conta de e-mail do sistema é configurada pelo administrador.'
    },
    {
        pergunta: 'Esqueci minha senha.',
        resposta: 'Na tela de login use "Esqueci a senha". Você recebe um link de uso único por e-mail para criar uma nova.'
    }
];

/**
 * SUPORTE (todos os perfis), mesmo padrão dos demais sistemas: o chamado vai por e-mail à equipe,
 * com o e-mail do usuário como "responder para"; telefone e WhatsApp opcionais para retorno.
 */
@Component({
    selector: 'app-suporte',
    standalone: true,
    imports: [CommonModule, FormsModule, ButtonModule, InputMaskModule, InputTextModule, TextareaModule, PageHeader],
    template: `
        <app-page-header title="Suporte" subtitle="Fale com a equipe ou consulte as perguntas frequentes" icon="pi pi-question-circle" />

        <div class="grid grid-cols-1 xl:grid-cols-12 gap-6">
            <div class="xl:col-span-8 card mb-0!">
                <div class="font-semibold text-lg mb-1">Abrir um chamado</div>
                <p class="text-sm text-surface-500 m-0 mb-5">A resposta chega no seu e-mail (<b class="[overflow-wrap:anywhere]">{{ auth.usuario()?.email }}</b>).</p>
                <div class="flex flex-col gap-5">
                    <div>
                        <label class="block font-medium mb-2" for="supAssunto">Assunto</label>
                        <input id="supAssunto" pInputText class="w-full" [(ngModel)]="assunto" maxlength="150" placeholder="Resuma o problema ou a dúvida, ex.: Gateway da sede off-line" />
                    </div>
                    <div class="grid grid-cols-1 sm:grid-cols-2 gap-5">
                        <div>
                            <label class="block font-medium mb-2" for="supTel">Telefone <span class="text-surface-400 font-normal">(opcional)</span></label>
                            <p-inputmask inputId="supTel" mask="(99) 9999-9999" [(ngModel)]="telefone" [autoClear]="false" placeholder="(00) 0000-0000" styleClass="w-full" />
                        </div>
                        <div>
                            <label class="block font-medium mb-2" for="supWhats">Celular / WhatsApp <span class="text-surface-400 font-normal">(opcional)</span></label>
                            <p-inputmask inputId="supWhats" mask="(99) 99999-9999" [(ngModel)]="whatsapp" [autoClear]="false" placeholder="(00) 90000-0000" styleClass="w-full" />
                        </div>
                    </div>
                    <div>
                        <label class="block font-medium mb-2" for="supMsg">Mensagem</label>
                        <textarea id="supMsg" pTextarea class="w-full" rows="7" [(ngModel)]="mensagem" maxlength="5000" placeholder="Descreva com detalhes: o que tentou fazer, em qual tela, o que apareceu e desde quando acontece"></textarea>
                        <div class="text-xs text-surface-500 text-right mt-1">{{ mensagem.length }}/5000</div>
                    </div>
                    <div class="flex justify-end">
                        <p-button label="Enviar" icon="pi pi-send" (onClick)="enviar()" [loading]="enviando()" [disabled]="enviando() || !assunto.trim() || !mensagem.trim()" />
                    </div>
                </div>
            </div>

            <div class="xl:col-span-4 flex flex-col gap-6">
                <div class="card mb-0! flex flex-col gap-4">
                    <div class="font-semibold text-lg">Outros canais</div>
                    <a [href]="linkWhats" target="_blank" rel="noopener noreferrer" class="flex items-start gap-3 no-underline text-inherit hover:text-primary">
                        <i class="pi pi-whatsapp text-green-500 text-xl! mt-0.5"></i>
                        <span><span class="block font-medium">WhatsApp</span><span class="text-sm text-surface-500">{{ whatsSuporte }}</span></span>
                    </a>
                    <a href="https://k8s.adailsilva.com.br" target="_blank" rel="noopener noreferrer" class="flex items-start gap-3 no-underline text-inherit hover:text-primary">
                        <i class="pi pi-globe text-primary text-xl! mt-0.5"></i>
                        <span><span class="block font-medium">Site</span><span class="text-sm text-surface-500">k8s.adailsilva.com.br</span></span>
                    </a>
                    <div class="flex items-start gap-3">
                        <i class="pi pi-clock text-primary text-xl! mt-0.5"></i>
                        <span><span class="block font-medium">Atendimento</span><span class="text-sm text-surface-500">Segunda a sexta, das 8h às 18h</span></span>
                    </div>
                </div>
                @if (ultimo(); as u) {
                    <div class="card mb-0! flex items-start gap-3">
                        <i class="pi pi-check-circle text-green-500 text-xl! mt-0.5"></i>
                        <span class="text-sm">Último chamado enviado às <b>{{ u }}</b>. Responderemos pelo seu e-mail.</span>
                    </div>
                }
            </div>

            <div class="xl:col-span-12 card mb-0!">
                <div class="font-semibold text-lg mb-3">Perguntas frequentes</div>
                <div class="divide-y divide-surface-200 dark:divide-surface-700">
                    @for (f of faqs; track f.pergunta; let i = $index) {
                        <div class="py-3">
                            <button type="button" class="w-full flex items-center justify-between gap-4 text-left bg-transparent border-0 cursor-pointer p-0 text-inherit" (click)="alternar(i)">
                                <span class="font-medium">{{ f.pergunta }}</span>
                                <i class="pi pi-chevron-down text-xs transition-transform shrink-0" [class.rotate-180]="aberta() === i"></i>
                            </button>
                            @if (aberta() === i) {
                                <p class="text-surface-500 text-sm mt-3 mb-0 leading-relaxed">{{ f.resposta }}</p>
                            }
                        </div>
                    }
                </div>
            </div>
        </div>
    `
})
export class Suporte {
    readonly auth = inject(AuthService);
    private http = inject(HttpClient);
    private notify = inject(NotifyService);

    assunto = '';
    mensagem = '';
    telefone = '';
    whatsapp = '';
    readonly enviando = signal(false);
    readonly ultimo = signal<string | null>(null);
    readonly aberta = signal<number | null>(null);
    readonly faqs = FAQS;
    readonly whatsSuporte = '(89) 99412-9256';
    readonly linkWhats = 'https://wa.me/5589994129256';

    /** Mensagem escrita e não enviada: sair da tela pergunta antes. */
    private readonly trava = travaEdicao({ modelo: () => [this.assunto, this.mensagem, this.telefone, this.whatsapp] });

    alternar(i: number): void {
        this.aberta.set(this.aberta() === i ? null : i);
    }

    enviar(): void {
        const corpo: SuporteEnvio = {
            assunto: this.assunto.trim(),
            mensagem: this.mensagem.trim(),
            telefone: digitosOuVazio(this.telefone, 10) ? this.telefone : '',
            whatsapp: digitosOuVazio(this.whatsapp, 11) ? this.whatsapp : ''
        };
        if (this.telefone && !corpo.telefone) {
            this.notify.aviso('Telefone incompleto. Corrija ou apague o campo.');
            return;
        }
        if (this.whatsapp && !corpo.whatsapp) {
            this.notify.aviso('WhatsApp incompleto. Corrija ou apague o campo.');
            return;
        }
        this.enviando.set(true);
        this.http.post<{ destinatario: string; enviadoEm: string }>(`${environment.apiUrl}/conta/suporte`, corpo).subscribe({
            next: (r) => {
                this.enviando.set(false);
                this.assunto = this.mensagem = this.telefone = this.whatsapp = '';
                this.trava.marcarLimpo();
                this.ultimo.set(new Date(r.enviadoEm).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }));
                this.notify.sucesso('Mensagem enviada. Responderemos pelo seu e-mail.');
            },
            error: (e) => {
                this.enviando.set(false);
                this.notify.erro(e);
            }
        });
    }
}

/** Vazio ou com o mínimo de dígitos (máscara completa). */
function digitosOuVazio(v: string, minimo: number): boolean {
    const d = (v ?? '').replace(/\D/g, '');
    return d.length === 0 || d.length >= minimo;
}
