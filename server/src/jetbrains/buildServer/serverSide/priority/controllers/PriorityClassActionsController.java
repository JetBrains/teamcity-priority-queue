

package jetbrains.buildServer.serverSide.priority.controllers;

import jetbrains.buildServer.controllers.BaseAjaxActionController;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class PriorityClassActionsController extends BaseAjaxActionController {

  public PriorityClassActionsController(@NotNull final PluginDescriptor pluginDescriptor,
                                        @NotNull final WebControllerManager controllerManager) {
    super(controllerManager);
    controllerManager.registerController(pluginDescriptor.getPluginResourcesPath() + "action.html", this);
  }
    
}