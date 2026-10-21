/* global window, event, bold, utc, length, output, ubound, $ */



/* --------------------------------------------

 Fancy box

-------------------------------------------- */



//		slideShow  : true,

//		fullScreen : true,

//		closeBtn   : true,

//		infobar : false,

//		arrows : true,

//		loop : true,



$(function () {

 

	$("[data-fancybox]").fancybox({

		

		speed : 330,



		fullScreen : true,

		closeBtn   : true,

		infobar: false,

		loop : false,

		

		 slideShow: {

			autoStart: false,

			speed: 3000

		 },



		thumbs : {

			showOnStart   : false // Display thumbnails on opening

		},

		margin : [ 50, 50, 50, 50 ],



		opacity : 'auto',

		image : {

			protect: false

		}

		

	});



});



//"toggleControls"  - show/hide controls



/* --------------------------------------------

	Cookie utility

-------------------------------------------- */



var setCookie = function (cname, cvalue, exdays) {

	var d = new Date();

	d.setTime(d.getTime() + (exdays * 24 * 60 * 60 * 1000));

	var expires = "expires="+d.toUTCString();

	document.cookie = cname + "=" + cvalue + ";" + expires + ";path=/";

}



var getCookie = function (cname) {

	var name = cname + "=";

	var ca = document.cookie.split(';');

	for(var i = 0; i < ca.length; i++) {

		var c = ca[i];

		while (c.charAt(0) == ' ') {

			c = c.substring(1);

		}

		if (c.indexOf(name) == 0) {

			return c.substring(name.length, c.length);

		}

	}

	return "";

}



/* --------------------------------------------

	Accept cookie

-------------------------------------------- */



$(function () {

	"use strict";

	

	var initAlertCookie = function() {

		

		//load cookie

		var asAcceptedCookie = getCookie("asAcceptedCookie");

		

		//check cookie

		if (asAcceptedCookie != "") {

			//hidden alert cookie

			$(".alert-cookie").addClass("hidden");

		}



	};	

	

	$("#accept_cookie").on("click", function(e) {

		$(".alert-cookie").addClass("alert-cookie__hidden");

		setCookie("asAcceptedCookie", "true", 365);

		event.preventDefault();

	});

	

	initAlertCookie();

	

});



/* --------------------------------------------

 Hamburger menu

-------------------------------------------- */



$(function () {



	var $hamburger = $(".hamburger");

	

	$(".nav-hamburger").on("click", function(e) {

		

		$hamburger.toggleClass("is-active");



		$(".section-nav__nav").toggleClass("active");

		$(".nav-hamburger__label").toggleClass("active");

		$(".nav-hamburger").toggleClass("active");

		$(".section-nav").toggleClass("active");

		



		// $(".nav").toggleClass("in");

		$("body").toggleClass("mobile-menu-is-active");



		return false;

			

	});



});



/* --------------------------------------------

 Anim

-------------------------------------------- */



$(function($) {

	"use strict";

	$("body").removeClass("anim");

	var i=0;

	$(".footer__signature").on("click", function(e) { i++; if (i==5) { $("body").addClass("anim"); } });

});



/* --------------------------------------------

 Smooth scrolling

-------------------------------------------- */



$(function () {

	

	if ($('[data-navbar-dynamic="true"]').length >0 ) {

	

		// Add smooth scrolling on all links inside the navbar

		$(".nav-default__link, .section-site-map__link").on('click', function(event) {



			// Make sure this.hash has a value before overriding default behavior

			if (this.hash !== "") {



				// Store hash

				var hash = this.hash;

				

				if ( ($(hash).length) > 0) {

					

					// Prevent default anchor click behavior

					event.preventDefault();



					// Using jQuery's animate() method to add smooth page scroll

					// The optional number (800) specifies the number of milliseconds it takes to scroll to the specified area

					$('html, body').animate({

						scrollTop: $(hash).offset().top-90

					}, 800, function(){



					// Add hash (#) to URL when done scrolling (default click behavior)

					// window.location.hash = hash;

					});

				} // End if



			} // End if



		});

		

	}

	

	

	// Add smooth scrolling on all [data-smooth-scroll]

	$("[data-smooth-scroll]").on('click', function(event) {

		

		console.log("click");



		// Make sure this.hash has a value before overriding default behavior

		if (this.hash !== "") {

			

			console.log("this.hash : "+this.hash);



			// Prevent default anchor click behavior

			event.preventDefault();

			// event.stopPropagation();



			// Store hash

			var hash = this.hash;



			// Height of the navbar

			var headerHeight = $("header").height();



			$('html, body').animate({

				scrollTop: $(hash).offset().top-headerHeight+1

			}, 800, function(){



			// Add hash (#) to URL when done scrolling (default click behavior)

//				window.location.hash = hash;

			});



		} // End if



	});



});





/*----------------------------------------------------



	back to top button



----------------------------------------------------*/



var initGoToTopButton = function() {

    

    "use strict";

	

	// When the user scrolls down 200px from the top of the document, show the button

	window.onscroll = function() {

		scrollFunction()

	};

	

	// Add button in HTML

	$( "body" ).append('<div class="btn-go-top-container"><div class="btn-go-top-container__spacer"></div><a id="gotoTopButton" class="btn-go-top" href="#home" title="Go to top"></a></div>' );

	

//	$( "body" ).append('<a id="gotoTopButton" class="btn-go-top" href="#home" title="Go to top"><i class="far fa-arrow-up"></i></a>' );



	// Show or hide button

	function scrollFunction() {

		if (document.body.scrollTop > 200 || document.documentElement.scrollTop > 200) {

			$("#gotoTopButton").addClass("show");

		} else {

			$("#gotoTopButton").removeClass("show");

		}

	}



	// Additional margin bottom if "nav-mobile-cta" is present

	if( $(".nav-mobile-cta").length>0 ) {

		$("#gotoTopButton").addClass("nav-mobile-cta-margin-bottom");

		$("body").addClass("nav-mobile-cta-margin-bottom");

	}

	if( $(".nav-mobile-cta-estate-detail").length>0 ) {

		$("#gotoTopButton").addClass("nav-mobile-cta-estate-detail-margin-bottom");

		$("body").addClass("nav-mobile-cta-estate-detail-margin-bottom");

	}

	

	

	// Interactivity : back to top

	$(document).ready(function() {

		// Add smooth scrolling

		$("#gotoTopButton").on('click', function() {



			$('html, body').animate({

				scrollTop: 0

			}, 900, function() {



			});

			

		});



	})

	

}



