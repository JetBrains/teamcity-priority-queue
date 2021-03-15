/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.serverSide.priority.controllers;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.serverSide.auth.Permission;
import jetbrains.buildServer.serverSide.auth.SecurityContext;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.web.openapi.PagePlaces;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.SimplePageExtension;
import jetbrains.buildServer.web.util.WebUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class BuildQueuePageExtension extends SimplePageExtension {

  private final SecurityContext mySecurityContext;

  public BuildQueuePageExtension(@NotNull final PagePlaces pagePlaces,
                                 @NotNull final PluginDescriptor pluginDescriptor,
                                 @NotNull final SecurityContext securityContext) {
    super(pagePlaces, PlaceId.BEFORE_CONTENT, pluginDescriptor.getPluginName(), "queuePageExtension.jsp");
    mySecurityContext = securityContext;
    register();
    new SimplePageExtension(pagePlaces, new PlaceId("SAKURA_QUEUE_ACTIONS"), pluginDescriptor.getPluginName(), "sakuraQueuePageExtension.jsp") {
      @Override
      public boolean isAvailable(@NotNull final HttpServletRequest request) {
        return isEnoughPermissions();
      }
    }.register();
  }

  private boolean isEnoughPermissions() {
    SUser authority = (SUser) mySecurityContext.getAuthorityHolder().getAssociatedUser();
    return authority != null && authority.isPermissionGrantedGlobally(Permission.CHANGE_SERVER_SETTINGS);
  }

  @Override
  public boolean isAvailable(@NotNull HttpServletRequest request) {
    return WebUtil.getPathWithoutAuthenticationType(WebUtil.getPathWithoutContext(request, WebUtil.getOriginalRequestUrl(request))).startsWith("/queue.html")
            && isEnoughPermissions();
  }

  @Override
  public void fillModel(@NotNull Map<String, Object> model, @NotNull HttpServletRequest request) {

  }
}
