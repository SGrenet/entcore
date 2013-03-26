	var admin = function(){
		var getAndRender = function (pathUrl, templateName){
			$.get(pathUrl)
				.done(function(data) {
					template.render(templateName, data);
				})
				.error(function() { // TODO: Manage error message
					template.render("error");
				});
		};

		var template = {
			render : function (nom, data) {
				template[nom](data);
			},
			error: function() {
				$('#log').html("ERREUR !");
			},
			logs : function (data) {
				var htmlString = "";
				var logs = data.records;
				for (i = 0; i < logs.length; i++){
					htmlString +=
						'<p><em style="color:red">' + logs[i].app + '</em>'
						+ '<span> - ' + logs[i].date + '</span>'
						+ '<span> - ' + logs[i].message + '</span>';
						+ '</p>'
				}
				$('#log').html(htmlString);
			}
		};

		return {
			init : function() {
				$('body').delegate('#historique', 'click',function(event) {
					event.preventDefault();
					if (!event.target.getAttribute('call')) return;
					var call = event.target.getAttribute('call');
					admin[call]({url : event.target.getAttribute('href'), id: event.id});
				});
			},
			logs : function(o) {
				getAndRender(o.url, "logs");
			}
		}
	}();

	$(document).ready(function(){
		admin.init(); 
	});