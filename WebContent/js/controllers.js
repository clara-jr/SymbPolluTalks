angular.module("module_name")
.controller("resourcesController_list", function($rootScope, $scope, $resource) {
	var regions = {
			map: {
				lat: 45,
				lng: 15,
				zoom: 3.5
			},
			Croatia: {
				lat: 45.795849,
				lng: 15.958879,
				zoom: 12
			},
			Barcelona: {
				lat: 41.386384,
				lng: 2.143104,
				zoom: 12
			},
			Austria: {
				lat: 48.1607235,
				lng: 16.3906138,
				zoom: 12
			},
			unset: {
				lat: 25,
				lng: 0,
				zoom: 1.5
			}
	};

	var map = L.map('map');
	var geojson;
	// control that shows state info on hover
	var info = L.control();
	info.onAdd = function (map) {
		this._div = L.DomUtil.create('div', 'info');
		this.update();
		return this._div;
	};
	info.update = function (props) {
		this._div.innerHTML = '<h4>Pollution</h4>' +  (props ?
				'<b>' + props.name + '</b><br />' + props.density
				: 'Hover over a zone');
	};
	info.addTo(map);
	function highlightFeature(e) {
		var layer = e.target;
		layer.setStyle({
			weight: 5,
			color: '#666',
			dashArray: '',
			fillOpacity: 0.4
		});
		if (!L.Browser.ie && !L.Browser.opera && !L.Browser.edge) {
			layer.bringToFront();
		}
		info.update(layer.feature.properties);
	}
	function resetHighlight(e) {
		geojson.resetStyle(e.target);
		info.update();
	}
	function onEachFeature(feature, layer) {
		layer.on({
			mouseover: highlightFeature,
			mouseout: resetHighlight
		});
	}
	var legend = L.control({position: 'bottomright'});
	legend.onAdd = function (map) {
		var div = L.DomUtil.create('div', 'info legend'),
		grades = [0, 50, 60, 70, 80, 90, 100],
		labels = [],
		from, to;
		for (var i = 0; i < grades.length; i++) {
			from = grades[i];
			to = grades[i + 1];
			labels.push(
					'<i style="background:' + getColor(from + 1) + '"></i> ' +
					from + (to ? '&ndash;' + to : '+'));
		}
		div.innerHTML = labels.join('<br>');
		return div;
	};
	legend.addTo(map);

	function style(feature) {
		return {
			weight: 2,
			opacity: 1,
			color: 'white',
			dashArray: '3',
			fillOpacity: 0.7,
			fillColor: getColor(feature.properties.density)
		};
	}
	function getColor(d) {
		return d > 200 ? '#800026' :
			d > 100  ? '#BD0026' :
				d > 90  ? '#E31A1C' :
					d > 80  ? '#FC4E2A' :
						d > 70   ? '#FD8D3C' :
							d > 60   ? '#FEB24C' :
								d > 50   ? '#FED976' :
									'#FFEDA0';
	}
	$rootScope.color = "rgba(0,0,0,0)";
	$rootScope.title = "Hackatown";
	var observationsData = [];
	$resource('http://localhost:8080/symbioteAPIREST/rest/initializeCollectionTask/').get().$promise.then(function(){  
		console.log("initializeCollectionTask. Init.");
	},function(error){
		console.log(error);
	}
	);
	$resource('http://localhost:8080/symbioteAPIREST/rest/featureCollection/:locationName/', {locationName: "@locationName"}).get({locationName: "Zagreb"}).$promise.then(function(data){  
		console.log(data);
		observationsData = data;
		$scope.geojson = {
				data: observationsData,
				style: style,
				onEachFeature: onEachFeature
		};
		geojson = L.geoJson(observationsData, {
			style: style,
			onEachFeature: onEachFeature
		}).addTo(map);
	},function(error){
		console.log(error);
	}
	);
	angular.extend($scope, {
		center: regions.Croatia,
		tiles: {
			name: 'Mapbox Light',
			url: 'https://api.tiles.mapbox.com/v4/{mapid}/{z}/{x}/{y}.png?access_token={apikey}',
			type: 'xyz',
			options: {
				apikey: 'pk.eyJ1IjoibWFwYm94IiwiYSI6ImNpejY4NXVycTA2emYycXBndHRqcmZ3N3gifQ.rJcFIG214AriISLbB6B5aw',
				mapid: 'mapbox.light'
			}
		},
		defaults: {
			scrollWheelZoom: false
		}
	});
	$scope.setRegion = function(region) {
		if (!region) {
			$scope.center = regions[map];
		} else {
			$resource('http://localhost:8080/symbioteAPIREST/rest/featureCollection/:locationName', {locationName: "@locationName"}).get({locationName: region}).$promise.then(function(data){  
				console.log(data);
				observationsData = data;
				$scope.center = regions[region];
				$scope.geojson = {
						data: observationsData,
						style: style,
						onEachFeature: onEachFeature
				};
				geojson = L.geoJson(observationsData, {
					style: style,
					onEachFeature: onEachFeature
				}).addTo(map);
			},function(error){
				console.log(error);
			}
			);
		}
	};
	$scope.setView = function(view) {
		console.log(view);
		if (!view.includes("Interpolator")) {
			$resource('http://localhost:8080/symbioteAPIREST/rest/featureCollection/:locationName/:platformId', {locationName: "@locationName", platformId: "@platformId"}).get({locationName: "Zagreb"},{platformId: "AITopen"}).$promise.then(function(data){  
				console.log(data);
				observationsData = data;
				$scope.center = regions["Croatia"];
				$scope.geojson = {
						data: observationsData,
						style: style,
						onEachFeature: onEachFeature
				};
				geojson = L.geoJson(observationsData, {
					style: style,
					onEachFeature: onEachFeature
				}).addTo(map);
			},function(error){
				console.log(error);
			}
			);
		} else {
			$resource('http://localhost:8080/rest/featureCollection/:locationName', {locationName: "@locationName"}).get({locationName: "Zagreb"}).$promise.then(function(data){  
				console.log(data);
				observationsData = data;
				$scope.center = regions["Croatia"];
				$scope.geojson = {
						data: observationsData,
						style: style,
						onEachFeature: onEachFeature
				};
				geojson = L.geoJson(observationsData, {
					style: style,
					onEachFeature: onEachFeature
				}).addTo(map);
			},function(error){
				console.log(error);
			}
			);
		}
	};
});