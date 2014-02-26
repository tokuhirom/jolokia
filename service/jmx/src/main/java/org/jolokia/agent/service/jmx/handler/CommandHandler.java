package org.jolokia.agent.service.jmx.handler;

import java.io.IOException;

import javax.management.*;

import org.jolokia.core.util.jmx.MBeanServerExecutor;
import org.jolokia.core.backend.NotChangedException;
import org.jolokia.core.config.ConfigKey;
import org.jolokia.core.request.JolokiaRequest;
import org.jolokia.core.service.JolokiaContext;
import org.jolokia.core.util.RequestType;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * A handler for dealing with a certain Jolokia command
 *
 * @author roland
 * @since Jun 12, 2009
 */
public abstract class CommandHandler<R extends JolokiaRequest> {

    // Restrictor for restricting operations
    protected final JolokiaContext context;

    /**
     * Construct a command for the given Jolokia context
     *
     * @param pContext context within this command works
     */
    protected CommandHandler(JolokiaContext pContext) {
        context = pContext;
    }


    /**
     * The type of request which can be served by this handler
     * @return the request typ of this handler
     */
    public abstract RequestType getType();

    /**
     * Override this if you want all servers as list in the argument, e.g.
     * to query each server on your own. By default, dispatching of the servers
     * are done for you
     *
     *
     * @param pRequest request to decide on whether to handle all request at once
     * @return whether you want to have
     * {@link #doHandleRequest(MBeanServerConnection, JolokiaRequest)}
     * (<code>false</code>) or
     * {@link #doHandleRequest(MBeanServerExecutor, JolokiaRequest, Object)} (<code>true</code>) called.
     */
    public boolean handleAllServersAtOnce(R pRequest) {
        return false;
    }

    /**
     * Handle a request for a single server and throw an
     * {@link javax.management.InstanceNotFoundException}
     * if the request cannot be handle by the provided server.
     * Does a check for restrictions as well
     *
     *
     * @param pServer server to try
     * @param pRequest request to process
     * @return the object result from the request
     *
     * @throws InstanceNotFoundException if the provided server cant handle the request
     * @throws AttributeNotFoundException
     * @throws ReflectionException
     * @throws MBeanException
     * @throws java.io.IOException
     */
    public Object handleRequest(MBeanServerConnection pServer, R pRequest)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        checkForRestriction(pRequest);
        checkHttpMethod(pRequest);
        return doHandleRequest(pServer, pRequest);
    }

    /**
     * Check whether there is a restriction on the type to apply. This method should be overwritten
     * by specific handlers if they support a more sophisticated check than only for the type
     *
     * @param pRequest request to check
     */
    protected abstract void checkForRestriction(R pRequest);

    /**
     * Check whether a command of the given type is allowed
     */
    protected void checkType() {
        if (!context.isTypeAllowed(getType())) {
            throw new SecurityException("Command type " +
                    getType() + " not allowed due to policy used");
        }
    }


    /**
     * Check whether the HTTP method with which the request was sent is allowed according to the policy
     * installed
     *
     * @param pRequest request to check
     */
    private void checkHttpMethod(R pRequest) {
        if (!context.isHttpMethodAllowed(pRequest.getHttpMethod())) {
            throw new SecurityException("HTTP method " + pRequest.getHttpMethod().getMethod() +
                    " is not allowed according to the installed security policy");
        }
    }

    /**
     * Abstract method to be subclassed by a concrete handler for performing the
     * request.
     *
     *
     * @param server server to try
     * @param request request to process
     * @return the object result from the request
     *
     * @throws InstanceNotFoundException
     * @throws AttributeNotFoundException
     * @throws ReflectionException
     * @throws MBeanException
     * @throws IOException
     */
    protected abstract Object doHandleRequest(MBeanServerConnection server, R request)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException;

    /**
     * Override this if you want to have all servers at once for processing the request
     * (like need for merging info as for a <code>list</code> command). This method
     * is only called when {@link #handleAllServersAtOnce(JolokiaRequest)} returns <code>true</code>
     *
     * @param pServerManager server manager holding all MBeans servers detected
     * @param request request to process
     * @param pPreviousResult a previous result which for merging requests can be used to merge files
     * @return the object found
     * @throws IOException
     * @throws AttributeNotFoundException
     * @throws InstanceNotFoundException
     * @throws MBeanException
     * @throws ReflectionException
     */
    public Object handleRequest(MBeanServerExecutor pServerManager, R request, Object pPreviousResult)
            throws ReflectionException, InstanceNotFoundException, MBeanException, AttributeNotFoundException, IOException, NotChangedException {
        checkForRestriction(request);
        return doHandleRequest(pServerManager,request, pPreviousResult);
    }

    /**
     * Default implementation fo handling a request for multiple servers at once. A subclass, which returns,
     * <code>true</code> on {@link #handleAllServersAtOnce(JolokiaRequest)}, needs to override this method.
     *
     * @param serverManager all MBean servers found in this JVM
     * @param request the original request
     * @param pPreviousResult a previous result which for merging requests can be used to merge files
     * @return the result of the the request.
     * @throws IOException
     * @throws AttributeNotFoundException
     * @throws InstanceNotFoundException
     * @throws MBeanException
     * @throws ReflectionException
     */
    protected Object doHandleRequest(MBeanServerExecutor serverManager, R request, Object pPreviousResult)
                throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        return null;
    }

    /**
     * Lifecycle method called when agent goes down. Should be overridden by
     * a handler if required.
     */
    public void destroy() throws JMException {

    }

    /**
     * Check, whether the set of MBeans for any managed MBeanServer has been change since the timestamp
     * provided in the given request
     * @param pServerManager manager for all MBeanServers
     * @param pRequest the request from where to fetch the timestamp
     * @throws NotChangedException if there has been no REGISTER/UNREGISTER notifications in the meantime
     */
    protected void checkForModifiedSince(MBeanServerExecutor pServerManager, JolokiaRequest pRequest)
            throws NotChangedException {
        int ifModifiedSince = pRequest.getParameterAsInt(ConfigKey.IF_MODIFIED_SINCE);
        if (!pServerManager.hasMBeansListChangedSince(ifModifiedSince)) {
            throw new NotChangedException(pRequest);
        }
    }
}