initGoToTopButton();



/*----------------------------------------------------



 counter

 

----------------------------------------------------*/



$(document).ready(function () {

	

    $('.count').each(function () {



        $(this).removeClass("count-hidden");



        $(this).prop('Counter', 0).animate({

            Counter: $(this).text()

        }, {

                duration: 2000,

                easing: 'swing',

                step: function (now) {

                    $(this).text(Math.ceil(now));

                }

            });

    });



});   





/*----------------------------------------------------



 slider

 

----------------------------------------------------*/



$(function($) {

	"use strict";



	// ------------------------- common ---------------------------



	var sliders



	// https://github.com/ganlanyuan/tiny-slider/issues/103

	// forEach function from Todd Motto's  blog, as mentioned above

	var forEach = function (array, callback, scope) {

		for (var i = 0; i < array.length; i++) {

		callback.call(scope, i, array[i]); // passes back stuff we need

		}

	};



	// ------------------------- data-slider="4-items ---------------------------



	sliders = document.querySelectorAll('[data-slider="4-items"]');



	forEach(sliders, function (index, value) {

		

		// now fetch YOUR-SLUG from html (use value.getAttribute('data-name') for < IE10 support)

		// let thumbnail = value.dataset.name;

		// let slider = tns({

		//   container: value,

		//   navContainer: '.thumbnails-' + thumbnail,

		//   navAsThumbnails: true,

		//   // more options ..

		// });



		let slider = tns({

		  container: value,

		  items: 4,

		  slideBy: 'page',

		//   gutter: '0', // Space between slides (in "px"). 65

		   edgePadding: '10', // Space on the outside (in "px"). 65

		  controlsPosition: 'bottom',

		  controlsText: ['<i class="far fa-angle-left"></i>', '<i class="far fa-angle-right"></i>'], //Text or markup in the prev/next buttons.

		  nav: true, //Controls the display and functionalities of nav components (dots). If true, display the nav and add all functionalities.

		  navPosition: 'bottom',

		  autoplay: true,

		  autoplayButtonOutput: false, //Output autoplayButton markup when autoplay is true but a customized autoplayButton is not provided.

		  mouseDrag: true,

		  responsive:{

			  0:{

				  items:1,

				  // nav:false,

				  //stagePadding: 32,

			  },

			  576:{

				  //stagePadding: 32,

				  // nav:false,

				  items:2

			  },

			  767:{

				  items:2

			  },

			  993:{

				  items:3

			  },

			  1201:{

				  items:4

			  }

		  }

		});

	});





	// ------------------------- data-slider="3-photos" ---------------------------



	sliders = document.querySelectorAll('[data-slider="3-photos"]');



	forEach(sliders, function (index, value) {

		

		// now fetch YOUR-SLUG from html (use value.getAttribute('data-name') for < IE10 support)

		// let thumbnail = value.dataset.name;

		// let slider = tns({

		//   container: value,

		//   navContainer: '.thumbnails-' + thumbnail,

		//   navAsThumbnails: true,

		//   // more options ..

		// });



		let slider = tns({

		  container: value,

		  center: true,

		  items: 3,

		  slideBy: 'page',

		//   gutter: '0', // Space between slides (in "px"). 65

		//    edgePadding: '300', // Space on the outside (in "px"). 65

		  controlsPosition: 'bottom',

		  controlsText: ['<i class="far fa-angle-left"></i>', '<i class="far fa-angle-right"></i>'], //Text or markup in the prev/next buttons.

		  nav: true, //Controls the display and functionalities of nav components (dots). If true, display the nav and add all functionalities.

		  navPosition: 'bottom',

		  autoplay: true,

		  autoplayButtonOutput: false, //Output autoplayButton markup when autoplay is true but a customized autoplayButton is not provided.

		  mouseDrag: true,

		  animateIn: "jello",

		  animateOut: "rollOut",

		  responsive:{

			  0:{
				  items:1,
				  // nav:false,
				  edgePadding: '64',
			  	// stagePadding: '64',
			  },
			  576:{
				  //stagePadding: 32,
				  // nav:false,
					edgePadding: '100',
					items:1
			  },
			  767:{
					edgePadding: '200',
					items:1
			  },
			  993:{
					edgePadding: '300',
					items:1
			  },
			  1201:{
					edgePadding: '350',
					items:1
			  },
			  1600:{
					edgePadding: '450',
					items:1
			  },
			  1920:{
					edgePadding: '650',
					items:1
			  },
			  2440:{
					edgePadding: '450',
					items:3
			  }

		  }

		});



		// slider.getInfo().index;



		// get current item

		var indexCurrent = slider.getInfo().index;



		// add special-active on current item (only one)

		slider.getInfo().slideItems[indexCurrent].classList.add('special-active');



		// select active item and active cloned item when is slided

		slider.events.on('indexChanged', function (info, eventName) {



			var indexCurrent = info.index;



				// remove all special-active item (active item is used by tiny slider and is all items visible on screen)

				$(".tns-item").removeClass("special-active");



				// add special-active on current item (only one)

				info.slideItems[indexCurrent].classList.add('special-active');



				// get src img of the current item

				var currentSrc = $(".special-active > picture > img").attr("src");



				// add special-active on all cloned item with the same img src of the active item

				$("img[src='"+currentSrc+"']").parents("li").addClass("special-active");

				

		  });





	});







	// ------------------------- data-slider="team-smartphone" ---------------------------



	sliders = document.querySelectorAll('[data-slider="team-smartphone"]');



	forEach(sliders, function (index, value) {

		

		// now fetch YOUR-SLUG from html (use value.getAttribute('data-name') for < IE10 support)

		// let thumbnail = value.dataset.name;

		// let slider = tns({

		//   container: value,

		//   navContainer: '.thumbnails-' + thumbnail,

		//   navAsThumbnails: true,

		//   // more options ..

		// });



		let slider = tns({

		  container: value,

		  items: 4,

		  slideBy: 'page',

		//   gutter: '0', // Space between slides (in "px"). 65

		   edgePadding: '10', // Space on the outside (in "px"). 65

		  controlsPosition: 'bottom',

		  controlsText: ['<i class="far fa-angle-left"></i>', '<i class="far fa-angle-right"></i>'], //Text or markup in the prev/next buttons.

		  nav: true, //Controls the display and functionalities of nav components (dots). If true, display the nav and add all functionalities.

		  navPosition: 'bottom',

		  autoplay: true,

		  autoplayButtonOutput: false, //Output autoplayButton markup when autoplay is true but a customized autoplayButton is not provided.

		  mouseDrag: true,

		  responsive:{

			  0:{

				  items:1,

				  // nav:false,

				  //stagePadding: 32,

			  },

			  576:{

				  //stagePadding: 32,

				  // nav:false,

				  items:1

			  },

			  767:{

				disable: true

			  }

		  }

		});

	});



	// ------------------------- data-slider="list-card-key-number-smartphone" ---------------------------



	sliders = document.querySelectorAll('[data-slider="list-card-key-number-smartphone"]');



	forEach(sliders, function (index, value) {

		

		// now fetch YOUR-SLUG from html (use value.getAttribute('data-name') for < IE10 support)

		// let thumbnail = value.dataset.name;

		// let slider = tns({

		//   container: value,

		//   navContainer: '.thumbnails-' + thumbnail,

		//   navAsThumbnails: true,

		//   // more options ..

		// });



		let slider = tns({

		  container: value,

		  items: 4,

		  slideBy: 'page',

		//   gutter: '0', // Space between slides (in "px"). 65

		   edgePadding: '10', // Space on the outside (in "px"). 65

		  controlsPosition: 'bottom',

		  controlsText: ['<i class="far fa-angle-left"></i>', '<i class="far fa-angle-right"></i>'], //Text or markup in the prev/next buttons.

		  nav: true, //Controls the display and functionalities of nav components (dots). If true, display the nav and add all functionalities.

		  navPosition: 'bottom',

		  autoplay: true,

		  autoplayButtonOutput: false, //Output autoplayButton markup when autoplay is true but a customized autoplayButton is not provided.

		  mouseDrag: true,

		  responsive:{

			  0:{

				  items:1,

				  // nav:false,

				  //stagePadding: 32,

			  },

			  576:{

				  //stagePadding: 32,

				  // nav:false,

				  items:2

			  },

			  767:{

				disable: true

			  }

		  }

		});

	});



	// ------------------------- data-slider="quotes" ---------------------------



	sliders = document.querySelectorAll('[data-slider="quotes"]');



	forEach(sliders, function (index, value) {

		

		// now fetch YOUR-SLUG from html (use value.getAttribute('data-name') for < IE10 support)

		// let thumbnail = value.dataset.name;

		// let slider = tns({

		//   container: value,

		//   navContainer: '.thumbnails-' + thumbnail,

		//   navAsThumbnails: true,

		//   // more options ..

		// });



		let slider = tns({

		  container: value,

		  items: 1,

		  slideBy: 'page',

		//   gutter: '0', // Space between slides (in "px"). 65

		  edgePadding: '0', // Space on the outside (in "px"). 65

		  controlsPosition: 'bottom',

		  controlsText: ['<i class="far fa-angle-left"></i>', '<i class="far fa-angle-right"></i>'], //Text or markup in the prev/next buttons.

		  controls: false,

		  nav: true, //Controls the display and functionalities of nav components (dots). If true, display the nav and add all functionalities.

		  navPosition: 'bottom',

		  autoplay: true,

		  autoplayButtonOutput: false, //Output autoplayButton markup when autoplay is true but a customized autoplayButton is not provided.

		  mouseDrag: true,

		});

	});



	// ------------------------- data-slider="mini-slider" ---------------------------



	sliders = document.querySelectorAll('[data-slider="mini-slider"]');



	forEach(sliders, function (index, value) {

		

		// now fetch YOUR-SLUG from html (use value.getAttribute('data-name') for < IE10 support)

		// let thumbnail = value.dataset.name;

		// let slider = tns({

		//   container: value,

		//   navContainer: '.thumbnails-' + thumbnail,

		//   navAsThumbnails: true,

		//   // more options ..

		// });



		let slider = tns({

			container: value,

			items: 1,

			slideBy: 'page',

		//   gutter: '0', // Space between slides (in "px"). 65

			edgePadding: '0', // Space on the outside (in "px"). 65

			controlsPosition: 'bottom',

			controlsText: ['<i class="far fa-angle-left"></i>', '<i class="far fa-angle-right"></i>'], //Text or markup in the prev/next buttons.

			controls: true,

			nav: false, //Controls the display and functionalities of nav components (dots). If true, display the nav and add all functionalities.

			navPosition: 'bottom',

			autoplay: false,

			autoplayButtonOutput: false, //Output autoplayButton markup when autoplay is true but a customized autoplayButton is not provided.

			mouseDrag: true,

		});



	});



	// ------------------------- data-slider="big-slider" estate detail main slider ---------------------------



	sliders = document.querySelectorAll('[data-slider="big-slider"]');



	forEach(sliders, function (index, value) {

		

		// now fetch YOUR-SLUG from html (use value.getAttribute('data-name') for < IE10 support)

		// let thumbnail = value.dataset.name;

		// let slider = tns({

		//   container: value,

		//   navContainer: '.thumbnails-' + thumbnail,

		//   navAsThumbnails: true,

		//   // more options ..

		// });



		let slider = tns({

			container: value,

			items: 1,

			slideBy: 'page',

		//   gutter: '0', // Space between slides (in "px"). 65

			edgePadding: '0', // Space on the outside (in "px"). 65

			controlsPosition: 'bottom',

			controlsText: ['<i class="far fa-angle-left"></i>', '<i class="far fa-angle-right"></i>'], //Text or markup in the prev/next buttons.

			controls: true,

			nav: true, //Controls the display and functionalities of nav components (dots). If true, display the nav and add all functionalities.

			navPosition: 'bottom',

			autoplay: false,

			autoplayButtonOutput: false, //Output autoplayButton markup when autoplay is true but a customized autoplayButton is not provided.

			mouseDrag: true,

		});



	});



	// ------------------------- slider tuning event ---------------------------



	$(".card-estate[href]").on("click", function(event) {

		// Avoid link to href when button next prev of tiny slider is clicked

		if ( String(event.target) == "[object HTMLButtonElement]" ) {

			return false;

		}

	});



	// $("#accept_cookie").on("click", function(e) {

	// 	$(".alert-cookie").addClass("alert-cookie__hidden");

	// 	setCookie("asAcceptedCookie", "true", 365);

	// 	event.preventDefault();

	// });

	



	// <a href="index.html" class="card-estate"></a>

	

})





