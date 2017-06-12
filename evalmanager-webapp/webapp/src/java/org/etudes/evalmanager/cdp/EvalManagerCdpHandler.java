/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/evalmanager/trunk/evalmanager-webapp/webapp/src/java/org/etudes/evalmanager/cdp/EvalManagerCdpHandler.java $
 * $Id: EvalManagerCdpHandler.java 9307 2014-11-21 21:38:18Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2014 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.evalmanager.cdp;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.cdp.api.CdpHandler;
import org.etudes.cdp.api.CdpStatus;
import org.etudes.cdp.util.CdpResponseHelper;
import org.etudes.mneme.api.Assessment;
import org.etudes.mneme.api.AssessmentPermissionException;
import org.etudes.mneme.api.AssessmentPolicyException;
import org.etudes.mneme.api.AssessmentService;
import org.etudes.mneme.api.MnemeTransferService;
import org.etudes.mneme.api.Pool;
import org.etudes.mneme.api.PoolService;
import org.etudes.mneme.api.Submission;
import org.etudes.mneme.api.SubmissionService;
import org.etudes.roster.api.RosterService;
import org.etudes.util.Different;
import org.sakaiproject.authz.api.AuthzGroup;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.GroupNotDefinedException;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.authz.api.RoleAlreadyDefinedException;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.StringUtil;

/**
 */
public class EvalManagerCdpHandler implements CdpHandler
{
	class Eval
	{
		Boolean archived = null;
		Long assessmentId = null;
		String assessmentTitle = null;
		Date due = null;
		String email = null;
		Date end = null;
		String instructors = null;
		String instructorsPlain = null;
		Boolean live = null;
		Boolean notify = null;
		String observerIids = null;
		String observers = null;
		String observersPlain = null;
		Date open = null;
		Boolean published = null;
		Date sent = null;
		String siteId = null;
		String siteTitle = null;
		Date start = null;
		Long termId = null;
	}

	class EvalSite
	{
		String instructors = null;
		String siteId = null;
		String siteSubject = null;
		String siteTitle = null;
		Long termId = null;
		String termName = null;
	}

	class InstructorsAndObservers
	{
		Date end;
		Set<String> instructors = new HashSet<String>();
		Set<String> observers = new HashSet<String>();
		Date start;
	}

	class ResponseRate
	{
		int submitted = 0;

		int total = 0;

		ResponseRate(Eval e)
		{
			Assessment assessment = assessmentService().getAssessment(e.assessmentId.toString());
			if (assessment == null)
			{
				M_log.warn("responseRate - assessment not found: " + e.assessmentId);
			}

			// collect all the submissions for the assessment
			List<Submission> submissions = submissionService().findAssessmentSubmissions(assessment,
					SubmissionService.FindAssessmentSubmissionsSort.sdate_a, Boolean.FALSE, null, null, null, null);

			// count
			for (Submission s : submissions)
			{
				if ((!s.getIsPhantom()) && s.getIsComplete())
				{
					this.submitted++;
				}

				this.total++;
			}
		}

		int pct()
		{
			return this.total > 0 ? (this.submitted * 100 / this.total) : 0;
		}
	}

	class ScheduleDates
	{
		Date end;
		Date start;
	}

	class Term
	{
		Long id = null;
		String name = null;
	}

	/** Our log (commons). */
	private static Log M_log = LogFactory.getLog(EvalManagerCdpHandler.class);

	public String getPrefix()
	{
		return "evalmanager";
	}

	public Map<String, Object> handle(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String requestPath,
			String path, String authenticatedUserId) throws ServletException, IOException
	{
		// if no authenticated user, we reject all requests
		if (authenticatedUserId == null)
		{
			Map<String, Object> rv = new HashMap<String, Object>();
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.notLoggedIn.getId());
			return rv;
		}

		else if (requestPath.equals("evals"))
		{
			return dispatchEvals(req, res, parameters, path);
		}

		else if (requestPath.equals("sites"))
		{
			return dispatchSites(req, res, parameters, path);
		}

		else if (requestPath.equals("distribute"))
		{
			return dispatchDistribute(req, res, parameters, path);
		}

		else if (requestPath.equals("retract"))
		{
			return dispatchRetract(req, res, parameters, path);
		}

		else if (requestPath.equals("restore"))
		{
			return dispatchRestore(req, res, parameters, path);
		}

		else if (requestPath.equals("resend"))
		{
			return dispatchResend(req, res, parameters, path);
		}

		else if (requestPath.equals("edit"))
		{
			return dispatchEdit(req, res, parameters, path);
		}

