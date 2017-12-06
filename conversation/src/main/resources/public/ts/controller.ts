import { ng, notify, idiom as lang, template, skin, Document, $, _ } from 'entcore';
import { Mail, User, UserFolder, sorts, quota, Conversation, Trash, SystemFolder } from './model';

export let conversationController = ng.controller('ConversationController', [
    '$scope', '$timeout', '$compile', '$sanitize', 'model', 'route', function ($scope, $timeout, $compile, $sanitize, model, route) {
        $scope.state = {
            selectAll: false,
            current: undefined,
            newItem: undefined
        };

        $scope.conversation = Conversation.instance;
    
        route({
            readMail: async function (params) {
                Conversation.instance.folders.openFolder('inbox');
                template.open('page', 'folders');
                $scope.readMail(new Mail(params.mailId));
                await Conversation.instance.sync();
                $scope.$apply();
            },
            writeMail: async function (params) {
                Conversation.instance.folders.openFolder('inbox');
                await Conversation.instance.sync();
                
                template.open('page', 'folders');
                let user = new User(params.userId)
                await user.findData();
                template.open('main', 'mail-actions/write-mail');
                $scope.addUser(user);
                $scope.$apply();
            },
            inbox: async () => {
                template.open('page', 'folders');
                await Conversation.instance.folders.openFolder('inbox');
                await Conversation.instance.sync();
                await Conversation.instance.folders.draft.sync();
                $scope.$apply();
            }
        });

        $scope.lang = lang;
        $scope.notify = notify;
        $scope.folders = Conversation.instance.folders;
        $scope.userFolders = Conversation.instance.userFolders;
        $scope.users = { list: Conversation.instance.users, search: '', found: [], foundCC: [] };
        $scope.state.newItem = new Mail();
        template.open('main', 'folders-templates/inbox');
        template.open('toaster', 'folders-templates/toaster');
        $scope.formatFileType = Document.role;
        $scope.sending = false;

        $scope.clearSearch = function () {
            $scope.users.found = [];
            $scope.users.search = '';
        };

        $scope.clearCCSearch = function () {
            $scope.users.foundCC = [];
            $scope.users.searchCC = '';
        };

        $scope.resetScope = function () {
            $scope.openInbox();
        };

        $scope.getSignature = () => {
            if(Conversation.instance.preference.useSignature)
                return Conversation.instance.preference.signature.replace(new RegExp('\n', 'g'),'<br>');
            return '';
        }

        $scope.state.newItem.setMailSignature($scope.getSignature());

        $scope.openFolder = async folderName => {
            if (!folderName) {
                if (Conversation.instance.currentFolder instanceof UserFolder) {
                    $scope.openUserFolder(Conversation.instance.currentFolder, {});
                    return;
                }
                folderName = (Conversation.instance.currentFolder as SystemFolder).folderName;
            }
            $scope.state.newItem = new Mail();
            $scope.state.newItem.setMailSignature($scope.getSignature());
            template.open('main', 'folders-templates/' + folderName);
            await Conversation.instance.folders.openFolder(folderName);
            $scope.$apply();
        };

        $scope.openUserFolder = async (folder: UserFolder, obj) => {
            $scope.mail = undefined;
            await folder.open();
            obj.template = '';
            obj.template = 'folder-content';
            $scope.$apply();
            template.open('main', 'folders-templates/user-folder');

            $timeout(function () {
                $('body').trigger('whereami.update');
            }, 100)
        };

        $scope.isParentOf = function (folder, targetFolder) {
            if (!targetFolder || !targetFolder.parentFolder)
                return false

            var ancestor = targetFolder.parentFolder
            while (ancestor) {
                if (folder.id === ancestor.id)
                    return true
                ancestor = ancestor.parentFolder
            }
            return false
        }

        $scope.variableMailAction = function (mail) {
            var systemFolder = mail.getSystemFolder();
            if (systemFolder === "DRAFT")
                return $scope.editDraft(mail);
            else if (systemFolder === "OUTBOX")
                return $scope.viewMail(mail);
            else
                return $scope.readMail(mail);
        }

        $scope.removeFromUserFolder = async (event, mail) => {
            if(Conversation.instance.currentFolder instanceof UserFolder){
                await Conversation.instance.currentFolder.removeMailsFromFolder();
                $scope.$apply();
            }
        };

        $scope.nextPage = async () => {
            if(template.containers.main.indexOf('mail-actions') < 0) {
                await Conversation.instance.currentFolder.nextPage();
                $scope.$apply();
            }
        };

        $scope.switchSelectAll = function () {
            if ($scope.state.selectAll) {
                Conversation.instance.currentFolder.selectAll();
            }
            else {
                Conversation.instance.currentFolder.deselectAll();
            }
        };

        function setCurrentMail(mail: Mail, doNotSelect?: boolean) {
            $scope.state.current = mail;
            Conversation.instance.currentFolder.deselectAll();
            if (!doNotSelect)
                $scope.state.current.selected = true;
            $scope.mail = mail;
        }

        $scope.viewMail = function (mail) {
            template.open('main', 'mail-actions/view-mail');
            setCurrentMail(mail);
            mail.open();
        };

        $scope.refresh = function () {
            notify.info('updating');
            Conversation.instance.currentFolder.mails.refresh();
            Conversation.instance.folders.inbox.countUnread();
        };

        $scope.readMail = async (mail: Mail) => {
            template.open('main', 'mail-actions/read-mail');
            setCurrentMail(mail, true);
            try{
                await mail.open();
                $scope.$root.$emit('refreshMails');
            }
            catch(e){
                template.open('page', 'errors/e404');
            }
        };

        $scope.transfer = async () => {
            template.open('main', 'mail-actions/write-mail');
            const mail = $scope.state.newItem as Mail;
            mail.parentConversation = $scope.mail;
            await mail.setMailContent($scope.mail, 'transfer', $compile, $sanitize, $scope, $scope.getSignature());
            await Conversation.instance.folders.draft.transfer(mail.parentConversation, $scope.state.newItem);
            $scope.$apply();
        };

        $scope.reply = async () => {
            template.open('main', 'mail-actions/write-mail');
            const mail = $scope.state.newItem as Mail;
            mail.parentConversation = $scope.mail;
            await mail.setMailContent($scope.mail, 'reply', $compile, $sanitize, $scope, $scope.getSignature());
            $scope.addUser($scope.mail.sender());
            $scope.$apply();
        };

        $scope.replyAll = async () => {
            template.open('main', 'mail-actions/write-mail');
            const mail = $scope.state.newItem as Mail;
            mail.parentConversation = $scope.mail;
            await mail.setMailContent($scope.mail,'reply', $compile, $sanitize, $scope, $scope.getSignature(), true);
            mail.to = _.filter($scope.state.newItem.to, function (user) { return user.id !== model.me.userId })
            mail.cc = _.filter($scope.state.newItem.cc, function (user) {
                return user.id !== model.me.userId && !_.findWhere($scope.state.newItem.to, { id: user.id })
            })
            if (!_.findWhere($scope.state.newItem.to, { id: $scope.mail.sender().id })){
                $scope.addUser($scope.mail.sender());
            }
            $scope.$apply();
        };

        $scope.replyOutbox = async () => {
            template.open('main', 'mail-actions/write-mail');
            const mail = $scope.state.newItem as Mail;
            mail.parentConversation = $scope.mail;
            await mail.setMailContent($scope.mail, 'reply', $compile, $sanitize, $scope, $scope.getSignature(), true);
            mail.cc = [];
            mail.to = _.filter($scope.state.newItem.to, function (user) { return user.id !== model.me.userId })
            $scope.$apply();
        };

        $scope.replyAllOutbox = async () => {
            template.open('main', 'mail-actions/write-mail');
            const mail = $scope.state.newItem as Mail;
            mail.parentConversation = $scope.mail;
            await mail.setMailContent($scope.mail,'reply', $compile, $sanitize, $scope, $scope.getSignature(), true);
            mail.to = _.filter($scope.state.newItem.to, function (user) { return user.id !== model.me.userId })
            mail.cc = _.filter($scope.state.newItem.cc, function (user) {
                return user.id !== model.me.userId && !_.findWhere($scope.state.newItem.to, { id: user.id })
            })
            $scope.$apply();
        };

        $scope.editDraft = async (draft: Mail) => {
            template.open('main', 'mail-actions/write-mail');
            $scope.state.newItem = draft;
            await draft.open();
            $scope.$apply();
        };

        $scope.saveDraft = async () => {
            notify.info('draft.saved');
            if(!$scope.draftSavingFlag)
                await Conversation.instance.folders.draft.saveDraft($scope.state.newItem);
            $scope.state.newItem = new Mail();
            $scope.state.newItem.setMailSignature($scope.getSignature());
            await $scope.openFolder(Conversation.instance.folders.draft.folderName);
        };

        $scope.saveDraftAuto = async () => {
            if(!$scope.draftSavingFlag) {
                $scope.draftSavingFlag = true;
                var temp = $scope.state.newItem;
                setTimeout(async function () {
                    if (!$scope.sending && temp.state != "SENT") {
                        await Conversation.instance.folders.draft.saveDraft(temp);
                    }
                    $scope.draftSavingFlag=false;
                }, 5000)
            }
        };

        $scope.refreshSignature = async(use: boolean) => {
            Conversation.instance.putPreference();
            var body = $($scope.state.newItem.body);
            var signature = $scope.getSignature();
            if(body.filter('.new-signature').length > 0){
                body.filter('.new-signature').text('');
                if (use)
                    body.filter('.new-signature').append(signature);
                $scope.state.newItem.body = _.map(body, function(el){ return el.outerHTML; }).join('');
            }else{
                $scope.state.newItem.setMailSignature(signature);
            }
        }

        $scope.inactives = [];

        $scope.sendMail = async () => {
            $scope.sending = true; //Blocks submit button while message hasn't been send
            const mail: Mail = $scope.state.newItem;
            $scope.inactives = await mail.send();
            $scope.state.newItem = new Mail();
            $scope.state.newItem.setMailSignature($scope.getSignature());
            await $scope.openFolder(Conversation.instance.folders.inbox.folderName);
            $scope.sending = false;
        };

        $scope.clearInactives = () => {
            $scope.inactives = [];
        };

        $scope.restore = async () => {
            await Conversation.instance.folders.trash.restore();
            $scope.refreshFolders();
        };

        $scope.removeSelection = async () => {
            await Conversation.instance.currentFolder.removeSelection();
            Conversation.instance.currentFolder.sync();
            $scope.refreshFolders();
        };

        $scope.toggleUnreadSelection = async (unread) => {
            await Conversation.instance.folders.inbox.toggleUnreadSelection(unread);
            Conversation.instance.currentFolder.sync();
            Conversation.instance.folders.inbox.countUnread();
            $scope.refreshFolders();
        };

        $scope.allReceivers = function (mail) {
            var receivers = mail.to.slice(0);
            mail.toName && mail.toName.forEach(function (deletedReceiver) {
                receivers.push({
                    deleted: true,
                    displayName: deletedReceiver
                });
            });
            return receivers;
        }

        $scope.filterUsers = function (mail) {
            return function (user) {
                if (user.deleted) {
                    return true
                }
                var mapped = mail.map(user)
                return typeof mapped !== 'undefined' && typeof mapped.displayName !== 'undefined' && mapped.displayName.length > 0
            }
        }

        $scope.updateFoundCCUsers = async () => {
            var include = [];
            var exclude = $scope.state.newItem.cc || [];
            if ($scope.mail) {
                include = _.map($scope.mail.displayNames, function (item) {
                    return new User(item[0], item[1]);
                });
            }
            $scope.users.foundCC = await Conversation.instance.users.findUser($scope.users.searchCC, include, exclude);
            $scope.$apply();
        };

        $scope.updateFoundUsers = async () => {
            var include = [];
            var exclude = $scope.state.newItem.to || [];
            if ($scope.mail) {
                include = _.map($scope.mail.displayNames, function (item) {
                    return new User(item[0], item[1]);
                });
            }
            $scope.users.found = await Conversation.instance.users.findUser($scope.users.search, include, exclude);
            $scope.$apply();
        };

        $scope.addUser = function (user) {
            if (!$scope.state.newItem.to) {
                $scope.state.newItem.to = [];
            }
            if (user) {
                $scope.state.newItem.currentReceiver = user;
            }
            $scope.state.newItem.to.push($scope.state.newItem.currentReceiver);
            Conversation.instance.folders.draft.saveDraft($scope.state.newItem);
        };

        $scope.removeUser = function (user) {
            $scope.state.newItem.to = _.reject($scope.state.newItem.to, function (item) { return item === user; });
            Conversation.instance.folders.draft.saveDraft($scope.state.newItem);
        };

        $scope.addCCUser = function (user) {
            if (!$scope.state.newItem.cc) {
                $scope.state.newItem.cc = [];
            }
            if (user) {
                $scope.state.newItem.currentCCReceiver = user;
            }
            $scope.state.newItem.cc.push($scope.state.newItem.currentCCReceiver);
            Conversation.instance.folders.draft.saveDraft($scope.state.newItem);
        };

        $scope.removeCCUser = function (user) {
            $scope.state.newItem.cc = _.reject($scope.state.newItem.cc, function (item) { return item === user; });
            Conversation.instance.folders.draft.saveDraft($scope.state.newItem);
        };

        $scope.template = template
        $scope.lightbox = {}

        $scope.rootFolderTemplate = { template: 'folder-root-template' }
        $scope.refreshFolders = async () => {
            await $scope.userFolders.sync();
            await Conversation.instance.currentFolder.sync();
            if(Conversation.instance.currentFolder instanceof UserFolder){
                $scope.openUserFolder(Conversation.instance.currentFolder, {});
            }
            $scope.rootFolderTemplate.template = ""
            $timeout(function () {
                $scope.$apply()
                $scope.rootFolderTemplate.template = 'folder-root-template'
            }, 100)
        }

        $scope.currentFolderDepth = function () {
            if (!($scope.currentFolder instanceof UserFolder))
                return 0

            return $scope.currentFolder.depth();
        }

        $scope.moveSelection = function () {
            $scope.destination = {}
            $scope.lightbox.show = true
            template.open('lightbox', 'move-mail')
        }

        $scope.moveToFolderClick = async (folder, obj) => {
            obj.template = ''

            if (folder.userFolders.length() > 0) {
                $timeout(function () {
                    obj.template = 'move-folders-content'
                }, 10)
                return
            }

            await folder.userFolders.sync();
            $timeout(function () {
                obj.template = 'move-folders-content'
            }, 10);
        }

        $scope.moveMessages = function (folderTarget) {
            $scope.lightbox.show = false
            template.close('lightbox')
            Conversation.instance.currentFolder.mails.moveSelection(folderTarget)
        }

        $scope.openNewFolderView = function () {
            $scope.newFolder = new UserFolder();
            if (Conversation.instance.currentFolder instanceof UserFolder) {
                $scope.newFolder.parentFolderId = (Conversation.instance.currentFolder as UserFolder).id;
            }
            
            $scope.lightbox.show = true
            template.open('lightbox', 'create-folder')
        }
        $scope.createFolder = async () => {
            await $scope.newFolder.create();
            $scope.refreshFolders();
            $scope.lightbox.show = false;
            template.close('lightbox');
            $scope.$apply();
        }
        $scope.openRenameFolderView = function (folder, $event) {
            $event.stopPropagation();
            $scope.targetFolder = new UserFolder();
            $scope.targetFolder.name = folder.name;
            $scope.targetFolder.id = folder.id;
            $scope.lightbox.show = true;
            template.open('lightbox', 'update-folder');
        }
        $scope.updateFolder = async () => {
            await $scope.targetFolder.update();
            $scope.refreshFolders();
            $scope.lightbox.show = false;
            template.close('lightbox');
            $scope.$apply();
        }
        $scope.trashFolder = async (folder: UserFolder) => {
            await folder.trash();
            $scope.refreshFolders();
            await Conversation.instance.folders.trash.sync();
            await $scope.openFolder('trash');
        }
        $scope.restoreFolder = function (folder) {
            folder.restore().done(function () {
                $scope.refreshFolders()
            })
        }
        $scope.deleteFolder = function (folder) {
            folder.delete().done(function () {
                $scope.refreshFolders()
            })
        }

        var letterIcon = document.createElement("img")
        letterIcon.src = skin.theme + "../../img/icons/message-icon.png"
        $scope.drag = function (item, $originalEvent) {
            var selected = [];
            if(Conversation.instance.currentFolder.mails.selection.selected.indexOf(item) > -1)
                selected = Conversation.instance.currentFolder.mails.selection.selected;
            else
                selected.push(item);

            $originalEvent.dataTransfer.setDragImage(letterIcon, 0, 0);
            try {
                $originalEvent.dataTransfer.setData('application/json', JSON.stringify(selected));
            } catch (e) {
                $originalEvent.dataTransfer.setData('Text', JSON.stringify(selected));
            }
        };
        $scope.dropCondition = function (targetItem) {
            return function (event) {
                let dataField = event.dataTransfer.types.indexOf && event.dataTransfer.types.indexOf("application/json") > -1 ? "application/json" : //Chrome & Safari
                    event.dataTransfer.types.contains && event.dataTransfer.types.contains("application/json") ? "application/json" : //Firefox
                        event.dataTransfer.types.contains && event.dataTransfer.types.contains("Text") ? "Text" : //IE
                            undefined;

                if (targetItem.foldersName && targetItem.foldersName !== 'trash')
                    return undefined;

                return dataField;
            }
        };

        $scope.dropTo = function (targetItem, $originalEvent) {
            var dataField = $scope.dropCondition(targetItem)($originalEvent)
            var originalItems = JSON.parse($originalEvent.dataTransfer.getData(dataField))
            if (targetItem.folderName === 'trash')
                $scope.dropTrash(originalItems);
            else
                $scope.dropMove(originalItems, targetItem);
        };

        $scope.removeMail = async () => {
            await $scope.mail.remove();
            $scope.openFolder();
        }

        $scope.dropMove = async (mails, folder) => {
            var mailObj
            mails.forEach(async mail => {
                mailObj = new Mail(mail.id);
                await mailObj.move(folder);
                $scope.$apply();
            })
        }
        $scope.dropTrash = async mails => {
            var mailObj;
            mails.forEach(async mail => {
                mailObj = new Mail(mail.id);
                await mailObj.trash();
                $scope.$apply();
            })

        }

        //Given a data size in bytes, returns a more "user friendly" representation.
        $scope.getAppropriateDataUnit = quota.appropriateDataUnit;

        $scope.formatSize = function (size) {
            var formattedData = $scope.getAppropriateDataUnit(size)
            return (Math.round(formattedData.nb * 10) / 10) + " " + formattedData.order
        }


        $scope.postAttachments = async () => {
            const mail = $scope.state.newItem as Mail;
            if (!mail.id) {
                await Conversation.instance.folders.draft.saveDraft(mail);
                await mail.postAttachments($scope);
            } else {
                await mail.postAttachments($scope);
            }
        }

        $scope.deleteAttachment = function (event, attachment, mail) {
            mail.deleteAttachment(attachment);
        }

        $scope.quota = quota;

        $scope.sortBy = sorts;

        $scope.setSort = function (box, sortFun) {
            if (box.sort === sortFun) {
                box.reverse = !box.reverse;
            } else {
                box.sort = sortFun;
                box.reverse = false;
            }
        }
    }]);