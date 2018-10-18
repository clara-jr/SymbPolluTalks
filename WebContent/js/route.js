var app = angular.module("module_name",["ngRoute", "ngResource", "leaflet-directive"])
.config(function($routeProvider) {
	$routeProvider
	.when("/", {
		controller: "resourcesController_list",
		templateUrl: "views/resources/map.html"
	})
	.otherwise("/");
})