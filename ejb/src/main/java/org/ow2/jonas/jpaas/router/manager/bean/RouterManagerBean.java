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
package org.ow2.jonas.jpaas.router.manager.bean;

import org.ow2.easybeans.osgi.annotation.OSGiResource;
import org.ow2.jonas.jpaas.catalog.api.IPaasCatalogFacade;
import org.ow2.jonas.jpaas.catalog.api.PaasCatalogException;
import org.ow2.jonas.jpaas.catalog.api.PaasConfiguration;
import org.ow2.jonas.jpaas.router.manager.api.RouterManager;
import org.ow2.jonas.jpaas.router.manager.api.RouterManagerBeanException;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasAgentFacade;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasAgentIaasComputeLink;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasApacheJkRouterFacade;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasResourceIaasComputeLink;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasResourcePaasAgentLink;
import org.ow2.jonas.jpaas.sr.facade.vo.ApacheJkVO;
import org.ow2.jonas.jpaas.sr.facade.vo.IaasComputeVO;
import org.ow2.jonas.jpaas.sr.facade.vo.LoadBalancerVO;
import org.ow2.jonas.jpaas.sr.facade.vo.PaasAgentVO;
import org.ow2.jonas.jpaas.sr.facade.vo.PaasResourceVO;
import org.ow2.jonas.jpaas.sr.facade.vo.WorkerVO;
import org.ow2.util.log.Log;
import org.ow2.util.log.LogFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import javax.ejb.Local;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.ws.rs.core.MultivaluedMap;

import java.util.LinkedList;
import java.util.List;

@Stateless(mappedName = "RouterManagerBean")
@Local(RouterManager.class)
@Remote(RouterManager.class)
public class RouterManagerBean implements RouterManager {

    /**
     * The logger
     */
    private Log logger = LogFactory.getLog(RouterManagerBean.class);

    /**
     * The context of the application
     */
    private static String CONTEXT = "jonas-api";

    /**
     * Http accepted status
     */
    private static final int HTTP_STATUS_ACCEPTED = 202;

    /**
     * Http Ok status
     */
    private static final int HTTP_STATUS_OK = 200;

    /**
     * Http no content status
     */
    private static final int HTTP_STATUS_NO_CONTENT = 204;

    /**
     * Http Created status
     */
    private static final int HTTP_STATUS_CREATED = 201;

    /**
     * Expected paas type
     */
    private static final String PAAS_TYPE = "router";

    /**
     * Expected paas subtype
     */
    private static final String PAAS_SUB_TYPE = "jk";

    /**
     * Sleeping period for async operation
     */
    private static final int SLEEPING_PERIOD = 1000;

    /**
     * REST request type
     */
    private enum REST_TYPE {
        PUT, POST, GET, DELETE
    }

    /**
     * Catalog facade
     */
    @OSGiResource
    private IPaasCatalogFacade catalogEjb;

    /**
     * SR facade router
     */
    @OSGiResource
    private ISrPaasApacheJkRouterFacade srApacheJkEjb;

    /**
     * SR facade agent
     */
    @OSGiResource
    private ISrPaasAgentFacade srAgentEjb;

    /**
     * SR facade apache - agent link
     */
    @OSGiResource
    private ISrPaasResourcePaasAgentLink srApacheAgentLinkEjb;

    /**
     * SR facade agent - iaasCompute link
     */
    @OSGiResource
    private ISrPaasAgentIaasComputeLink srPaasAgentIaasComputeLink;

    /**
     * SR facade paasResource - iaasCompute link
     */
    @OSGiResource
    private ISrPaasResourceIaasComputeLink srPaasResourceIaasComputeLink;

    /**
     * Constructor
     */
    public RouterManagerBean() {
    }

