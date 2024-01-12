

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