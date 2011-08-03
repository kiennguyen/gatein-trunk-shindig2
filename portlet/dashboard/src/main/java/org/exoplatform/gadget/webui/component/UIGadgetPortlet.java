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

package org.exoplatform.gadget.webui.component;

import org.exoplatform.application.gadget.Gadget;
import org.exoplatform.application.gadget.GadgetImporter;
import org.exoplatform.application.gadget.GadgetRegistryService;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.portal.webui.application.GadgetUtil;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.application.portlet.PortletRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.core.UIApplication;
import org.exoplatform.webui.core.UIPortletApplication;
import org.exoplatform.webui.core.lifecycle.UIApplicationLifecycle;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.json.JSONException;
import org.json.JSONObject;

import javax.portlet.PortletPreferences;

/**
 * Created by The eXo Platform SARL
 * Author : dang.tung
 *          tungcnw@gmail.com
 * June 27, 2008
 */
@ComponentConfig(lifecycle = UIApplicationLifecycle.class, template = "app:/groovy/gadget/webui/component/UIGadgetPortlet.gtmpl")
public class UIGadgetPortlet extends UIPortletApplication
{
   final static public String LOCAL_STRING = "local://";

   private static final Logger log = LoggerFactory.getLogger(GadgetImporter.class);

   public UIGadgetPortlet() throws Exception
   {
      addChild(UIGadgetViewMode.class, null, null);
   }

   public String getUrl()
   {
      PortletRequestContext pcontext = (PortletRequestContext)WebuiRequestContext.getCurrentInstance();
      PortletPreferences pref = pcontext.getRequest().getPreferences();
      String urlPref = pref.getValue("url", "http://www.google.com/ig/modules/horoscope.xml");
      if (urlPref.startsWith(LOCAL_STRING))
      {
         try
         {
            String gadgetName = urlPref.replaceFirst(LOCAL_STRING, "");
            ExoContainer container = ExoContainerContext.getCurrentContainer();
            GadgetRegistryService gadgetService =
               (GadgetRegistryService)container.getComponentInstanceOfType(GadgetRegistryService.class);
            Gadget gadget = gadgetService.getGadget(gadgetName);
            if (gadget != null)
            {
               return GadgetUtil.reproduceUrl(gadget.getUrl(), gadget.isLocal());
            }
            else 
            {
            	if (log.isWarnEnabled())
            	{
            	   log.warn("The local gadget '" + gadgetName + "' was not found, nothing rendered");
            	}
            	return null;
            }
         }
         catch (Exception e)
         {
            log.warn("Failure retrieving gadget from url!");
         }
      }
      return urlPref;
   }

   public String getMetadata()
   {
      String url = getUrl();
      if (url == null)
      {
         WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
         UIApplication uiApplication = context.getUIApplication();
         uiApplication.addMessage(new ApplicationMessage("UIGadgetPortlet.msg.url-invalid", null));
      }

      return getMetadata(url);
   }
   
   public String getMetadata(String url)
   {
      String metadata_ = GadgetUtil.fetchGagdetMetadata(url);
      try
      {
         JSONObject jsonObj = new JSONObject(metadata_);
         JSONObject obj = jsonObj.getJSONArray("gadgets").getJSONObject(0);
         String token = GadgetUtil.createToken(url, new Long(hashCode()));
         obj.put("secureToken", token);
         metadata_ = jsonObj.toString();
      }
      catch (JSONException e)
      {
         e.printStackTrace(); //To change body of catch statement use File | Settings | File Templates.
      }
      return metadata_;
   }
}