		return null;
	}

	protected void addObserver(final String siteId, final String observerId)
	{
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				addObserverTx(siteId, observerId);
			}
		}, "addObserver: " + siteId);
	}

	protected void addObserverTx(String siteId, String observerId)
	{
		String sql = "INSERT INTO EVALMANAGER_OBSERVER (SITE_ID, OBSERVER_ID) VALUES (?,?)";
		Object[] fields = new Object[2];
		fields[0] = siteId;
		fields[1] = observerId;
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("addObserverTx: db write failed: " + siteId);
		}
	}

	protected void assureSchedule(String siteId, Date start, Date end, Set<User> observers)
	{
		Long id = checkSchedule(siteId);
		if (id != null)
		{
			updateSchedule(id, start, end);
		}
		else
		{
			insertSchedule(siteId, start, end);
		}

		removeObservers(siteId);
		for (User observer : observers)
		{
			addObserver(siteId, observer.getId());
		}
	}

	protected Long checkSchedule(String siteId)
	{
		String sql = "SELECT ID FROM EVALMANAGER_SCHEDULE WHERE SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		final List<Long> rv = new ArrayList<Long>();
		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					int i = 1;
					Long id = readLong(result, i++);
					rv.add(id);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("checkSchedule: " + e);
					return null;
				}
			}
		});

		return (rv.size() == 1) ? rv.get(0) : null;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> dispatchDistribute(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path)
			throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// get the site id parameter
		String siteId = (String) parameters.get("siteId");
		if (siteId == null)
		{
			M_log.warn("dispatchDistribute - no siteId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		Site site = null;
		try
		{
			site = siteService().getSite(siteId);
		}
		catch (IdUnusedException e)
		{
		}

		if (site == null)
		{
			M_log.warn("dispatchDistribute - missing site: " + siteId);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// security - must have formal eval permission in the site
		if (!assessmentService().allowSetFormalCourseEvaluation(siteId))
		{
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// the assessment to distribute
		String assessmentId = (String) parameters.get("assessmentId");
		if (assessmentId == null)
		{
			M_log.warn("dispatchDistribute - no assessmentId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		// get assessment
		Assessment source = assessmentService().getAssessment(assessmentId);
		if (source == null)
		{
			M_log.warn("dispatchDistribute - assessment not found");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		Set<String> toDistribute = new HashSet<String>();
		toDistribute.add(assessmentId);

		// get the toSites ids parameter
		String toSites = (String) parameters.get("toSites");
		if (toSites == null)
		{
			M_log.warn("dispatchDistribute - no toSites parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		String title = StringUtil.trimToNull((String) parameters.get("title"));
		String email = StringUtil.trimToNull((String) parameters.get("email"));
		Boolean notify = "1".equals(StringUtil.trimToNull((String) parameters.get("notify")));

		String openDateStr = StringUtil.trimToNull((String) parameters.get("openDate"));
		Date openDate = null;
		if (openDateStr != null)
		{
			try
			{
				openDate = CdpResponseHelper.dateFromDateTimeDisplayInUserZone(openDateStr);
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		String dueDateStr = StringUtil.trimToNull((String) parameters.get("dueDate"));
		Date dueDate = null;
		if (dueDateStr != null)
		{
			try
			{
				dueDate = CdpResponseHelper.dateFromDateTimeDisplayInUserZone(dueDateStr);
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		String startDateStr = StringUtil.trimToNull((String) parameters.get("startDate"));
		Date startDate = null;
		if (startDateStr != null)
		{
			try
			{
				startDate = CdpResponseHelper.dateFromDateTimeDisplayInUserZone(startDateStr);
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		String endDateStr = StringUtil.trimToNull((String) parameters.get("endDate"));
		Date endDate = null;
		if (endDateStr != null)
		{
			try
			{
				endDate = CdpResponseHelper.dateFromDateTimeDisplayInUserZone(endDateStr);
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		StringBuilder badIids = new StringBuilder();
		Set<String> observerIdSet = new HashSet<String>();
		List<User> observers = processObserversParameter(site, (String) parameters.get("observers"), badIids, observerIdSet);

		boolean ok = true;
		if (badIids.length() > 0)
		{
			ok = false;

			// warn the user
			rv.put("invalid", CdpResponseHelper.formatBoolean(Boolean.TRUE));
			rv.put("invalidIids", badIids.toString());
		}

		if (ok)
		{
			String[] idStrs = StringUtil.split(toSites, "\t");
			String[] ids = new String[idStrs.length];
			int i = 0;
			for (String id : idStrs)
			{
				ids[i++] = id;
			}
			for (String sid : ids)
			{
				try
				{
					pushAdvisor();

					Site destination = siteService().getSite(sid);

					// get the instructors
					List<User> instructors = new ArrayList<User>();
					Set<String> instructorIds = destination.getUsersHasRole("Instructor");
					for (String id : instructorIds)
					{
						try
						{
							User user = userDirectoryService().getUser(id);
							instructors.add(user);
						}
						catch (UserNotDefinedException e)
						{
							M_log.warn("dispatchDistribute: missing instructor user: " + id + "  for site: " + site.getId());
						}
					}

					Assessment a = mnemeTransferService().distribute(source, sid, title, openDate, dueDate, email, notify);
					if (a == null)
					{
						ok = false;
						rv.put("invalid", CdpResponseHelper.formatBoolean(Boolean.TRUE));
						break;
					}
					else
					{
						// for each site instructor
						for (User instructor : instructors)
						{
							notifyDistribution(instructor, destination.getTitle(), a.getTitle());
						}
					}

					processObservers(destination, instructors, startDate, endDate, observerIdSet, observers);
				}
				catch (IdUnusedException e)
				{
					M_log.warn("dispatchDistribute: " + e);
				}
				finally
				{
					popAdvisor();
				}
			}
		}

		String[] siteParts = StringUtil.split(site.getTitle(), " ");
		String sitePrefix = siteParts[0].toLowerCase();

		String termCode = (String) parameters.get("term");
		Long term = null;
		if (termCode != null)
		{
			term = Long.parseLong(termCode);
		}

		// find assessments that are formal course evals in all the sites that share this site prefix, in this term
		List<Eval> evals = readEvals(sitePrefix, site.getId(), term);

		// build up a map to return - the main map has a "evals" object
		List<Map<String, String>> evalsMap = new ArrayList<Map<String, String>>();
		rv.put("evals", evalsMap);

		for (Eval eval : evals)
		{
			Map<String, String> evalMap = new HashMap<String, String>();
			evalsMap.add(evalMap);

			doEval(eval, site, evalMap);
		}

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> dispatchEdit(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path)
			throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// get the site id parameter
		String siteId = (String) parameters.get("siteId");
		if (siteId == null)
		{
			M_log.warn("dispatchEdit - no siteId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		Site site = null;
		try
		{
			site = siteService().getSite(siteId);
		}
		catch (IdUnusedException e)
		{
		}

		if (site == null)
		{
			M_log.warn("dispatchEdit - missing site: " + siteId);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// security - must have formal eval permission in the site
		if (!assessmentService().allowSetFormalCourseEvaluation(siteId))
		{
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// the assessment to edit
		String assessmentId = (String) parameters.get("assessmentId");
		if (assessmentId == null)
		{
			M_log.warn("dispatchEdit - no assessmentId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		// get assessment
		Assessment assessment = assessmentService().getAssessment(assessmentId);
		if (assessment == null)
		{
			M_log.warn("dispatchEdit - assessment not found");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		String title = StringUtil.trimToNull((String) parameters.get("title"));
		String email = StringUtil.trimToNull((String) parameters.get("email"));
		Boolean notify = "1".equals(StringUtil.trimToNull((String) parameters.get("notify")));

		String openDateStr = StringUtil.trimToNull((String) parameters.get("openDate"));
		Date openDate = null;
		if (openDateStr != null)
		{
			try
			{
				openDate = CdpResponseHelper.dateFromDateTimeDisplayInUserZone(openDateStr);
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		String dueDateStr = StringUtil.trimToNull((String) parameters.get("dueDate"));
		Date dueDate = null;
		if (dueDateStr != null)
		{
			try
			{
				dueDate = CdpResponseHelper.dateFromDateTimeDisplayInUserZone(dueDateStr);
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		String startDateStr = StringUtil.trimToNull((String) parameters.get("startDate"));
		Date startDate = null;
		if (startDateStr != null)
		{
			try
			{
				startDate = CdpResponseHelper.dateFromDateTimeDisplayInUserZone(startDateStr);
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		String endDateStr = StringUtil.trimToNull((String) parameters.get("endDate"));
		Date endDate = null;
		if (endDateStr != null)
		{
			try
			{
				endDate = CdpResponseHelper.dateFromDateTimeDisplayInUserZone(endDateStr);
			}
			catch (IllegalArgumentException e)
			{
			}
		}

		StringBuilder badIids = new StringBuilder();
		Set<String> observerIdSet = new HashSet<String>();
		List<User> observers = processObserversParameter(site, (String) parameters.get("observers"), badIids, observerIdSet);

		boolean ok = true;
		if (badIids.length() > 0)
		{
			ok = false;

			// warn the user
			rv.put("invalid", CdpResponseHelper.formatBoolean(Boolean.TRUE));
			rv.put("invalidIids", badIids.toString());
		}

		if (ok)
		{
			try
			{
				pushAdvisor();

				if (title != null) assessment.setTitle(title);
				if (openDate != null) assessment.getDates().setOpenDate(openDate);
				if (dueDate != null) assessment.getDates().setDueDate(dueDate);
				if (email != null) assessment.setResultsEmail(email);
				if (notify != null) assessment.setNotifyEval(notify);

				if (assessment.getIsValid())
				{
					try
					{
						assessmentService().saveAssessment(assessment);
					}
					catch (AssessmentPolicyException e)
					{
						updateAssessment(assessment, title, email, openDate, dueDate, notify);
					}
				}
				else
				{
					ok = false;
					rv.put("invalid", CdpResponseHelper.formatBoolean(Boolean.TRUE));
				}

				// the observers
				if (ok)
				{
					Site destination = siteService().getSite(assessment.getContext());

					// get the instructors
					List<User> instructors = new ArrayList<User>();
					Set<String> instructorIds = destination.getUsersHasRole("Instructor");
					for (String id : instructorIds)
					{
						try
						{
							User user = userDirectoryService().getUser(id);
							instructors.add(user);
						}
						catch (UserNotDefinedException e)
						{
							M_log.warn("dispatchEdit: missing instructor user: " + id + "  for site: " + site.getId());
						}
					}

					processObservers(destination, instructors, startDate, endDate, observerIdSet, observers);
				}
			}
			catch (AssessmentPermissionException e)
			{
				M_log.warn("dispatchEdit: " + e.toString());
			}
			catch (IdUnusedException e)
			{
				M_log.warn("dispatchEdit: " + e.toString());
			}
			finally
			{
				popAdvisor();
			}
		}

		String[] siteParts = StringUtil.split(site.getTitle(), " ");
		String sitePrefix = siteParts[0].toLowerCase();

		String termCode = (String) parameters.get("term");
		Long term = null;
		if (termCode != null)
		{
			term = Long.parseLong(termCode);
		}

		// find assessments that are formal course evals in all the sites that share this site prefix, in this term
		List<Eval> evals = readEvals(sitePrefix, site.getId(), term);

		// build up a map to return - the main map has a "evals" object
		List<Map<String, String>> evalsMap = new ArrayList<Map<String, String>>();
		rv.put("evals", evalsMap);

		for (Eval eval : evals)
		{
			Map<String, String> evalMap = new HashMap<String, String>();
			evalsMap.add(evalMap);

			doEval(eval, site, evalMap);
		}

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	protected Map<String, Object> dispatchEvals(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path)
			throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// get the site id parameter
		String siteId = (String) parameters.get("siteId");
		if (siteId == null)
		{
			M_log.warn("dispatchEvals - no siteId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		Site site = null;
		try
		{
			site = siteService().getSite(siteId);
		}
		catch (IdUnusedException e)
		{
		}

		if (site == null)
		{
			M_log.warn("dispatchEvals - missing site: " + siteId);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// security - must have formal eval permission in the site
		if (!assessmentService().allowSetFormalCourseEvaluation(siteId))
		{
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		String[] siteParts = StringUtil.split(site.getTitle(), " ");
		String sitePrefix = siteParts[0].toLowerCase();

		String termCode = (String) parameters.get("term");
		Long term = null;
		if (termCode != null)
		{
			term = Long.parseLong(termCode);
		}

		String extra = StringUtil.trimToNull((String) parameters.get("extra"));

		// get the client's active terms (terms with sites)
		List<Term> terms = readTerms(sitePrefix);

		// pick the latest term if none specified
		if ((term == null) && (!terms.isEmpty()))
		{
			term = terms.get(0).id;
		}

		// find assessments that are formal course evals in all the sites that share this site prefix, in this term
		List<Eval> evals = readEvals(sitePrefix, site.getId(), term);

		// build up a map to return - the main map has a "evals" object
		List<Map<String, String>> evalsMap = new ArrayList<Map<String, String>>();
		rv.put("evals", evalsMap);

		for (Eval eval : evals)
		{
			Map<String, String> evalMap = new HashMap<String, String>();
			evalsMap.add(evalMap);

			doEval(eval, site, evalMap);
		}

		// if we want the extra information
		if (extra != null)
		{
			evals = readReadys(site.getId());
			List<Map<String, String>> readysMap = new ArrayList<Map<String, String>>();
			rv.put("readys", readysMap);

			for (Eval eval : evals)
			{
				Map<String, String> evalMap = new HashMap<String, String>();
				readysMap.add(evalMap);

				doEval(eval, site, evalMap);
			}

			// terms
			List<Map<String, String>> termsMap = new ArrayList<Map<String, String>>();
			rv.put("terms", termsMap);

			for (Term t : terms)
			{
				Map<String, String> termMap = new HashMap<String, String>();
				termsMap.add(termMap);

				termMap.put("code", t.id.toString());
				termMap.put("name", t.name);
			}

			// get the subjects from the client's sites
			List<String> subjects = readSubjects(sitePrefix);

			List<Map<String, String>> subjectsMap = new ArrayList<Map<String, String>>();
			rv.put("subjects", subjectsMap);
			for (String s : subjects)
			{
				Map<String, String> subjectMap = new HashMap<String, String>();
				subjectsMap.add(subjectMap);

				subjectMap.put("code", s);
			}
		}

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	protected Map<String, Object> dispatchResend(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path)
			throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// get the site id parameter
		String siteId = (String) parameters.get("siteId");
		if (siteId == null)
		{
			M_log.warn("dispatchResend - no siteId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		Site site = null;
		try
		{
			site = siteService().getSite(siteId);
		}
		catch (IdUnusedException e)
		{
		}

		if (site == null)
		{
			M_log.warn("dispatchResend - missing site: " + siteId);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// security - must have formal eval permission in the site
		if (!assessmentService().allowSetFormalCourseEvaluation(siteId))
		{
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// the assessment to retract
		String assessmentId = (String) parameters.get("assessmentId");
		if (assessmentId == null)
		{
			M_log.warn("dispatchResend - no assessmentId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		// get assessment?
		Assessment assessment = assessmentService().getAssessment(assessmentId);
		if (assessment == null)
		{
			M_log.warn("dispatchResend - assessment not found");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		try
		{
			pushAdvisor();

			// re-send the email
			assessmentService().sendResults(assessment);
		}
		finally
		{
			popAdvisor();
		}

		String[] siteParts = StringUtil.split(site.getTitle(), " ");
		String sitePrefix = siteParts[0].toLowerCase();

		String termCode = (String) parameters.get("term");
		Long term = null;
		if (termCode != null)
		{
			term = Long.parseLong(termCode);
		}

		// find assessments that are formal course evals in all the sites that share this site prefix, in this term
		List<Eval> evals = readEvals(sitePrefix, site.getId(), term);

		// build up a map to return - the main map has a "evals" object
		List<Map<String, String>> evalsMap = new ArrayList<Map<String, String>>();
		rv.put("evals", evalsMap);

		for (Eval eval : evals)
		{
			Map<String, String> evalMap = new HashMap<String, String>();
			evalsMap.add(evalMap);

			doEval(eval, site, evalMap);
		}

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	protected Map<String, Object> dispatchRestore(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path)
			throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// get the site id parameter
		String siteId = (String) parameters.get("siteId");
		if (siteId == null)
		{
			M_log.warn("dispatchRestore - no siteId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		Site site = null;
		try
		{
			site = siteService().getSite(siteId);
		}
		catch (IdUnusedException e)
		{
		}

		if (site == null)
		{
			M_log.warn("dispatchRestore - missing site: " + siteId);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// security - must have formal eval permission in the site
		if (!assessmentService().allowSetFormalCourseEvaluation(siteId))
		{
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// the assessment to restore
		String assessmentId = (String) parameters.get("assessmentId");
		if (assessmentId == null)
		{
			M_log.warn("dispatchRestore - no assessmentId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		// get assessment
		Assessment assessment = assessmentService().getAssessment(assessmentId);
		if (assessment == null)
		{
			M_log.warn("dispatchRetract - assessment not found");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		try
		{
			pushAdvisor();

			assessment.setArchived(Boolean.FALSE);
			assessment.setPublished(Boolean.TRUE);
			assessmentService().saveAssessment(assessment);
		}
		catch (AssessmentPermissionException e)
		{
			M_log.warn("dispatchRetract: " + e.toString());
		}
		catch (AssessmentPolicyException e)
		{
			M_log.warn("dispatchRetract: " + e.toString());
		}
		finally
		{
			popAdvisor();
		}

		String[] siteParts = StringUtil.split(site.getTitle(), " ");
		String sitePrefix = siteParts[0].toLowerCase();

		String termCode = (String) parameters.get("term");
		Long term = null;
		if (termCode != null)
		{
			term = Long.parseLong(termCode);
		}

		// find assessments that are formal course evals in all the sites that share this site prefix, in this term
		List<Eval> evals = readEvals(sitePrefix, site.getId(), term);

		// build up a map to return - the main map has a "evals" object
		List<Map<String, String>> evalsMap = new ArrayList<Map<String, String>>();
		rv.put("evals", evalsMap);

		for (Eval eval : evals)
		{
			Map<String, String> evalMap = new HashMap<String, String>();
			evalsMap.add(evalMap);

			doEval(eval, site, evalMap);
		}

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> dispatchRetract(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path)
			throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// get the site id parameter
		String siteId = (String) parameters.get("siteId");
		if (siteId == null)
		{
			M_log.warn("dispatchRetract - no siteId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		Site site = null;
		try
		{
			site = siteService().getSite(siteId);
		}
		catch (IdUnusedException e)
		{
		}

		if (site == null)
		{
			M_log.warn("dispatchRetract - missing site: " + siteId);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// security - must have formal eval permission in the site
		if (!assessmentService().allowSetFormalCourseEvaluation(siteId))
		{
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// the assessment to retract
		String assessmentId = (String) parameters.get("assessmentId");
		if (assessmentId == null)
		{
			M_log.warn("dispatchRetract - no assessmentId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		// get assessment
		Assessment assessment = assessmentService().getAssessment(assessmentId);
		if (assessment == null)
		{
			M_log.warn("dispatchRetract - assessment not found");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		try
		{
			pushAdvisor();

			// delete if possible
			if (assessmentService().allowRemoveAssessment(assessment))
			{
				Pool pool = assessment.getPool();
				assessmentService().removeAssessment(assessment);
				if (pool != null)
				{
					poolService().removePool(pool);
				}
			}

			// archive
			else
			{
				assessment.setArchived(Boolean.TRUE);
				assessmentService().saveAssessment(assessment);
			}

			Site destination = siteService().getSite(assessment.getContext());

			// remove existing observers & schedule
			Set<String> existingObservers = destination.getUsersHasRole("Observer");
			for (String existingObserverId : existingObservers)
			{
				destination.removeMember(existingObserverId);
			}
			removeSchedule(destination.getId());

			siteService().save(destination);
		}
		catch (AssessmentPermissionException e)
		{
			M_log.warn("dispatchRetract: " + e.toString());
		}
		catch (AssessmentPolicyException e)
		{
			M_log.warn("dispatchRetract: " + e.toString());
		}
		catch (IdUnusedException e)
		{
			M_log.warn("dispatchRetract: " + e.toString());
		}
		catch (PermissionException e)
		{
			M_log.warn("dispatchRetract: " + e.toString());
		}
		finally
		{
			popAdvisor();
		}

		String[] siteParts = StringUtil.split(site.getTitle(), " ");
		String sitePrefix = siteParts[0].toLowerCase();

		String termCode = (String) parameters.get("term");
		Long term = null;
		if (termCode != null)
		{
			term = Long.parseLong(termCode);
		}

		// find assessments that are formal course evals in all the sites that share this site prefix, in this term
		List<Eval> evals = readEvals(sitePrefix, site.getId(), term);

		// build up a map to return - the main map has a "evals" object
		List<Map<String, String>> evalsMap = new ArrayList<Map<String, String>>();
		rv.put("evals", evalsMap);

		for (Eval eval : evals)
		{
			Map<String, String> evalMap = new HashMap<String, String>();
			evalsMap.add(evalMap);

			doEval(eval, site, evalMap);
		}

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	protected Map<String, Object> dispatchSites(HttpServletRequest req, HttpServletResponse res, Map<String, Object> parameters, String path)
			throws ServletException, IOException
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		// get the site id parameter
		String siteId = (String) parameters.get("siteId");
		if (siteId == null)
		{
			M_log.warn("dispatchSites - no siteId parameter");

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.badRequest.getId());
			return rv;
		}

		Site site = null;
		try
		{
			site = siteService().getSite(siteId);
		}
		catch (IdUnusedException e)
		{
		}

		if (site == null)
		{
			M_log.warn("dispatchSites - missing site: " + siteId);

			// add status parameter
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		// security - must have formal eval permission in the site
		if (!assessmentService().allowSetFormalCourseEvaluation(siteId))
		{
			rv.put(CdpStatus.CDP_STATUS, CdpStatus.accessDenied.getId());
			return rv;
		}

		String[] siteParts = StringUtil.split(site.getTitle(), " ");
		String sitePrefix = siteParts[0].toLowerCase();
		String subject = StringUtil.trimToNull((String) parameters.get("subject"));

		// if the title has the prefix, remove it (since we are searching on that anyway)
		String title = StringUtil.trimToNull((String) parameters.get("siteTitle"));
		if (title != null)
		{
			if (title.toLowerCase().startsWith(sitePrefix))
			{
				title = StringUtil.trimToNull(title.substring(sitePrefix.length()));
			}

			// if the title has the subject, remove it too
			if ((title != null) && (title.toLowerCase().startsWith(subject.toLowerCase())))
			{
				title = StringUtil.trimToNull(title.substring(subject.length()));
			}
		}

		String termCode = (String) parameters.get("term");
		Long term = null;
		if (termCode != null)
		{
			term = Long.parseLong(termCode);
		}

		respondSites(rv, sitePrefix, site.getId(), title, term, subject);

		// add status parameter
		rv.put(CdpStatus.CDP_STATUS, CdpStatus.success.getId());

		return rv;
	}

	protected void doEval(Eval eval, Site setupSite, Map<String, String> evalMap)
	{
		if (eval.assessmentId != null) evalMap.put("assessmentId", eval.assessmentId.toString());
		if (eval.siteTitle != null) evalMap.put("siteTitle", eval.siteTitle);
		if (eval.assessmentTitle != null) evalMap.put("assessmentTitle", eval.assessmentTitle);
		if (eval.open != null) evalMap.put("openDate", CdpResponseHelper.dateTimeDisplayInUserZone(eval.open.getTime()));
		if (eval.due != null) evalMap.put("dueDate", CdpResponseHelper.dateTimeDisplayInUserZone(eval.due.getTime()));
		if (eval.start != null) evalMap.put("startDate", CdpResponseHelper.dateTimeDisplayInUserZone(eval.start.getTime()));
		if (eval.end != null) evalMap.put("endDate", CdpResponseHelper.dateTimeDisplayInUserZone(eval.end.getTime()));
		if (eval.email != null) evalMap.put("resultsEmail", eval.email);
		if (eval.sent != null) evalMap.put("resultsSent", CdpResponseHelper.dateTimeDisplayInUserZone(eval.sent.getTime()));
		evalMap.put("live", CdpResponseHelper.formatBoolean(eval.live));
		evalMap.put("notify", CdpResponseHelper.formatBoolean(eval.notify));
		evalMap.put("archived", CdpResponseHelper.formatBoolean(eval.archived));
		evalMap.put("published", CdpResponseHelper.formatBoolean(eval.published));
		if (eval.instructors != null) evalMap.put("instructors", eval.instructors);
		if (eval.observers != null) evalMap.put("observers", eval.observers);
		if (eval.observerIids != null) evalMap.put("observerIids", eval.observerIids);

		// a portal directtool url to view this eval in the setup site's at&s
		ToolConfiguration mnemeTool = setupSite.getToolForCommonId("sakai.mneme");
		ToolConfiguration evalTool = setupSite.getToolForCommonId("e3.evalmanager");
		if ((mnemeTool != null) && (evalTool != null))
		{
			String reviewUrl = "/" + mnemeTool.getId() + "/guest_view/" + eval.assessmentId + "/!portal!/" + evalTool.getId();
			evalMap.put("reviewUrl", reviewUrl);
		}

		// response rates
		ResponseRate rr = new ResponseRate(eval);
		evalMap.put("stats", rr.submitted + " of " + rr.total + " (" + rr.pct() + "%)");
	}

	protected void insertSchedule(final String siteId, final Date start, final Date end)
	{
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				insertScheduleTx(siteId, start, end);
			}
		}, "insertSchedule: " + siteId);
	}

	protected void insertScheduleTx(String siteId, Date start, Date end)
	{
		String sql = "INSERT INTO EVALMANAGER_SCHEDULE (SITE_ID, OBSERVATION_START, OBSERVATION_END, ACTIVE) VALUES (?,?,?,'1')";
		Object[] fields = new Object[3];
		fields[0] = siteId;
		fields[1] = sqlService().valueForDate(start);
		fields[2] = sqlService().valueForDate(end);
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("insertScheduleTx: db write failed: " + siteId);
		}
	}

	/**
	 * Notify an instructor in a site that an FCE was distributed.
	 * 
	 * @param user
	 *        The user.
	 * @param siteTitle
	 *        The site title.
	 * @param assessmentTitle
	 *        the FCE's title.
	 */
	protected void notifyDistribution(User user, String siteTitle, String assessmentTitle)
	{
		String from = "\"" + serverConfigurationService().getString("ui.service", "Sakai") + "\"<no-reply@"
				+ serverConfigurationService().getServerName() + ">";
		String productionSiteName = serverConfigurationService().getString("ui.service", "");

		String to = user.getEmail();
		String headerTo = user.getEmail();
		String replyTo = user.getEmail();
		String subject = "Formal Evaluation Added to " + productionSiteName + " Site " + siteTitle;

		String content = "";

		if (from != null && user.getEmail() != null)
		{
			StringBuffer buf = new StringBuffer();

			buf.append("Dear " + user.getDisplayName() + ":\n\n");
			buf.append(userDirectoryService().getCurrentUser().getDisplayName() + " has added the formal evaluation \"" + assessmentTitle
					+ "\" to your " + productionSiteName + " site \"" + siteTitle + "\".\n\n");

			buf.append("---------------------\n\nThis is an automatically generated email from Etudes. Do not reply to it!\n\n");

			content = buf.toString();
			emailService().send(from, to, subject, content, headerTo, replyTo, null);
		}
	}

	/**
	 * Notify the member on being added to a site.
	 * 
	 * @param user
	 *        The user.
	 * @param siteTitle
	 *        The site title.
	 */
	protected void notifyMember(User user, String siteTitle, Date start, Date end)
	{
		String from = "\"" + serverConfigurationService().getString("ui.service", "Sakai") + "\"<no-reply@"
				+ serverConfigurationService().getServerName() + ">";
		String productionSiteName = serverConfigurationService().getString("ui.service", "");
		String productionSiteUrl = serverConfigurationService().getPortalUrl();

		String to = user.getEmail();
		String headerTo = user.getEmail();
		String replyTo = user.getEmail();
		String subject = "Added to " + productionSiteName + " Site " + siteTitle;

		String content = "";

		if (from != null && user.getEmail() != null)
		{
			StringBuffer buf = new StringBuffer();

			buf.append("Dear " + user.getDisplayName() + ":\n\n");
			buf.append(userDirectoryService().getCurrentUser().getDisplayName() + " has added you as an \"observer\" to the " + productionSiteName
					+ " site \"" + siteTitle + "\".\n\n");

			if ((start != null) && (end != null))
			{
				buf.append("You will have access to the site between " + CdpResponseHelper.dateTimeDisplayInUserZone(start.getTime(), user.getId())
						+ " and " + CdpResponseHelper.dateTimeDisplayInUserZone(end.getTime(), user.getId()) + ".\n\n");
			}
			else if (start != null)
			{
				buf.append("You will have access to the site starting " + CdpResponseHelper.dateTimeDisplayInUserZone(start.getTime(), user.getId())
						+ ".\n\n");
			}
			else if (end != null)
			{
				buf.append("You will have access to the site until " + CdpResponseHelper.dateTimeDisplayInUserZone(end.getTime(), user.getId())
						+ ".\n\n");
			}

			buf.append("As an observer, you have access to the course site, and may review published materials, even those that have closed or are not yet open."
					+ "  You may not participate in the site nor see student submissions, grades, or class tracking data.\n\n");

			buf.append("To access this site, log on at: " + productionSiteUrl + "\n\n");

			buf.append("Log in using your user id \"" + user.getEid() + "\" and the password you have set for this account.\n\n");
			buf.append("If you don't remember your password, use \"Reset Password\" on the left of Etudes.\n\n");

			buf.append("---------------------\n\nThis is an automatically generated email from Etudes. Do not reply to it!\n\n");

			content = buf.toString();
			emailService().send(from, to, subject, content, headerTo, replyTo, null);
		}
	}

	/**
	 * Notify an instructor in a site that Observers were added to the site.
	 * 
	 * @param user
	 *        The user.
	 * @param siteTitle
	 *        The site title.
	 * @param observers
	 *        A List of Users for the observers being added to the site.
	 */
	protected void notifyObserversAdded(User user, String siteTitle, List<User> observers, Date start, Date end)
	{
		String from = "\"" + serverConfigurationService().getString("ui.service", "Sakai") + "\"<no-reply@"
				+ serverConfigurationService().getServerName() + ">";
		String productionSiteName = serverConfigurationService().getString("ui.service", "");

		String to = user.getEmail();
		String headerTo = user.getEmail();
		String replyTo = user.getEmail();
		String subject = "Observers Added to " + productionSiteName + " Site " + siteTitle;

		String content = "";

		if (from != null && user.getEmail() != null)
		{
			StringBuffer buf = new StringBuffer();

			buf.append("Dear " + user.getDisplayName() + ":\n\n");
			buf.append(userDirectoryService().getCurrentUser().getDisplayName() + " has added the following \"observers\" to your "
					+ productionSiteName + " site \"" + siteTitle + "\":\n\n");

			for (User observer : observers)
			{
				buf.append("  - " + observer.getDisplayName() + "\n");
			}
			buf.append("\n");

			if ((start != null) && (end != null))
			{
				buf.append("Observers will have access to the site between "
						+ CdpResponseHelper.dateTimeDisplayInUserZone(start.getTime(), user.getId()) + " and "
						+ CdpResponseHelper.dateTimeDisplayInUserZone(end.getTime(), user.getId()) + ".\n\n");
			}
			else if (start != null)
			{
				buf.append("Observers will have access to the site starting "
						+ CdpResponseHelper.dateTimeDisplayInUserZone(start.getTime(), user.getId()) + ".\n\n");
			}
			else if (end != null)
			{
				buf.append("Observers will have access to the site until " + CdpResponseHelper.dateTimeDisplayInUserZone(end.getTime(), user.getId())
						+ ".\n\n");
			}

			buf.append("Observers have access to your course site, and may review published materials, even those that have closed or are not yet open."
					+ "  They may not participate in the site nor see student submissions, grades, or class tracking data.\n\n");

			buf.append("---------------------\n\nThis is an automatically generated email from Etudes. Do not reply to it!\n\n");

			content = buf.toString();
			emailService().send(from, to, subject, content, headerTo, replyTo, null);
		}
	}

	/**
	 * Remove our security advisor.
	 */
	protected void popAdvisor()
	{
		securityService().popAdvisor();
	}

	@SuppressWarnings("unchecked")
	protected void processObservers(Site destination, List<User> instructors, Date startDate, Date endDate, Set<String> observerIdSet,
			List<User> observers)
	{
		try
		{
			// Date now = new Date();
			// boolean immediate = (((startDate == null) || startDate.before(now) || startDate.equals(now)) && ((endDate == null) || endDate.after(now)));
			boolean toSchedule = ((startDate != null) || (endDate != null));

			boolean needToSaveSite = false;

			// assure the site has the observer role
			if (destination.getRole("Observer") == null)
			{
				// copy it from the !site.template.course
				AuthzGroup template = authzGroupService().getAuthzGroup("!site.template.course");
				Role observer = template.getRole("Observer");
				destination.addRole("Observer", observer);
				needToSaveSite = true;
			}

			List<User> observersAdded = new ArrayList<User>();

			// if we don't have any dates, we won't schedule, and will apply the observers now
			if (!toSchedule)
			{
				// remove any existing schedule
				removeSchedule(destination.getId());

				// remove existing observers not being added
				Set<String> existingObservers = destination.getUsersHasRole("Observer");
				for (String existingObserverId : existingObservers)
				{
					if (!observerIdSet.contains(existingObserverId))
					{
						destination.removeMember(existingObserverId);
						needToSaveSite = true;
					}
				}

				// add observers not already there
				for (User observer : observers)
				{
					// not if instructor, ta, observer or student - guest is ok
					Member existingRole = destination.getMember(observer.getId());
					if ((existingRole == null) || (existingRole.getRole().getId().equals("Guest")))
					{
						// add user as observer in destination
						destination.addMember(observer.getId(), "Observer", true, true);
						needToSaveSite = true;
						observersAdded.add(observer);
					}
				}
			}

			// otherwise, we will make an active schedule, and let the scheduler handle it
			else
			{
				Set<User> observersToSchedule = new HashSet<User>();

				// make sure there are no active observers now
				Set<String> existingObservers = destination.getUsersHasRole("Observer");
				for (String existingObserverId : existingObservers)
				{
					destination.removeMember(existingObserverId);
					needToSaveSite = true;
				}

				// who's new? who can be an observer?
				Set<String> existingScheduledObservers = readScheduledObserverIds(destination.getId());
				for (User observer : observers)
				{
					Member existingRole = destination.getMember(observer.getId());
					if ((existingRole == null) || (existingRole.getRole().getId().equals("Guest"))
							|| (existingRole.getRole().getId().equals("Observer")))
					{
						observersToSchedule.add(observer);
						if (!existingScheduledObservers.contains(observer.getId()))
						{
							observersAdded.add(observer);
						}
					}
				}

				// if the dates have changed, trigger the emails by saying that everyone got added
				ScheduleDates dates = readScheduleDates(destination.getId());
				boolean datesChanged = (dates == null) || Different.different(dates.start, startDate) || Different.different(dates.end, endDate);
				if (datesChanged)
				{
					observersAdded.clear();
					observersAdded.addAll(observersToSchedule);
				}

				// create / update the record with the dates, observersToSchedule - if we have any
				if (!observersToSchedule.isEmpty())
				{
					// immediate processing
					Date now = new Date();
					for (User observer : observersToSchedule)
					{
						if (processSchedule(startDate, endDate, destination, observer.getId(), now)) needToSaveSite = true;
					}

					assureSchedule(destination.getId(), startDate, endDate, observersToSchedule);
				}

				// otherwise, if empty, then remove the schedule
				else
				{
					removeSchedule(destination.getId());
				}
			}

			if (!observersAdded.isEmpty())
			{
				// tell the observers
				for (User observer : observersAdded)
				{
					notifyMember(observer, destination.getTitle(), startDate, endDate);
				}

				// tell the instructors
				for (User instructor : instructors)
				{
					notifyObserversAdded(instructor, destination.getTitle(), observersAdded, startDate, endDate);
				}
			}

			if (needToSaveSite)
			{
				siteService().save(destination);
			}
		}
		catch (IdUnusedException e)
		{
			M_log.warn("processObservers: " + e);
		}
		catch (PermissionException e)
		{
			M_log.warn("processObservers: " + e);
		}
		catch (RoleAlreadyDefinedException e)
		{
			M_log.warn("processObservers: " + e);
		}
		catch (GroupNotDefinedException e)
		{
			M_log.warn("processObservers: " + e);
		}
	}

	protected List<User> processObserversParameter(Site site, String observersParameter, StringBuilder badIids, Set<String> observerIdSet)
	{
		List<User> observers = new ArrayList<User>();
		String observerIds = StringUtil.trimToNull(observersParameter);
		if (observerIds != null)
		{
			// allow <> comments
			observerIds = observerIds.replaceAll("<(.+?)>", "");

			String[] iidStrs = StringUtil.split(observerIds, ",");
			for (String iid : iidStrs)
			{
				iid = StringUtil.trimToNull(iid);
				if (iid == null) continue;

				// if the iid is in the form 383838@FH, ignore the @FH
				int pos = iid.indexOf("@");
				if (pos != -1)
				{
					iid = iid.substring(0, pos);
				}

				// get the user with this iid
				String iidCode = rosterService().findInstitutionCode(site.getTitle());
				try
				{
					User user = userDirectoryService().getUserByIid(iidCode, iid);
					observers.add(user);
					observerIdSet.add(user.getId());
				}
				catch (UserNotDefinedException e)
				{
					if (badIids.length() == 0)
					{
						badIids.append(iid);
					}
					else
					{
						badIids.append(", " + iid);
					}
				}
			}
		}

		return observers;
	}

	protected boolean processSchedule(Date start, Date end, Site site, String observerId, Date now)
	{
		boolean needToSaveSite = false;

		Member existingRole = site.getMember(observerId);

		boolean effective = (((start == null) || start.before(now) || start.equals(now)) && ((end == null) || end.after(now)));

		// add the user if not in the site, or if a guest
		if (effective)
		{
			if ((existingRole == null) || (existingRole.getRole().getId().equals("Guest")))
			{
				site.addMember(observerId, "Observer", true, true);
				needToSaveSite = true;
			}
		}

		// remove the user, if in the site as an Observer
		else
		{
			if ((existingRole != null) && (existingRole.getRole().getId().equals("Observer")))
			{
				site.removeMember(observerId);
				needToSaveSite = true;
			}
		}

		return needToSaveSite;
	}

	/**
	 * Setup a security advisor.
	 */
	protected void pushAdvisor()
	{
		// setup a security advisor
		securityService().pushAdvisor(new SecurityAdvisor()
		{
			public SecurityAdvice isAllowed(String userId, String function, String reference)
			{
				return SecurityAdvice.ALLOWED;
			}
		});
	}

	protected Boolean readBoolean(ResultSet result, int index) throws SQLException
	{
		String value = result.getString(index);
		return "1".equals(value);
	}

	protected Date readDate(ResultSet result, int index) throws SQLException
	{
		long time = result.getLong(index);
		if (time == 0) return null;
		return new Date(time);
	}

	protected List<Eval> readEvals(String clientPrefix, String excludeSiteId, Long term)
	{
		final List<Eval> rv = new ArrayList<Eval>();

		int numFields = 2;
		String termClause = "";
		if (term != null)
		{
			termClause = " AND T.TERM_ID = ?";
			numFields++;
		}

		// first, read the assessments
		String sql = "SELECT S.SITE_ID, S.TITLE, T.TERM_ID, A.ID, A.TITLE, A.DATES_OPEN, A.DATES_DUE,"
				+ " A.RESULTS_EMAIL, A.RESULTS_SENT, A.LIVE, A.NOTIFY_EVAL, A.ARCHIVED, A.PUBLISHED" //
				+ " FROM MNEME_ASSESSMENT A JOIN SAKAI_SITE S ON A.CONTEXT = S.SITE_ID" //
				+ " JOIN ARCHIVES_SITE_TERM T ON S.SITE_ID = T.SITE_ID" //
				+ " WHERE S.TITLE LIKE ? AND S.SITE_ID != ? AND A.FORMAL_EVAL='1'" + termClause //
				+ " ORDER BY T.TERM_ID DESC, S.TITLE ASC, A.TITLE ASC, A.ID ASC";

		Object[] fields = new Object[numFields];
		fields[0] = clientPrefix + " %";
		fields[1] = excludeSiteId;
		if (term != null) fields[2] = term;

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Eval eval = new Eval();
					int i = 1;
					eval.siteId = readString(result, i++);
					eval.siteTitle = readString(result, i++);
					eval.termId = readLong(result, i++);
					eval.assessmentId = readLong(result, i++);
					eval.assessmentTitle = readString(result, i++);
					eval.open = readDate(result, i++);
					eval.due = readDate(result, i++);
					eval.email = readString(result, i++);
					eval.sent = readDate(result, i++);
					eval.live = readBoolean(result, i++);
					eval.notify = readBoolean(result, i++);
					eval.archived = readBoolean(result, i++);
					eval.published = readBoolean(result, i++);

					rv.add(eval);
					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readEvals: " + e);
					return null;
				}
			}
		});

		// get the instructors and observers for the sites
		sql = "SELECT S.SITE_ID, RR.ROLE_NAME, G.USER_ID" //
				+ " FROM MNEME_ASSESSMENT A JOIN SAKAI_SITE S ON A.CONTEXT = S.SITE_ID" //
				+ " JOIN ARCHIVES_SITE_TERM T ON S.SITE_ID = T.SITE_ID" //
				+ " JOIN SAKAI_REALM R ON CONCAT('/site/', S.SITE_ID) = R.REALM_ID" //
				+ " JOIN SAKAI_REALM_RL_GR G ON R.REALM_KEY = G.REALM_KEY" //
				+ " JOIN SAKAI_REALM_ROLE RR ON G.ROLE_KEY = RR.ROLE_KEY" //
				+ " WHERE S.TITLE LIKE ? AND S.SITE_ID != ? AND A.FORMAL_EVAL='1'" + termClause //
				+ " AND (RR.ROLE_NAME = 'Instructor' OR RR.ROLE_NAME = 'Observer')" //
				+ " AND G.ACTIVE = '1' AND G.PROVIDED = '1'" //
				+ " ORDER BY S.SITE_ID ASC";

		final Map<String, InstructorsAndObservers> instructorsAndObservers = new HashMap<String, InstructorsAndObservers>();
		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					int i = 1;
					String siteId = sqlService().readString(result, i++);
					String role = sqlService().readString(result, i++);
					String userId = sqlService().readString(result, i++);

					InstructorsAndObservers forSite = instructorsAndObservers.get(siteId);
					if (forSite == null)
					{
						forSite = new InstructorsAndObservers();
						instructorsAndObservers.put(siteId, forSite);
					}

					if (role.equals("Instructor"))
					{
						forSite.instructors.add(userId);
					}
					else if (role.equals("Observer"))
					{
						forSite.observers.add(userId);
					}

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readEvals: " + e);
					return null;
				}
			}
		});

		// pick up potential observers
		sql = "SELECT E.SITE_ID, E.OBSERVATION_START, E.OBSERVATION_END, O.OBSERVER_ID" //
				+ " FROM EVALMANAGER_SCHEDULE E" //
				+ " JOIN EVALMANAGER_OBSERVER O ON E.SITE_ID = O.SITE_ID" //
				+ " JOIN SAKAI_SITE S ON E.SITE_ID = S.SITE_ID " //
				+ " WHERE S.TITLE LIKE ? AND S.SITE_ID != ?";
		fields = new Object[2];
		fields[0] = clientPrefix + " %";
		fields[1] = excludeSiteId;

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					int i = 1;
					String siteId = readString(result, i++);
					Date start = sqlService().readDate(result, i++);
					Date end = sqlService().readDate(result, i++);
					String userId = sqlService().readString(result, i++);

					InstructorsAndObservers forSite = instructorsAndObservers.get(siteId);
					if (forSite == null)
					{
						forSite = new InstructorsAndObservers();
						instructorsAndObservers.put(siteId, forSite);
					}

					forSite.start = start;
					forSite.end = end;
					forSite.observers.add(userId);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readEvals: " + e);
					return null;
				}
			}
		});

		// fill out the evals with site instructor and observer info
		for (Eval eval : rv)
		{
			InstructorsAndObservers forSite = instructorsAndObservers.get(eval.siteId);
			if (forSite != null)
			{
				eval.instructorsPlain = null;
				eval.instructors = null;
				for (String userId : forSite.instructors)
				{
					try
					{
						User u = userDirectoryService().getUser(userId);
						String name = u.getSortName();
						String nameHtml = "<span style='white-space: nowrap;'>" + name + "</span>";
						if (eval.instructors == null)
						{
							eval.instructorsPlain = name;
							eval.instructors = nameHtml;
						}
						else
						{
							eval.instructorsPlain += " | " + name;
							eval.instructors += "<br />" + nameHtml;
						}
					}
					catch (UserNotDefinedException e)
					{
					}
				}

				eval.observersPlain = null;
				eval.observers = null;
				eval.observerIids = null;
				for (String userId : forSite.observers)
				{
					try
					{
						User u = userDirectoryService().getUser(userId);
						String name = u.getSortName();
						String nameHtml = "<span style='white-space: nowrap;'>" + name + "</span>";
						String iid = u.getIidInContext(eval.siteId) + " <" + name + ">";
						if (eval.observers == null)
						{
							eval.observersPlain = name;
							eval.observers = nameHtml;
							eval.observerIids = iid;
						}
						else
						{
							eval.observersPlain += " | " + name;
							eval.observers += "<br />" + nameHtml;
							eval.observerIids += ", " + iid;
						}
					}
					catch (UserNotDefinedException e)
					{
					}
				}

				eval.start = forSite.start;
				eval.end = forSite.end;
			}
		}

		return rv;
	}

	protected Long readLong(ResultSet result, int index) throws SQLException
	{
		String str = StringUtil.trimToNull(result.getString(index));
		if (str == null) return null;
		try
		{
			return Long.valueOf(str);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	protected List<Eval> readReadys(String siteId)
	{
		final List<Eval> rv = new ArrayList<Eval>();
		String sql = "SELECT A.ID, A.TITLE, A.DATES_OPEN, A.DATES_DUE, A.RESULTS_EMAIL, A.NOTIFY_EVAL" //
				+ " FROM MNEME_ASSESSMENT A JOIN SAKAI_SITE S ON A.CONTEXT = S.SITE_ID" //
				+ " WHERE S.SITE_ID = ? AND A.ARCHIVED = '0' AND A.FORMAL_EVAL='1'" //
				+ " ORDER BY A.TITLE ASC";

		Object[] fields = new Object[1];
		fields[0] = siteId;
		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Eval eval = new Eval();
					int i = 1;
					eval.assessmentId = readLong(result, i++);
					eval.assessmentTitle = readString(result, i++);
					eval.open = readDate(result, i++);
					eval.due = readDate(result, i++);
					eval.email = readString(result, i++);
					eval.notify = readBoolean(result, i++);

					rv.add(eval);
					return null;
				}
				catch (SQLException e)
				{
					e.printStackTrace();
					M_log.warn("readReadys: " + e);
					return null;
				}
			}
		});

		return rv;
	}

	protected ScheduleDates readScheduleDates(final String siteId)
	{
		String sql = "SELECT E.OBSERVATION_START, E.OBSERVATION_END FROM EVALMANAGER_SCHEDULE E WHERE E.SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		final List<ScheduleDates> rv = new ArrayList<ScheduleDates>();
		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					ScheduleDates s = new ScheduleDates();
					int i = 1;
					s.start = sqlService().readDate(result, i++);
					s.end = sqlService().readDate(result, i++);

					rv.add(s);
					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readSchedule: " + e);
					return null;
				}
			}
		});

		return rv.isEmpty() ? null : rv.get(0);
	}

	protected Set<String> readScheduledObserverIds(String siteId)
	{
		String sql = "SELECT OBSERVER_ID FROM EVALMANAGER_OBSERVER WHERE SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		final Set<String> rv = new HashSet<String>();
		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					int i = 1;
					String observerId = readString(result, i++);
					rv.add(observerId);

					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readScheduledObserverIds: " + e);
					return null;
				}
			}
		});

		return rv;
	}

	protected List<EvalSite> readSites(String clientPrefix, String excludeSiteId, String title, Long term, String subject)
	{
		final List<EvalSite> rv = new ArrayList<EvalSite>();

		String termClause = "";
		int numFields = 2;
		if (term != null)
		{
			termClause = " AND T.TERM_ID = ?";
			numFields++;
		}

		String sql = "SELECT S.SITE_ID, S.TITLE, T.TERM_ID, AT.SUFFIX, U.LAST_NAME, U.FIRST_NAME" //
				+ " FROM SAKAI_SITE S JOIN ARCHIVES_SITE_TERM T ON S.SITE_ID = T.SITE_ID" //
				+ " JOIN ARCHIVES_TERM AT ON T.TERM_ID = AT.ID" //
				+ " JOIN SAKAI_REALM R ON CONCAT('/site/', S.SITE_ID) = R.REALM_ID" //
				+ " JOIN SAKAI_REALM_RL_GR G ON R.REALM_KEY = G.REALM_KEY" //
				+ " JOIN SAKAI_USER U ON G.USER_ID = U.USER_ID" //
				+ " JOIN SAKAI_REALM_ROLE RR ON G.ROLE_KEY = RR.ROLE_KEY" //
				+ " LEFT OUTER JOIN MNEME_ASSESSMENT A ON S.SITE_ID = A.CONTEXT AND A.ARCHIVED = '0' AND A.FORMAL_EVAL = '1'"
				+ " WHERE S.TITLE LIKE ? AND S.SITE_ID != ?" + termClause //
				+ " AND RR.ROLE_NAME = 'Instructor' AND G.ACTIVE = '1' AND G.PROVIDED = '1' AND A.ID IS NULL" //
				+ " ORDER BY T.TERM_ID DESC, S.TITLE ASC";

		String titlePattern = clientPrefix + ((subject != null) ? (" " + subject) : "") + " %" + (title == null ? "" : (title + "%"));
		Object[] fields = new Object[numFields];
		fields[0] = titlePattern;
		fields[1] = excludeSiteId;
		if (term != null)
		{
			fields[2] = term;
		}

		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					EvalSite s = new EvalSite();
					int i = 1;
					s.siteId = readString(result, i++);
					s.siteTitle = readString(result, i++);
					String[] parts = StringUtil.split(s.siteTitle, " ");
					if (parts.length > 1)
					{
						s.siteSubject = parts[1];
					}
					s.termId = readLong(result, i++);
					s.termName = CdpResponseHelper.describeTerm(readStringZero(result, i++));
					s.instructors = "<span style='white-space: nowrap;'>" + readString(result, i++) + ", " + readString(result, i++) + "</span>";

					rv.add(s);
					return null;
				}
				catch (SQLException e)
				{
					e.printStackTrace();
					M_log.warn("readSites: " + e);
					return null;
				}
			}
		});

		List<EvalSite> combined = new ArrayList<EvalSite>();
		EvalSite prev = null;
		for (EvalSite s : rv)
		{
			if (prev != null)
			{
				if (prev.siteId.equals(s.siteId))
				{
					if (s.instructors != null)
					{
						if (prev.instructors != null)
						{
							prev.instructors = prev.instructors + "<br />" + s.instructors;
						}
						else
						{
							prev.instructors = s.instructors;
						}
					}
				}
				else
				{
					combined.add(s);
					prev = s;
				}
			}
			else
			{
				combined.add(s);
				prev = s;
			}
		}

		return combined;
	}

	protected String readString(ResultSet result, int index) throws SQLException
	{
		return StringUtil.trimToNull(result.getString(index));
	}

	protected String readStringZero(ResultSet result, int index) throws SQLException
	{
		return StringUtil.trimToZero(result.getString(index));
	}

	protected List<String> readSubjects(String clientPrefix)
	{
		final Set<String> set = new HashSet<String>();

		String sql = "SELECT S.TITLE FROM SAKAI_SITE S" //
				+ " JOIN ARCHIVES_SITE_TERM T ON S.SITE_ID = T.SITE_ID" //
				+ " WHERE S.TITLE LIKE ? AND T.TERM_ID > 6" // > dev
				+ " ORDER BY S.TITLE ASC";

		Object[] fields = new Object[1];
		fields[0] = clientPrefix + " %";
		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String title = readString(result, 1);

					String[] parts = StringUtil.split(title, " ");
					if (parts.length > 1)
					{
						set.add(parts[1]);
					}

					return null;
				}
				catch (SQLException e)
				{
					e.printStackTrace();
					M_log.warn("readTerms: " + e);
					return null;
				}
			}
		});

		List<String> rv = new ArrayList<String>(set);
		Collections.sort(rv);
		return rv;
	}

	protected List<Term> readTerms(String clientPrefix)
	{
		final List<Term> rv = new ArrayList<Term>();

		String sql = "SELECT AT.ID, AT.SUFFIX FROM ARCHIVES_TERM AT WHERE AT.ID IN" //
				+ " (SELECT DISTINCT TT.TERM_ID" //
				+ " FROM SAKAI_SITE SS JOIN ARCHIVES_SITE_TERM TT ON SS.SITE_ID = TT.SITE_ID" //
				+ " WHERE SS.TITLE LIKE ? AND TT.TERM_ID > 6)" // > dev
				+ " ORDER BY AT.ID DESC";

		Object[] fields = new Object[1];
		fields[0] = clientPrefix + " %";
		sqlService().dbRead(sql, fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Term t = new Term();
					int i = 1;
					t.id = readLong(result, i++);
					t.name = readString(result, i++);

					rv.add(t);
					return null;
				}
				catch (SQLException e)
				{
					e.printStackTrace();
					M_log.warn("readTerms: " + e);
					return null;
				}
			}
		});

		return rv;
	}

	protected void removeObservers(final String siteId)
	{
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				removeObserversTx(siteId);
			}
		}, "removeObservers: " + siteId);
	}

	protected void removeObserversTx(String siteId)
	{
		String sql = "DELETE FROM EVALMANAGER_OBSERVER WHERE SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("removeObserversTx: db write failed: " + siteId);
		}
	}

	protected void removeSchedule(final String siteId)
	{
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				removeScheduleTx(siteId);
			}
		}, "removeObservers: " + siteId);
	}

	protected void removeScheduleTx(String siteId)
	{
		String sql = "DELETE FROM EVALMANAGER_OBSERVER WHERE SITE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("removeObserversTx: db write failed: " + siteId);
		}

		sql = "DELETE FROM EVALMANAGER_SCHEDULE WHERE SITE_ID = ?";
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("removeObserversTx: db write failed: " + siteId);
		}
	}

	protected void respondSites(Map<String, Object> rv, String sitePrefix, String siteId, String title, Long term, String subject)
	{
		String firstSubject = null;
		List<EvalSite> sites = readSites(sitePrefix, siteId, title, term, (("*".equals(subject)) ? null : subject));
		List<Map<String, String>> sitesMap = new ArrayList<Map<String, String>>();
		rv.put("sites", sitesMap);
		for (EvalSite es : sites)
		{
			// if subject is null, return only sites of the first subject found
			if ((es.siteSubject != null) && (subject == null))
			{
				if (firstSubject == null)
				{
					firstSubject = es.siteSubject;
				}
				else
				{
					if (!es.siteSubject.equals(firstSubject))
					{
						continue;
					}
				}
			}

			Map<String, String> siteMap = new HashMap<String, String>();
			sitesMap.add(siteMap);

			siteMap.put("siteId", es.siteId);
			siteMap.put("siteTitle", es.siteTitle);
			if (es.siteSubject != null) siteMap.put("siteSubject", es.siteSubject);
			siteMap.put("termId", es.termId.toString());
			siteMap.put("termName", es.termName);
			siteMap.put("instructors", es.instructors);
		}
	}

	protected String sqlBoolean(Boolean b)
	{
		return b ? "1" : "0";
	}

	/**
	 * @return The SqlService, via the component manager.
	 */
	protected SqlService sqlService()
	{
		return (SqlService) ComponentManager.get(SqlService.class);
	}

	protected Long timeOrNull(Date d)
	{
		if (d == null) return null;
		return d.getTime();
	}

	protected void updateAssessment(final Assessment assessment, final String title, final String email, final Date open, final Date due,
			final Boolean notify)
	{
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				updateAssessmentTx(assessment, title, email, open, due, notify);
			}
		}, "updateAssessment: " + assessment.getId());
	}

	protected void updateAssessmentTx(Assessment assessment, String title, String email, Date open, Date due, Boolean notify)
	{
		String sql = "UPDATE MNEME_ASSESSMENT SET TITLE=?, RESULTS_EMAIL=?, DATES_OPEN=?, DATES_DUE=?, NOTIFY_EVAL=? WHERE ID=?";
		Object[] fields = new Object[6];
		fields[0] = (title == null) ? assessment.getTitle() : title;
		fields[1] = (email == null) ? assessment.getResultsEmail() : email;
		fields[2] = timeOrNull((open == null) ? assessment.getDates().getOpenDate() : open);
		fields[3] = timeOrNull((due == null) ? assessment.getDates().getDueDate() : due);
		fields[4] = sqlBoolean((notify == null) ? assessment.getNotifyEval() : notify);
		fields[5] = Long.valueOf(assessment.getId());
		if (!sqlService().dbWrite(sql.toString(), fields))
		{
			throw new RuntimeException("updateAssessmentTx: db write failed");
		}
	}

	protected void updateSchedule(final Long id, final Date start, final Date end)
	{
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				updateScheduleTx(id, start, end);
			}
		}, "updateSchedule: " + id);
	}

	protected void updateScheduleTx(Long id, Date start, Date end)
	{
		String sql = "UPDATE EVALMANAGER_SCHEDULE SET OBSERVATION_START = ?, OBSERVATION_END = ?, ACTIVE = '1' WHERE ID = ?";
		Object[] fields = new Object[3];
		fields[0] = sqlService().valueForDate(start);
		fields[1] = sqlService().valueForDate(end);
		fields[2] = id;
		if (!sqlService().dbWrite(sql, fields))
		{
			throw new RuntimeException("updateScheduleTx: db write failed: " + id);
		}
	}

	/**
	 * @return The AssessmentService, via the component manager.
	 */
	private AssessmentService assessmentService()
	{
		return (AssessmentService) ComponentManager.get(AssessmentService.class);
	}

	/**
	 * @return The AuthzGroupService, via the component manager.
	 */
	private AuthzGroupService authzGroupService()
	{
		return (AuthzGroupService) ComponentManager.get(AuthzGroupService.class);
	}

	/**
	 * @return The AuthzGroupService, via the component manager.
	 */
	private EmailService emailService()
	{
		return (EmailService) ComponentManager.get(EmailService.class);
	}

	/**
	 * @return The MnemeTransferService, via the component manager.
	 */
	private MnemeTransferService mnemeTransferService()
	{
		return (MnemeTransferService) ComponentManager.get(MnemeTransferService.class);
	}

	/**
	 * @return The PoolService, via the component manager.
	 */
	private PoolService poolService()
	{
		return (PoolService) ComponentManager.get(PoolService.class);
	}

	/**
	 * @return The RosterService, via the component manager.
	 */
	private RosterService rosterService()
	{
		return (RosterService) ComponentManager.get(RosterService.class);
	}

	/**
	 * @return The SecurityService, via the component manager.
	 */
	private SecurityService securityService()
	{
		return (SecurityService) ComponentManager.get(SecurityService.class);
	}

	/**
	 * @return The ServerConfigurationService, via the component manager.
	 */
	private ServerConfigurationService serverConfigurationService()
	{
		return (ServerConfigurationService) ComponentManager.get(ServerConfigurationService.class);
	}

	/**
	 * @return The SiteService, via the component manager.
	 */
	private SiteService siteService()
	{
		return (SiteService) ComponentManager.get(SiteService.class);
	}

	/**
	 * @return The SubmissionService, via the component manager.
	 */
	private SubmissionService submissionService()
	{
		return (SubmissionService) ComponentManager.get(SubmissionService.class);
	}

	/**
	 * @return The UserDirectoryService, via the component manager.
	 */
	private UserDirectoryService userDirectoryService()
	{
		return (UserDirectoryService) ComponentManager.get(UserDirectoryService.class);
	}
}
