

package jetbrains.buildServer.serverSide.priority.controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BasePopupController;
import jetbrains.buildServer.serverSide.BuildTypeComparator;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.PriorityClass;
import jetbrains.buildServer.serverSide.priority.PriorityClassManager;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;

/**
 * @author dmitry.neverov
 */
public class PriorityClassConfigurationsPopupController extends BasePopupController {
  private final PriorityClassManager myPriorityClassManager;
  private final PluginDescriptor myPluginDescriptor;
  private static final int MAX_CONFIGURATIONS_TO_SHOW = 30;

  public PriorityClassConfigurationsPopupController(@NotNull final PluginDescriptor pluginDescriptor,
                                                    @NotNull final WebControllerManager controllerManager,
                                                    @NotNull final PriorityClassManager priorityClassManager) {
    myPluginDescriptor = pluginDescriptor;
    myPriorityClassManager = priorityClassManager;
    controllerManager.registerController(myPluginDescriptor.getPluginResourcesPath() + "priorityClassConfigurationsPopup.html", this);
  }

  @Override
  protected ModelAndView processRequest(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
    String priorityClassId = request.getParameter("priorityClassId");
    if (priorityClassId != null) {
      PriorityClass priorityClass = myPriorityClassManager.findPriorityClassById(priorityClassId);
      if (priorityClass != null) {
        ModelAndView mv = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("priorityClassConfigurationsPopup.jsp"));
        List<SBuildType> buildTypes = new ArrayList<SBuildType>(priorityClass.getBuildTypes());
        Collections.sort(buildTypes, new PriorityQueueBuildTypeComparator());
        List<SBuildType> buildTypesToShow = buildTypes.subList(0, Math.min(buildTypes.size(), MAX_CONFIGURATIONS_TO_SHOW));
        mv.getModel().put("priorityClass", priorityClass);
        mv.getModel().put("buildTypeList", buildTypesToShow);
        return mv;
      }
    }
    return simpleView("Priority Class does not exist");
  }
}