/*----------------------------------------------------



 Google maps

 

----------------------------------------------------*/





/* --------------------------------------------

	Accept cookie

-------------------------------------------- */



$(function () {

	"use strict";

	

	var initAlertCookie = function() {

		

		//load cookie

		var asAcceptedCookie = getCookie("asAcceptedCookie");

		

		//check cookie

		if (asAcceptedCookie != "") {

			//hidden alert cookie

			$(".alert-cookie").addClass("hidden");

		}



	};	

	

	$("#accept_cookie").on("click", function(e) {

		$(".alert-cookie").addClass("alert-cookie__hidden");

		setCookie("asAcceptedCookie", "true", 365);

		event.preventDefault();

	});

	

	initAlertCookie();

	

});







/*----------------------------------------------------



 Google maps

 

----------------------------------------------------*/





/* --------------------------------------------

	Accept cookie

-------------------------------------------- */



$(function () {

	"use strict";

	

	var initAlertCookie = function() {

		

		//load cookie

		var asAcceptedCookie = getCookie("asAcceptedCookie");

		

		//check cookie

		if (asAcceptedCookie != "") {

			//hidden alert cookie

			$(".alert-cookie").addClass("hidden");

		}



	};	

	

	$("#accept_cookie").on("click", function(e) {

		$(".alert-cookie").addClass("alert-cookie__hidden");

		setCookie("asAcceptedCookie", "true", 365);

		event.preventDefault();

	});

	

	initAlertCookie();

	

});







