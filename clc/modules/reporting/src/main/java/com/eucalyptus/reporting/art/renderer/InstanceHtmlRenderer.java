/*************************************************************************
 * Copyright 2009-2012 Eucalyptus Systems, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Please contact Eucalyptus Systems, Inc., 6755 Hollister Ave., Goleta
 * CA 93117, USA or visit http://www.eucalyptus.com/licenses/ if you need
 * additional information or have any questions.
 *
 * This file may incorporate work covered under the following copyright
 * and permission notice:
 *
 *   Software License Agreement (BSD License)
 *
 *   Copyright (c) 2008, Regents of the University of California
 *   All rights reserved.
 *
 *   Redistribution and use of this software in source and binary forms,
 *   with or without modification, are permitted provided that the
 *   following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *
 *   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *   FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *   COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *   INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *   BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *   LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *   CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *   LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *   ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *   POSSIBILITY OF SUCH DAMAGE. USERS OF THIS SOFTWARE ACKNOWLEDGE
 *   THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE LICENSED MATERIAL,
 *   COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS SOFTWARE,
 *   AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 *   IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA,
 *   SANTA BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY,
 *   WHICH IN THE REGENTS' DISCRETION MAY INCLUDE, WITHOUT LIMITATION,
 *   REPLACEMENT OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO
 *   IDENTIFIED, OR WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT
 *   NEEDED TO COMPLY WITH ANY SUCH LICENSES OR RIGHTS.
 ************************************************************************/
package com.eucalyptus.reporting.art.renderer;

import java.io.*;
import java.util.Date;
import java.util.Map;

import com.eucalyptus.reporting.art.entity.*;
import com.eucalyptus.reporting.units.SizeUnit;
import com.eucalyptus.reporting.units.TimeUnit;
import com.eucalyptus.reporting.units.UnitUtil;
import com.eucalyptus.reporting.units.Units;

