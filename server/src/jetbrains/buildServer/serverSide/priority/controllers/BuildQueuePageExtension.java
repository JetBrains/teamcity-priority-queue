

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