/* --------------------------------------------

 Maps skin

 https://mapstyle.withgoogle.com/

-------------------------------------------- */



var mapStyleDefault = [

    {

        "featureType": "all",

        "elementType": "geometry.fill",

        "stylers": [

            {

                "visibility": "on"

            }

        ]

    },

    {

        "featureType": "administrative.country",

        "elementType": "labels.text",

        "stylers": [

            {

                "visibility": "simplified"

            },

            {

                "color": "#f5f4f2"

            }

        ]

    },

    {

        "featureType": "administrative.province",

        "elementType": "labels.text",

        "stylers": [

            {

                "visibility": "simplified"

            },

            {

                "color": "#f5f4f2"

            }

        ]

    },

    {

        "featureType": "administrative.locality",

        "elementType": "labels.text",

        "stylers": [

            {

                "visibility": "simplified"

            },

            {

                "color": "#f5f4f2"

            }

        ]

    },

    {

        "featureType": "administrative.neighborhood",

        "elementType": "labels.text",

        "stylers": [

            {

                "visibility": "simplified"

            },

            {

                "color": "#f5f4f2"

            }

        ]

    },

    {

        "featureType": "landscape",

        "elementType": "geometry.fill",

        "stylers": [

            {

                "color": "#2e2e2e"

            }

        ]

    },

    {

        "featureType": "landscape.man_made",

        "elementType": "geometry.stroke",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "landscape.man_made",

        "elementType": "labels.text",

        "stylers": [

            {

                "visibility": "simplified"

            }

        ]

    },

    {

        "featureType": "poi",

        "elementType": "geometry.fill",

        "stylers": [

            {

                "color": "#866644"

            },

            {

                "visibility": "simplified"

            }

        ]

    },

    {

        "featureType": "poi",

        "elementType": "labels",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "poi",

        "elementType": "labels.text",

        "stylers": [

            {

                "visibility": "simplified"

            },

            {

                "color": "#866644"

            }

        ]

    },

    {

        "featureType": "poi",

        "elementType": "labels.icon",

        "stylers": [

            {

                "visibility": "on"

            }

        ]

    },

    {

        "featureType": "poi.attraction",

        "elementType": "labels",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "poi.business",

        "elementType": "labels",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "poi.medical",

        "elementType": "labels",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "poi.park",

        "elementType": "geometry.fill",

        "stylers": [

            {

                "visibility": "on"

            }

        ]

    },

    {

        "featureType": "poi.place_of_worship",

        "elementType": "labels",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "poi.school",

        "elementType": "labels",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "poi.sports_complex",

        "elementType": "labels",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "road",

        "elementType": "geometry.fill",

        "stylers": [

            {

                "color": "#696361"

            }

        ]

    },

    {

        "featureType": "road",

        "elementType": "geometry.stroke",

        "stylers": [

            {

                "visibility": "off"

            }

        ]

    },

    {

        "featureType": "road",

        "elementType": "labels",

        "stylers": [

            {

                "hue": "#ff0000"

            }

        ]

    },

    {

        "featureType": "road",

        "elementType": "labels.text",

        "stylers": [

            {

                "color": "#f5f4f2"

            },

            {

                "visibility": "simplified"

            }

        ]

    },

    {

        "featureType": "water",

        "elementType": "geometry.fill",

        "stylers": [

            {

                "color": "#446d86"

            }

        ]

    }

]





/* --------------------------------------------

 Maps

-------------------------------------------- */



