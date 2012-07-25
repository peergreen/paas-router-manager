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
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasApacheJkRouterFacade;
import org.ow2.jonas.jpaas.sr.facade.api.ISrPaasResourcePaasAgentLink;
import org.ow2.jonas.jpaas.sr.facade.vo.ApacheJkVO;
import org.ow2.jonas.jpaas.sr.facade.vo.PaasAgentVO;
import org.ow2.jonas.jpaas.sr.facade.vo.PaasResourceVO;
import org.ow2.jonas.jpaas.sr.facade.vo.PaasRouterVO;
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
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import java.util.ArrayList;
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
     * SR facade jonas - agent link
     */
    @OSGiResource
    private ISrPaasResourcePaasAgentLink srApacheAgentLinkEjb;

    /**
     * Constructor
     */
    public RouterManagerBean() {
    }

    /**
     * Create a router
     * @param routerName
     * @param paasAgentName
     * @param paasConfigurationName
     * @param listenPort
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
        srApacheJkEjb.createApacheJkRouter(apacheJk);

        // if the link doesn't exist between agent and router, create it
        boolean alreadyExist = false;
        List <PaasResourceVO> paasResources = srApacheAgentLinkEjb.findPaasResourcesByAgent(paasAgentName);
        for (PaasResourceVO paasResourceVO : paasResources) {
            if (paasResourceVO instanceof ApacheJkVO) {
                ApacheJkVO apacheJkResourceVO = (ApacheJkVO) paasResourceVO;
                if (apacheJkResourceVO.getName().equals(routerName)) {
                    logger.debug("Link between router '"  + routerName + "' and agent '" + paasAgentName + "' already exist!");
                    alreadyExist = true;
                    break;
                }
            }
        }
        if (!alreadyExist) {
            srApacheAgentLinkEjb.addPaasResourceAgentLink(routerName, paasAgentName);
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
     * @param routerName
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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        //TODO remove the vhost

        // update state in sr
        srApacheJkEjb.deleteApacheJkRouter(routerName);

        logger.info("Router '" + routerName + "' deleted.");
    }

    /**
     * Start a router
     * @param routerName
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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        //HTTPD should be already started.
        // Ask for a reload
        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "/reload"),
                null,
                null);

        // update state in sr
        apacheJk.setState("RUNNING");
        srApacheJkEjb.updateApacheJkRouter(apacheJk);

        logger.info("Router '" + routerName + "' started.");
    }

    /**
     * Stop a router
     * @param routerName
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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // TODO stop httpd

        // update state in sr
        apacheJk.setState("STOPPED");
        srApacheJkEjb.updateApacheJkRouter(apacheJk);

        logger.info("Router '" + routerName + "' stopped.");
    }

    /**
     * Add a worker
     * @param routerName
     * @param workerName
     * @param targetHost
     * @param targetPortNumber
     * @throws RouterManagerBeanException
     */
    public void createWorker(String routerName, String workerName,
            String targetHost, Integer targetPortNumber)
            throws RouterManagerBeanException {

        logger.info("Router '" + routerName + "' - Create Worker '" +  workerName + "' (host=" + targetHost + ", port=" + targetPortNumber + ")");

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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Add a worker
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", workerName);
        params.add("host", targetHost);
        params.add("port", targetPortNumber.toString());
        params.add("lbFactor", "1");

        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "/worker/" + workerName),
                params,
                null);

        // create the worker in sr
        srApacheJkEjb.addWorker(routerName, workerName, targetHost, targetPortNumber);

        logger.info("Router '" + routerName + "' - Worker '" +  workerName + "' created !");
    }

    /**
     * Remove a worker
     * @param routerName
     * @param workerName
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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Add a worker
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", workerName);

        sendRequestWithReply(
                REST_TYPE.DELETE,
                getUrl(agent.getApiUrl(), "/worker/" + workerName),
                params,
                null);

        // create the worker in sr
        srApacheJkEjb.removeWorker(routerName, workerName);

        logger.info("Router '" + routerName + "' - Worker '" +  workerName + "' removed !");
    }

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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Add a worker
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", workerName);

        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "/worker/" + workerName + "/disable"),
                params,
                null);

        // TODO disable the worker in sr

        logger.info("Router '" + routerName + "' - Worker '" +  workerName + "' disabled !");
    }

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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Add a worker
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", workerName);

        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "/worker/" + workerName + "/enable"),
                params,
                null);

        // TODO disable the worker in sr

        logger.info("Router '" + routerName + "' - Worker '" +  workerName + "' enabled !");
    }

    /**
     * Create a loadbalancer
     * @param routerName
     * @param IbName
     * @param workedList
     * @param mountsPoints
     * @throws RouterManagerBeanException
     */
    public void createLoadBalancer(String routerName, String IbName,
            List<String> workedList, List<String> mountsPoints)
            throws RouterManagerBeanException {

        logger.info("Router '" + routerName + "' - Create Loadbalancer '" +  IbName + "' (wk=" + workedList + ", mt=" + mountsPoints + ")");

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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Add a loadbalancer
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

        String mp = "";
        first = true;
        for(String m : mountsPoints) {
            if (first) {
                first=false;
            } else {
                mp += ",";
            }
            mp += m;
        }

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", IbName);
        params.add("wl", wl);
        params.add("mp", mp);

        sendRequestWithReply(
                REST_TYPE.POST,
                getUrl(agent.getApiUrl(), "/loadbalancer/" + IbName),
                params,
                null);

        // create the worker in sr
        List<WorkerVO> workerVOs = apacheJk.getWorkerList();
        List<WorkerVO> workerVOForThisLoadbalancer = new ArrayList<WorkerVO>();

        // TODO : getName is missing on WorkerVO !!!

        for (WorkerVO wVO : workerVOs) {
            for(String s : workedList) {
                if (false) {
                    //if (wVO.getName().equals(s)) {
                    workerVOForThisLoadbalancer.add(wVO);
                    break;
                }
            }
        }

        srApacheJkEjb.addLoadBalancer(routerName, IbName, workedList, workerVOForThisLoadbalancer);

        logger.info("Router '" + routerName + "' - Loadbalancer '" +  IbName + "' created !");
    }

    /**
     * Remove a loadbalancer
     * @param routerName
     * @param IbName
     * @throws RouterManagerBeanException
     */
    public void removeLoadBalancer(String routerName, String IbName)
            throws RouterManagerBeanException {
        logger.info("Router '" + routerName + "' - Delete Loadbalancer '" +  IbName + "'");

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
        PaasAgentVO agent = srApacheAgentLinkEjb.findAgentByPaasResource(routerName);

        if (agent == null) {
            throw new RouterManagerBeanException("Unable to get the agent for router '" + routerName + "' !");
        }

        // Add a worker
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("name", IbName);

        sendRequestWithReply(
                REST_TYPE.DELETE,
                getUrl(agent.getApiUrl(), "/loadbalancer/" + IbName),
                params,
                null);

        // create the worker in sr
        srApacheJkEjb.removeLoadBalancer(routerName, IbName);

        logger.info("Router '" + routerName + "' - Loadbalancer '" +  IbName + "' removed !");
    }

    /**
     * @param path
     * @return the HTTP URL
     */
    private String getUrl(final String agentApi, final String path) {
        String url = agentApi + "/" + path;
        return url;
    }

    /**
     * Send a REST request and get response
     *
     * @param url
     *            request path
     * @param requestContent
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

        WebResource webResource = client.resource(url);

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
                && status != HTTP_STATUS_NO_CONTENT) {
            throw new RouterManagerBeanException(
                    "Error on JOnAS agent request : " + status);
        }

        ResponseClass r = null;

        if (status != HTTP_STATUS_NO_CONTENT) {
            if (clientResponse.getType() != MediaType.APPLICATION_XML_TYPE) {
                throw new RouterManagerBeanException(
                        "Error on JOnAS agent response, unexpected type : "
                                + clientResponse.getType());
            }

            if (responseClass != null)
                r = clientResponse.getEntity(responseClass);
        }

        client.destroy();

        return r;

    }
}
