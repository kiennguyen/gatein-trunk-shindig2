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

package org.exoplatform.web.application.javascript;

import org.exoplatform.commons.utils.Safe;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.RootContainer.PortalContainerPostInitTask;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.gatein.wci.WebAppEvent;
import org.gatein.wci.WebAppLifeCycleEvent;
import org.gatein.wci.WebAppListener;
import org.gatein.wci.impl.DefaultServletContainerFactory;
import org.picocontainer.Startable;

import java.io.InputStream;

import javax.servlet.ServletContext;

/**
 * Created by The eXo Platform SAS
 * Jan 19, 2007
 */

public class JavascriptDeployer implements WebAppListener, Startable
{

   private static final String GATEIN_CONFIG_RESOURCE = "/WEB-INF/gatein-resources.xml";

   /**
    * Logger
    */
   private static final Log LOG = ExoLogger.getLogger(JavascriptDeployer.class);

   /** . */
   private final JavascriptConfigService javascriptService;

   /**
    * The name of the portal container
    */
   private final String portalContainerName;

   public JavascriptDeployer(String portalContainerName, JavascriptConfigService javascriptService)
   {
      this.javascriptService = javascriptService;
      this.portalContainerName = portalContainerName;
   }

   public void start()
   {
      DefaultServletContainerFactory.getInstance().getServletContainer().addWebAppListener(this);
   }

   public void stop()
   {
      DefaultServletContainerFactory.getInstance().getServletContainer().removeWebAppListener(this);
   }

   public void onEvent(WebAppEvent event)
   {
      if (event instanceof WebAppLifeCycleEvent)
      {
         WebAppLifeCycleEvent waEvent = (WebAppLifeCycleEvent)event;
         if (waEvent.getType() == WebAppLifeCycleEvent.ADDED)
         {
            InputStream is = null;
            ServletContext scontext = null;
            try
            {
               scontext = event.getWebApp().getServletContext();
               is = scontext.getResourceAsStream(GATEIN_CONFIG_RESOURCE);
               if (is != null)
               {
                  final PortalContainerPostInitTask task = new PortalContainerPostInitTask()
                  {

                     public void execute(ServletContext scontext, PortalContainer portalContainer)
                     {
                        register(scontext, portalContainer);
                     }
                  };
                  PortalContainer.addInitTask(scontext, task, portalContainerName);
               }
            }
            catch (Exception ex)
            {
               LOG.error("An error occurs while registering 'Javascript in gatein-resources.xml' from the context '"
                  + (scontext == null ? "unknown" : scontext.getServletContextName()) + "'", ex);
            }
            finally
            {
               Safe.close(is);
            }
         }
      }
   }

   private void register(ServletContext scontext, PortalContainer container)
   {
      InputStream is = null;
      try
      {
         is = scontext.getResourceAsStream(GATEIN_CONFIG_RESOURCE);
         JavascriptConfigParser.processConfigResource(is, javascriptService, scontext);
      }
      catch (Exception ex)
      {
         LOG.error("An error occurs while processing 'Javascript in gatein-resources.xml' from the context '"
            + scontext.getServletContextName() + "'", ex);
      }
      finally
      {
         Safe.close(is);
      }
   }
}