$(function() {

	"use strict";	

	

	if ( $("#googleMap").length > 0 ) {

		

		var geocoder = new google.maps.Geocoder();



		var initGoogleMap = function () {



			var options = {

				mapTypeControlOptions: {

					mapTypeIds: ['Styled']

				},

				zoom: 11,

				scrollwheel: false,

				navigationControl: false,

				mapTypeControl: false,

				zoomControl: true,

				disableDefaultUI: true,	

				mapTypeId: 'Styled'

			};



			var div = document.getElementById('googleMap');



			var map = new google.maps.Map(div, options);

			var bounds = new google.maps.LatLngBounds();



			var markersGoogleMaps_array = new Array();

			var lastActiveMarker_i = -1; // last active marker

			var lastActiveMarker

			

			var initialTitle = $("[data-map-title] h3").html();

			var initialText = $("[data-map-text]").html();

			var initialPict = $("[data-map-pict]").attr("style");

			

			$( "body" ).append('<div style="position:absolute; width:10px; height:10px; top:-100px; left:-100px" id="imgCache"></div>' );

			

			//-------------------------------------------------------------------------------------------



			// Loop through our array of markers & place each one on the map  

			for( var i = 0; i < markers.length; i++ ) {



				// position

				var position = new google.maps.LatLng(markers[i][1], markers[i][2]);

				bounds.extend(position);

				

				// set image picto and size

				var image = {

					url: markers[i][0],

					

					size: new google.maps.Size(parseInt(markers[i][3]), parseInt(markers[i][4])),

					scaledSize: new google.maps.Size(parseInt(markers[i][3]), parseInt(markers[i][4])),

					anchor: new google.maps.Point(parseInt(markers[i][5]), parseInt(markers[i][6])),			

//					origin: new google.maps.Point(0, 0),

					

				};



				// set marker

				var marker = new google.maps.Marker({

					position: position,

					map: map,

					draggable:false,

					animation: google.maps.Animation.DROP,

					icon: image,

					title: markers[i][7],

					zIndex: i+2

				});



				markersGoogleMaps_array[i] = marker;

				

				//Construct picture cache



				if (markers[i][10]!=undefined) {

					$( "#imgCache" ).append('<img style="width:20px;" src="'+markers[i][10]+'">' );

				}

				

				//----- Interactivity ----- 

				

				



//				data-map-title  7

//				data-map-text   8

//				data-map-pict   10				

				

				

		        google.maps.event.addListener(marker, 'click', (function(marker, i) {

		            return function() {

						

						var currentID = i;

						

						

						var title = markers[i][7];

						var text = "<p>"+markers[i][9]+"</p>"+"<p><strong>"+markers[i][8]+"</strong></p>";

						var pict = "background-image: url('"+markers[i][10]+"')";

						

						console.log(pict);

						

						if ( pict == "background-image: url('undefined')" ) {

							title = initialTitle;

							text = initialText;

							pict = initialPict;

						}



						$("[data-map-title]").removeClass("active");

						$("[data-map-title] h3").html(title);

						$("[data-map-title]").addClass("active");

						

						$("[data-map-text]").removeClass("active");

						$("[data-map-text]").html(text);

						$("[data-map-text]").addClass("active");

						

						$("[data-map-pict]").removeClass("active");

						$("[data-map-pict]").attr("style",pict);

						$("[data-map-pict]").addClass("active");

						

//						var $currentTarget = $('[data-map-text-id="'+currentID+'"]');



//						$("[data-map-text-id]").removeClass("active");

//						$currentTarget.addClass("active");

						



						//----- Change the marker icon -----

						

//						var defaultIcon = baseUrlWebSite +"/"+ markers[i][0];

//						defaultIcon = defaultIcon.replace(".png","-on.png");

//						

//						var image = {

//							url: defaultIcon,

//							scaledSize: new google.maps.Size(markerWidth, markerHeight),

//							size: new google.maps.Size(markerWidth, markerHeight),

//							anchor: new google.maps.Point(markerAnchorX, markerAnchorY)

//						};

//						

//						marker.setIcon(image);

						

						//----- Restore last icon on initial state -----

						

//						if (lastActiveMarker_i > -1 && lastActiveMarker_i !=i ) {

//							defaultIcon = baseUrlWebSite +"/"+ markers[lastActiveMarker_i][0];

//							

//							var image = {

//								url: defaultIcon,

//								scaledSize: new google.maps.Size(markerWidth, markerHeight),

//								size: new google.maps.Size(markerWidth, markerHeight),

//								anchor: new google.maps.Point(markerAnchorX, markerAnchorY)

//							};

//

//							lastActiveMarker.setIcon(image);

//						}

//						

//						lastActiveMarker = marker;

//						lastActiveMarker_i = i;

						

		            }

		        })(marker, i));

				

				

				

				

			}



			

			console.log("markers.length : "+markers.length);

			

			// Automatically center the map fitting all markers on the screen

			

//			var center = map.getCenter();

//			map.setCenter(center);

//			map.setZoom(1);

			

			if (markers.length<2) {

				// si point unique sur la carte

				map.setCenter(position);

				map.setZoom(16);

				

			} else {

				// si plusieurs points sur la carte

				map.fitBounds(bounds,  5 );

			}

			

			



			//-------------------------------------------------------------------------------------------





			var styledMapType = new google.maps.StyledMapType(mapStyleDefault, { name: 'Styled' });



			map.mapTypes.set('Styled', styledMapType);



			// event [ resize ] si plusieurs points

			if (markers.length>1) {

				google.maps.event.addDomListener(window, 'resize', function() {

					var center = map.getCenter();

					google.maps.event.trigger(map, "resize");

					map.setCenter(center);

					// Automatically center the map fitting all markers on the screen

					map.fitBounds(bounds,  5 );

				});

			}



		}



		google.maps.event.addDomListener(window, 'load', initGoogleMap);



	}



});









/* --------------------------------------------------------

 ANIMATED PAGE ON REVEALED

----------------------------------------------------------- */

$(function($) {

	

	//	$.appear('.animated', {force_process:true} ); // It supports optional hash with "force_process" and "interval" keys. Check source code for details.



	var activeAppear = true;

	

	if (activeAppear===true) {

	

		$('[data-animation]').appear(); //$('.animated').appear();



		$('[data-animation]').addClass('invisible animated');



		//$('.animated').on('appear', function(event, $all_appeared_elements) {

		$('[data-animation]').on('appear', function(event, $all_appeared_elements) {



			// this element is now inside browser viewport



			var $this = $(this);

			var dataAnimation = $this.attr("data-animation");

			var dataAnimationDelay = $this.attr("data-animation-delay");



			// default value

			if ( dataAnimation === undefined ) { dataAnimation = "fadeIn"; }

			if ( dataAnimationDelay === undefined ) { dataAnimationDelay = 0; } else { dataAnimationDelay = Number(dataAnimationDelay); }



			if ( dataAnimationDelay != 0 ) {

				setTimeout(function(){

					$this.addClass( dataAnimation + " visible" );

					$this.removeClass("invisible");

				}, dataAnimationDelay);



			} else {

				$this.addClass( dataAnimation + " visible" );

				$this.removeClass("invisible");

			}



		});

	

	}

});







