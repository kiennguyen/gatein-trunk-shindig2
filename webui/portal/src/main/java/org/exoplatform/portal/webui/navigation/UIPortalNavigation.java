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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.portal.mop.SiteType;
import org.exoplatform.portal.mop.Visibility;
import org.exoplatform.portal.mop.navigation.NodeChange;
import org.exoplatform.portal.mop.navigation.NodeChangeQueue;
import org.exoplatform.portal.mop.navigation.Scope;
import org.exoplatform.portal.mop.user.UserNavigation;
import org.exoplatform.portal.mop.user.UserNode;
import org.exoplatform.portal.mop.user.UserNodeFilterConfig;
import org.exoplatform.portal.mop.user.UserPortal;
import org.exoplatform.portal.webui.portal.PageNodeEvent;
import org.exoplatform.portal.webui.portal.UIPortal;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.webui.application.WebuiRequestContext;
import org.exoplatform.webui.core.UIComponent;
import org.exoplatform.webui.event.Event;
import org.exoplatform.webui.event.EventListener;

/**
 * Created by The eXo Platform SARL Author : Dang Van Minh minhdv81@yahoo.com
 * Jul 12, 2006
 */
public class UIPortalNavigation extends UIComponent
{
   private boolean useAJAX = true;

   private boolean showUserNavigation = true;

   private TreeNode treeNode_;

   private String cssClassName = "";

   private String template;

   private final UserNodeFilterConfig NAVIGATION_FILTER_CONFIG;
   
   private Scope navigationScope;
   
   private Log log = ExoLogger.getExoLogger(UIPortalNavigation.class);

   public UIPortalNavigation()
   {
      UserNodeFilterConfig.Builder filterConfigBuilder = UserNodeFilterConfig.builder();
      filterConfigBuilder.withAuthorizationCheck().withVisibility(Visibility.DISPLAYED, Visibility.TEMPORAL);
      filterConfigBuilder.withTemporalCheck();
      NAVIGATION_FILTER_CONFIG = filterConfigBuilder.build();
   }

   @Override
   public String getTemplate()
   {
      return template != null ? template : super.getTemplate();
   }

   public void setTemplate(String template)
   {
      this.template = template;
   }

   public UIComponent getViewModeUIComponent()
   {
      return null;
   }

   public void setUseAjax(boolean bl)
   {
      useAJAX = bl;
   }

   public boolean isUseAjax()
   {
      return useAJAX;
   }

   public boolean isShowUserNavigation()
   {
      return showUserNavigation;
   }

   public void setShowUserNavigation(boolean showUserNavigation)
   {
      this.showUserNavigation = showUserNavigation;
   }

   public void setCssClassName(String cssClassName)
   {
      this.cssClassName = cssClassName;
   }

   public String getCssClassName()
   {
      return cssClassName;
   }

   public List<UserNode> getNavigations() throws Exception
   {
      WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
      List<UserNode> nodes = new ArrayList<UserNode>();
      if (context.getRemoteUser() != null)
      {
         UserNode currRootNode = getCurrentNavigation();
         if (currRootNode != null)
         {
            nodes.add(currRootNode);  
         }
      }
      else
      {
         UserPortal userPortal = Util.getUIPortalApplication().getUserPortalConfig().getUserPortal();
         List<UserNavigation> navigations = userPortal.getNavigations();
         for (UserNavigation userNav : navigations)
         {
            if (!showUserNavigation && userNav.getKey().getType().equals(SiteType.USER))
            {
               continue;
            }

            UserNode rootNode = userPortal.getNode(userNav, navigationScope, NAVIGATION_FILTER_CONFIG, null);
            if (rootNode != null)
            {
               nodes.add(rootNode);
            }
         }
      }
      return nodes;
   }

   public void loadTreeNodes() throws Exception
   {
      treeNode_ = new TreeNode();

      UserPortal userPortal = Util.getUIPortalApplication().getUserPortalConfig().getUserPortal();
      List<UserNavigation> listNavigations = userPortal.getNavigations();

      List<UserNode> childNodes = new LinkedList<UserNode>();
      for (UserNavigation nav : rearrangeNavigations(listNavigations))
      {
         if (!showUserNavigation && nav.getKey().getTypeName().equals(PortalConfig.USER_TYPE))
         {
            continue;
         }
         try 
         {
            UserNode rootNode = userPortal.getNode(nav, navigationScope, NAVIGATION_FILTER_CONFIG, null);
            if (rootNode != null)
            {
               childNodes.addAll(rootNode.getChildren());
            }            
         }
         catch (Exception ex)
         {            
            log.error(ex.getMessage(), ex);
         }
      }
      treeNode_.setChildren(childNodes);
   }
   
