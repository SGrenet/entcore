import { Component, Input, ViewChild, ChangeDetectorRef, ChangeDetectionStrategy } from '@angular/core'
import { AbstractControl } from '@angular/forms'
import { Router } from '@angular/router'

import { AbstractSection } from '../abstract.section'
import { LoadingService, UserListService } from '../../../../../../services'
import { globalStore, StructureCollection, UserModel } from '../../../../../../store'

@Component({
    selector: 'user-structures-section',
    template: `
        <panel-section section-title="users.details.section.structures" [folded]="true">
            <button (click)="showStructuresLightbox = true">
                <s5l>add.structure</s5l><i class="fa fa-plus-circle"></i>
            </button>
            <light-box class="inner-list"
                    [show]="showStructuresLightbox" (onClose)="showStructuresLightbox = false">
                <div class="padded">
                    <h3><s5l>add.structure</s5l></h3>
                    <list-component class="inner-list"
                        [model]="structureCollection.data"
                        [inputFilter]="filterByInput"
                        [filters]="filterStructures"
                        searchPlaceholder="search.structure"
                        sort="name"
                        (inputChange)="inputFilter = $event"
                        [isDisabled]="disableStructure"
                        (onSelect)="ls.perform($event.id, user?.addStructure($event.id), 0)">
                        <ng-template let-item>
                            <span class="display-name">
                                {{ item?.name }}
                            </span>
                        </ng-template>
                    </list-component>
                </div>
            </light-box>
            <ul class="actions-list">
                <li *ngFor="let structure of user.visibleStructures()">
                    <span>{{ structure.name }}</span>
                    <i  class="fa fa-times action" (click)="ls.perform(structure.id, user?.removeStructure(structure.id), 0)"
                        [tooltip]="'delete.this.structure' | translate"
                        [ngClass]="{ disabled: ls.isLoading(structure.id)}"></i>
                </li>
                <li *ngFor="let structure of user.invisibleStructures()">
                    <span>{{ structure.name }}</span>
                </li>
            </ul>
        </panel-section>
    `,
    inputs: ['user', 'structure'],
    providers: [UserListService]
})
export class UserStructuresSection extends AbstractSection {

    constructor(private userListService: UserListService,
            private router: Router,
            protected ls: LoadingService,
            protected cdRef: ChangeDetectorRef) {
        super()
    }

    private structureCollection : StructureCollection = globalStore.structures

    @ViewChild("codeInput") codeInput : AbstractControl

    protected onUserChange(){}

    private disableStructure = (s) => {
        return this.ls.isLoading(s.id)
    }

    private isVisibleStructure = (s) => {
        return this.structureCollection.data.find(struct => struct.id === s)
    }

    // Filters
    private _inputFilter = ""
    set inputFilter(filter: string) {
        this._inputFilter = filter
    }
    get inputFilter() {
        return this._inputFilter
    }
    private filterByInput = (s: {id: string, name: string}) => {
        if(!this.inputFilter) return true
        return `${s.name}`.toLowerCase().indexOf(this.inputFilter.toLowerCase()) >= 0
    }
    private filterStructures = (s: {id: string, name: string}) => {
        return !this.user.structures.find(struct => s.id === struct.id)
    }

    //Routing
    private routeToStructure(structureId: string) {
        this.router.navigate(['/admin', structureId, 'users', this.user.id])
    }

}