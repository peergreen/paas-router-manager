/**
 * JPaaS
 * Copyright 2012 Bull S.A.S.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * $Id:$
 */
package org.ow2.jonas.jpaas.router.manager.bean;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
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

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;



@Component
@Provides
@Instantiate
public class RouterManagerBean implements RouterManager {

    /**
     * The logger
     */
    private Log logger = LogFactory.getLog(RouterManagerBean.class);

    /**
     * Expected paas type
     */
    private static final String PAAS_TYPE = "router";

    /**
     * Expected paas subtype
     */
    private static final String PAAS_SUB_TYPE = "jk";

     /**
     * Catalog facade
     */
    @Requires
    private IPaasCatalogFacade catalogEjb;

    /**
     * SR facade router
     */
    @Requires
    private ISrPaasApacheJkRouterFacade srApacheJkEjb;

    /**
     * SR facade agent
     */
    @Requires
    private ISrPaasAgentFacade srAgentEjb;

    /**
     * SR facade apache - agent link
     */
    @Requires
    private ISrPaasResourcePaasAgentLink srApacheAgentLinkEjb;

    /**
     * SR facade agent - iaasCompute link
     */
    @Requires
    private ISrPaasAgentIaasComputeLink srPaasAgentIaasComputeLink;

    /**
     * SR facade paasResource - iaasCompute link
     */
    @Requires
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

        //remove the worker in LoadBalancer workers list
        List<LoadBalancerVO> loadBalancerVOList = apacheJk.getLoadBalancerList();
        for (LoadBalancerVO loadBalancer : loadBalancerVOList) {
            List<String> workers = loadBalancer.getWorkers();
            for (ListIterator<String> iterator = workers.listIterator(); iterator.hasNext();) {
                String tmp = iterator.next();
                if (tmp.equals(workerName)) {
                    iterator.remove();
                    logger.debug("Worker " + workerName + " removed in LoadBalancer " + loadBalancer.getName() + ".");
                    break;
                }
            }
        }
        apacheJk = srApacheJkEjb.updateApacheJkRouter(apacheJk);

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

    }
}
