/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/evalmanager/trunk/evalmanager-webapp/webapp/src/java/org/etudes/evalmanager/cdp/EvalManagerScheduler.java $
 * $Id: EvalManagerScheduler.java 9227 2014-11-18 03:26:15Z ggolden $
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.util.StringUtil;

/**
 * EvalManagerScheduler handles scheduled adding and removing of observers.
 */
public class EvalManagerScheduler implements Runnable
{
	class Schedule
	{
		Date end;
		Long id;
		String observerId;
		String siteId;
		Date start;
	}

	/** Our logger. */
	private static Log M_log = LogFactory.getLog(EvalManagerScheduler.class);

	/** The scheduler thread. */
	protected Thread schedulerThread = null;

	/** How long to wait (ms) between checks (1 minute) */
	protected long SLEEP = 1000L * 60L;

	/** The thread quit flag. */
	protected boolean threadStop = false;

	/**
	 * Construct
	 */
	public EvalManagerScheduler()
	{
		start();
	}

	/**
	 * Run the scheduler thread, checking to see if it is time to process files.
	 */
	public void run()
	{
		// since we might be running while the component manager is still being created and populated,
		// such as at server startup, wait here for a complete component manager
		ComponentManager.waitTillConfigured();

		// loop till told to stop
		while ((!threadStop) && (!Thread.currentThread().isInterrupted()))
		{
			try
			{
				pushAdvisor();
				Date now = new Date();

				List<Schedule> schedules = readSchedules();
				for (Schedule s : schedules)
				{
					try
					{
						Site site = siteService().getSite(s.siteId);
						boolean needToSaveSite = false;

						Member existingRole = site.getMember(s.observerId);

						boolean effective = (((s.start == null) || s.start.before(now) || s.start.equals(now)) && ((s.end == null) || s.end
								.after(now)));

						// add the user if not in the site, or if a guest
						if (effective)
						{
							if ((existingRole == null) || (existingRole.getRole().getId().equals("Guest")))
							{
								site.addMember(s.observerId, "Observer", true, true);
								needToSaveSite = true;
							}
						}

						// remove the user, if in the site as an Observer
						else
						{
							if ((existingRole != null) && (existingRole.getRole().getId().equals("Observer")))
							{
								site.removeMember(s.observerId);
								needToSaveSite = true;
							}
						}

						if (needToSaveSite)
						{
							siteService().save(site);
						}
					}
					catch (IdUnusedException e)
					{
						M_log.warn("run: missing site: " + s.siteId);
						deactivate(s);
					}
					catch (PermissionException e)
					{
						M_log.warn("run: " + e);
						deactivate(s);
					}

					// if fully processed, deactivate the schedule
					boolean startProcessed = (s.start == null) || s.start.equals(now) || s.start.before(now);
					boolean endProcessed = (s.end == null) || s.end.equals(now) || s.end.before(now);
					if (startProcessed && endProcessed)
					{
						deactivate(s);
					}
				}
			}
			catch (Throwable e)
			{
				M_log.warn("run: will continue: ", e);
			}
			finally
			{
				// clear out any current current bindings
				threadLocalManager().clear();
				popAdvisor();
			}

			// take a small nap
			try
			{
				Thread.sleep(SLEEP);
			}
			catch (Exception ignore)
			{
			}
		}
	}

	protected void deactivate(final Schedule a)
	{
		sqlService().transact(new Runnable()
		{
			public void run()
			{
				deactivateTx(a);
			}
		}, "deactivate: " + a.id);
	}

	protected void deactivateTx(Schedule s)
	{
		String sql = "UPDATE EVALMANAGER_SCHEDULE SET ACTIVE = '0' WHERE ID = ?";

		Object[] fields = new Object[1];
		fields[0] = s.id;

		if (!sqlService().dbWrite(sql.toString(), fields))
		{
			throw new RuntimeException("deactivateTx: db write failed");
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

	protected List<Schedule> readSchedules()
	{
		final List<Schedule> rv = new ArrayList<Schedule>();

		String sql = "SELECT E.ID, E.SITE_ID, E.OBSERVATION_START, E.OBSERVATION_END, O.OBSERVER_ID" //
				+ " FROM EVALMANAGER_SCHEDULE E" //
				+ " JOIN EVALMANAGER_OBSERVER O ON E.SITE_ID = O.SITE_ID" //
				+ " WHERE E.ACTIVE = '1'";

		sqlService().dbRead(sql, null, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Schedule s = new Schedule();
					int i = 1;
					s.id = readLong(result, i++);
					s.siteId = readString(result, i++);
					s.start = sqlService().readDate(result, i++);
					s.end = sqlService().readDate(result, i++);
					s.observerId = readString(result, i++);

					rv.add(s);
					return null;
				}
				catch (SQLException e)
				{
					M_log.warn("readSchedules: " + e);
					return null;
				}
			}
		});

		return rv;
	}

	protected String readString(ResultSet result, int index) throws SQLException
	{
		return StringUtil.trimToNull(result.getString(index));
	}

	/**
	 * Start the scheduler thread.
	 */
	protected void start()
	{
		threadStop = false;

		schedulerThread = new Thread(this, getClass().getName() + ".automator");
		schedulerThread.setDaemon(true);
		schedulerThread.start();
	}

	/**
	 * Stop the scheduler thread.
	 */
	protected void stop()
	{
		if (schedulerThread == null) return;

		// signal the thread to stop
		threadStop = true;

		// wake up the thread
		schedulerThread.interrupt();

		schedulerThread = null;
	}

	/**
	 * @return The SecurityService, via the component manager.
	 */
	private SecurityService securityService()
	{
		return (SecurityService) ComponentManager.get(SecurityService.class);
	}

	/**
	 * @return The SiteService, via the component manager.
	 */
	private SiteService siteService()
	{
		return (SiteService) ComponentManager.get(SiteService.class);
	}

	/**
	 * @return The SqlService, via the component manager.
	 */
	private SqlService sqlService()
	{
		return (SqlService) ComponentManager.get(SqlService.class);
	}

	/**
	 * @return The ThreadLocalManager, via the component manager.
	 */
	private ThreadLocalManager threadLocalManager()
	{
		return (ThreadLocalManager) ComponentManager.get(ThreadLocalManager.class);
	}
}
