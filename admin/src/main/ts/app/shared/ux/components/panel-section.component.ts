import { Component, Input, ContentChild, TemplateRef } from '@angular/core'

@Component({
    selector: 'panel-section',
    template: `
        <section class="panel-section">
            <div class="panel-section-header" [class.foldable]="folded !== null">
                {{ sectionTitle | translate }}
                <ng-template [ngTemplateOutlet]="otherActions" ></ng-template>
                <i class="opener" *ngIf="folded !== null"
                    (click)="folded !== null ? folded=!folded : null"
                    [class.opened]="!folded"></i>
            </div>
            <div class="panel-section-content" *ngIf="!folded">
                <ng-content></ng-content>
            </div>
        </section>
    `,
    styles: [`
        .panel-section {}
        .panel-section-header {
            font-size: 1.1em;
            padding: 10px 10px;
        }
        .panel-section-header.foldable .opener{
            cursor: pointer;
        }
        .panel-section-content {
            padding: 15px;
        }
    `]
})
export class PanelSectionComponent {
    @Input("section-title") sectionTitle : string
    @Input() folded : boolean = null
    @ContentChild(TemplateRef) otherActions:TemplateRef<any>;
}