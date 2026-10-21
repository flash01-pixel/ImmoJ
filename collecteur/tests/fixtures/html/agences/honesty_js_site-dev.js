// ANTI SPAM 


function checkCheckSums(check1, check2, result) {
	let c1 = parseInt(check1);
	let c2 = parseInt(check2);

	return  (!isNaN(c1) && !isNaN(c2) && ( c1 + c2 == result) );		
}


function checkbot() {
	let res = checkCheckSums($('#inputCheckSum1').val(), $('#inputCheckSum2').val(), $('#inputCheckSum').val());
	
	if (res == false) {
		$('#checksumerror').show();
	} else {
		$('#checksumerror').hide();
	}

	return res;
}


$('#inputCheckSum').change(function() {

	checkbot();
	

});



// END ANTI SPAM


function renderPriceSubEstate(estate) {
	if (estate == null) return '';

	// test if estate is sold, rented, option
	if ([3,4,5,6,22].includes(estate.purposeStatus.id)) {
		if (estate.purposeStatus.id == 3) return 'Vendu';
		else if (estate.purposeStatus.id == 4) return 'Loué';
		else if (estate.purposeStatus.id == 5) return 'Option'; // vendu sous option
		else if (estate.purposeStatus.id == 6) return 'Option'; // loué sous option
		else if (estate.purposeStatus.id == 22) return 'Option'; // sous comprimis
	}

	if (! estate.displayPrice) {
		return '';
	}

	if (estate.price == '') return '';

	var res = "";
	if (estate.makeOffer) res += "A partir de ";

	res += formatPrice(estate.price);

	return res;

}


function renderPrice(estate) {
	if (estate == null) return '';

	// test if estate is sold, rented, option
	if ([3,4,5,6,22].includes(estate.purposeStatus.id)) return '';

	if (! estate.displayPrice) {
		return '';
	}

	if (estate.price == '') return '';

	var res = "";
	if (estate.makeOffer) res += "A partir de ";

	res += formatPrice(estate.price);

	return res;
}

