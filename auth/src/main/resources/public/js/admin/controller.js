﻿function AdminDirectoryController($scope) {
    template.open('main', 'admin/welcome-message');
    $scope.message = model.message;
    $scope.message.sync(function () {
        setTimeout(function () {
            $scope.message.display = true;
            $scope.$apply();
        }, 500);
    });

    $scope.saveChanges = function () {
        this.message.save();
    }
}