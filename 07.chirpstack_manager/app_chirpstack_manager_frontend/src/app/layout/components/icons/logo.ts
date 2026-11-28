import { Component } from '@angular/core';

/**
 * Símbolo do ChirpStack Manager (brand/chirpstack-manager-simbolo.svg): a antena da marca
 * ChirpStack em branco sobre o azul do sistema, com o filete teal sob o mastro.
 */
@Component({
    selector: 'logo',
    standalone: true,
    template: `
        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 40 40" fill="none" aria-hidden="true">
            <defs>
                <linearGradient id="cm-logo-g" x1="0" y1="0" x2="40" y2="40" gradientUnits="userSpaceOnUse">
                    <stop offset="0" stop-color="#3b82f6" />
                    <stop offset="1" stop-color="#1d4ed8" />
                </linearGradient>
            </defs>
            <rect width="40" height="40" rx="12" fill="url(#cm-logo-g)" />
            <path d="M20 21V31" stroke="#fff" stroke-width="3" stroke-linecap="round" />
            <circle cx="20" cy="18" r="3" fill="#fff" />
            <path d="M14.3 12.3A8 8 0 0 0 14.3 23.7M25.7 12.3A8 8 0 0 1 25.7 23.7" stroke="#fff" stroke-opacity=".85" stroke-width="2.4" stroke-linecap="round" />
            <path d="M10.4 8.4A13.5 13.5 0 0 0 10.4 27.6M29.6 8.4A13.5 13.5 0 0 1 29.6 27.6" stroke="#fff" stroke-opacity=".5" stroke-width="2.4" stroke-linecap="round" />
            <rect x="14.6" y="33.2" width="10.8" height="2" rx="1" fill="#2dd4bf" />
        </svg>
    `
})
export class Logo {}
