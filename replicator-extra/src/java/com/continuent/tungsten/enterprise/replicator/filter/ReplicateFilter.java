/**
 * Tungsten Scale-Out Stack
 * Copyright (C) 2010 Continuent Inc.
 * 
 * This code is property of Continuent, Inc.  All rights reserved. 
 */
package com.continuent.tungsten.enterprise.replicator.filter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import com.continuent.tungsten.replicator.ReplicatorException;
import com.continuent.tungsten.replicator.conf.ReplicatorConf;
import com.continuent.tungsten.replicator.dbms.DBMSData;
import com.continuent.tungsten.replicator.dbms.OneRowChange;
import com.continuent.tungsten.replicator.dbms.RowChangeData;
import com.continuent.tungsten.replicator.dbms.StatementData;
import com.continuent.tungsten.replicator.event.ReplDBMSEvent;
import com.continuent.tungsten.replicator.filter.Filter;
import com.continuent.tungsten.replicator.plugin.PluginContext;

/**
 * @author <a href="mailto:stephane.giron@continuent.com">Stephane Giron</a>
 * @version 1.0
 */
public class ReplicateFilter implements Filter
{

    private static Logger logger = Logger.getLogger(ReplicateFilter.class);

    private Pattern       doDbPattern;
    private Matcher       doDbMatcher;

    private Pattern       ignoreDbPattern;
    private Matcher       ignoreDbMatcher;

    private Pattern       doTablePattern;
    private Matcher       doTableMatcher;

    private Pattern       ignoreTablePattern;
    private Matcher       ignoreTableMatcher;

    private Matcher       ignoreWildTableMatcher;
    private Matcher       doWildTableMatcher;

    private String        doFilter;
    private String        ignoreFilter;
    private Pattern       doWildTablePattern;
    private Pattern       ignoreWildTablePattern;

    private String        tungstenSchema;

    public void setDoFilter(String doFilter)
    {
        this.doFilter = doFilter;
    }

    public void setIgnoreFilter(String ignoreFilter)
    {
        this.ignoreFilter = ignoreFilter;

    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.filter.Filter#filter(com.continuent.tungsten.replicator.event.ReplDBMSEvent)
     */
    public ReplDBMSEvent filter(ReplDBMSEvent event)
            throws ReplicatorException, InterruptedException
    {
        ArrayList<DBMSData> data = event.getData();
        
        if (data == null)
            return event;

        for (Iterator<DBMSData> iterator = data.iterator(); iterator.hasNext();)
        {
            DBMSData dataElem = iterator.next();
            if (dataElem instanceof RowChangeData)
            {
                RowChangeData rdata = (RowChangeData) dataElem;
                for (Iterator<OneRowChange> iterator2 = rdata.getRowChanges()
                        .iterator(); iterator2.hasNext();)
                {
                    OneRowChange orc = iterator2.next();

                    if (filterEvent(orc.getSchemaName(), orc.getTableName()))
                    {
                        iterator2.remove();
                    }
                }
                if (rdata.getRowChanges().isEmpty())
                {
                    iterator.remove();
                }
            }
            else if (dataElem instanceof StatementData)
            {
                // TODO this requires some parsing ?
            }
        }

        if (data.isEmpty())
        {
            return null;
        }
        return event;
    }

    private boolean filterEvent(String schema, String table)
    {
        // if schema not provided, cannot filter
        if (schema.length() == 0)
            return false;
        
        if(schema.equals(tungstenSchema))
            return false;

        if (doDbPattern != null)
        {
            if (doDbMatcher == null)
                doDbMatcher = doDbPattern.matcher(schema);
            else
                doDbMatcher.reset(schema);

            if (doDbMatcher.matches())
                return false;
        }

        if (ignoreDbPattern != null)
        {
            if (ignoreDbMatcher == null)
                ignoreDbMatcher = ignoreDbPattern.matcher(schema);
            else
                ignoreDbMatcher.reset(schema);

            if (ignoreDbMatcher.matches())
                return true;
        }

        // From this point, if table not provided, cannot filter
        if (table.length() == 0)
            return false;

        String searchedTable = schema + "." + table;

        if (doTablePattern != null)
        {
            if (doTableMatcher == null)
                doTableMatcher = doTablePattern.matcher(searchedTable);
            else
                doTableMatcher.reset(searchedTable);

            if (doTableMatcher.matches())
                return false;
        }

        if (ignoreTablePattern != null)
        {
            if (ignoreTableMatcher == null)
                ignoreTableMatcher = ignoreTablePattern.matcher(searchedTable);
            else
                ignoreTableMatcher.reset(searchedTable);

            if (ignoreTableMatcher.matches())
                return true;
        }

        if (doWildTablePattern != null)
        {
            if (doWildTableMatcher == null)
                doWildTableMatcher = doWildTablePattern.matcher(searchedTable);
            else
                doWildTableMatcher.reset(searchedTable);

            if (doWildTableMatcher.matches())
                return false;
        }

        if (ignoreWildTablePattern != null)
        {
            if (ignoreWildTableMatcher == null)
                ignoreWildTableMatcher = ignoreWildTablePattern
                        .matcher(searchedTable);
            else
                ignoreWildTableMatcher.reset(searchedTable);

            if (ignoreWildTableMatcher.matches())
                return true;
        }

        return (doTablePattern != null && doTablePattern.pattern().length() > 0)
                || (doWildTablePattern != null && doWildTablePattern.pattern()
                        .length() > 0);
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#configure(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void configure(PluginContext context) throws ReplicatorException,
            InterruptedException
    {
        tungstenSchema = context.getReplicatorProperties().getString(
                ReplicatorConf.METADATA_SCHEMA);
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#prepare(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void prepare(PluginContext context) throws ReplicatorException,
            InterruptedException
    {
        logger.warn("Preparing Replicate Filter");
        extractFilters(doFilter, false);
        extractFilters(ignoreFilter, true);

    }

    private void extractFilters(String doOrIgnorefilter, boolean ignore)
    {
        StringBuffer db = new StringBuffer("");
        StringBuffer table = new StringBuffer("");
        StringBuffer wildTable = new StringBuffer("");

        String[] filterArr = doOrIgnorefilter.split(",");

        for (int i = 0; i < filterArr.length; i++)
        {
            String filter = filterArr[i].trim();
            if (filter.length() == 0)
                continue;

            if (!filter.contains("."))
            {
                // This is a schema
                if (db.length() > 0)
                    db.append("|");
                db.append(filter);
            }
            else
            {
                filter = filter.replace(".", "\\.");
                if (filter.contains("%") || filter.contains("_"))
                {
                    if (wildTable.length() > 0)
                        wildTable.append("|");
                    wildTable.append(filter.replaceAll("%", ".*").replaceAll(
                            "_", "."));
                }
                else
                {
                    if (table.length() > 0)
                        table.append("|");
                    table.append(filter);
                }
            }
            if (ignore)
            {
                ignoreDbPattern = Pattern.compile(db.toString());
                ignoreTablePattern = Pattern.compile(table.toString());
                ignoreWildTablePattern = Pattern.compile(wildTable.toString());
            }
            else
            {
                doDbPattern = Pattern.compile(db.toString());
                doTablePattern = Pattern.compile(table.toString());
                doWildTablePattern = Pattern.compile(wildTable.toString());

            }
        }
    }

    /**
     * {@inheritDoc}
     * 
     * @see com.continuent.tungsten.replicator.plugin.ReplicatorPlugin#release(com.continuent.tungsten.replicator.plugin.PluginContext)
     */
    public void release(PluginContext context) throws ReplicatorException,
            InterruptedException
    {

    }

}