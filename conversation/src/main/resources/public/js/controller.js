// Copyright © WebServices pour l'Éducation, 2014
//
// This file is part of ENT Core. ENT Core is a versatile ENT engine based on the JVM.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as
// published by the Free Software Foundation (version 3 of the License).
//
// For the sake of explanation, any module that communicate over native
// Web protocols, such as HTTP, with ENT Core is outside the scope of this
// license and could be license under its own terms. This is merely considered
// normal use of ENT Core, and does not fall under the heading of "covered work".
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
routes.define(function($routeProvider){
	$routeProvider
		.when("/read-mail/:mailId", {
			action: "readMail"
		})
		.when("/write-mail/:userId", {
			action: "writeMail"
		})
		.when('/inbox', {
			action: 'inbox'
		})
		.otherwise({
			redirectTo: "/inbox"
		})
});

function Conversation($scope, date, notify, route, model){
	route({
		readMail: function(params){
			model.folders.openFolder('inbox');
			$scope.openView('folders', 'page');
			$scope.readMail(new Mail({ id: params.mailId }));
		},
		writeMail: function(params){
			model.folders.openFolder('inbox');
			model.users.on('sync', function(){
				if(this.findWhere({ id: params.userId })){
					$scope.openView('folders', 'page');
					new User({ id: params.userId }).findData(function(){
						$scope.openView('write-mail', 'main');
						$scope.addUser(this);
					});
				}
				else{
					$scope.openView('e401', 'page');
				}
			});
		},
		inbox: function(){
			$scope.openView('folders', 'page');
		}
	});

	$scope.clearSearch = function(){
		$scope.users.found = [];
		$scope.users.foundCC = [];
		$scope.users.search = '';
		$scope.users.searchCC = '';
	};

	$scope.resetScope = function(){
		$scope.openInbox();
	};

	$scope.viewsContainers = {};
	$scope.openView = function(view, name){
		$scope.clearSearch();
		$scope.newItem = new Mail();
		$scope.selection.selectAll = false;
		var viewsPath = '/conversation/public/template/';
		$scope.viewsContainers[name] = viewsPath + view + '.html';
	};

	$scope.containsView = function(name, view){
		var viewsPath = '/conversation/public/template/';
		return $scope.viewsContainers[name] === viewsPath + view + '.html';
	};

	$scope.openFolder = function(folderName, cb){
		if(!folderName){
			folderName = model.folders.current.folderName;
		}
		$scope.mail = undefined;
		model.folders.openFolder(folderName, cb);
		$scope.openView(folderName, 'main');
	};

	$scope.nextPage = function(){
		model.folders.current.nextPage();
	};

	$scope.selection = {
		selectAll: false
	};

	$scope.switchSelectAll = function(){
		if($scope.selection.selectAll){
			model.folders.current.mails.selectAll();
		}
		else{
			model.folders.current.mails.deselectAll();
		}
	};

	function setCurrentMail(mail){
		model.folders.current.mails.current = mail;
		model.folders.current.mails.deselectAll();
		model.folders.current.mails.current.selected = true;
		$scope.mail = mail;
	}

	$scope.viewMail = function(mail){
		$scope.openView('view-mail', 'main');
		setCurrentMail(mail);
		mail.open();
	};

	$scope.refresh = function(){
		notify.info('updating');
		model.folders.current.mails.refresh();
	}

	$scope.readMail = function(mail){
		$scope.openView('read-mail', 'main');
		setCurrentMail(mail);
		mail.open(function(){
			if(!mail.state){
				$scope.openView('e404', 'page');
			}
		});
	};

	var format = {
		reply: {
			prefix: lang.translate('reply.re')
		},
		transfer: {
			prefix: lang.translate('reply.fw')
		}
	};

	http().get('/conversation/public/template/mail-content/transfer.html').done(function(content){
		format.transfer.content = content;
	});

	http().get('/conversation/public/template/mail-content/reply.html').done(function(content){
		format.reply.content = content;
	});

	function setMailContent(mailType){
		if($scope.mail.subject.indexOf(format[mailType].prefix) === -1){
			$scope.newItem.subject = format[mailType].prefix + $scope.mail.subject;
		}
		else{
			$scope.newItem.subject = $scope.mail.subject;
		}

		$scope.newItem.body = format[mailType].content + '<blockquote>' + $scope.mail.body + '</blockquote>';
	}

	$scope.transfer = function(){
		$scope.openView('write-mail', 'main');
		setMailContent('transfer');
	};

	$scope.reply = function(){
		$scope.openView('write-mail', 'main');
		$scope.newItem.parentConversation = $scope.mail;
		$scope.addUser($scope.mail.sender());
		setMailContent('reply');
	};

	$scope.replyAll = function(){
		$scope.openView('write-mail', 'main');
		$scope.newItem.parentConversation = $scope.mail;
		setMailContent('reply');
		$scope.mail.displayNames.forEach(function(user){
			if(user[0] === model.me.userId){
				return;
			}
			$scope.addUser(new User({ id: user[0], displayName: user[1] }));
		});
	};

	$scope.editDraft = function(draft){
		$scope.openView('write-mail', 'main');
		draft.open();
		$scope.newItem = draft;
	};

	$scope.saveDraft = function(){
		notify.info('draft.saved');

		model.folders.draft.saveDraft($scope.newItem);
		$scope.openFolder();
	};

	$scope.sendMail = function(){
		$scope.newItem.send(function(result){
			if(parseInt(result.sent) > 0){
				notify.info('mail.sent');
			}
			var inactives = '';
			result.inactive.forEach(function(name){
				inactives += name + lang.translate('invalid') + '<br />';
			});
			if(result.inactive.length > 0){
				notify.info(inactives);
			}

			result.undelivered.forEach(function(name){
				notify.error(name + lang.translate('undelivered'));
			});
		});
		$scope.openFolder();
	};

	$scope.restore = function(){
		model.folders.trash.mails.restoreMails();
	};

	$scope.removeSelection = function(){
		model.folders.current.mails.removeMails();
	};

	$scope.updateFoundCCUsers = function(){
		var include = [];
		var exclude = $scope.newItem.cc || [];
		if($scope.mail){
			include = _.map($scope.mail.displayNames, function(item){
				return new User({ id: item[0], displayName: item[1] });
			});
		}
		$scope.users.foundCC = model.users.find($scope.users.searchCC, include, exclude);
	};

	$scope.updateFoundUsers = function(){
		var include = [];
		var exclude = $scope.newItem.to || [];
		if($scope.mail){
			include = _.map($scope.mail.displayNames, function(item){
				return new User({ id: item[0], displayName: item[1] });
			});
		}
		$scope.users.found = model.users.find($scope.users.search, include, exclude);
	};

	$scope.addUser = function(user){
		if(!$scope.newItem.to){
			$scope.newItem.to = [];
		}
		if(user){
			$scope.newItem.currentReceiver = user;
		}
		$scope.newItem.to.push($scope.newItem.currentReceiver);
	};

	$scope.removeUser = function(user){
		$scope.newItem.to = _.reject($scope.newItem.to, function(item){ return item === user; });
	};

	$scope.addCCUser = function(user){
		if(!$scope.newItem.cc){
			$scope.newItem.cc = [];
		}
		if(user){
			$scope.newItem.currentCCReceiver = user;
		}
		$scope.newItem.cc.push($scope.newItem.currentCCReceiver);
	};

	$scope.removeCCUser = function(user){
		$scope.newItem.cc = _.reject($scope.newItem.cc, function(item){ return item === user; });
	};

	$scope.lang = lang;
	$scope.notify = notify;
	$scope.folders = model.folders;
	$scope.users = { list: model.users, search: '', found: [], foundCC: [] };

	$scope.newItem = new Mail();

	$scope.openView('inbox', 'main');
}