   public UserNode resolvePath(String path) throws Exception
   {
      WebuiRequestContext context = WebuiRequestContext.getCurrentInstance();
      UserPortal userPortal = Util.getUIPortalApplication().getUserPortalConfig().getUserPortal();
      
      UserNode node;
      if (context.getRemoteUser() != null)
      {
         node = userPortal.resolvePath(Util.getUIPortal().getUserNavigation(), NAVIGATION_FILTER_CONFIG, path);
      }
      else
      {
         node = userPortal.resolvePath(NAVIGATION_FILTER_CONFIG, path);
      }
      
      if (node != null && !node.getURI().equals(path))
      {
         //Node has been deleted
         return null;
      }
      return updateNode(node);
   }
   
   public UserNode updateNode(UserNode node) throws Exception
   {
      if (node == null)
      {
         return null;
      }
      UserPortal userPortal = Util.getUIPortalApplication().getUserPortalConfig().getUserPortal();
      NodeChangeQueue<UserNode> queue = new NodeChangeQueue<UserNode>();
      userPortal.updateNode(node, navigationScope, queue);
      for (NodeChange<UserNode> change : queue)
      {
         if (change instanceof NodeChange.Removed)
         {
            UserNode deletedNode = ((NodeChange.Removed<UserNode>)change).getTarget();
            if (hasRelationship(deletedNode, node))
            {
               //Node has been deleted
               return null;
            }
         }
      }
      return node;      
   }
      
   private boolean hasRelationship(UserNode parent, UserNode userNode)
   {
      if (parent.getId().equals(userNode.getId()))
      {
         return true;
      }
      for (UserNode child : parent.getChildren())
      {
         if (hasRelationship(child, userNode))
         {
            return true;
         }
      }
      return false;
   }
   
   /**
    * 
    * @param listNavigation
    * @return
    */
   private List<UserNavigation> rearrangeNavigations(List<UserNavigation> listNavigation)
   {
      List<UserNavigation> returnNavs = new ArrayList<UserNavigation>();

      List<UserNavigation> portalNavs = new ArrayList<UserNavigation>();
      List<UserNavigation> groupNavs = new ArrayList<UserNavigation>();
      List<UserNavigation> userNavs = new ArrayList<UserNavigation>();

      for (UserNavigation nav : listNavigation)
      {
         String ownerType = nav.getKey().getTypeName();
         if (PortalConfig.PORTAL_TYPE.equals(ownerType))
         {
            portalNavs.add(nav);
         }
         else if (PortalConfig.GROUP_TYPE.equals(ownerType))
         {
            groupNavs.add(nav);
         }
         else if (PortalConfig.USER_TYPE.equals(ownerType))
         {
            userNavs.add(nav);
         }
      }

      returnNavs.addAll(portalNavs);
      returnNavs.addAll(groupNavs);
      returnNavs.addAll(userNavs);

      return returnNavs;
   }

   public TreeNode getTreeNodes()
   {
      return treeNode_;
   }

   public UserNode getSelectedNode() throws Exception
   {
      UIPortal uiPortal = Util.getUIPortal();
      if (uiPortal != null)
      {
         return uiPortal.getSelectedUserNode();
      }
      return null;
   }

   private UserNode getCurrentNavigation() throws Exception
   {
      UserPortal userPortal = Util.getUIPortalApplication().getUserPortalConfig().getUserPortal();
      UserNavigation userNavigation = Util.getUIPortal().getUserNavigation();
      try 
      {
         UserNode rootNode = userPortal.getNode(userNavigation, navigationScope, NAVIGATION_FILTER_CONFIG, null);      
         return rootNode;
      } 
      catch (Exception ex)
      {
         log.error("Navigation has been deleted");
      }
      return null;
   }
   
   public void setScope(Scope scope)
   {
      this.navigationScope = scope;
   }   
   
