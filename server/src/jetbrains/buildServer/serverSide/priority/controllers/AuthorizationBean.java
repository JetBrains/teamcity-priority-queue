/*
 * Copyright 2000-2019 JetBrains s.r.o.
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

import javax.servlet.http.HttpServletRequest;
import jetbrains.buildServer.controllers.AuthorizationInterceptor;
import jetbrains.buildServer.controllers.RequestPermissionsChecker;
import jetbrains.buildServer.controllers.RequestPermissionsCheckerEx;
import jetbrains.buildServer.serverSide.SecurityContextEx;
import jetbrains.buildServer.serverSide.auth.AccessDeniedException;
import jetbrains.buildServer.serverSide.auth.Permission;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class AuthorizationBean {

  public AuthorizationBean(@NotNull AuthorizationInterceptor authInterceptor) {
    final RequestPermissionsChecker permissionsChecker = new RequestPermissionsCheckerEx() {
      public void checkPermissions(@NotNull SecurityContextEx securityContext, @NotNull HttpServletRequest request) throws AccessDeniedException {
        securityContext.getAccessChecker().checkHasGlobalPermission(Permission.CHANGE_SERVER_SETTINGS);
      }
    };

    String[] paths = new String[] {
            "/plugins/priority-queue/attachConfigurationsDialog.html",
            "/plugins/priority-queue/deletePriorityClassDialog.html",
            "/plugins/priority-queue/priorityClassList.html",
            "/plugins/priority-queue/action.html",
            "/plugins/priority-queue/priorityClassConfigurationsPopup.html",
            "/plugins/priority-queue/priorityClassList.html",
            "/plugins/priority-queue/createPriorityClass.html",
            "/plugins/priority-queue/editPriorityClass.html"};
    for (String path : paths) {
      authInterceptor.addPathBasedPermissionsChecker(path, permissionsChecker);
    }
  }

}
