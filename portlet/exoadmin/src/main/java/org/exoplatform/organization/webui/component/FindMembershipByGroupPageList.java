/*
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

package org.exoplatform.organization.webui.component;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.ListAccessImpl;
import org.exoplatform.commons.utils.PageListAccess;
import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.MembershipHandler;
import org.exoplatform.services.organization.OrganizationService;

import java.util.List;

/**
 * @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a>
 * @version $Revision$
 */
public class FindMembershipByGroupPageList extends PageListAccess<Membership, String>
{

   public FindMembershipByGroupPageList(String groupId, int pageSize)
   {
      super(groupId, pageSize);
   }

   @Override
   protected ListAccess<Membership> create(String state) throws Exception
   {
      ExoContainer container = PortalContainer.getInstance();
      OrganizationService service = (OrganizationService)container.getComponentInstance(OrganizationService.class);
      MembershipHandler handler = service.getMembershipHandler();
      Group group = service.getGroupHandler().findGroupById(state);
      List<Membership> memberships = (List<Membership>)handler.findMembershipsByGroup(group);
      return new ListAccessImpl<Membership>(Membership.class, memberships);
   }
}
