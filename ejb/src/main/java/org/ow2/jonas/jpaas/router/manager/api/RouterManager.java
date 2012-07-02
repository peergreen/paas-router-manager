/**
 * JPaaS Util
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
  public void createRouter(String routerName, String paasAgentName, String paasConfigurationName, Integer listenPort) throws RouterManagerBeanException;

  public void removeRouter(String routerName) throws RouterManagerBeanException ;

  public void startRouter(String routerName) throws RouterManagerBeanException ;

  public void stopRouter(String routerName) throws RouterManagerBeanException ;

  public void createWorker(String routerName, String workerName, String targetHost, Integer targetPortNumber) throws RouterManagerBeanException ;

  public void removeWorker(String routerName, String workerName) throws RouterManagerBeanException ;

  public void disableWorker(String routerName, String workerName) throws RouterManagerBeanException;

  public void enableWorker(String routerName, String workerName) throws RouterManagerBeanException;

  public void createLoadBalancer(String routerName, String IbName, List<String> workedList, List<String> mountsPoints) throws RouterManagerBeanException;

  public void removeLoadBalancer(String routerName, String IbName) throws RouterManagerBeanException ;
}