    /**
     * Create a router
     * @param routerName Name of the router to create
     * @param paasAgentName Name of the PaasAgent
     * @param paasConfigurationName Name of the PaasConfiguration to use
     * @param listenPort the listen port
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createRouter(String routerName, String paasAgentName,
            String paasConfigurationName, Integer listenPort)
            throws RouterManagerBeanException {

        logger.info("Router '" + routerName + "' creating ....");

        // Get the agent
        PaasAgentVO agent = null;
        List<PaasAgentVO> paasAgentVOList = srAgentEjb.findAgents();
        for (PaasAgentVO tmp : paasAgentVOList) {
            if (tmp.getName().equals(paasAgentName)) {
                agent = tmp;
                break;
            }
        }
        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent '" + paasAgentName + "' !");
        }

        // Get configuration from catalog
        PaasConfiguration containerConf = null;
        try {
            containerConf = catalogEjb
                    .getPaasConfiguration(paasConfigurationName);
        } catch (PaasCatalogException e) {
            throw new RouterManagerBeanException("Error to find the PaaS Configuration named " +
                    paasConfigurationName + ".", e);
        }
        if (!containerConf.getType().equals(PAAS_TYPE)) {
            throw new RouterManagerBeanException("Invalid paas type : "
                    + containerConf.getType().equals(PAAS_TYPE) + " - expected : "
                    + PAAS_TYPE);
        }
        if (!containerConf.getSubType().equals(PAAS_SUB_TYPE)) {
            throw new RouterManagerBeanException("Invalid paas sub type : "
                    + containerConf.getType().equals(PAAS_SUB_TYPE) + " - expected : "
                    + PAAS_SUB_TYPE);
        }

        // Create the router in the SR
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                throw new RouterManagerBeanException("Router '" + routerName + "' already exist!");
            }
        }

        ApacheJkVO apacheJk = new ApacheJkVO();
        apacheJk.setName(routerName);
        apacheJk.setState("Init");
        apacheJk = srApacheJkEjb.createApacheJkRouter(apacheJk);

        // if the link doesn't exist between agent and router, create it
        boolean alreadyExist = false;
        List <PaasResourceVO> paasResources = srApacheAgentLinkEjb.findPaasResourcesByAgent(agent.getId());
        for (PaasResourceVO paasResourceVO : paasResources) {
            if (paasResourceVO instanceof ApacheJkVO) {
                ApacheJkVO apacheJkResourceVO = (ApacheJkVO) paasResourceVO;
                if (apacheJkResourceVO.getId().equals(apacheJk.getId())) {
                    logger.debug("Link between router '"  + routerName + "' and agent '" + paasAgentName +
                            "' already exist!");
                    alreadyExist = true;
                    break;
                }
            }
        }
        if (!alreadyExist) {
            srApacheAgentLinkEjb.addPaasResourceAgentLink(apacheJk.getId(), agent.getId());
        }

        //create the link between the PaaS Router and the IaaS Compute
        IaasComputeVO iaasCompute = srPaasAgentIaasComputeLink.findIaasComputeByPaasAgent(agent.getId());
        if (iaasCompute != null) {
            srPaasResourceIaasComputeLink.addPaasResourceIaasComputeLink(apacheJk.getId(), iaasCompute.getId());
        }

        // TODO use port range to customize apache conf (vhost)

        // TODO Create the virtual host for this router

        // update state in sr
        apacheJk.setState("CREATED");
        srApacheJkEjb.updateApacheJkRouter(apacheJk);

        logger.info("Router '" + routerName + "' created. Status=" + apacheJk.getState());

    }

    /**
     * Remove a router
     * @param routerName name of the router to remove
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void removeRouter(String routerName)
            throws RouterManagerBeanException {

        logger.info("Router '" + routerName + "' deleting ....");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        apacheJk.setState("DELETING");
        srApacheJkEjb.updateApacheJkRouter(apacheJk);

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        //TODO remove the vhost

        //remove apache - iaasCompute link
        IaasComputeVO iaasCompute = srPaasResourceIaasComputeLink.findIaasComputeByPaasResource(apacheJk.getId());
        if (iaasCompute != null) {
            srPaasResourceIaasComputeLink.removePaasResourceIaasComputeLink(apacheJk.getId(),
                    iaasCompute.getId());
        }

        // remove router in sr
        srApacheJkEjb.deleteApacheJkRouter(apacheJk.getId());

        logger.info("Router '" + routerName + "' deleted.");
    }

    /**
     * Start a router
     * @param routerName Name of the router to start
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void startRouter(String routerName)
            throws RouterManagerBeanException {

        logger.info("Router '" + routerName + "' starting ....");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        apacheJk.setState("STARTING");
        srApacheJkEjb.updateApacheJkRouter(apacheJk);

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        //HTTPD should be already started.
        // Ask for a reload
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "apache-manager/server/action/reload"),
                null,
                null);

        // update state in sr
        apacheJk.setState("RUNNING");
        srApacheJkEjb.updateApacheJkRouter(apacheJk);

        logger.info("Router '" + routerName + "' started.");
    }

    /**
     * Stop a router
     * @param routerName Name of the router to stop
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void stopRouter(String routerName) throws RouterManagerBeanException {

        logger.info("Router '" + routerName + "' stopping ....");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        apacheJk.setState("STOPPING");
        srApacheJkEjb.updateApacheJkRouter(apacheJk);

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Stop httpd
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "apache-manager/server/action/stop"),
                null,
                null);

        // update state in sr
        apacheJk.setState("STOPPED");
        srApacheJkEjb.updateApacheJkRouter(apacheJk);

        logger.info("Router '" + routerName + "' stopped.");
    }

    /**
     * Add a worker
     * @param routerName Name of the router
     * @param workerName Name of the worker to create
     * @param targetHost The worker target host
     * @param targetPortNumber the worker target port number
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createWorker(String routerName, String workerName,
            String targetHost, Integer targetPortNumber)
            throws RouterManagerBeanException {

        logger.info("Router '" + routerName + "' - Create Worker '" +  workerName + "' (host=" + targetHost +
                ", port=" + targetPortNumber + ")");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        //Do nothing if there is already a worker with the same name
        boolean workerExists = false;
        List<WorkerVO> workerVOList = apacheJk.getWorkerList();
        for (WorkerVO worker : workerVOList) {
            if (worker.getName().equals(workerName)) {
                workerExists=true;
                break;
            }
        }

        if (!workerExists) {
            // Get the agent
            PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

            if (agent == null) {
                throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
            }

            // Add a worker
            MultivaluedMap<String, String> params = new MultivaluedMapImpl();
            params.add("name", workerName);
            params.add("host", targetHost);
            params.add("port", targetPortNumber.toString());

            sendRequestWithReply(
                    REST_TYPE.POST,
                    getUrl(agent.getApiUrl(), "jkmanager/worker/" + workerName),
                    params,
                    null);

            // Ask for a reload
            sendRequestWithReply(
                    REST_TYPE.POST,
                    getUrl(agent.getApiUrl(), "apache-manager/server/action/reload"),
                    null,
                    null);

            // create the worker in sr
            srApacheJkEjb.addWorker(apacheJk.getId(), workerName, targetHost, targetPortNumber);

            logger.info("Router '" + routerName + "' - Worker '" +  workerName + "' created !");
        }
    }

    /**
     * Remove a worker
     * @param routerName Name of the router
     * @param workerName Name of the worker to remove
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void removeWorker(String routerName, String workerName)
            throws RouterManagerBeanException {
        logger.info("Router '" + routerName + "' - Delete Worker '" +  workerName + "'");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Remove a worker
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", workerName);

        sendRequestWithReply(
                REST_TYPE.DELETE,
                getUrl(agent.getApiUrl(), "jkmanager/worker/" + workerName),
                params,
                null);

        // Ask for a reload
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "apache-manager/server/action/reload"),
                null,
                null);

        // remove the worker in sr
        srApacheJkEjb.removeWorker(apacheJk.getId(), workerName);

        logger.info("Router '" + routerName + "' - Worker '" +  workerName + "' removed !");
    }

    /**
     * Disable a worker
     * @param routerName Name of the router
     * @param workerName Name of the worker to disable
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void disableWorker(String routerName, String workerName)
            throws RouterManagerBeanException {
        logger.info("Router '" + routerName + "' - Disable Worker '" +  workerName + "'");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        //Send request to the Agent to disable worker
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "jkmanager/worker/" + workerName + "/disable"),
                null,
                null);

        // Ask for a reload
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "apache-manager/server/action/reload"),
                null,
                null);

        // disable the worker in sr
        List<WorkerVO> workerVOs = apacheJk.getWorkerList();

        for (WorkerVO wVO : workerVOs) {
            if (wVO.getName().equals(workerName)) {
                wVO.setStatus("DISABLE");
                break;
            }
        }
        srApacheJkEjb.updateApacheJkRouter(apacheJk);


        logger.info("Router '" + routerName + "' - Worker '" +  workerName + "' disabled !");
    }

    /**
     * Enable a worker
     * @param routerName Name of the router
     * @param workerName Name of the worker to enable
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void enableWorker(String routerName, String workerName)
            throws RouterManagerBeanException {
        logger.info("Router '" + routerName + "' - Enable Worker '" +  workerName + "'");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        //Send request to the Agent to enable worker
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "jkmanager/worker/" + workerName + "/enable"),
                null,
                null);

        // Ask for a reload
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "apache-manager/server/action/reload"),
                null,
                null);

        //enable the worker in sr
        List<WorkerVO> workerVOs = apacheJk.getWorkerList();

        for (WorkerVO wVO : workerVOs) {
            if (wVO.getName().equals(workerName)) {
                wVO.setStatus("ENABLE");
                break;
            }
        }
        srApacheJkEjb.updateApacheJkRouter(apacheJk);


        logger.info("Router '" + routerName + "' - Worker '" +  workerName + "' enabled !");
    }

    /**
     * Create a loadbalancer
     * @param routerName Name of the router
     * @param lbName  Name of the load balancer
     * @param workedList  the workers balanced by this load balancer
     * @param mountsPoints the mount Points of this load balancer
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void createLoadBalancer(String routerName, String lbName,
            List<String> workedList, List<String> mountsPoints)
            throws RouterManagerBeanException {

        logger.info("Router '" + routerName + "' - Create Loadbalancer '" +  lbName + "' (wk=" + workedList +
                ", mt=" + mountsPoints + ")");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Create a String from the WorkedList
        String wl = "";
        boolean first = true;
        for(String s : workedList) {
            if (first) {
                first=false;
            } else {
                wl += ",";
            }
            wl += s;
        }

        //Send request to the Agent to create the loadBalancer
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", lbName);
        params.add("wl", wl);

        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "jkmanager/loadbalancer/" + lbName),
                params,
                null);


        //Send requests to the Agent to create the loadBalancer Mount Points
        for (String path : mountsPoints) {
            params.clear();
            params.add("path", path);

            sendRequestWithReply(
                    REST_TYPE.POST,
                    getUrl(agent.getApiUrl(), "jkmanager/mount/" + lbName),
                    params,
                    null);
        }

        // Ask for a reload
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "apache-manager/server/action/reload"),
                null,
                null);

        // create the LoadBalancer in sr
        srApacheJkEjb.addLoadBalancer(apacheJk.getId(), lbName, mountsPoints, workedList);

        logger.info("Router '" + routerName + "' - Loadbalancer '" +  lbName + "' created !");
    }

    /**
     * Remove a loadbalancer
     * @param routerName Name of the router
     * @param lbName  Name of the load balancer
     * @throws RouterManagerBeanException
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void removeLoadBalancer(String routerName, String lbName)
            throws RouterManagerBeanException {
        logger.info("Router '" + routerName + "' - Delete Loadbalancer '" +  lbName + "'");

        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Get the Load Balancer
        List<LoadBalancerVO> loadBalancerVOList = apacheJk.getLoadBalancerList();
        LoadBalancerVO loadBalancer = null;
        for (LoadBalancerVO lbVO : loadBalancerVOList) {
            if (lbName.equals(lbVO.getName())) {
                loadBalancer = lbVO;
                break;
            }
        }
        if (loadBalancer == null) {
            throw new RouterManagerBeanException("Unable to get the Load Balancer '" + lbName + "' for router '" +
                    routerName + "' !");
        }

        // Remove a loadbalancer

        //Send request to the Agent to remove the Load Balancer        
        sendRequestWithReply(
                REST_TYPE.DELETE,
                getUrl(agent.getApiUrl(), "jkmanager/loadbalancer/" + lbName),
                null,
                null);

        // Ask for a reload
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "apache-manager/server/action/reload"),
                null,
                null);

        //Send requests to the Agent to remove the loadBalancer Mount Points
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        for (String path : loadBalancer.getMountPoints()) {
            params.clear();
            params.add("path", path);

            sendRequestWithReply(
                    REST_TYPE.DELETE,
                    getUrl(agent.getApiUrl(), "jkmanager/mount/" + lbName),
                    params,
                    null);
        }

        // remove the loadbalancer in sr
        srApacheJkEjb.removeLoadBalancer(apacheJk.getId(), lbName);

        logger.info("Router '" + routerName + "' - Loadbalancer '" +  lbName + "' removed !");
    }

    /**
     * add a worker to a loadbalancer
     *
     * @param routerName Name of the router
     * @param lbName     Name of the load balancer
     * @throws org.ow2.jonas.jpaas.router.manager.api.RouterManagerBeanException
     *
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void addWorkerToLoadBalancer(String routerName, String lbName, String workerName) throws RouterManagerBeanException {
        // get the router from SR
        ApacheJkVO apacheJk = null;
        List<ApacheJkVO> apacheJkVOList = srApacheJkEjb.findApacheJkRouters();
        for (ApacheJkVO tmp : apacheJkVOList) {
            if (tmp.getName().equals(routerName)) {
                apacheJk = tmp;
                break;
            }
        }
        if (apacheJk == null) {
            throw new RouterManagerBeanException("Router '" + routerName + "' doesn't exist !");
        }

        // Get the agent
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(apacheJk.getId());

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Get the Load Balancer
        List<LoadBalancerVO> loadBalancerVOList = apacheJk.getLoadBalancerList();
        LoadBalancerVO loadBalancer = null;
        for (LoadBalancerVO lbVO : loadBalancerVOList) {
            if (lbName.equals(lbVO.getName())) {
                loadBalancer = lbVO;
                break;
            }
        }
        if (loadBalancer == null) {
            throw new RouterManagerBeanException("Unable to get the Load Balancer '" + lbName + "' for router '" +
                    routerName + "' !");
        }

        List<String> workerList = loadBalancer.getWorkers();
        if (workerList == null) {
            workerList = new LinkedList<String>();
        }
        workerList.add(workerName);

        // Create a String from the WorkerList
        String wl = "";
        boolean first = true;
        for(String s : workerList) {
            if (first) {
                first=false;
            } else {
                wl += ",";
            }
            wl += s;
        }

        //Send request to the Agent to create the loadBalancer
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", lbName);
        params.add("wl", wl);

        sendRequestWithReply(
                REST_TYPE.PUT,
                getUrl(agent.getApiUrl(), "jkmanager/loadbalancer/" + lbName),
                params,
                null);

        // Ask for a reload
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "apache-manager/server/action/reload"),
                null,
                null);
    }

    /**
     * @param agentApi the api url
     * @param path the path to add
     * @return the HTTP URL
     */
    private String getUrl(final String agentApi, final String path) {
        return agentApi + "/" + path;
    }