/* --------------------------------------------------------

 PAGE LOADER

----------------------------------------------------------- */



$(function() { 

	"use strict";	



	var playAnnimations = true;



	// if (playAnnimations==true) {

	// 	$("body").imagesLoaded(function(){

	// 		initAnimations();

	// 		// $(".loader-item").delay(200).fadeOut();

	// 		// $(".pageloader").delay(300).fadeOut("slow");

	// 	});

	// } else {

	// 	$(".pageloader").remove();

	// }

});



var getIndex = function($obj_p) {

	

	// Find current class

	var currentClass = $obj_p.attr("class");

	

	// Find parent

	var $currentParent = $obj_p.parent();

	

	// Find all children of the parent (find brothers)

	var allChildren = $currentParent.find("."+currentClass);

	

	//Find index of the current child

	var index = $(allChildren).index($obj_p);

	

	return index

}





var addAnimation = function(selector_p,animation_p, animationDelay_p) {

	

	//Add animation

	$( selector_p ).each(function( ) {

		$(this).attr("data-animation-delay",animationDelay_p);

		$(this).attr("data-animation",animation_p);

	});

	

}





var addAnimationGroup = function(selector_p,animation_p, animationDelayIncrement_p) {

	

	//Add animation group

	$( selector_p ).each(function( ) {

		var index = getIndex($(this));

		$(this).attr("data-animation-delay",index*animationDelayIncrement_p);

		$(this).attr("data-animation",animation_p);

	});

	

}







/* --------------------------------------------------------

 ADD ANIMATION

----------------------------------------------------------- */



var initAnimations = function() {

	

	

	//Add simple animation

	

	// selector , animation , animationDelayIncrement



	var beforeAnim = 200;

	

	addAnimation("section","fadeIn",0);

	

	

	addAnimation(".section-splash__main-logo","zoomIn",300);

	

	

	addAnimation(".section-splash__link:nth-child(1)","zoomIn",900);

	addAnimation(".section-splash__link:nth-child(2)","zoomIn",1200);

	

//	addAnimationGroup(".section-splash__link","zoomIn",600);

	

	

	

	addAnimationGroup(".section-intro__macaron","zoomIn",beforeAnim);

	

	

	addAnimationGroup(".section-key-facts__item","zoomIn",beforeAnim);

	

	

	addAnimation(".item-project-spotlight","slideInLeft",beforeAnim);

	addAnimation(".section-news-carousel__list","slideInLeft",beforeAnim);

	

	addAnimation(".section-footer-form__col","slideInLeft",beforeAnim);

	

	

//	addAnimation(".item-project-list .img-utility","slideInLeft",beforeAnim);

//	addAnimation(".item-project-list .item-project-list__aside","slideInLeft",beforeAnim+500);

	

	addAnimationGroup(".section-facts__item","zoomIn",beforeAnim);

	

	

	addAnimationGroup(".mosaic","zoomIn",150);

	

	addAnimation(".section-site-map__item","fadeIn",beforeAnim);

	

	

	

	

	addAnimation(".section-photo-carousel__list","slideInLeft",beforeAnim);

	

	

	addAnimation(".section-facts-anim__item","zoomIn",beforeAnim);

	

	addAnimation(".section-box-title-text .row","zoomIn",beforeAnim);

	

	addAnimation(".member","zoomIn",beforeAnim);

	

	addAnimation(".section-alternate-content__picture","zoomIn",beforeAnim);

	addAnimation(".section-alternate-content__content","zoomIn",beforeAnim+300);

	

	

	

	addAnimation(".item-list-news","zoomIn",beforeAnim);

	

	

	addAnimation(".section-img-carousel","zoomIn",beforeAnim);

	

	

	

	

	addAnimationGroup(".section-plan-download__item-container","zoomIn",150);

	

	addAnimationGroup(".picto-list__item","zoomIn",150);

	

	addAnimationGroup(".fact-list__item","zoomIn",150);

	

	

	

//	addAnimation(".section-intro__text","zoomIn",0);

//	

//	addAnimation(".section-title-text-cta__text","zoomIn",0);	

//	addAnimation(".section-title-text-cta__cta","zoomIn",0);

//	

//	addAnimation(".section-alternate-content__picture","slideInUp",0);

//	addAnimation(".section-alternate-content__content","slideInUp",150);

//	

//	addAnimationGroup(".list-ico-text__item","slideInRight",50);

//	

//	addAnimation(".section-cta__col","zoomIn",0);

	



//	addAnimation("section","slideInLeft",0);



	

	$('[data-animation]').appear(); //$('.animated').appear();



	$('[data-animation]').addClass('invisible animated');



	//$('.animated').on('appear', function(event, $all_appeared_elements) {

	$('[data-animation]').on('appear', function(event, $all_appeared_elements) {



		// this element is now inside browser viewport



		var $this = $(this);

		var dataAnimation = $this.attr("data-animation");

		var dataAnimationDelay = $this.attr("data-animation-delay");



		// default value

		if ( dataAnimation === undefined ) { dataAnimation = "fadeIn"; }

		if ( dataAnimationDelay === undefined ) { dataAnimationDelay = 0; } else { dataAnimationDelay = Number(dataAnimationDelay); }



		if ( dataAnimationDelay != 0 ) {

			setTimeout(function(){

				$this.addClass( dataAnimation + " visible" );

				$this.removeClass("invisible");

			}, dataAnimationDelay);



		} else {

			$this.addClass( dataAnimation + " visible" );

			$this.removeClass("invisible");

		}



	});

	

}





/* --------------------------------------------

 more info

-------------------------------------------- */



$(function() {

	"use strict";	

	

	if ( $(".section-more-info").length > 0 ) {



		

		$(".section-more-info__btn-open").on('click', function(event) {

			$(".section-more-info").addClass("active");

			return false;

		});

		$(".section-more-info__btn-close").on('click', function(event) {

			$(".section-more-info").removeClass("active");

			return false;

		});



		



	}

	

});







/* --------------------------------------------

 mosaic

-------------------------------------------- */



