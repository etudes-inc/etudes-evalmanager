/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/evalmanager/trunk/evalmanager-webapp/webapp/src/java/org/etudes/evalmanager/cdp/EvalManagerAccessProvider.java $
 * $Id: EvalManagerAccessProvider.java 9165 2014-11-12 21:07:27Z ggolden $
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

import java.io.OutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.evalmanager.cdp.EvalManagerCdpHandler.Eval;
import org.etudes.evalmanager.cdp.EvalManagerCdpHandler.ResponseRate;
import org.etudes.mneme.api.AssessmentService;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.entity.api.Entity;
import org.sakaiproject.entity.api.EntityAccessOverloadException;
import org.sakaiproject.entity.api.EntityCopyrightException;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.EntityNotDefinedException;
import org.sakaiproject.entity.api.EntityPermissionException;
import org.sakaiproject.entity.api.EntityProducer;
import org.sakaiproject.entity.api.HttpAccess;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.api.ResourceProperties;
import org.sakaiproject.entity.api.ResourcePropertiesEdit;
import org.sakaiproject.exception.CopyrightException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.util.BaseResourcePropertiesEdit;
import org.sakaiproject.util.StringUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * EvalManagerAccessProvider implements EntityProducer for the evaluation manager.
 */
public class EvalManagerAccessProvider implements EntityProducer
{
	/** The reference type. */
	static final String REF_TYPE = "etudes:evalmanager";

	/** This string starts the references to download requests. */
	static final String REFERENCE_ROOT = "/evalmanager";

	static final String SEPARATOR = ",";

	/** Our logger. */
	private static Log M_log = LogFactory.getLog(EvalManagerAccessProvider.class);

	/** The cdp handler we use for help getting data. */
	EvalManagerCdpHandler cdpHandler = null;

