/**
 * JPaaS
 * Copyright (C) 2012 Bull S.A.S.
 * Contact: jasmine@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * --------------------------------------------------------------------------
 * $Id$
 * --------------------------------------------------------------------------
 */
package org.ow2.jonas.jpaas.router.manager.api;


import java.util.List;

public interface RouterManager {

    /**
     * Create a router
     * @param routerName Name of the router to create
     * @param paasAgentName Name of the PaasAgent
     * @param paasConfigurationName Name of the PaasConfiguration to use
     * @param listenPort the listen port
     * @throws RouterManagerBeanException
     */
    public void createRouter(String routerName, String paasAgentName, String paasConfigurationName, Integer listenPort)
            throws RouterManagerBeanException;

    /**
     * Remove a router
     * @param routerName name of the router to remove
     * @throws RouterManagerBeanException
     */
    public void removeRouter(String routerName) throws RouterManagerBeanException ;

    /**
     * Start a router
     * @param routerName Name of the router to start
     * @throws RouterManagerBeanException
     */
    public void startRouter(String routerName) throws RouterManagerBeanException ;

    /**
     * Stop a router
     * @param routerName Name of the router to stop
     * @throws RouterManagerBeanException
     */
    public void stopRouter(String routerName) throws RouterManagerBeanException ;

    /**
     * Add a worker
     * @param routerName Name of the router
     * @param workerName Name of the worker to create
     * @param targetHost The worker target host
     * @param targetPortNumber the worker target port number
     * @throws RouterManagerBeanException
     */
    public void createWorker(String routerName, String workerName, String targetHost, Integer targetPortNumber)
            throws RouterManagerBeanException ;

    /**
     * Remove a worker
     * @param routerName Name of the router
     * @param workerName Name of the worker to remove
     * @throws RouterManagerBeanException
     */
    public void removeWorker(String routerName, String workerName) throws RouterManagerBeanException ;

    /**
     * Disable a worker
     * @param routerName Name of the router
     * @param workerName Name of the worker to disable
     * @throws RouterManagerBeanException
     */
    public void disableWorker(String routerName, String workerName) throws RouterManagerBeanException;

    /**
     * Enable a worker
     * @param routerName Name of the router
     * @param workerName Name of the worker to enable
     * @throws RouterManagerBeanException
     */
    public void enableWorker(String routerName, String workerName) throws RouterManagerBeanException;

    /**
     * Create a loadbalancer
     * @param routerName Name of the router
     * @param lbName  Name of the load balancer
     * @param workedList  the workers balanced by this load balancer
     * @param mountsPoints the mount Points of this load balancer
     * @throws RouterManagerBeanException
     */
    public void createLoadBalancer(String routerName, String lbName, List<String> workedList, List<String> mountsPoints)
            throws RouterManagerBeanException;

    /**
     * Remove a loadbalancer
     * @param routerName Name of the router
     * @param lbName  Name of the load balancer
     * @throws RouterManagerBeanException
     */
    public void removeLoadBalancer(String routerName, String lbName) throws RouterManagerBeanException;

    /**
     * add a worker to a loadbalancer
     * @param routerName Name of the router
     * @param lbName  Name of the load balancer
     * @throws RouterManagerBeanException
     */
    public void addWorkerToLoadBalancer(String routerName, String lbName, String workerName)
            throws RouterManagerBeanException;


}
