/* --------------------------------------------
	DATA
-------------------------------------------- */

var zipSelectorMapData = (function() {

	//	town
		
		/* --------------------------------------------
		 mapStyleDefault
		-------------------------------------------- */
		/* https://mapstyle.withgoogle.com/ */
	
		var mapStyleDefault = [
		  {
			"elementType": "geometry",
			"stylers": [
			  {
				"color": "#f5f5f5"
			  }
			]
		  },
		  {
			"elementType": "labels.icon",
			"stylers": [
			  {
				"visibility": "off"
			  }
			]
		  },
		  {
			"elementType": "labels.text.fill",
			"stylers": [
			  {
				"color": "#616161"
			  }
			]
		  },
		  {
			"elementType": "labels.text.stroke",
			"stylers": [
			  {
				"color": "#f5f5f5"
			  }
			]
		  },
		  {
			"featureType": "administrative.land_parcel",
			"elementType": "labels.text.fill",
			"stylers": [
			  {
				"color": "#bdbdbd"
			  }
			]
		  },
		  {
			"featureType": "poi",
			"elementType": "geometry",
			"stylers": [
			  {
				"color": "#eeeeee"
			  }
			]
		  },
		  {
			"featureType": "poi",
			"elementType": "labels.text.fill",
			"stylers": [
			  {
				"color": "#757575"
			  }
			]
		  },
		  {
			"featureType": "poi.business",
			"stylers": [
			  {
				"visibility": "off"
			  }
			]
		  },
		  {
			"featureType": "poi.park",
			"elementType": "geometry",
			"stylers": [
			  {
				"color": "#e5e5e5"
			  }
			]
		  },
		  {
			"featureType": "poi.park",
			"elementType": "labels.text",
			"stylers": [
			  {
				"visibility": "off"
			  }
			]
		  },
		  {
			"featureType": "poi.park",
			"elementType": "labels.text.fill",
			"stylers": [
			  {
				"color": "#9e9e9e"
			  }
			]
		  },
		  {
			"featureType": "road",
			"elementType": "geometry",
			"stylers": [
			  {
				"color": "#ffffff"
			  }
			]
		  },
		  {
			"featureType": "road.arterial",
			"elementType": "labels.text.fill",
			"stylers": [
			  {
				"color": "#757575"
			  }
			]
		  },
		  {
			"featureType": "road.highway",
			"elementType": "geometry",
			"stylers": [
			  {
				"color": "#dadada"
			  }
			]
		  },
		  {
			"featureType": "road.highway",
			"elementType": "labels.text.fill",
			"stylers": [
			  {
				"color": "#616161"
			  }
			]
		  },
		  {
			"featureType": "road.local",
			"elementType": "labels.text.fill",
			"stylers": [
			  {
				"color": "#9e9e9e"
			  }
			]
		  },
		  {
			"featureType": "transit.line",
			"elementType": "geometry",
			"stylers": [
			  {
				"color": "#e5e5e5"
			  }
			]
		  },
		  {
			"featureType": "transit.station",
			"elementType": "geometry",
			"stylers": [
			  {
				"color": "#eeeeee"
			  }
			]
		  },
		  {
			"featureType": "water",
			"elementType": "geometry",
			"stylers": [
			  {
				"color": "#c9c9c9"
			  }
			]
		  },
		  {
			"featureType": "water",
			"elementType": "labels.text.fill",
			"stylers": [
			  {
				"color": "#9e9e9e"
			  }
			]
		  }
		];
		
		
		/* --------------------------------------------
		 TOWN DATA
		-------------------------------------------- */
		
		return {
		
			getMapStyleDefault: function() {
				
				return mapStyleDefault;
				
			}
			
		};
		
	
		
		
	})();
	
	
		//alert(zipSelectorMapData.getTown());
	
	/* --------------------------------------------
	
		estateMap.js
		08/12/2016 - v1.0
		10/03/2018 - v1.1 : if no town
		
		author : Webulous
		
		Dependecy :
		fancybox + sumoselect + jquery + googlemaps
		
		----- estateMap -----
		
		var estateMapOptions = {
			mainColor : "#fb6c26",
			thumbBackgroundSizeCss : "content", // content - cover
			estate : [
				{
					href: "/fr-BE/Details/Index/2781739",
					imgSrc: "http://www.webulous.be/Pictures/2781739/200/9ea1f21bf4bf4cf4b210f84322acd336.jpg",
					legend: "<strong>À vendre</strong><br>Bruxelles",
					btnCaption : "Voir le bien",
					lat: 50.8495063,
					long: 4.337444 
				},
			],
			town : [
				{
					name: "Uccle",
					text: "Texte...",
					href: "/fr-BE/Details/Index/2781739",
					btnCaption : "Voir les 30 biens sur Uccle",
					totalEstate: 30,
					zipCode: 1170,
					lat: 50.80237,
					long: 4.34023			
				}
			]
		};
	
		mapTest = new estateMap("map-canvas",estateMapOptions);
	
		----- zipSelectorMap -----
	
		var zipSelectorMapOptions = {
			mainColor : "#fb6c26",
			secondColor : "#B2B2B2",
			selectTownStr : "Sélectionnez les communes sur la carte.",
			closeBtnStr : "<i class='fa fa-check'></i> Valider"
		}
	
		zipSelectorMapTest = new zipSelectorMap("zipSelectorMap-map",zipSelectorMapOptions);
	
	-------------------------------------------- */
	
	
	/* --------------------------------------------
	
	 estateMap
	 08/12/2016 - v1.0
	 10/03/2018 - v1.1 : if no town
	 04/05/2018 - v1.2
	 
	-------------------------------------------- */
	
	var estateMap = function(idTarget_p,mapOptions_p) {
		
		"use strict";
		
		// member variables	
	//    var myLatLng = new google.maps.LatLng(50.846858, 40.358987)
		var	latlngbounds = new google.maps.LatLngBounds()
	
	
	//<polygon class="st0" points="60,0 0,99.4 119.9,99.4 "/>
		
		var svgIco = 'M0-165c-27.618 0-50 21.966-50 49.054C-50-88.849 0 0 0 0s50-88.849 50-115.946C50-143.034 27.605-165 0-165z';
		
		var pin = {
			path: svgIco,
			fillColor: mapOptions_p.mainColor,
			fillOpacity: 0.7,
			anchor: new google.maps.Point(0,0),
			strokeWeight: 0,
			scale: 1/4
		  };
		  
		var pinHover = {
			path: svgIco,
			fillColor: "#FF7D00",
			fillOpacity: 1,
			anchor: new google.maps.Point(0,0),
			strokeWeight: 0,
			scale: 1/4
		  };
		  
		// General Options for googleMap
		var mapOptions = {
			scrollwheel: false,
			mapTypeId: google.maps.MapTypeId.RoadMap,
			mapTypeControl: false,
			mapTypeControlOptions: {
				style: google.maps.MapTypeControlStyle.HORIZONTAL_BAR,
				position: google.maps.ControlPosition.TOP_CENTER
			},
			zoomControl: true,
			zoomControlOptions: {
				position: google.maps.ControlPosition.LEFT_TOP
			},
			scaleControl: true,
			streetViewControl: false,
			streetViewControlOptions: {
				position: google.maps.ControlPosition.LEFT_TOP
			}
			
		};
	
		// Zoom map
		if ( mapOptions_p.zoom !== undefined  ) { mapOptions.zoom = mapOptions_p.zoom; }
		
		// Center by lat and long
		if ( mapOptions_p.center !== undefined  ) {
			mapOptions.center = new google.maps.LatLng(mapOptions_p.center.lat, mapOptions_p.center.lng);
		};
	
		// Styles
		var mapStyleDefault = zipSelectorMapData.getMapStyleDefault();
		var styledMap = new google.maps.StyledMapType(mapStyleDefault, {name: "webulous-default"});
		
		// Init
		var map = new google.maps.Map(document.getElementById(idTarget_p),mapOptions);
		
		// Init Styles
		map.mapTypes.set('map_style', styledMap);
		map.setMapTypeId('map_style');	
		
		// Center map for responsive
		google.maps.event.addDomListener(window, "resize", function() {
			var center = map.getCenter();
			google.maps.event.trigger(map, "resize");
			map.setCenter(center); 
		});
		
		// private functions (must be before public function to avoid to be bypassed by the return)
		
		var estateMarkerArray = new Array;
		var estateMarkerArray_i = 0;
		
		// Draw estate on map
		for (var iEstate = 0; iEstate < mapOptions_p.estate.length; iEstate++) {
			createEstateMarker(mapOptions_p.estate[iEstate]);
		}
		
		if (mapOptions_p.town != undefined) {
			// Draw town on map
			for (var iTown = 0; iTown < mapOptions_p.town.length; iTown++) {
				createTownMarker(mapOptions_p.town[iTown]);
			}
		}

		//Center auto if no lat and long
		if ( mapOptions_p.center === undefined  ) {
			map.fitBounds(latlngbounds);
		}	
		
		//Legend of the map
		if ( mapOptions_p.legend !== undefined) {
			
			// Construct HTML legend
			var htmlStr = '<style>.map-canvas-legend .fa { color: '+mapOptions_p.mainColor+'; font-size: 12px; }</style>';
				htmlStr += '<div class="map-canvas-legend" style="position: absolute; z-index: 1; font-size: 12px; background-color: white; line-height:20px; padding: 10px; margin-top: 20px; right: 15px;">';
				htmlStr += mapOptions_p.legend;
				htmlStr += '</div>';
	
			$("#"+idTarget_p).append(htmlStr);
			
		}
		
		var lastEstateInfoWindowOpen;
	
		function createEstateMarker(estate_obj) {
			// if no coord provided, we do not localize the estate
			if (estate_obj.lat === 0 || estate_obj.long === 0) { return; }
	
			// position
			var estateLocation = new google.maps.LatLng(estate_obj.lat, estate_obj.long);
	
			latlngbounds.extend(estateLocation);

			// set image picto and size
			var image = {
				url: estate_obj.pinImg,
				size: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
				scaledSize: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
				anchor: new google.maps.Point(estate_obj.markerAnchorX, estate_obj.markerAnchorY),			
				// origin: new google.maps.Point(0, 0),	
			};

			// set marker
			var marker = new google.maps.Marker({
				map: map,
				position: estateLocation,
				icon: image
				// icon: pin
				//,title : "test"
			});
			
			// Save settings 
			$("[data-estate-map-id='"+estateMarkerArray_i+"']").attr("data-test",estateMarkerArray_i);

			estateMarkerArray[estateMarkerArray_i] = marker;
			estateMarkerArray_i++;
			
			//Mouse Over
			google.maps.event.addListener(marker, 'mouseover', function() {

				image = {
					url: estate_obj.pinImgActive,
					size: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
					scaledSize: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
					anchor: new google.maps.Point(estate_obj.markerAnchorX, estate_obj.markerAnchorY),
				};

				marker.setIcon(image);
			});
			
			//Mouse Out
			google.maps.event.addListener(marker, 'mouseout', function() {

				image = {
					url: estate_obj.pinImg,
					size: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
					scaledSize: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
					anchor: new google.maps.Point(estate_obj.markerAnchorX, estate_obj.markerAnchorY),
				};

				marker.setIcon(image);
			});
			
			//Construct HTML
			var	estateInfoWindowContent = estate_obj.htmlStr;

			//Set info window
			//var estateInfoWindow = new google.maps.InfoWindow({ content: estateInfoWindowContent, maxWidth : 250 });
			
			//Mouse click
			/*google.maps.event.addListener(marker, 'click', function () {
				if (lastEstateInfoWindowOpen !== undefined) { lastEstateInfoWindowOpen.close(); }
				estateInfoWindow.open(map, marker);
				lastEstateInfoWindowOpen = estateInfoWindow;
			});*/
			
			if( estate_obj.htmlStr != undefined ) {

			

			
			var boxText = estateInfoWindowContent;
			
			// pixelOffset: new google.maps.Size(-140, -55) align infoWindow

			var myOptions = {
				 content: boxText
				,disableAutoPan: false
				,alignBottom: true
				,maxWidth: 50
				,pixelOffset: new google.maps.Size(-140, -25)
				,zIndex: null
				,boxStyle: { }
				,closeBoxMargin: "0px"
				,closeBoxURL: "http://www.google.com/intl/en_us/mapfiles/close.gif"
				,infoBoxClearance: new google.maps.Size(1, 1)
				,visible: true
				,pane: "floatPane"
				,enableEventPropagation: false
			};
			
			
			google.maps.event.addListener(marker, "click", function (e) {
				if (lastInfobox != undefined) { lastInfobox.close(map); }
				currentInfobox.open(map, this);
				// @rbo : todo : init tiny slider
				lastInfobox = currentInfobox;
			});
			
			var currentInfobox = new InfoBox(myOptions);

			}
		}
		
		var lastInfobox;
		
		/* rbo */
		// Roll over item estate of list
		$("[data-estate-map-id]").hover(function() {
			
			var estate_obj = mapOptions_p.estate[$(this).attr("data-estate-map-id")];
			
			var image = {
				url: estate_obj.pinImgActive,
				size: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
				scaledSize: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
				anchor: new google.maps.Point(estate_obj.markerAnchorX, estate_obj.markerAnchorY)
			};

			//Trigger Over Pin
			estateMarkerArray[$(this).attr("data-estate-map-id")].setIcon(image);

			//Trigger Click on Pin
			google.maps.event.trigger(estateMarkerArray[$(this).attr("data-estate-map-id")], 'click');

		}).mouseleave (function() {

			var estate_obj = mapOptions_p.estate[$(this).attr("data-estate-map-id")];
			
			var image = {
				url: estate_obj.pinImg,
				size: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
				scaledSize: new google.maps.Size(estate_obj.markerWidth, estate_obj.markerHeight),
				anchor: new google.maps.Point(estate_obj.markerAnchorX, estate_obj.markerAnchorY)
			};

			//Mouse leave
			estateMarkerArray[$(this).attr("data-estate-map-id")].setIcon(image);
		});
		
		function createTownMarker(townMap_obj) {
			var townLocation = new google.maps.LatLng(townMap_obj.lat, townMap_obj.long);
	
			latlngbounds.extend(townLocation);
	
			//Scale of the marker
			var markerScale = 20;
			if(townMap_obj.totalEstate > 3 )  { markerScale = 20; }
			if(townMap_obj.totalEstate > 5 )  { markerScale = 25; }
			if(townMap_obj.totalEstate > 10 ) { markerScale = 30; }
			
			var marker = new google.maps.Marker({
				map: map,
				position: townLocation,
				icon: {
					path: google.maps.SymbolPath.CIRCLE,
					fillOpacity: 0.3,
					fillColor: mapOptions_p.mainColor,
					strokeOpacity: 1.0,
					strokeColor: '#fff000',
					strokeWeight: 0, 
					scale: markerScale //pixels
				  }
			});
			
			//Construct HTML
			var townInfoWindowContent = "<div style='width:150px; margin-top:5px;'>"+townMap_obj.text+"</div>";
			townInfoWindowContent += "<a style='display: block; background: "+mapOptions_p.mainColor+"; margin-top:5px; padding: 5px; text-decoration:none; color: #ffffff;' href='"+townMap_obj.href+"'>"+townMap_obj.btnCaption+"</a>";
			
			//Set info window
			var estateInfoWindow = new google.maps.InfoWindow({ content: townInfoWindowContent });
			
			google.maps.event.addListener(marker, 'click', function () {
				if (lastEstateInfoWindowOpen !== undefined) { lastEstateInfoWindowOpen.close(); }
				estateInfoWindow.open(map, marker);
				lastEstateInfoWindowOpen = estateInfoWindow;
			});	
	
		}	
		
	};
	
	/* --------------------------------------------
	
	 zipSelectorMap
	 08/12/2016 - v1.0
	 
	-------------------------------------------- */
	
	var zipSelectorMap = function(idTarget_p,mapOptions_p) {
		
		"use strict";
		
		// Construct HTML fancybox
		var htmlStr  = '<div class="zipSelectorMap-container" id="inline">';
			htmlStr += '<div id="zipSelectorMap-map"></div>';
			htmlStr += '<div class="zipSelectorMap-footer">';
			htmlStr += '<div class="zipSelectorMap-text" id="output">'+mapOptions_p.selectTownStr+'</div>';
			htmlStr += '<div class="zipSelectorMap-window-control">';
			htmlStr += '<a href="#" class="zipSelectorMap-window-control-validate">'+mapOptions_p.closeBtnStr+'</a>';
			htmlStr += '</div>';
			htmlStr += '</div>';
			htmlStr += '</div>';
	
		$("body").append(htmlStr);
		
		var myLatLng = new google.maps.LatLng(50.84686,4.35349),
			latlngbounds = new google.maps.LatLngBounds()
		
		// General Options for googleMap
		var mapOptions = {
			zoom: 11,
			scrollwheel: false,
			center: myLatLng,
			mapTypeId: google.maps.MapTypeId.RoadMap,
			mapTypeControl: false,
			mapTypeControlOptions: {
				style: google.maps.MapTypeControlStyle.HORIZONTAL_BAR,
				position: google.maps.ControlPosition.TOP_CENTER
			},
			zoomControl: true,
			zoomControlOptions: {
				position: google.maps.ControlPosition.RIGHT_CENTER
			},
			scaleControl: true,
			streetViewControl: false,
			streetViewControlOptions: {
				position: google.maps.ControlPosition.LEFT_TOP
			}
		};
		
		var mapStyleDefault = zipSelectorMapData.getMapStyleDefault();
		var styledMap = new google.maps.StyledMapType(mapStyleDefault, {name: "webulous-default"});
		
		var map = new google.maps.Map(document.getElementById(idTarget_p),mapOptions);
		
		map.mapTypes.set('map_style', styledMap);
		map.setMapTypeId('map_style');	
		
		// Color settings
		
		// mapOptions_p.mainColor;
		// mapOptions_p.secondColor;
	
		var strokeColorDefault = mapOptions_p.secondColor;
		var strokeColorHover = mapOptions_p.secondColor;
		var strokeColorSelected = mapOptions_p.secondColor;
		
		var strokeOpacityDefault = "1";
		var strokeOpacityHover = "1";
		var strokeOpacitySelected = "1";
	
		var strokeWeightDefault = 1;
		var strokeWeightHover = 1;
		var strokeWeightSelected = 1;
		
		var fillColorDefault = mapOptions_p.mainColor;
		var fillColorHover = mapOptions_p.mainColor;
		var fillColorSelected = mapOptions_p.mainColor;
		
		var fillOpacityDefault = "0.15";
		var fillOpacityHover = "0.3";
		var fillOpacitySelected = "0.6";
	
	
		zipSelectorMapData.getTown().forEach(function (element) {
	
			var currentTownPolygon = new google.maps.Polygon({
				paths: element.path,
				strokeColor: strokeColorDefault,
				strokeOpacity: strokeOpacityDefault,
				strokeWeight: strokeWeightDefault,
				fillColor: fillColorDefault,
				fillOpacity: fillOpacityDefault
			});
			currentTownPolygon.setMap(map);
			currentTownPolygon.dataElement = element;
			
			element.polygon = currentTownPolygon;
	
			// event [ resize ]
	
			google.maps.event.addDomListener(window, 'resize', function() {
				var center = map.getCenter();
				google.maps.event.trigger(map, "resize");
				map.setCenter(center);
			});
	
			// mouse [ over ]
			google.maps.event.addListener(currentTownPolygon, "mouseover", function () {
				if (this.dataElement.selected === false) {
					this.setOptions({ strokeColor: strokeColorHover, strokeOpacity: strokeOpacityHover, strokeWeight: strokeWeightHover, fillColor: fillColorHover, fillOpacity: fillOpacityHover });
				}
			});
	
			// mouse [ out ]
			google.maps.event.addListener(currentTownPolygon, "mouseout", function () {
				if (this.dataElement.selected === false) {
					this.setOptions({ strokeColor: strokeColorDefault, strokeOpacity: strokeOpacityDefault, strokeWeight: strokeWeightDefault, fillColor: fillColorDefault, fillOpacity: fillOpacityDefault });
				}
			});
	
			// mouse [ click ]
			google.maps.event.addListener(currentTownPolygon, "click", function () {
	
				if (this.dataElement.selected === false) {
					//Select polygon	
					this.setOptions({ strokeColor: strokeColorSelected, strokeOpacity: strokeOpacitySelected, strokeWeight: strokeWeightSelected, fillColor: fillColorSelected, fillOpacity: fillOpacitySelected });
					this.dataElement.selected = true;
				} else {
					//Unselect polygon
					this.setOptions({ strokeColor: strokeColorDefault, strokeOpacity: strokeOpacityDefault, strokeWeight: strokeWeightDefault, fillColor: fillColorDefault, fillOpacity: fillOpacityDefault });
					this.dataElement.selected = false;
				}
	
				refreshTownList();
	
			});
	
		});
		
	
	
		
	
		
	
		
		
		
	};
	
	