$(function() {

	"use strict";	

	

	if ( $(".mosaic").length > 0 ) {



		// Listen mouse event

		$(".mosaic").mouseenter(function( ) {

			$(this).attr("mouseOver","on");

		}).mouseleave(function( ) {

			$(this).removeAttr("mouseOver");

		});

		

		var totalItem = $(".mosaic__mosaic-1 .mosaic__item").length;

		var lastItemShow = 2;

		var mosaicNth = 0;

				

		//init

		$(".mosaic__mosaic-1 .mosaic__item:nth-child(1)").addClass("active");

		$(".mosaic__mosaic-2 .mosaic__item:nth-child(2)").addClass("active");

		$(".mosaic__mosaic-3 .mosaic__item:nth-child(3)").addClass("active");

		



//		function myTimer() {

		var myTimer = function () {

			

			var attr = $(".mosaic").attr("mouseOver");

			

			//No change if mouse is over

			if ( typeof attr !== typeof undefined && attr !== false ) {} else {

				lastItemShow++;

				if (lastItemShow==totalItem) { lastItemShow=0 };



				mosaicNth++;

				if (mosaicNth==4) { mosaicNth=1 };



				// tire au hasard la mosaÃ¯que Ã  changer

	//			var mosaicNth = Math.floor(Math.random() * 3)+1;



				// cache l'image

				$(".mosaic__mosaic-"+mosaicNth+" .mosaic__item").removeClass("active");



				// montre la nouvelle image

				$(".mosaic__mosaic-"+mosaicNth+" .mosaic__item:nth-child("+(lastItemShow+1)+")").addClass("active");

				

			}

		}



		var myVar = setInterval(myTimer, 2000);



		

	}

	

});







/* --------------------------------------------

 mosaic

-------------------------------------------- */



$(function() {

	"use strict";	

	

	if ( $(".section-mosaic-map").length > 0 ) {





		$("[data-map-text-target]").on('click', function(event) {

			

			var currentID = $(this).attr("data-map-text-target");

			

			var $currentTarget = $('[data-map-text-id="'+currentID+'"]');

			

			$("[data-map-text-id]").removeClass("active");

			

			$currentTarget.addClass("active");

			

//			alert("click : "+currentTarget);

//			$(".section-more-info").addClass("active");

		});







	}

	

});







/* --------------------------------------------

projecten filter

-------------------------------------------- */





$("[data-filter]").on("click", function(e) {

	

	$(".section-project-list__nav .btn").removeClass("btn-secondary");

	$(".section-project-list__nav .btn").addClass("btn-outline-secondary");

	

	$(this).removeClass("btn-outline-secondary");

	$(this).addClass("btn-secondary");

	

	var datafilter = $(this).attr("data-filter");

	

	if (datafilter==".all") {

		$(".section-project-list__col").show();

		$(".section-project-list__carousel, .section-project-list__footer-title, .section-project-list__footer-cta").hide();

		

		

	} else {

		$(".section-project-list__col, .section-project-list__carousel").hide();

		$(".section-project-list__footer-title, .section-project-list__footer-cta").show();

		$(datafilter).show();

	}

	event.preventDefault();

});







/* --------------------------------------------

over picto

-------------------------------------------- */



$(function() {

	

	"use strict";	

	var $target

	

	if ( $("[data-hover]").length > 0 ) {

		

		console.log("activated");



		$("[data-hover]").mouseenter( function(e) {



			$target = $(this).find("img");



			$target.attr("src",$target.attr("data-src-on"));

			event.preventDefault();



		}).mouseleave( function(e) {



			$target = $(this).find("img");



			$target.attr("src",$target.attr("data-src-off"));

			event.preventDefault();



		});



	}

	

});

/* --------------------------------------------

team more

-------------------------------------------- */





$("[data-more-team]").on("click", function(e) {

	

	if (($(this).attr("data-more-team")) =="open") {

		

		//close

		$("[data-more-team-hidden]").addClass("member--hidden");

		$(this).attr("data-more-team","");

		

	} else {

		

		//open

		$(".member--hidden").attr("data-more-team-hidden","");

		$(".member").removeClass("member--hidden");

		$(this).attr("data-more-team","open");	

		

	}

		

	event.preventDefault();

	

});



/* --------------------------------------------

 action bar

-------------------------------------------- */



$(function($) {

	"use strict";

	

	if ($('[data-navbar-dynamic="false"]').length > 0) {

		$("body").addClass("navbar-reduced");

	} else {

	



		var header_height = $("header").height();



		var footer_height = $("footer").height();

		var body_height = $("body").height();

		var window_scrollTop = $(window).scrollTop();

		var window_height = $( window ).height();

		var window_width = $( window ).width();

		var actionBar_height = 90; /*$(".action-bar").height();*/



		/* init */

		var actionBar_init = function () {



			$(window).scroll(function() {

				actionBar_refresh();

			});



			actionBar_refresh();



		};



		/* refresh */

		var actionBar_refresh = function () {



				window_scrollTop = $(window).scrollTop();

				window_height = $( window ).height();

				footer_height = $("footer").height();

				body_height = $("body").height();



	//			console.log("header_height : "+header_height + " body_height : "+body_height + " actionBar_height : " + actionBar_height + " window_scrollTop : " + window_scrollTop + " window_height : " + window_height );



				if ( window_scrollTop > header_height ) {

					$("body").addClass("navbar-reduced");

				} else {

					$("body").removeClass("navbar-reduced");

				}



				if ( (window_scrollTop+window_height-actionBar_height) < (body_height-footer_height) && window_scrollTop>(window_height) )  {

					/* show bar with transition */

					$( ".action-bar" ).removeClass("action-bar--fixed-out");

					$( ".action-bar" ).addClass("action-bar--fixed");



				} else {

					if (window_scrollTop>(window_height)) {

						/* intial state of the bar */

						$( ".action-bar" ).removeClass("action-bar--fixed");

						$( ".action-bar" ).removeClass("action-bar--fixed-out");



					} else {

						/* hide bar with transition */

						$( ".action-bar" ).removeClass("action-bar--fixed");

						$( ".action-bar" ).addClass("action-bar--fixed-out");

					}

				}



		};



		/* call init if an action-bar is present*/

	//	if ( $( ".action-bar" ).length && window_width > 768 ) {

	// 		actionBar_init();

	//	}



		actionBar_init();

	}

});