function webulous_slugify(str) {
	return String(str)
	  .normalize('NFKD') // split accented characters into their base characters and diacritical marks
	  .replace(/[\u0300-\u036f]/g, '') // remove all the accents, which happen to be all in the \u03xx UNICODE block.
	  .trim() // trim leading or trailing whitespace
	  .toLowerCase() // convert to lowercase
	  .replace(/\//g, '_')
	  .replace(/[^a-z0-9 -]/g, '') // remove non-alphanumeric characters
	  .replace(/\s+/g, '-') // replace spaces with hyphens
	  .replace(/-+/g, '-'); // remove consecutive hyphens
  } 

function renderURIDetailSEOProject(estate) {
	if (estate == null) return '';

	var res = "";
	res += webulous_slugify(estate.purpose.name) + "/";
	res += webulous_slugify(estate.city) + "/";
	
	var temp = webulous_slugify(estate.name);	
	res += temp + "/";
	res += estate.id;

	return encodeURI(res);


}

function renderURIDetailSEO(estate) {
	if (estate == null) return '';

	var res = "";
	var temp = estate.category.name.replace(/\//g, '_');
	res += webulous_slugify(temp) + "/";
	res += webulous_slugify(estate.purpose.name) + "/";
	res += webulous_slugify(estate.city) + "/";
	res += estate.id;

	return encodeURI(res);

}


function formatPrice(price) {
	var formatter = new Intl.NumberFormat('de-DE', {
	style: 'currency',
	currency: 'EUR',
	// These options are needed to round to whole numbers if that's what you want.
	minimumFractionDigits: 0, // (this suffices for whole numbers, but will print 2500.10 as $2,500.1)
	maximumFractionDigits: 0, // (causes 2500.99 to be printed as $2,501)
	});

	return formatter.format(price);
}

function formatDateFromJson(jsonDate) {
	/*if (jsonDate == null  || typeof jsonDate !== 'string' || jsonDate.length < 7) return '';

	var date = new Date(parseInt(jsonDate.substr(6)));
	var options = {
        year: "numeric",
        month: "2-digit",
        day: "numeric"
    };

	return date.toLocaleDateString("fr-FR", options); */

	if (jsonDate == null || typeof jsonDate !== 'string' || jsonDate.length < 10) return false;

	var partsDate = jsonDate.substring(0,10).split('-');
	if (partsDate.length < 3) return false;
	var date =  new Date(parseInt(partsDate[0]), parseInt(partsDate[1]) - 1, parseInt(partsDate[2]) ); 
	var options = {
        year: "numeric",
        month: "2-digit",
        day: "numeric"
    };

	return date.toLocaleDateString("fr-FR", options);


}

function isEstateNew(jsonDate, nbDaysForNew) {

	// test on jsonDate
	if (jsonDate == null || typeof jsonDate !== 'string' || jsonDate.length < 10) return false;

	var partsDate = jsonDate.substring(0,10).split('-');
	if (partsDate.length < 3) return false;
	var date =  new Date(parseInt(partsDate[0]), parseInt(partsDate[1]) - 1, parseInt(partsDate[2]) ); 
	var today = new Date();

	var difference = today.getTime() - date.getTime();

	var days =  Math.ceil(difference / (1000 * 3600 * 24)) - 1 ;

	//console.log(date, days);
	
	return days <= nbDaysForNew;
}

function getDifferenceNew(jsonDate) {

	// test on jsonDate
	if (jsonDate == null || typeof jsonDate !== 'string' || jsonDate.length < 10) return false;

	var partsDate = jsonDate.substring(0,10).split('-');
	
	if (partsDate.length < 3) return false;
	var date =  new Date(parseInt(partsDate[0]), parseInt(partsDate[1]) - 1, parseInt(partsDate[2]) ); 
	var today = new Date();


	var difference = today.getTime() - date.getTime();

	var days =  Math.ceil(difference / (1000 * 3600 * 24)) - 1;
	
	return days;
}

function capitalize(s) {
  if (typeof s !== 'string') return '';
  else return s.charAt(0).toUpperCase() + s.slice(1);
}

function getEstateDetail(details, detailId) {
	var res = "";
	details.forEach(function (detail) {
		if (detail.id == detailId) {
			res= detail.value;				
		}

	});

	return res;
}

/**
 * Function to sort alphabetically an array of objects by some specific key.
 * 
 * @param {String} property Key of the object to sort.
 * see : https://ourcodeworld.com/articles/read/764/how-to-sort-alphabetically-an-array-of-objects-by-key-in-javascript
 */

function dynamicSort(property) {
    var sortOrder = 1;

    if(property[0] === "-") {
        sortOrder = -1;
        property = property.substr(1);
    }

    return function (a,b) {
        if(sortOrder == -1){
            return b[property].localeCompare(a[property]);
        }else{
            return a[property].localeCompare(b[property]);
        }        
    }
}


function hideLoader() {
	$('.estate-list__item.estate-list__item--loader').hide();
}

function showLoader() {
	$('.estate-list__item.estate-list__item--loader').show();
}


$(function () {
	$('.select-all').on("click", function(e) {
		if ( $(this).hasClass('selected') ) {
			$('.select-all label').html('Tout désélectionner');
			// $('.select-all span i').hide();
		} else {
			$('.select-all label').html('Tout sélectionner');
			// $('.select-all span i').show();
		}
	});
});


/* poisson d'avril */
/*
var debugMode = false;

$(document).ready(function () {
	const now = new Date();

	// Période valide : du 1er avril 00:00 au 2 avril 23:59
	const start = new Date(now.getFullYear(), 3, 1, 0, 0, 0); // Avril = 3
	const end = new Date(now.getFullYear(), 3, 2, 23, 59, 59);

	if (debugMode || (now >= start && now <= end) ) {
		console.log("Poisson d'avril 🐟 ACTIVÉ");

		$('#section-title--joke2025').show();

		const $fish = $('.fish-joke2025 i');
		const $container = $('.fish-joke2025');
		const $sectionResult = $('.section-estate-result');
		const $sectionTitle = $('#section-title--joke2025');
		let animationActive = true;

		$sectionResult.hide();

		const centerFish = () => {
		const containerWidth = $container.width();
		const fishWidth = $fish.width();
		const centerPos = (containerWidth / 2) - (fishWidth / 2);
		$fish.css({ left: centerPos + 'px' });
		};

		const bounceFish = () => {
		if (!animationActive) return;
		const containerWidth = $container.width();
		const fishWidth = $fish.width();
		const maxRight = containerWidth - fishWidth;

		// Aller à droite (face normale)
		$fish.css('transform', 'scaleX(1)').animate(
			{ left: maxRight + 'px' },
			6000,
			function () {
			// Aller à gauche (retourné)
			$fish.css('transform', 'scaleX(-1)').animate(
				{ left: '0px' },
				6000,
				bounceFish
			);
			}
		);
		};

		centerFish();
		bounceFish();

		$sectionTitle.on('click', function () {
		animationActive = false;
		$fish.stop(true);
		centerFish();

		$fish.animate({ fontSize: '100px' }, 400, function () {
			$sectionTitle.fadeOut(10, function () {
			$sectionResult.fadeIn(10);
			});
		});
		});

		$(window).on('resize', function () {
		if (!animationActive) centerFish();
		});
	} else {
		console.log("Poisson d'avril 🐟 désactivé – en dehors de la période !");
	}
});
*/