	/**
	 * Construct
	 * 
	 * @param cdpHandler
	 *        The cdp handler we use for help getting data.
	 */
	public EvalManagerAccessProvider(EvalManagerCdpHandler cdpHandler)
	{
		this.cdpHandler = cdpHandler;

		// register as an entity producer
		entityManager().registerEntityProducer(this, REFERENCE_ROOT);
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("rawtypes")
	public String archive(String siteId, Document doc, Stack stack, String archivePath, List attachments)
	{
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public Entity getEntity(Reference ref)
	{
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("rawtypes")
	public Collection getEntityAuthzGroups(Reference ref, String userId)
	{
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getEntityDescription(Reference ref)
	{
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public ResourceProperties getEntityResourceProperties(Reference ref)
	{
		// decide on security
		if (!checkSecurity(ref)) return null;

		ResourcePropertiesEdit props = new BaseResourcePropertiesEdit();

		props.addProperty(ResourceProperties.PROP_CONTENT_TYPE, "text/csv");
		props.addProperty(ResourceProperties.PROP_IS_COLLECTION, "FALSE");
		props.addProperty("DAV:displayname", "Formal Evaluations");

		return props;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getEntityUrl(Reference ref)
	{
		return serverConfigurationService().getAccessUrl() + ref.getReference();
	}

	/**
	 * {@inheritDoc}
	 */
	public HttpAccess getHttpAccess()
	{
		return new HttpAccess()
		{
			@SuppressWarnings("rawtypes")
			public void handleAccess(HttpServletRequest req, HttpServletResponse res, Reference ref, Collection copyrightAcceptedRefs)
					throws EntityPermissionException, EntityNotDefinedException, EntityAccessOverloadException, EntityCopyrightException
			{
				// decide on security
				if (!checkSecurity(ref))
				{
					throw new EntityPermissionException(sessionManager().getCurrentSessionUserId(), "access", ref.getReference());
				}

				handleAccessDownload(req, res, ref, copyrightAcceptedRefs);
			}
		};
	}

	/**
	 * {@inheritDoc}
	 */
	public String getLabel()
	{
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("rawtypes")
	public String merge(String siteId, Element root, String archivePath, String fromSiteId, Map attachmentNames, Map userIdTrans,
			Set userListAllowImport)
	{
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean parseEntityReference(String reference, Reference ref)
	{
		if (reference.startsWith(REFERENCE_ROOT))
		{
			// /evalmanager/<setup site id>/<term>
			String id = null;
			String context = null;
			String subType = null;
			String[] parts = StringUtil.split(reference, Entity.SEPARATOR);
			String container = null;

			// overview export
			if (parts.length == 4)
			{
				// setup site id
				context = parts[2];

				// term id
				container = parts[3];
			}

			else
			{
				return false;
			}

			ref.set(REF_TYPE, subType, id, container, context);

			return true;
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean willArchiveMerge()
	{
		return false;
	}

	/**
	 * Check security for this entity.
	 * 
	 * @param ref
	 *        The Reference to the entity.
	 * @return true if allowed, false if not.
	 */
	protected boolean checkSecurity(Reference ref)
	{
		// user must have permission to the site
		// return siteService().allowAccessSite(ref.getContext());

		// user must have mneme fce permission
		return assessmentService().allowSetFormalCourseEvaluation(ref.getContext());
	}

	/**
	 * @return The AssessmentService, via the component manager.
	 */
	private AssessmentService assessmentService()
	{
		return (AssessmentService) ComponentManager.get(AssessmentService.class);
	}

	/**
	 * If the value contains the separator, escape it with quotes.
	 * 
	 * @param value
	 *        The value to escape.
	 * @return The escaped value.
	 */
	protected String escape(String value)
	{
		if (value.indexOf(SEPARATOR) != -1)
		{
			// any quotes in here need to be doubled
			value = value.replaceAll("\"", "\"\"");

			return "\"" + value + "\"";
		}

		return value;
	}

	/**
	 * Format a date for inclusion in the csv file
	 * 
	 * @param date
	 *        The date.
	 * @return The formatted date.
	 */
	protected String formatDate(Date date)
	{
		if (date == null) return "-";
		DateFormat format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US);
		return escape(removeSeconds(format.format(date)));
	}

	/**
	 * Format an Float for inclusion in the csv.
	 * 
	 * @param value
	 *        The value.
	 * @return The value formatted.
	 */
	protected String formatFloat(Float value)
	{
		if (value == null) return "-";
		return escape(value.toString());
	}

	/**
	 * Format an Integer for inclusion in the csv.
	 * 
	 * @param value
	 *        The value.
	 * @return The value formatted.
	 */
	protected String formatInteger(Integer value)
	{
		if (value == null) return "-";
		return escape(value.toString());
	}

	/**
	 * Format a String for inclusion in the csv.
	 * 
	 * @param value
	 *        The value.
	 * @return The value formatted.
	 */
	protected String formatString(String value)
	{
		if (value == null) return "-";
		return escape(value);
	}

	/**
	 * Process the access request for a download (not CHS private docs).
	 * 
	 * @param req
	 * @param res
	 * @param ref
	 * @param copyrightAcceptedRefs
	 * @throws PermissionException
	 * @throws IdUnusedException
	 * @throws ServerOverloadException
	 * @throws CopyrightException
	 */
	@SuppressWarnings("rawtypes")
	protected void handleAccessDownload(HttpServletRequest req, HttpServletResponse res, Reference ref, Collection copyrightAcceptedRefs)
			throws EntityPermissionException, EntityNotDefinedException, EntityAccessOverloadException, EntityCopyrightException
	{
		PrintStream printer = null;
		try
		{
			Site site = siteService().getSite(ref.getContext());
			String[] siteParts = StringUtil.split(site.getTitle(), " ");
			String sitePrefix = siteParts[0].toLowerCase();
			Long term = Long.parseLong(ref.getContainer());

			res.setContentType("text/csv");
			res.addHeader("Content-Disposition", "attachment; filename=\"" + sitePrefix + "_" + term + "_evaluations.csv" + "\"");

			OutputStream out = res.getOutputStream();
			printer = new PrintStream(out, true, "UTF-8");

			printEvaluationsReport(printer, site, sitePrefix, term);

			printer.flush();
		}
		catch (Throwable e)
		{
			M_log.warn("handleAccessDownload: ", e);
		}
		finally
		{
			if (printer != null)
			{
				try
				{
					printer.close();
				}
				catch (Throwable e)
				{
					M_log.warn("closing printer: " + e.toString());
				}
			}
		}
	}

	/**
	 * Remove our security advisor.
	 */
	protected void popAdvisor()
	{
		securityService().popAdvisor();
	}

	/**
	 * Format the overview csv for the evaluations report.
	 * 
	 * @param out
	 *        The print stream.
	 * @param ref
	 *        The requested reference.
	 */
	protected void printEvaluationsReport(PrintStream out, Site site, String client, Long term)
	{
		List<Eval> evals = this.cdpHandler.readEvals(client, site.getId(), term);

		out.println("Site" + SEPARATOR + "Instructors" + SEPARATOR + "Observers" + SEPARATOR + "Title" + SEPARATOR + "Open" + SEPARATOR + "Due" + SEPARATOR + "Submissions" + SEPARATOR
				+ "Students" + SEPARATOR + "Percent Responded" + SEPARATOR + "Report Email" + SEPARATOR + "Last Sent");

		for (Eval e : evals)
		{
			ResponseRate rr = this.cdpHandler.new ResponseRate(e);
			out.print(formatString(e.siteTitle) + SEPARATOR);
			out.print(formatString(e.instructorsPlain) + SEPARATOR);
			out.print(formatString(e.observersPlain) + SEPARATOR);
			out.print(formatString(e.assessmentTitle) + SEPARATOR);
			out.print(formatDate(e.open) + SEPARATOR);
			out.print(formatDate(e.due) + SEPARATOR);
			out.print(formatInteger(rr.submitted) + SEPARATOR);
			out.print(formatInteger(rr.total) + SEPARATOR);
			out.print(formatInteger(rr.pct()) + SEPARATOR);
			out.print(formatString(e.email) + SEPARATOR);
			out.println(formatDate(e.sent));
		}
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

	/**
	 * Remove the ":xx" seconds part of a MEDIUM date format display.
	 * 
	 * @param display
	 *        The MEDIUM formatted date.
	 * @return The MEDIUM formatted date with the seconds removed.
	 */
	protected String removeSeconds(String display)
	{
		int i = display.lastIndexOf(":");
		if ((i == -1) || ((i + 3) >= display.length())) return display;

		String rv = display.substring(0, i) + display.substring(i + 3);
		return rv;
	}

	/**
	 * @return The EntityManager, via the component manager.
	 */
	private EntityManager entityManager()
	{
		return (EntityManager) ComponentManager.get(EntityManager.class);
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
	 * @return The SessionManager, via the component manager.
	 */
	private SessionManager sessionManager()
	{
		return (SessionManager) ComponentManager.get(SessionManager.class);
	}

	/**
	 * @return The SiteService, via the component manager.
	 */
	private SiteService siteService()
	{
		return (SiteService) ComponentManager.get(SiteService.class);
	}
}