/* --------------------------------------------

 section itinerary

-------------------------------------------- */



$(function($) {

	"use strict";

	

	if ($('[data-itinerary-btn]').length > 0) {

		

		$('[data-itinerary-btn]').on('click', function(event) {

			

			var itineraryFrom = $('[data-itinerary-from]').val();

			var itineraryTo = $('[data-itinerary-to]').children("option:selected").val();

			

			console.log(itineraryFrom);

			console.log(itineraryTo);

			

			window.open("https://maps.google.com/maps?saddr="+itineraryFrom+"&daddr="+itineraryTo);

			

			return false;

		});

		

	}

	

});

		

/* --------------------------------------------

 widget-agencies-map

-------------------------------------------- */	



$(function($) {

	"use strict";

	

	if ($('.widget-agencies-map').length > 0) {



		$(".agencies__map-svg > *").each(function () {

			$(this).attr("data-class-initial",$(this).attr("class"));

		});



		// Interactivité sur la liste

		$(".mini-list-agency__link").mouseenter( function() {



			var cityName = $(this).attr("data-city");

			var regionName = $(this).attr("data-region");



			var $city = $(".agencies__map-svg [data-city="+cityName+"]");

			var $region = $(".agencies__map-svg [data-region="+regionName+"]");



			$city.each(function () {

				var currentClass = $(this).attr("class");

				$(this).attr("class",currentClass+" active");

			});

			$region.each(function () {

				var currentClass = $(this).attr("class");

				$(this).attr("class",currentClass+" active");

			});



		}).mouseleave( function() {

			var $svgElement = $(".agencies__map-svg > *");

			$svgElement.each(function () {

				$(this).attr("class",$(this).attr("data-class-initial"));

			});



		});

		

	}

	

});		



/* --------------------------------------------

 SumoSelect

-------------------------------------------- */



$(function($) {

	"use strict";

	

	window.sumoselect = $('[multiple].sumo-select').SumoSelect({okCancelInMulti:true, selectAll:true });



	window.sumoselect = $('.sumo-select').not("[multiple]").SumoSelect({okCancelInMulti:true, selectAll:true });

	

	/* Must target multiple and simple separatly to avoid bug with buttons ok/annuler */

	

	/* FR */

	$("[multiple].sumo-select--fr").SumoSelect({

		okCancelInMulti: true ,

		triggerChangeCombined: true,

		forceCustomRendering: true,

		captionFormat:'{0} Sélectionné(s)',

        captionFormatAllSelected:'Tous les {0} séléctionnés !',

		placeholder: 'Sélectionner...',

		locale: ['Ok', 'Annuler', 'Tout sélectionner'],

		search: true,

		searchText: 'Rechercher ici...',

		selectAll: true,

		noMatch: 'Pas de résultats pour "{0}"'

	});

	$(".sumo-select--fr").not("[multiple]").SumoSelect({

		okCancelInMulti: false ,

		triggerChangeCombined: true,

		forceCustomRendering: true,

		captionFormat:'{0} Sélectionné(s)',

        captionFormatAllSelected:'Tous les {0} séléctionnés !',

		placeholder: 'Sélectionner...',

		locale: ['Ok', 'Annuler', 'Tout sélectionner'],

		search: false,

		searchText: 'Rechercher ici...',

		noMatch: 'Pas de résultats pour "{0}"'

	});



	/* NL */

	$("[multiple].sumo-select--nl").SumoSelect({

		okCancelInMulti: true ,

		triggerChangeCombined: true,

		forceCustomRendering: true,

        captionFormat:'{0} Selected',

        captionFormatAllSelected:'{0} all selected!',

		placeholder: 'Select Here',

		locale: ['Ok', 'Cancel', 'Select All'],

		search: true,

		searchText: 'Enter here...',

		noMatch: 'No matches for "{0}"'

	});

	$(".sumo-select--nl").not("[multiple]").SumoSelect({

		okCancelInMulti: true ,

		triggerChangeCombined: true,

		forceCustomRendering: true,

        captionFormat:'{0} Selected',

        captionFormatAllSelected:'{0} all selected!',

		placeholder: 'Select Here',

		locale: ['Ok', 'Cancel', 'Select All'],

		search: false,

		searchText: 'Enter here...',

		noMatch: 'No matches for "{0}"'

	});

	

	/* EN */

	$("[multiple].sumo-select--en").SumoSelect({

		okCancelInMulti: true ,

		triggerChangeCombined: true,

		forceCustomRendering: true,

        captionFormat:'{0} Selected',

        captionFormatAllSelected:'{0} all selected!',

		placeholder: 'Select Here',

		locale: ['Ok', 'Cancel', 'Select All'],

		search: true,

		searchText: 'Enter here...',

		noMatch: 'No matches for "{0}"'

	});

	$(".sumo-select--en").not("[multiple]").SumoSelect({

		okCancelInMulti: true ,

		triggerChangeCombined: true,

		forceCustomRendering: true,

        captionFormat:'{0} Selected',

        captionFormatAllSelected:'{0} all selected!',

		placeholder: 'Select Here',

		locale: ['Ok', 'Cancel', 'Select All'],

		search: false,

		searchText: 'Enter here...',

		noMatch: 'No matches for "{0}"'

	});

	

});



/* --------------------------------------------

 card-estate

-------------------------------------------- */	



$(function($) {

	"use strict";

	

	if ($('.card-estate').length > 0) {



		// Avoid latence when user want to slide on photo

		$(".card-estate").mouseenter( function() {

			$(this).find("img").attr("loading","");

		});



		// add interactivity on element with a parent <a>

		// $(".card-estate button[href]").on("click", function(e) {



		// 	if( $(this).attr("target") == "_blank" ) {

		// 		// for href with target _blank

		// 		window.open($(this).attr("href"), '_blank');

		// 	} else {

		// 		// for href with no target _blank or execute Javascript

		// 		window.location = $(this).attr("href");   

		// 	}

			

		// });	

		

	}

	

});	



/* --------------------------------------------

 clik on table tr

-------------------------------------------- */	



$(function($) {

	"use strict";

	

	if ($("tr[data-href]").length > 0) {

		$("tr[data-href]").click(function() {

			window.location = $(this).data("href");

		});



	}

	

});	