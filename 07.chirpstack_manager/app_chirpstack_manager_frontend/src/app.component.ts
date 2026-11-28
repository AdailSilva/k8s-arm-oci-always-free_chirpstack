import { Component, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { EdicaoPendenteDialog } from '@/app/layout/components/app.edicao-pendente-dialog';
import { PreferenciasService } from '@/app/core/preferencias/preferencias.service';

@Component({
    selector: 'app-root',
    standalone: true,
    imports: [RouterModule, ToastModule, ConfirmDialogModule, EdicaoPendenteDialog],
    template: `
        <p-toast position="top-right" />
        <p-confirmdialog [style]="{ width: '30rem' }" [breakpoints]="{ '640px': '95vw' }" />
        <!-- "Deseja realmente sair?" (alterações não salvas): único, acima de qualquer diálogo. -->
        <app-edicao-pendente-dialog />
        <router-outlet></router-outlet>
    `
})
export class AppComponent {
    /** Preferências de aparência no servidor: valem desde o início, inclusive no login. */
    private readonly preferencias = inject(PreferenciasService);
}
