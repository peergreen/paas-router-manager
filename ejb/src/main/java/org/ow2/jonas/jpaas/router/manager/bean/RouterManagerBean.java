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
package org.ow2.jonas.jpaas.router.manager.bean;

import org.ow2.jonas.jpaas.router.manager.api.RouterManager;
import org.ow2.jonas.jpaas.router.manager.api.RouterManagerBeanException;

import javax.ejb.Local;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import java.util.List;

@Stateless(mappedName="RouterManagerBean")
@Local(RouterManager.class)
@Remote(RouterManager.class)
public class RouterManagerBean {

  public RouterManagerBean() {
  }

  public void createRouter(String routerName, String paasAgentName, String paasConfigurationName, Integer listenPort) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / createRouter called");
  }

  public void removeRouter(String routerName) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / removeRouter called");
  }

  public void startRouter(String routerName) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / removeRouter called");
  }

  public void stopRouter(String routerName) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / routerName called");
  }

  public void createWorker(String routerName, String workerName, String targetHost, Integer targetPortNumber) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / createWorker called");
  }

  public void removeWorker(String routerName, String workerName) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / removeWorker called");
  }

  public void disableWorker(String routerName, String workerName) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / disableWorker called");
  }

  public void enableWorker(String routerName, String workerName) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / enableWorker called");
  }

  public void createLoadBalancer(String routerName, String IbName, List<String> workedList, List<String> mountsPoints) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / createLoadBalancer called");
  }

  public void removeLoadBalancer(String routerName, String IbName) throws RouterManagerBeanException {
     //TODO
    System.out.println("JPAAS-ROUTER-MANAGER / removeLoadBalancer called");
  }
}