   static public class SelectNodeActionListener extends EventListener<UIPortalNavigation>
   {
      public void execute(Event<UIPortalNavigation> event) throws Exception
      {
         UIPortal uiPortal = Util.getUIPortal();
         String treePath = event.getRequestContext().getRequestParameter(OBJECTID);

         TreeNode selectedNode = event.getSource().getTreeNodes().findNodes(treePath);
         //There're may be interuption between browser and server
         if (selectedNode == null)
         {
            event.getRequestContext().addUIComponentToUpdateByAjax(event.getSource());
            return;
         }
         
         PageNodeEvent<UIPortal> pnevent;
         pnevent = new PageNodeEvent<UIPortal>(uiPortal, PageNodeEvent.CHANGE_PAGE_NODE, selectedNode.getNode().getURI());
         uiPortal.broadcast(pnevent, Event.Phase.PROCESS);
      }
   }

   //Now we use serveSource method to expand a node
/*   
   static public class ExpandNodeActionListener extends EventListener<UIPortalNavigation>
   {
      public void execute(Event<UIPortalNavigation> event) throws Exception
      {
         String treePath = event.getRequestContext().getRequestParameter(OBJECTID);
                                                        
         TreeNode treeNode = event.getSource().getTreeNodes();
         TreeNode expandTree = treeNode.findNodes(treePath);
         //There're may be interuption between browser and server
         if (expandTree == null)
         {
            event.getRequestContext().addUIComponentToUpdateByAjax(event.getSource());
            return;
         }

         UserPortal userPortal = Util.getUIPortalApplication().getUserPortalConfig().getUserPortal();

         UserNode node = expandTree.getNode();
         userPortal.updateNode(node, event.getSource().navigationScope, null);
         if (node == null)
         {
            event.getSource().loadTreeNodes();
            event.getRequestContext().getUIApplication().addMessage(new
               ApplicationMessage("UIPortalNavigation.msg.staleData", null, ApplicationMessage.WARNING));
         }
         else
         {
            node.filter(event.getSource().NAVIGATION_FILTER_CONFIG);
            expandTree.setChildren(node.getChildren());
            expandTree.setExpanded(true);
         }
                               
         event.getRequestContext().addUIComponentToUpdateByAjax(event.getSource());
      }
   }
*/
   
   static public class CollapseNodeActionListener extends EventListener<UIPortalNavigation>
   {
      public void execute(Event<UIPortalNavigation> event) throws Exception
      {
         // get URI
         String treePath = event.getRequestContext().getRequestParameter(OBJECTID);

         UIPortalNavigation uiNavigation = event.getSource();
         TreeNode rootNode = uiNavigation.getTreeNodes();
         
         TreeNode collapseTree = rootNode.findNodes(treePath);
         if (collapseTree != null)
         {
            collapseTree.setExpanded(false);
         }         
         
         Util.getPortalRequestContext().setResponseComplete(true);
      }
   }

   static public class CollapseAllNodeActionListener extends EventListener<UIPortalNavigation>
   {
      public void execute(Event<UIPortalNavigation> event) throws Exception
      {
         UIPortalNavigation uiNavigation = event.getSource();
         uiNavigation.loadTreeNodes();

         event.getRequestContext().addUIComponentToUpdateByAjax(uiNavigation);
      }
   }

   //Expand all will not be allowed - The nodes are lazy loaded now
/*   
   static public class ExpandAllNodeActionListener extends EventListener<UIPortalNavigation>
   {
      public void execute(Event<UIPortalNavigation> event) throws Exception
      {
         UIPortalNavigation uiNavigation = event.getSource();
         // reload TreeNodes
         uiNavigation.loadTreeNodes();
         TreeNode treeNode = uiNavigation.getTreeNodes();

         expandAllNode(treeNode);

         event.getRequestContext().addUIComponentToUpdateByAjax(uiNavigation);
      }

      public void expandAllNode(TreeNode treeNode) throws Exception
      {

         if (treeNode.getChildren().size() > 0)
         {
            for (TreeNode child : treeNode.getChildren())
            {
//               PageNode expandNode = child.getNode();
//               PageNavigation selectNav = child.getNavigation();
//
//               // set node to child tree
//               if (expandNode.getChildren().size() > 0)
//               {
//                  child.setChildren(expandNode.getChildren(), selectNav);
//               }

               // expand child tree
               expandAllNode(child);
            }
         }
      }
   }
*/

}
