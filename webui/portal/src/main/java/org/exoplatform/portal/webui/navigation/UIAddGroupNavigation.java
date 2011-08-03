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

package org.exoplatform.portal.webui.navigation;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.exoplatform.commons.utils.ObjectPageList;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.config.DataStorage;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.mop.SiteKey;
import org.exoplatform.portal.mop.navigation.NavigationContext;
import org.exoplatform.portal.mop.navigation.NavigationService;
import org.exoplatform.portal.mop.navigation.NavigationState;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.portal.webui.workspace.UIMaskWorkspace;
import org.exoplatform.portal.webui.workspace.UIPortalApplication;
import org.exoplatform.portal.webui.workspace.UIWorkingWorkspace;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.web.application.ApplicationMessage;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.config.annotation.ComponentConfig;
import org.exoplatform.webui.config.annotation.ComponentConfigs;
import org.exoplatform.webui.config.annotation.EventConfig;
import org.exoplatform.webui.core.UIContainer;
import org.exoplatform.webui.core.UIPopupWindow;
import org.exoplatform.webui.core.UIRepeater;
import org.exoplatform.webui.core.UIVirtualList;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

/*
 * Created by The eXo Platform SAS
 * Author : tam.nguyen
 *          tamndrok@gmail.com
 * May 28, 2009  
 */
@ComponentConfigs({
   @ComponentConfig(template = "system:/groovy/portal/webui/navigation/UIAddGroupNavigation.gtmpl", events = {
      @EventConfig(listeners = UIMaskWorkspace.CloseActionListener.class),
      @EventConfig(listeners = UIAddGroupNavigation.AddNavigationActionListener.class)}),
   @ComponentConfig(id = "UIAddGroupNavigationGrid", type = UIRepeater.class, template = "system:/groovy/portal/webui/navigation/UIGroupGrid.gtmpl")})
public class UIAddGroupNavigation extends UIContainer
{

   public UIAddGroupNavigation() throws Exception
   {
      UIVirtualList virtualList = addChild(UIVirtualList.class, null, "AddGroupNavList");
      virtualList.setPageSize(6);
      UIRepeater repeater = createUIComponent(UIRepeater.class, "UIAddGroupNavigationGrid", null);
      virtualList.setUIComponent(repeater);
      UIPopupWindow editGroup = addChild(UIPopupWindow.class, null, "EditGroup");
   }

   public void loadGroups() throws Exception
   {

      PortalRequestContext pContext = Util.getPortalRequestContext();
      UserPortalConfigService dataService = getApplicationComponent(UserPortalConfigService.class);
      UserACL userACL = getApplicationComponent(UserACL.class);
      OrganizationService orgService = getApplicationComponent(OrganizationService.class);
      List<String> listGroup = null;
      // get all group that user has permission
      if (userACL.isUserInGroup(userACL.getAdminGroups()) && !userACL.getSuperUser().equals(pContext.getRemoteUser()))
      {
         Collection<?> temp = (List)orgService.getGroupHandler().findGroupsOfUser(pContext.getRemoteUser());
         if (temp != null)
         {
            listGroup = new ArrayList<String>();
            for (Object group : temp)
            {
               Group m = (Group)group;
               String groupId = m.getId().trim();
               listGroup.add(groupId);
            }
         }
      }
      else
      {
         listGroup = dataService.getMakableNavigations(pContext.getRemoteUser(), false);
      }

      if (listGroup == null)
      {
         listGroup = new ArrayList<String>();
      }

      //Filter all groups having navigation
      NavigationService navigationService = getApplicationComponent(NavigationService.class);
      List<String> groupsHavingNavigation = new ArrayList<String>();
      for(String groupName : listGroup)
      {
         NavigationContext navigation = navigationService.loadNavigation(SiteKey.group(groupName));
         if(navigation != null && navigation.getState() != null)
         {
            groupsHavingNavigation.add(groupName);
         }
      }
      listGroup.removeAll(groupsHavingNavigation);

      UIVirtualList virtualList = getChild(UIVirtualList.class);
      virtualList.dataBind(new ObjectPageList<String>(listGroup, listGroup.size()));
   }

   static public class AddNavigationActionListener extends EventListener<UIAddGroupNavigation>
   {
      public void execute(Event<UIAddGroupNavigation> event) throws Exception
      {
         WebuiRequestContext ctx = event.getRequestContext();
         UIAddGroupNavigation uicomp = event.getSource();

         // get navigation id
         String ownerId = event.getRequestContext().getRequestParameter(OBJECTID);
         ownerId = URLDecoder.decode(ownerId);

         UIPortalApplication uiPortalApp = Util.getUIPortal().getAncestorOfType(UIPortalApplication.class);

         // ensure this navigation does not exist
         NavigationService navigationService = uicomp.getApplicationComponent(NavigationService.class);
         NavigationContext navigation = navigationService.loadNavigation(SiteKey.group(ownerId));
         if (navigation != null && navigation.getState() != null)
         {
            uiPortalApp.addMessage(new ApplicationMessage("UIPageNavigationForm.msg.existPageNavigation",
               new String[]{ownerId}));
         }
         else
         {
            // Create portal config of the group when it does not exist
            DataStorage dataService = uicomp.getApplicationComponent(DataStorage.class);
            if (dataService.getPortalConfig("group", ownerId) == null)
            {
               UserPortalConfigService configService = uicomp.getApplicationComponent(UserPortalConfigService.class);
               configService.createGroupSite(ownerId);
            }

            // create navigation for group
            SiteKey key = SiteKey.group(ownerId);
            NavigationContext existing  = navigationService.loadNavigation(key);
            if (existing == null)
            {
               navigationService.saveNavigation(new NavigationContext(key, new NavigationState(0)));
            }
         }

         //Update group navigation list
         ctx.addUIComponentToUpdateByAjax(uicomp);

         UIWorkingWorkspace uiWorkingWS = uiPortalApp.getChild(UIWorkingWorkspace.class);
         uiWorkingWS.updatePortletsByName("GroupNavigationPortlet");
         uiWorkingWS.updatePortletsByName("UserToolbarGroupPortlet");
      }
   }
}
