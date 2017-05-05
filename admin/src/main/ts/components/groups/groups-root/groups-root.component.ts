import { LoadingService } from '../../../services'
import { GroupsDataService } from '../../../services/groups/groups.data.service'
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core'
import { ActivatedRoute, Data, Router } from '@angular/router'
import { Subscription } from 'rxjs'
import { routing } from '../../../routing/routing.utils'

@Component({
    selector: 'groups-root',
    template: `
        <div class="tabs">
            <button class="tab" *ngFor="let tab of tabs"
                [routerLink]="tab.view"
                routerLinkActive="active">
                {{ tab.label | translate }}
            </button>
        </div>
        <h1>
            <i class="fa fa-users"></i>
            <s5l>groups</s5l>
        </h1>

        <router-outlet></router-outlet>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [GroupsDataService]
})
export class GroupsRoot implements OnInit, OnDestroy {

     // Subscriberts
    private structureSubscriber: Subscription

    // Tabs
    private tabs = [
        { label: "manual.groups", view: "manual" },
        { label: "profile.groups", view: "profile" },
        { label: "functional.groups", view: "functional" }
    ]

    private error: Error

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private cdRef: ChangeDetectorRef,
        private dataService: GroupsDataService,
        private ls: LoadingService) { }

    ngOnInit(): void {
        // Watch selected structure
        this.structureSubscriber = routing.observe(this.route, "data").subscribe((data: Data) => {
            if(data['structure']) {
                this.dataService.structure = data['structure']
                this.cdRef.markForCheck()
            }
        })
    }

    ngOnDestroy(): void {
        this.structureSubscriber.unsubscribe()
    }


    onError(error: Error){
        console.error(error)
        this.error = error
    }
}