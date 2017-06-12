tool_obj =
{
	title: "FORMAL EVALUATION MANAGER",
	showReset: true,

	siteId : null,
	evals: [],
	readys: [],
	sites: [],
	terms: [],
	subjects: [],
	assessmentToDistribute: null,
	assessmentToEdit: null,
	distributing: false,

	start: function(obj, data)
	{
		obj.siteId = data.siteId;
 		setTitle(obj.title);
		setupDialog("evalmanager_distribute_dialog", "Continue", function()
		{
			if(!anyOidsSelected("selectSite"))
			{
				openAlert("evalmanager_alertSelectSite");
				return false;
			}

			setTimeout(function(){obj.reviewDistribute(obj);}, 500);
			return true;
		});
		setupDialog("evalmanager_review_dialog", "Distribute", function(){return obj.doDistribute(obj);});
		setupDialog("evalmanager_edit_dialog", "Save", function(){return obj.doEdit(obj);});
		$('#evalmanager_distribute_dialog_site').unbind('change').change(function(){obj.filterSites(obj); return true;});
		$('#evalmanager_distribute_dialog_term').unbind('change').change(function(){obj.filterSites(obj); return true;});
		$('#evalmanager_distribute_dialog_subject').unbind('change').change(function(){obj.filterSites(obj); return true;});
		$('#evalmanager_current_term').unbind('change').change(function(){obj.loadEvals(obj); return true;});
		$("#evalmanager_distribute_dialog_serach").unbind("click").click(function(){obj.loadEvals(obj); return false;});
		$(".selectAll").unbind("click").click(function()
		{
			$('[sid="' + $(this).attr("oid") + '"]').prop("checked", $(this).prop("checked"));
		});

		startHeartbeat();
 		obj.load(obj);
	},

	stop: function(obj, save)
	{
		stopHeartbeat();
	},

	reset: function(obj)
	{
	},

	load: function(obj)
	{
		var params = new Object();
		params.siteId = obj.siteId;
		params.extra = "x";
		requestCdp("evalmanager_evals", params, function(data)
		{
			obj.evals = data.evals || [];
			obj.readys = data.readys || [];
			obj.terms = data.terms || [];
			obj.subjects = data.subjects || [];
			obj.populateTerms(obj);
			obj.populateReadys(obj);
			obj.populateEvals(obj);
			obj.populateSites(obj);
			obj.populateSubjects(obj);
		});
	},

	loadEvals: function(obj)
	{
		var params = new Object();
		params.siteId = obj.siteId;
		params.term = $.trim($("#evalmanager_current_term").val());
		requestCdp("evalmanager_evals", params, function(data)
		{
			obj.evals = data.evals || [];
			obj.populateEvals(obj);
		});
	},

	populateReadys: function(obj)
	{
		$("#evalmanager_ready_table tbody").empty();
		$.each(obj.readys || [], function(index, eval)
		{
			var tr = $("<tr />");
			$("#evalmanager_ready_table tbody").append(tr);

			obj.createIconTd(tr, "transmit_go.png", "Distribute Evaluation", function(){obj.distribute(obj, eval);});
			createTextTd(tr, eval.assessmentTitle);
			var td = createTextTd(tr, eval.openDate);
			$(td).css("white-space","nowrap");
			td = createTextTd(tr, eval.dueDate);
			$(td).css("white-space","nowrap");
			createTextTd(tr, eval.resultsEmail);
		});

		// obj.updateActions(obj, "selectReady", "evalmanager_ready_actions");

		adjustForNewHeight();
	},

	populateEvals: function(obj)
	{
		$("#evalmanager_export").attr("href","/access/evalmanager/" + obj.siteId + "/" + $.trim($("#evalmanager_current_term").val()));

		$("#evalmanager_evals_table tbody").empty();
		$.each(obj.evals || [], function(index, eval)
		{
			var tr = $("<tr />");
			$("#evalmanager_evals_table tbody").append(tr);
			$(tr).css("vertical-align", "top");

			if ((index % 2) == 1) $(tr).css("background-color","#F4EDE3");

			createIconTd(tr, "gear-edit.png", "Edit Evaluation Options", function(){obj.edit(obj, eval);});
			
			if (eval.archived == "1")
			{
				createIconTd(tr, "restore.png", "Restore Evaluation", function(){obj.restore(obj, eval);});
			}
			else
			{
				createIconTd(tr, "delete.png", "Retract Evaluation", function(){obj.retract(obj, eval);});
			}
			var td = createHtmlTd(tr, "<span style='white-space:nowrap;'>" + eval.siteTitle 
					+ "</span><br /><div style='color:#656565; font-size:10px; display:inline-block;'>" + eval.instructors + "</div>");
			
			if (eval.observers != null)
			{	
				var observerDisplay = eval.observers;
				if ((eval.observers.length > 0) && ((eval.startDate != null) || (eval.endDate != null)))
				{
					observerDisplay += "<br /><span style='color:#656565; font-size:10px; white-space:nowrap;'>";
					if (eval.startDate != null)
					{
						observerDisplay += "starting " + eval.startDate;
					}
					if ((eval.startDate != null) && (eval.endDate != null))
					{
						observerDisplay += "</span><br /><span style='color:#656565; font-size:10px; white-space:nowrap;'>";
					}
					if (eval.endDate != null)
					{
						observerDisplay += "ending " + eval.endDate;
					}
					observerDisplay += "</span>";
				}
				createHtmlTd(tr, observerDisplay);
			}
			else
			{
				createTextTd(tr, "");
			}

			if (eval.archived == "1")
			{
				obj.createIconTd(tr, "make_inactive.png", "Archived");
			}
			else if (eval.published == "0")
			{
				obj.createIconTd(tr, "forbidden.png", "Unpublished");
			}
			else if (eval.live == "1")
			{
				obj.createIconTd(tr, "grade_student.png", "Live Evaluation");
			}
			else
			{
				obj.createIconTd(tr, "publish.png", "Published");
			}

			if (eval.reviewUrl != null)
			{
				obj.createIconTd(tr, "fce_view.png", "View", function(){obj.view(obj, eval);});
			}
			else
			{
				createTextTd(tr, "");
			}
	
			if (eval.stats != null)
			{
				createHtmlTd(tr, eval.assessmentTitle + "<br /><span style='color:#656565; font-size:10px;'>" + eval.stats + "</span>");
			}
			else
			{
				createTextTd(tr, eval.assessmentTitle);
			}

			td = createHtmlTd(tr, (eval.openDate == null ? "<i>open</i>" : eval.openDate) + "<br />" + eval.dueDate);
			$(td).css("white-space","nowrap");
			
			if (eval.resultsSent != null)
			{
				createIconTd(tr, "email.png", "Resend Results", function(){obj.resend(obj, eval);});
			}
			else
			{
				td = createTextTd(tr, "");
				$(td).css("width","16");
			}

			var resultsHtml = eval.resultsEmail;
			if (eval.resultsSent != null) resultsHtml +=
				"<br /><span style='color:#656565; font-size:10px;'>sent " + eval.resultsSent + "</span>";
			td = createHtmlTd(tr, resultsHtml);
		});

		if ((obj.evals || []).length == 0)
		{
			var tr = $("<tr />");
			$("#evalmanager_evals_table tbody").append(tr);
			createHeaderTd(tr, "No Formal Evaluations Found In Term", 10);
		}

		obj.updateActions(obj, "selectEval", "evalmanager_evals_actions");

		adjustForNewHeight();
	},

	populateSites: function(obj)
	{
		$("#evalmanager_distribute_dialog_sites_table tbody").empty();
		$.each(obj.sites || [], function(index, site)
		{
			var tr = $("<tr />");
			$("#evalmanager_distribute_dialog_sites_table tbody").append(tr);
			$(tr).css("vertical-align", "top");

			if ((index % 2) == 1) $(tr).css("background-color","#F4EDE3");

			obj.createSelectCheckboxTd(obj, tr, "selectSite", site.siteId, site.siteTitle, "evalmanager_distribute_dialog_sites_table_actions");
			createTextTd(tr, site.siteTitle);
			createHtmlTd(tr, site.instructors);
			createTextTd(tr, site.termName);
		});

		if ((obj.sites || []).length == 0)
		{
			var tr = $("<tr />");
			$("#evalmanager_distribute_dialog_sites_table tbody").append(tr);
			createHeaderTd(tr, "No Sites Found", 4);
		}

		adjustForNewHeight();
	},

	populateTerms: function(obj)
	{
		obj.populateTerm(obj, "evalmanager_distribute_dialog_term");
		obj.populateTerm(obj, "evalmanager_current_term");
	},

	populateTerm: function(obj, id)
	{		
		var sel = $("#" + id);
		$(sel).empty();
		$.each(obj.terms, function(index, term)
		{
			$(sel).append($("<option />", {value: term.code, text: term.name}));
		});
		
		if (obj.terms.length > 0) $("#" + id).val(obj.terms[0].code);
	},

	populateSubjects: function(obj)
	{		
		var sel = $("#evalmanager_distribute_dialog_subject");
		$(sel).empty();
		$.each(obj.subjects || [], function(index, subject)
		{
			$(sel).append($("<option />", {value: subject.code, text: subject.code}));
		});

		$(sel).append($("<option />", {value: "*", text: "- all -"}));		

		if (obj.subjects.length > 0) $("#evalmanager_distribute_dialog_subject").val(obj.subjects[0]);
	},

	distribute: function(obj, eval)
	{
		obj.assessmentToDistribute = eval;
		$("#evalmanager_distribute_title").text(eval.assessmentTitle);
		$("#evalmanager_distribute_dialog_site").val("");
		if (obj.subjects.length > 0)
		{
			$("#evalmanager_distribute_dialog_subject").val(obj.subjects[0]);
		}
		else
		{
			$("#evalmanager_distribute_dialog_subject").val("*");
		}

		var params = new Object();
		params.siteId = obj.siteId;
		params.siteTitle = $.trim($("#evalmanager_distribute_dialog_site").val());
		params.term = $.trim($("#evalmanager_distribute_dialog_term").val());
		params.subject = $.trim($("#evalmanager_distribute_dialog_subject").val());
		requestCdp("evalmanager_sites", params, function(data)
		{
			obj.sites = data.sites || [];
			obj.populateSites(obj);
			clearSelectAll("selectSite");
			updateSelectAll("selectSite");
			$("#evalmanager_distribute_dialog").dialog('open');
		});
	},

	filterSites: function(obj)
	{
		var params = new Object();
		params.siteId = obj.siteId;
		params.siteTitle = $.trim($("#evalmanager_distribute_dialog_site").val());
		params.term = $.trim($("#evalmanager_distribute_dialog_term").val());
		params.subject = $.trim($("#evalmanager_distribute_dialog_subject").val());
		requestCdp("evalmanager_sites", params, function(data)
		{
			obj.sites = data.sites || [];
			obj.populateSites(obj);
			clearSelectAll("selectSite");
			updateSelectAll("selectSite");
		});
	},

	dateTimePickerConfig:
	{
		dayNamesMin: ["Sun", "Mon" ,"Tue", "Wed", "Thu", "Fri", "Sat"],
		dateFormat: "M dd, yy",
		showButtonPanel: true,
		changeMonth: true,
		changeYear: true,
		showOn: "both", // "button"
		buttonImage: "support/icons/date.png",
		buttonImageOnly: true,
		timeFormat: "hh:mm TT",
		controlType: "select",
		showTime: false,
		closeText: "OK",
		hour: 8,
		minute: 0
	},

	dateTimePickerConfig2:
	{
		dayNamesMin: ["Sun", "Mon" ,"Tue", "Wed", "Thu", "Fri", "Sat"],
		dateFormat: "M dd, yy",
		showButtonPanel: true,
		changeMonth: true,
		changeYear: true,
		showOn: "both", // "button"
		buttonImage: "support/icons/date.png",
		buttonImageOnly: true,
		timeFormat: "hh:mm TT",
		controlType: "select",
		showTime: false,
		closeText: "OK",
		hour: 23,
		minute: 59
	},

	reviewDistribute: function(obj)
	{
		$("#evalmanager_review_title").text(obj.assessmentToDistribute.assessmentTitle);
		$("#evalmanager_review_asTitle").val(obj.assessmentToDistribute.assessmentTitle);
		$("#evalmanager_review_asEmail").val(obj.assessmentToDistribute.resultsEmail);
		
		$("#evalmanager_review_opendate").datetimepicker("destroy");
		$("#evalmanager_review_opendate").datetimepicker(obj.dateTimePickerConfig);
		if (obj.assessmentToDistribute.openDate != null) $("#evalmanager_review_opendate").datetimepicker("setDate", obj.assessmentToDistribute.openDate);

		$("#evalmanager_review_duedate").datetimepicker("destroy");
		$("#evalmanager_review_duedate").datetimepicker(obj.dateTimePickerConfig2);
		if (obj.assessmentToDistribute.dueDate != null) $("#evalmanager_review_duedate").datetimepicker("setDate", obj.assessmentToDistribute.dueDate);

		$("#evalmanager_review_notify").prop("checked", obj.assessmentToDistribute.notify == "1");
		$("#evalmanager_review_observers").val("");

		$("#evalmanager_review_startdate").datetimepicker("destroy");
		$("#evalmanager_review_startdate").val("");
		$("#evalmanager_review_startdate").datetimepicker(obj.dateTimePickerConfig);
		if (obj.assessmentToDistribute.startDate != null) $("#evalmanager_review_startdate").datetimepicker("setDate", obj.assessmentToDistribute.startDate);

		$("#evalmanager_review_enddate").datetimepicker("destroy");
		$("#evalmanager_review_enddate").val("");
		$("#evalmanager_review_enddate").datetimepicker(obj.dateTimePickerConfig2);
		if (obj.assessmentToDistribute.endDate != null) $("#evalmanager_review_enddate").datetimepicker("setDate", obj.assessmentToDistribute.endDate);

		var ul = $("#evalmanager_review_sites");
		$(ul).empty();
		var siteTitles = collectSelectedAttrArray("selectSite", "siteTitle");
		$.each(siteTitles, function(index, siteTitle)
		{
			var li = $("<li />");
			$(ul).append(li);
			$(li).text(siteTitle);
		});

		$("#evalmanager_review_dialog").dialog('open');
		obj.distributing = false;

		return true;
	},

	validate: function(obj, params)
	{
		if (params.title == "")
		{
			openAlert("evalmanager_alertInvalidTitle");		
			return false;
		}
		if (params.email == "")
		{
			openAlert("evalmanager_alertInvalidEmail");		
			return false;
		}
		if (params.dueDate == "")
		{
			openAlert("evalmanager_alertInvalidDueDate");		
			return false;
		}
		if ((params.notify == "1") && (params.openDate == ""))
		{
			openAlert("evalmanager_alertInvalidNotify");		
			return false;
		}
		// open before due
		// start before end
		// invalid email address

		return true;
	},

	doDistribute: function(obj)
	{
		if (obj.distributing) return false;
		obj.distributing = true;

		var params = new Object();
		params.assessmentId = obj.assessmentToDistribute.assessmentId;
		params.toSites = collectSelectedOids("selectSite");
		params.title = $.trim($("#evalmanager_review_asTitle").val());
		params.email = $.trim($("#evalmanager_review_asEmail").val());
		params.openDate = $.trim($("#evalmanager_review_opendate").val());
		params.dueDate = $.trim($("#evalmanager_review_duedate").val());		
		params.notify = ($("#evalmanager_review_notify").is(':checked') ? "1" : "0");
		params.observers = $.trim($("#evalmanager_review_observers").val());
		params.startDate = $.trim($("#evalmanager_review_startdate").val());
		params.endDate = $.trim($("#evalmanager_review_enddate").val());

		if (!obj.validate(obj, params)) return false;

		params.siteId = obj.siteId;
		params.term = $.trim($("#evalmanager_current_term").val());

		requestCdp("evalmanager_distribute", params, function(data)
		{
			obj.evals = data.evals || [];
			obj.populateEvals(obj);
			
			if (data.invalid == "1")
			{
				if (data.invalidIids == null)
				{
					openAlert("evalmanager_alertInvalid");
				}
				else
				{
					$("#evalmanager_alertInvalidObserverIids").text(data.invalidIids);
					openAlert("evalmanager_alertInvalidObserver");
				}
			}
			else
			{
				$("#evalmanager_review_dialog").dialog("close");

				setTimeout(function()
				{
					var ul = $("#evalmanager_alertDistributedSites");
					$(ul).empty();
					var siteTitles = collectSelectedAttrArray("selectSite", "siteTitle");
					$.each(siteTitles, function(index, siteTitle)
					{
						var li = $("<li />");
						$(ul).append(li);
						$(li).text(siteTitle);
					});
					$("#evalmanager_alertDistributedTitle").text(params.title);
					openAlert("evalmanager_alertDistributed");
				}, 500);
			}
		});

		return false;
	},

	retract: function(obj, eval)
	{
		$("#evalmanager_confirmRetract_title").text(eval.assessmentTitle);
		$("#evalmanager_confirmRetract_site").text(eval.siteTitle);
		if (eval.live == "1")
		{
			$("#evalmanager_confirmRetract_live").removeClass("e3_offstage");
		}
		else
		{
			$("#evalmanager_confirmRetract_live").addClass("e3_offstage");
		}

		openConfirm("evalmanager_confirmRetract", "Retract", function(){obj.doRetract(obj, eval);});		
	},

	doRetract: function(obj, eval)
	{
		var params = new Object();
		params.assessmentId = eval.assessmentId;
		params.siteId = obj.siteId;
		params.term = $.trim($("#evalmanager_current_term").val());

		requestCdp("evalmanager_retract", params, function(data)
		{
			obj.evals = data.evals || [];
			obj.populateEvals(obj);
			
			$("#evalmanager_alertRetractedTitle").text(obj.assessmentToEdit.assessmentTitle);
			$("#evalmanager_alertRetractedSite").text(obj.assessmentToEdit.siteTitle);
			openAlert("evalmanager_alertRetracted");
		});

		return true;
	},

	restore: function(obj, eval)
	{
		$("#evalmanager_confirmRestore_title").text(eval.assessmentTitle);
		$("#evalmanager_confirmRestore_site").text(eval.siteTitle);

		openConfirm("evalmanager_confirmRestore", "Restore", function(){obj.doRestore(obj, eval);});		
	},

	doRestore: function(obj, eval)
	{
		var params = new Object();
		params.assessmentId = eval.assessmentId;
		params.siteId = obj.siteId;
		params.term = $.trim($("#evalmanager_current_term").val());

		requestCdp("evalmanager_restore", params, function(data)
		{
			obj.evals = data.evals || [];
			obj.populateEvals(obj);
			
			$("#evalmanager_alertRestoredTitle").text(obj.assessmentToEdit.assessmentTitle);
			$("#evalmanager_alertRestoredSite").text(obj.assessmentToEdit.siteTitle);
			openAlert("evalmanager_alertRestored");
		});

		return true;
	},

	resend: function(obj, eval)
	{
		$("#evalmanager_confirmResend_title").text(eval.assessmentTitle);
		$("#evalmanager_confirmResend_site").text(eval.siteTitle);
		$("#evalmanager_confirmResend_email").text(eval.resultsEmail);

		openConfirm("evalmanager_confirmResend", "Send", function(){obj.doResend(obj, eval);});		
	},

	doResend: function(obj, eval)
	{
		var params = new Object();
		params.assessmentId = eval.assessmentId;
		params.siteId = obj.siteId;
		params.term = $.trim($("#evalmanager_current_term").val());

		requestCdp("evalmanager_resend", params, function(data)
		{
			obj.evals = data.evals || [];
			obj.populateEvals(obj);
		});

		return true;
	},

	edit: function(obj, eval)
	{
		obj.assessmentToEdit = eval;
		$("#evalmanager_edit_site").text(obj.assessmentToEdit.siteTitle);
		$("#evalmanager_edit_title").val(obj.assessmentToEdit.assessmentTitle);
		$("#evalmanager_edit_email").val(obj.assessmentToEdit.resultsEmail);
		$("#evalmanager_edit_observers").val(obj.assessmentToEdit.observerIids);

		$("#evalmanager_edit_opendate").datetimepicker("destroy");
		$("#evalmanager_edit_opendate").val("");
		$("#evalmanager_edit_opendate").datetimepicker(obj.dateTimePickerConfig);
		if (obj.assessmentToEdit.openDate != null) $("#evalmanager_edit_opendate").datetimepicker("setDate", obj.assessmentToEdit.openDate);

		$("#evalmanager_edit_duedate").datetimepicker("destroy");
		$("#evalmanager_edit_duedate").val("");
		$("#evalmanager_edit_duedate").datetimepicker(obj.dateTimePickerConfig2);
		if (obj.assessmentToEdit.dueDate != null) $("#evalmanager_edit_duedate").datetimepicker("setDate", obj.assessmentToEdit.dueDate);

		$("#evalmanager_edit_notify").prop("checked", obj.assessmentToEdit.notify == "1");
		
		$("#evalmanager_edit_startdate").datetimepicker("destroy");
		$("#evalmanager_edit_startdate").val("");
		$("#evalmanager_edit_startdate").datetimepicker(obj.dateTimePickerConfig);
		if (obj.assessmentToEdit.startDate != null) $("#evalmanager_edit_startdate").datetimepicker("setDate", obj.assessmentToEdit.startDate);

		$("#evalmanager_edit_enddate").datetimepicker("destroy");
		$("#evalmanager_edit_enddate").val("");
		$("#evalmanager_edit_enddate").datetimepicker(obj.dateTimePickerConfig2);
		if (obj.assessmentToEdit.endDate != null) $("#evalmanager_edit_enddate").datetimepicker("setDate", obj.assessmentToEdit.endDate);

		$("#evalmanager_edit_dialog").dialog('open');

		return true;
	},

	doEdit: function(obj)
	{
		var params = new Object();
		params.assessmentId = obj.assessmentToEdit.assessmentId;
		params.title = $.trim($("#evalmanager_edit_title").val());
		params.email = $.trim($("#evalmanager_edit_email").val());
		params.openDate = $.trim($("#evalmanager_edit_opendate").val());
		params.dueDate = $.trim($("#evalmanager_edit_duedate").val());
		params.notify = ($("#evalmanager_edit_notify").is(':checked') ? "1" : "0");
		params.observers = $.trim($("#evalmanager_edit_observers").val());
		params.startDate = $.trim($("#evalmanager_edit_startdate").val());
		params.endDate = $.trim($("#evalmanager_edit_enddate").val());

		if (!obj.validate(obj, params)) return false;

		params.siteId = obj.siteId;
		params.term = $.trim($("#evalmanager_current_term").val());

		requestCdp("evalmanager_edit", params, function(data)
		{
			obj.evals = data.evals || [];
			obj.populateEvals(obj);

			if (data.invalid == "1")
			{
				if (data.invalidIids == null)
				{
					openAlert("evalmanager_alertInvalid");
				}
				else
				{
					$("#evalmanager_alertInvalidObserverIids").text(data.invalidIids);
					openAlert("evalmanager_alertInvalidObserver");
				}
			}
			else
			{
				$("#evalmanager_edit_dialog").dialog("close");
				setTimeout(function()
				{
					$("#evalmanager_alertSavedTitle").text(params.title);
					$("#evalmanager_alertSavedSite").text(obj.assessmentToEdit.siteTitle);
					openAlert("evalmanager_alertSaved");
				}, 500);
			}
		});

		return false;
	},

	view : function(obj, eval)
	{
		selectDirectTool(eval.reviewUrl);
	},

	createSelectCheckboxTd : function(obj, tr, id, oid, siteTitle, area)
	{
		var td = $('<td />');
		$(tr).append(td);
		$(td).css("width","16px");
		var input = $('<input type="checkbox" sid="' + id + '" oid="' + oid + '" siteTitle="' + siteTitle + '" />');
		$(td).append(input);
		$(input).click(function()
		{
			obj.updateActions(obj, id, area);
			updateSelectAll(id);
		});
		return td;
	},

	createIconTd: function(tr, icon, popup, click)
	{
		var td = $('<td />');
		$(tr).append(td);

		$(td).addClass("center");
		$(td).html('<img src="/evalmanager/icons/' + icon + '" title="' + popup + '" />');
		
		if (click != null)
		{
			$(td).click(click).addClass("hot");
		}
		return td;
	},

	updateActions : function(obj, id, area)
	{
		var noneChecked = true;
		var numChecked = 0;
		var anyDefined = false;
		$('[sid="' + id + '"]').each(function(index)
		{
			if ($(this).prop("checked") == true)
			{
				noneChecked = false;
				numChecked = numChecked + 1;
			}
			anyDefined = true;
		});
		
		// if there are no check boxes, hide all the options
		if (!anyDefined)
		{
			$("#" + area + " ul li[selectRequired=" + id + "]").addClass("offstage");
			$("#" + area + " ul li[select1Required=" + id + "]").addClass("offstage");
		}

		// update those tool actions that require something in this select id to be selected
		else
		{
			$("#" + area + " ul li[selectRequired=" + id + "]").removeClass("offstage");
			$("#" + area + " ul li[select1Required=" + id + "]").removeClass("offstage");

			if (noneChecked)
			{
				$("#" + area + " ul li[selectRequired=" + id + "] a").addClass("disabled");
				$("#" + area + " ul li[select1Required=" + id + "] a").addClass("disabled");
			}
			else
			{
				$("#" + area + " ul li[selectRequired=" + id + "] a").removeClass("disabled");
				
				if (numChecked == 1)
				{
					$("#" + area + " ul li[select1Required=" + id + "] a").removeClass("disabled");				
				}
				else
				{
					$("#" + area + " ul li[select1Required=" + id + "] a").addClass("disabled");				
				}
			}
		}
	}
};

completeToolLoad();