public class InstanceHtmlRenderer
	implements HtmlRenderer
{
	private static final int LABEL_WIDTH = 50;
	private static final int VALUE_WIDTH = 80;
	
	public InstanceHtmlRenderer()
	{
		
	}
	
	public void render(ReportArtEntity report, OutputStream os, Units units)
		throws IOException
	{
        Writer writer = new OutputStreamWriter(os);
        writer.write("<html><body>\n");
        writer.write("<h1>Instance Report</h1>\n");
        writer.write("<h4>Begin:" + ms2Date(report.getBeginMs()) + "</h4>\n");
        writer.write("<h4>End:" + ms2Date(report.getEndMs()) + "</h4>\n");

        writer.write("<h3>Resource Usage Section</h3>\n");
        writer.write("<table border=0>\n");
        writer.write((new Row(VALUE_WIDTH)).addEmptyCols(6, LABEL_WIDTH).addEmptyCols(5, VALUE_WIDTH)
        		.addCol("Net In " + units.getSizeUnit(), VALUE_WIDTH, 3, "center")
        		.addCol("Net Out " + units.getSizeUnit(), VALUE_WIDTH, 3, "center").toString());
        writer.write((new Row(VALUE_WIDTH)).addEmptyCols(6, LABEL_WIDTH).addCol("InstanceId")
        		.addCol("Type").addCol("#").addCol("Time").addCol("CpuUsage%")
        		.addCol("Between").addCol("Within").addCol("Public").addCol("Between").addCol("Within")
        		.addCol("Public").toString());
        for(String zoneName : report.getZones().keySet()) {
        	AvailabilityZoneArtEntity zone = report.getZones().get(zoneName);
            writer.write((new InsRow()).addCol("Zone: " + zoneName, LABEL_WIDTH, 3, "left").addEmptyCols(3, LABEL_WIDTH)
            		.addCol("cumul.")
            		.addCol("cumul.")
            		.addUsageCols(zone.getUsageTotals().getInstanceTotals(), units).toString());
            for (String clusterName: zone.getClusters().keySet()) {
            	ClusterArtEntity cluster = zone.getClusters().get(clusterName);
                writer.write((new InsRow()).addEmptyCols(1,LABEL_WIDTH)
                		.addCol("Cluster: " + clusterName, LABEL_WIDTH, 3, "left").addEmptyCols(2, LABEL_WIDTH)
                		.addCol("cumul.")
                		.addCol("cumul.")
                		.addUsageCols(cluster.getUsageTotals().getInstanceTotals(),units).toString());
                for (String accountName: cluster.getAccounts().keySet()) {
                	AccountArtEntity account = cluster.getAccounts().get(accountName);
                    writer.write((new InsRow()).addEmptyCols(2,LABEL_WIDTH)
                    		.addCol("Account: " + accountName, LABEL_WIDTH, 3, "left").addEmptyCols(1, LABEL_WIDTH)
                    		.addCol("cumul.")
                    		.addCol("cumul.")
                    		.addUsageCols(account.getUsageTotals().getInstanceTotals(),units).toString());
                    for (String userName: account.getUsers().keySet()) {
                    	UserArtEntity user = account.getUsers().get(userName);
                        writer.write((new InsRow()).addEmptyCols(3,LABEL_WIDTH)
                        		.addCol("User: " + userName, LABEL_WIDTH, 3, "left")
                        		.addCol("cumul.")
                        		.addCol("cumul.")
                        		.addUsageCols(user.getUsageTotals().getInstanceTotals(),units).toString());
                        for (String instanceUuid: user.getInstances().keySet()) {
                        	InstanceArtEntity instance = user.getInstances().get(instanceUuid);
                        	writer.write((new InsRow()).addEmptyCols(6,LABEL_WIDTH)
                        			.addCol(instance.getInstanceId())
                        			.addCol(instance.getInstanceType())
                        			.addUsageCols(instance.getUsage(), units).toString());
                        }
                    }
                }
            }
        }
        writer.write("</table>\n");


        writer.write("<h3>Instance Running Times Section</h3>\n");
        writer.write("<table border=0>\n");
        writer.write((new Row(VALUE_WIDTH)).addEmptyCols(6, LABEL_WIDTH)
        		.addCol("m1.Small", VALUE_WIDTH, 2, "center")
        		.addCol("c1.Medium", VALUE_WIDTH, 2, "center")
        		.addCol("m1.Large", VALUE_WIDTH, 2, "center")
        		.addCol("c1.Large", VALUE_WIDTH, 2, "center")
        		.addCol("m1.XLarge", VALUE_WIDTH, 2, "center").toString());
        writer.write(new Row(VALUE_WIDTH).addEmptyCols(6, LABEL_WIDTH)
        		.addCol("num", VALUE_WIDTH, 1, "center").addCol("time", VALUE_WIDTH, 1, "center")
        		.addCol("num", VALUE_WIDTH, 1, "center").addCol("time", VALUE_WIDTH, 1, "center")
        		.addCol("num", VALUE_WIDTH, 1, "center").addCol("time", VALUE_WIDTH, 1, "center")
        		.addCol("num", VALUE_WIDTH, 1, "center").addCol("time", VALUE_WIDTH, 1, "center")
        		.addCol("num", VALUE_WIDTH, 1, "center").addCol("time", VALUE_WIDTH, 1, "center").toString());
        for(String zoneName : report.getZones().keySet()) {
        	AvailabilityZoneArtEntity zone = report.getZones().get(zoneName);
            writer.write((new InsRow()).addCol("Zone: " + zoneName, LABEL_WIDTH, 3, "left").addEmptyCols(3, LABEL_WIDTH)
            		.addTimeCols(zone.getUsageTotals(), units)
            		.toString());
            for (String clusterName: zone.getClusters().keySet()) {
            	ClusterArtEntity cluster = zone.getClusters().get(clusterName);
                writer.write((new InsRow()).addEmptyCols(1,LABEL_WIDTH)
                		.addCol("Cluster: " + clusterName, LABEL_WIDTH, 3, "left").addEmptyCols(2, LABEL_WIDTH)
                		.addTimeCols(cluster.getUsageTotals(),units)
                		.toString());
                for (String accountName: cluster.getAccounts().keySet()) {
                	AccountArtEntity account = cluster.getAccounts().get(accountName);
                    writer.write((new InsRow()).addEmptyCols(2,LABEL_WIDTH)
                    		.addCol("Account: " + accountName, LABEL_WIDTH, 3, "left").addEmptyCols(1, LABEL_WIDTH)
                    		.addTimeCols(account.getUsageTotals(),units)
                    		.toString());
                    for (String userName: account.getUsers().keySet()) {
                    	UserArtEntity user = account.getUsers().get(userName);
                        writer.write((new InsRow()).addEmptyCols(3,LABEL_WIDTH)
                        		.addCol("User: " + userName, LABEL_WIDTH, 3, "left")
                        		.addTimeCols(user.getUsageTotals(),units)
                        		.toString());
                    }
                }
            }
        }
        writer.write("</table>\n");

        writer.write("</body></html>\n");
        writer.flush();		
	}
	
	private static class InsRow
		extends Row
	{
		public InsRow()
		{
			super(VALUE_WIDTH);
		}
		
	    public InsRow addCol(String val)
	    {
	        return addCol(val, VALUE_WIDTH, 1, "center");
	    }

	    public InsRow addCol(Long val)
	    {
            return addCol((val==null)?null:val.toString(), VALUE_WIDTH, 1, "center");
	    }

	    public InsRow addCol(Double val)
	    {
            return addCol((val==null)?null:String.format("%3.1f", val), VALUE_WIDTH, 1, "center");
	    }

	    public InsRow addCol(String val, int width, int colspan, String align)
	    {
	    	super.addCol(val, width, colspan, align);
	        return this;
	    }

	    public InsRow addEmptyCols(int num, int width)
	    {
	    	super.addEmptyCols(num, width);
	        return this;
	    }

		public Row addUsageCols(InstanceUsageArtEntity entity, Units units)
		{
			addCol((long)entity.getInstanceCnt());
			addCol(UnitUtil.convertTime(entity.getDurationMs(), TimeUnit.MS, units.getTimeUnit()));
			addCol(entity.getCpuPercentAvg());
			addCol(UnitUtil.convertSize(entity.getNetIoBetweenZoneInMegs(), SizeUnit.MB, units.getSizeUnit()));
			addCol(UnitUtil.convertSize(entity.getNetIoWithinZoneInMegs(), SizeUnit.MB, units.getSizeUnit()));
			addCol(UnitUtil.convertSize(entity.getNetIoPublicIpInMegs(), SizeUnit.MB, units.getSizeUnit()));
			addCol(UnitUtil.convertSize(entity.getNetIoBetweenZoneOutMegs(), SizeUnit.MB, units.getSizeUnit()));
			addCol(UnitUtil.convertSize(entity.getNetIoWithinZoneOutMegs(), SizeUnit.MB, units.getSizeUnit()));
			addCol(UnitUtil.convertSize(entity.getNetIoPublicIpOutMegs(), SizeUnit.MB, units.getSizeUnit()));
			return this;
		}
		
		public Row addTimeCols(UsageTotalsArtEntity totals, Units units)
		{
			Map<String,InstanceUsageArtEntity> typeTotals = totals.getTypeTotals();
			addCol((long)typeTotals.get("m1.small").getInstanceCnt());
			addCol(UnitUtil.convertTime(typeTotals.get("m1.small").getDurationMs(), TimeUnit.MS, units.getTimeUnit()));
			addCol((long)typeTotals.get("c1.medium").getInstanceCnt());
			addCol(UnitUtil.convertTime(typeTotals.get("c1.medium").getDurationMs(), TimeUnit.MS, units.getTimeUnit()));
			addCol((long)typeTotals.get("m1.large").getInstanceCnt());
			addCol(UnitUtil.convertTime(typeTotals.get("m1.large").getDurationMs(), TimeUnit.MS, units.getTimeUnit()));
			addCol((long)typeTotals.get("c1.large").getInstanceCnt());
			addCol(UnitUtil.convertTime(typeTotals.get("c1.large").getDurationMs(), TimeUnit.MS, units.getTimeUnit()));
			addCol((long)typeTotals.get("m1.xlarge").getInstanceCnt());
			addCol(UnitUtil.convertTime(typeTotals.get("m1.xlarge").getDurationMs(), TimeUnit.MS, units.getTimeUnit()));
			return this;
		}
	}
	
	private static String ms2Date(long ms)
	{
		return new Date(ms).toString();
	}
	
}
