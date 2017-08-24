import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, 
    OnInit } from '@angular/core'
import { ActivatedRoute, Data, NavigationEnd, Router } from '@angular/router'
import { Subscription } from 'rxjs/Subscription'

import { StructureModel, UserModel } from '../core/store'
import { routing } from '../core/services/routing.service'
import { UserlistFiltersService, SpinnerService, NotifyService } from '../core/services'
import { UsersStore } from './users.store'

@Component({
    selector: 'users-root',
    template: `
        <div class="flex-header">
            <h1><i class="fa fa-user"></i><s5l>users.title</s5l></h1>
            <button [routerLink]="['create']"
                [class.hidden]="router.isActive('/admin/' + usersStore.structure?.id + '/users/create', true)">
                <s5l>create.user</s5l>
                <i class="fa fa-user-plus"></i>
            </button>
        </div>
        <side-layout (closeCompanion)="closeCompanion()"
                [showCompanion]="!router.isActive('/admin/' + usersStore.structure?.id + '/users', true)">
            <div side-card>
                <user-list [userlist]="usersStore.structure.users.data"
                    (listCompanionChange)="openCompanionView($event)"
                    [selectedUser]="usersStore.user"
                    (selectedUserChange)="openUserDetail($event)"></user-list>
            </div>
            <div side-companion>
                <router-outlet></router-outlet>
            </div>
        </side-layout>
    `,
    providers: [UsersStore],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsersComponent implements OnInit, OnDestroy {

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private cdRef: ChangeDetectorRef,
        public usersStore: UsersStore,
        private listFilters: UserlistFiltersService,
        private spinner: SpinnerService,
        private ns: NotifyService){}

    private dataSubscriber : Subscription
    private routerSubscriber : Subscription

    ngOnInit(): void {
        this.dataSubscriber = routing.observe(this.route, "data").subscribe((data: Data) => {
            console.log(data);
            if(data['structure']) {
                let structure: StructureModel = data['structure']
                this.usersStore.structure = structure
                this.initFilters(structure)
                this.cdRef.detectChanges()
            }
        })

        this.routerSubscriber = this.router.events.subscribe(e => {
            if(e instanceof NavigationEnd)
                this.cdRef.markForCheck()
        })
    }

    ngOnDestroy(): void {
        this.dataSubscriber.unsubscribe()
        this.routerSubscriber.unsubscribe()
    }

    closeCompanion() {
        this.router.navigate(['../users'], {relativeTo: this.route }).then(() => {
            this.usersStore.user = null
        })
    }

    openUserDetail(user) {
        this.usersStore.user = user
        this.spinner.perform('portal-content', this.router.navigate([user.id], {relativeTo: this.route }))
    }

    openCompanionView(view) {
        this.router.navigate([view], { relativeTo: this.route })
    }

    private initFilters(structure: StructureModel) {
        this.listFilters.resetFilters()
        
        this.listFilters.setClasses(structure.classes)
        this.listFilters.setSources(structure.sources)
        this.listFilters.setFunctions(structure.aafFunctions)
        this.listFilters.setProfiles(structure.profiles.map(p => p.name))
        this.listFilters.setFunctionalGroups(
            structure.groups.data.filter(g => g.type === 'FunctionalGroup').map(g => g.name))
        this.listFilters.setManualGroups(
            structure.groups.data.filter(g => g.type === 'ManualGroup').map(g => g.name))
    }
    
}