/**
 * Copyright (C) 2009 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.portal.pc;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.resources.ResourceBundleService;
import org.gatein.pc.api.PortletInvoker;
import org.gatein.pc.bridge.BridgeInterceptor;
import org.gatein.pc.federation.FederatingPortletInvoker;
import org.gatein.pc.federation.impl.FederatingPortletInvokerService;
import org.gatein.pc.mc.PortletApplicationDeployer;
import org.gatein.pc.portlet.PortletInvokerInterceptor;
import org.gatein.pc.portlet.aspects.CCPPInterceptor;
import org.gatein.pc.portlet.aspects.ConsumerCacheInterceptor;
import org.gatein.pc.portlet.aspects.ContextDispatcherInterceptor;
import org.gatein.pc.portlet.aspects.EventPayloadInterceptor;
import org.gatein.pc.portlet.aspects.PortletCustomizationInterceptor;
import org.gatein.pc.portlet.aspects.ProducerCacheInterceptor;
import org.gatein.pc.portlet.aspects.RequestAttributeConversationInterceptor;
import org.gatein.pc.portlet.aspects.SecureTransportInterceptor;
import org.gatein.pc.portlet.aspects.SessionInvalidatorInterceptor;
import org.gatein.pc.portlet.aspects.ValveInterceptor;
import org.gatein.pc.portlet.container.ContainerPortletDispatcher;
import org.gatein.pc.portlet.container.ContainerPortletInvoker;
import org.gatein.pc.portlet.impl.state.StateManagementPolicyService;
import org.gatein.pc.portlet.impl.state.producer.PortletStatePersistenceManagerService;
import org.gatein.pc.portlet.state.StateConverter;
import org.gatein.pc.portlet.state.producer.ProducerPortletInvoker;
import org.gatein.wci.impl.DefaultServletContainerFactory;
import org.picocontainer.Startable;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class ExoKernelIntegration implements Startable
{

   protected PortletApplicationDeployer portletApplicationRegistry;

   /** Exo Context */
   private final ExoContainer container;

   /** DO NOT REMOVE ME, OTHERWISE YOU'LL BREAK THINGS. */
   private final ResourceBundleService resourceBundleService;

   /**
    * We enforce the dependency with the ResourceBundleService since it must be stared before the
    * <code>portletApplicationRegistry</code>
    *
    * @param context the exo container context
    * @param resourceBundleService the resource bundle service that is here for the sake of creating a dependency
    */
   public ExoKernelIntegration(ExoContainerContext context, ResourceBundleService resourceBundleService)
   {
      this.container = context.getContainer();
      this.resourceBundleService = resourceBundleService;
   }

   public void start()
   {
      // The portlet container invoker used by producer to dispatch to portlets
      ContainerPortletInvoker containerPortletInvoker = new ContainerPortletInvoker();

      // The portlet application deployer
      portletApplicationRegistry = new ExoPortletApplicationDeployer();
      portletApplicationRegistry.setContainerPortletInvoker(containerPortletInvoker);

      //Container Stack
      ContainerPortletDispatcher portletContainerDispatcher = new ContainerPortletDispatcher();
      
      // Federating portlet invoker
      FederatingPortletInvoker federatingPortletInvoker = new FederatingPortletInvokerService();
      
      EventPayloadInterceptor eventPayloadInterceptor = new EventPayloadInterceptor();
      eventPayloadInterceptor.setNext(portletContainerDispatcher);
      RequestAttributeConversationInterceptor requestAttributeConversationInterceptor =
         new RequestAttributeConversationInterceptor();
      requestAttributeConversationInterceptor.setNext(eventPayloadInterceptor);
      CCPPInterceptor ccppInterceptor = new CCPPInterceptor();
      ccppInterceptor.setNext(requestAttributeConversationInterceptor);
      BridgeInterceptor bridgepInterceptor = new BridgeInterceptor();
      bridgepInterceptor.setNext(ccppInterceptor);
      ProducerCacheInterceptor producerCacheInterceptor = new ProducerCacheInterceptor();
      producerCacheInterceptor.setNext(bridgepInterceptor);
      SessionInvalidatorInterceptor sessionInvalidatorInterceptor = new SessionInvalidatorInterceptor();
      sessionInvalidatorInterceptor.setNext(producerCacheInterceptor);
      ContextDispatcherInterceptor contextDispatcherInterceptor = new ContextDispatcherInterceptor();
      contextDispatcherInterceptor.setNext(sessionInvalidatorInterceptor);
      SecureTransportInterceptor secureTransportInterceptor = new SecureTransportInterceptor();
      secureTransportInterceptor.setNext(contextDispatcherInterceptor);
      ValveInterceptor valveInterceptor = new ValveInterceptor();
      valveInterceptor.setPortletApplicationRegistry(portletApplicationRegistry);
      valveInterceptor.setNext(secureTransportInterceptor);

      portletApplicationRegistry.setServletContainerFactory(DefaultServletContainerFactory.getInstance());
      contextDispatcherInterceptor.setServletContainerFactory(DefaultServletContainerFactory.getInstance());

      // The portlet container invoker continued
      containerPortletInvoker.setNext(valveInterceptor);

      // register container invoker so that WSRP can use it, WSRP uses its own ProducerPortletInvoker
      container.registerComponentInstance(ContainerPortletInvoker.class, containerPortletInvoker);

      // The producer persistence manager
      PortletStatePersistenceManagerService producerPersistenceManager = new PortletStatePersistenceManagerService();

      // The producer state management policy
      StateManagementPolicyService producerStateManagementPolicy = new StateManagementPolicyService();
      producerStateManagementPolicy.setPersistLocally(false);

      // The producer state converter
      StateConverter producerStateConverter = new ExoStateConverter();//StateConverterV0();

      // The producer portlet invoker
      ProducerPortletInvoker producerPortletInvoker = new ProducerPortletInvoker();
      producerPortletInvoker.setNext(containerPortletInvoker);
      federatingPortletInvoker.registerInvoker(PortletInvoker.LOCAL_PORTLET_INVOKER_ID, producerPortletInvoker);

      producerPortletInvoker.setPersistenceManager(producerPersistenceManager);
      producerPortletInvoker.setStateManagementPolicy(producerStateManagementPolicy);
      producerPortletInvoker.setStateConverter(producerStateConverter);

      // The consumer portlet invoker
      PortletCustomizationInterceptor portletCustomizationInterceptor = new PortletCustomizationInterceptor();
      portletCustomizationInterceptor.setNext(federatingPortletInvoker);
      ConsumerCacheInterceptor consumerCacheInterceptor = new ConsumerCacheInterceptor();
      consumerCacheInterceptor.setNext(portletCustomizationInterceptor);
      PortletInvokerInterceptor consumerPortletInvoker = new PortletInvokerInterceptor();
      consumerPortletInvoker.setNext(consumerCacheInterceptor);

      // register federating portlet and consumerPortletInvoker invoker with container
      container.registerComponentInstance(PortletInvoker.class, consumerPortletInvoker);
      container.registerComponentInstance(FederatingPortletInvoker.class, federatingPortletInvoker);

      portletApplicationRegistry.start();
   }

   public void stop()
   {
      if (portletApplicationRegistry != null)
      {
         portletApplicationRegistry.stop();
      }
   }

   public PortletApplicationDeployer getPortletApplicationRegistry()
   {
      return portletApplicationRegistry;
   }
}