    /**
     * Send a REST request and get response
     *
     * @param type
     *            Http type of the request
     * @param url
     *            request path
     * @param params
     *            XML content of the request
     * @param responseClass
     *            response class
     * @return ResponseClass response class
     */
    private <ResponseClass> ResponseClass sendRequestWithReply(REST_TYPE type,
            String url, MultivaluedMap<String, String> params,
            java.lang.Class<ResponseClass> responseClass)
            throws RouterManagerBeanException {

        Client client = Client.create();

        WebResource webResource = client.resource(removeRedundantForwardSlash(url));

        if (params != null) {
            webResource = webResource.queryParams(params);
        }

        ClientResponse clientResponse;
        switch (type) {
            case PUT:
                clientResponse = webResource.put(ClientResponse.class);
                break;
            case GET:
                clientResponse = webResource.get(ClientResponse.class);
                break;
            case POST:
                clientResponse = webResource.post(ClientResponse.class);
                break;
            case DELETE:
                clientResponse = webResource.delete(ClientResponse.class);
                break;
            default:// put
                clientResponse = webResource.put(ClientResponse.class);
                break;
        }

        int status = clientResponse.getStatus();

        if (status != HTTP_STATUS_ACCEPTED && status != HTTP_STATUS_OK
                && status != HTTP_STATUS_NO_CONTENT && status != HTTP_STATUS_CREATED) {
            throw new RouterManagerBeanException(
                    "Error on JOnAS agent request : " + status);
        }

        ResponseClass r = null;

        if (status != HTTP_STATUS_NO_CONTENT) {
            //ToDo Apache-Manager REST interfaces need to be harmonized
/*            if (clientResponse.getType() != MediaType.APPLICATION_XML_TYPE) {
                throw new RouterManagerBeanException(
                        "Error on JOnAS agent response, unexpected type : "
                                + clientResponse.getType());
            }*/

            if (responseClass != null)
                r = clientResponse.getEntity(responseClass);
        }

        client.destroy();

        return r;

    }

    /**
     * Remove redundant forward slash in a String url
     * @param s a String url
     * @return The String url without redundant forward slash
     */
    private String removeRedundantForwardSlash(String s) {
        String tmp = s.replaceAll("/+", "/");
        return tmp.replaceAll(":/", "://");
    }
}
