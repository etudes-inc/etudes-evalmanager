--*********************************************************************************
-- $URL: https://source.etudes.org/svn/apps/evalmanager/trunk/evalmanager-webapp/webapp/src/webapp/WEB-INF/classes/mysql/evalmanager.sql $
-- $Id: evalmanager.sql 9227 2014-11-18 03:26:15Z ggolden $
--**********************************************************************************
--
-- Copyright (c) 2014 Etudes, Inc.
-- 
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
-- 
--      http://www.apache.org/licenses/LICENSE-2.0
-- 
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
--*********************************************************************************/

-----------------------------------------------------------------------------
-- Evalmanager DDL
-----------------------------------------------------------------------------

CREATE TABLE EVALMANAGER_SCHEDULE
(
	ID                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    SITE_ID            VARCHAR (99) NOT NULL,
    OBSERVATION_START  BIGINT,
    OBSERVATION_END    BIGINT,
    ACTIVE             CHAR (1) NOT NULL CHECK (ACTIVE IN ('0','1')),
    UNIQUE KEY         IDX_ES_SITE_ID (SITE_ID),
    KEY                IDX_ES_ACTIVE (ACTIVE)
);

CREATE TABLE EVALMANAGER_OBSERVER
(
    SITE_ID            VARCHAR (99) NOT NULL,
    OBSERVER_ID        VARCHAR (99) NOT NULL,
    UNIQUE KEY         IDX_EO_SCHEDULE_ID (SITE_ID, OBSERVER_ID)
);
